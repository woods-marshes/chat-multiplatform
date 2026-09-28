package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.data.model.toUiModel
import com.github.woodsmarshes.chat.core.data.paging.MessageRemoteMediator
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.Normal
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class OfflineFirstMessageRepositoryImpl(
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val participantDao: ParticipantDao,
    private val messageApi: RealtimeApi,
    private val conversationApi: ConversationApi,
    private val conversationDao: ConversationDao,
    private val userSettingDataSource: UserSettingDataSource,
    private val scope: CoroutineScope
) : MessageRepository, KoinComponent {
    private val log = KotlinLogging.logger {}

    private companion object {
        /** Wait this long after sending before a retry is considered. */
        val RETRY_DELAY = 10.seconds

        /** Typing entries older than this are dropped defensively. */
        const val TYPING_ENTRY_TTL_MS = 60_000L

        /** Periodic sweep interval for the outbox. */
        val RETRY_POLL_INTERVAL = 15.seconds

        /** Give up on unacked messages older than this. */
        val MESSAGE_TTL = 24.hours
    }
    val ownUser = userSettingDataSource.user

    private var messageConsumptionJob: Job? = null
    private var outboxRetryJob: Job? = null

    /**
     * Highest gap-free realtime seq seen per conversation. A jump in the
     * stream means events were lost in flight (socket hiccup or a server-side
     * drop); the missing span is then repaired from REST history.
     */
    private val seqMutex = Mutex()
    private val deliveredSeqs = mutableMapOf<Uuid, Long>()
    private val gapRepairs = mutableMapOf<Uuid, Job>()

    init {
        startMessageConsumption()
        startOutboxRetryLoop()
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int
    ): Flow<PagingData<MessageUiModel>> {
        log.info { "[getMessages] called, conversationId=$conversationId ownUserId=$ownUserId" }
        return Pager(
            config = PagingConfig(
                pageSize = limit,
                enablePlaceholders = false,
            ),
            remoteMediator = get<MessageRemoteMediator> {
                parametersOf(ownUserId, conversationId, isGroup)
            },
            pagingSourceFactory = {
                messageDao.pagingSource(
                    conversationId = conversationId,
                    pageSize = limit.toLong()
                )
            }
        ).flow
            .onEach {
                log.info { "[getMessages] Pager emitted new PagingData" }
            }
            .map {
                log.info { "[getMessages] mapping PagingData to UiModel" }
                it.map(KeyedMessagesWithRelations::toUiModel)
            }
    }

    override suspend fun sendMessage(
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid?,
    ): Result<Unit, MessageError> {
        return try {
            val currentUser = ownUser.firstOrNull()
                ?: return Err(MessageError.PermissionDenied).also {
                    log.warn { "[sendMessage] PermissionDenied: ownUser is null" }
                }

            val requestId = Uuid.generateV7()
            log.info { "[sendMessage] sending, requestId=$requestId conversationId=$conversationId" }

            // The socket send is a silent no-op when disconnected; without this
            // guard the message would sit in SENDING forever.
            if (messageApi.connectionState.value !is ConnectionState.Connected) {
                log.warn { "[sendMessage] socket not connected, persisting as FAILED" }
                messageDao.insertMessage(
                    message = MessageEntity(
                        id = requestId,
                        conversation_id = conversationId,
                        user_id = currentUser.id,
                        category = when (content) {
                            is System -> MessageCategory.SYSTEM
                            is Normal -> MessageCategory.NORMAL
                        },
                        render_type = determineRenderType(content),
                        content = content,
                        reply_to_message_id = replyToMessageId,
                        created_at = Clock.System.now(),
                        revoked_at = null,
                        local_send_status = MessageStatus.FAILED
                    )
                )
                return Err(MessageError.OperationFailed)
            }

            val request = MessageRequest.Send(
                senderId = currentUser.id,
                conversationId = conversationId,
                content = content,
                requestId = requestId.toString(),
                replyToMessageId = replyToMessageId,
            )

            messageApi.send(request)
            log.info { "[sendMessage] ws send done, inserting local msg id=$requestId" }

            messageDao.transaction {
                messageDao.insertMessage(
                    message = MessageEntity(
                        id = requestId,
                        conversation_id = conversationId,
                        user_id = currentUser.id,
                        category = when (content) {
                            is System -> MessageCategory.SYSTEM
                            is Normal -> MessageCategory.NORMAL
                        },
                        render_type = determineRenderType(content),
                        content = content,
                        reply_to_message_id = replyToMessageId,
                        created_at = Clock.System.now(),
                        revoked_at = null,
                        local_send_status = MessageStatus.SENDING
                    )
                )
                conversationDao.updateLastMessage(
                    id = conversationId,
                    lastMessageId = requestId,
                    updatedAt = Clock.System.now()
                )
            }
//            _invalidationEvents.tryEmit(Unit)
            log.info { "[sendMessage] local insert done, requestId=$requestId" }
            Ok(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[sendMessage] exception: ${e.message}" }
            Err(MessageError.Unknown(e.message))
        }
    }

    override suspend fun revokeMessage(messageId: Uuid) {
        try {
            val currentUser = ownUser.firstOrNull() ?: return

            val request = MessageRequest.Withdraw(
                senderId = currentUser.id,
                messageId = messageId
            )

            messageApi.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send realtime request" }
        }
    }

    override suspend fun markAsRead(conversationId: Uuid, messageId: Uuid) {
        try {
            val currentUser = ownUser.firstOrNull() ?: return

            val request = MessageRequest.Read(
                senderId = currentUser.id,
                conversationId = conversationId,
                messageId = messageId
            )

            messageApi.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send realtime request" }
        }
    }

    // conversationId -> (userId -> last typing-event epoch millis)
    private val _typingUsers = MutableStateFlow<Map<Uuid, Map<Uuid, Long>>>(emptyMap())

    override fun getTypingUsersFlow(conversationId: Uuid): Flow<Map<Uuid, Long>> =
        _typingUsers.map { it[conversationId].orEmpty() }

    override suspend fun sendTyping(conversationId: Uuid, isTyping: Boolean) {
        try {
            val currentUser = ownUser.firstOrNull() ?: return

            val request = MessageRequest.Typing(
                senderId = currentUser.id,
                conversationId = conversationId,
                isTyping = isTyping,
            )

            messageApi.send(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send typing state" }
        }
    }

    private fun handleUserTyping(event: MessageEventResponse.UserTyping) {
        val now = Clock.System.now().toEpochMilliseconds()
        _typingUsers.update { all ->
            // Drop stale entries in case a "stopped typing" event was lost.
            val perConversation = all[event.conversationId].orEmpty()
                .filterValues { now - it < TYPING_ENTRY_TTL_MS }
                .toMutableMap()
            if (event.isTyping) {
                perConversation[event.userId] = now
            } else {
                perConversation.remove(event.userId)
            }
            if (perConversation.isEmpty()) {
                all - event.conversationId
            } else {
                all + (event.conversationId to perConversation)
            }
        }
    }

    private fun startMessageConsumption() {
        messageConsumptionJob?.cancel()
        messageConsumptionJob = scope.launch {
            log.info { "[ws-consume] starting event consumption" }
            try {
                messageApi.events.collectLatest { event ->
                    log.info { "[ws-consume] received event: ${event::class.simpleName}" }
                    when (event) {
                        is MessageEventResponse.Received -> {
                            log.info { "[ws-consume] handling Received: requestId=${event.requestId} senderId=${event.senderId} msgId=${event.message.id}" }
                            handleReceivedMessage(event)
                        }
                        is MessageEventResponse.Withdrawn -> {
                            handleWithdrawnMessage(event)
                        }
                        is MessageEventResponse.Read -> {
                            handleReadMessage(event)
                        }
                        is MessageEventResponse.UserTyping -> {
                            handleUserTyping(event)
                        }
                        else -> {
                            log.debug { "[ws-consume] unknown event: ${event::class.simpleName}" }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "[ws-consume] error in event consumption: ${e.message}" }
            }
        }
    }

    private suspend fun handleReceivedMessage(event: MessageEventResponse.Received) {
        try {
            val message = event.message
            val currentUser = ownUser.firstOrNull()

            val isOwnMessage = currentUser != null && event.senderId == currentUser.id
            log.info { "[handleReceived] isOwnMessage=$isOwnMessage requestId=${event.requestId} serverMsgId=${message.id}" }

            trackDeliverySeq(message)
            persistServerMessage(message, ownRequestId = event.requestId.takeIf { isOwnMessage })
            log.info { "[handleReceived] db transaction done" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[handleReceived] error: ${e.message}" }
        }
    }

    private suspend fun trackDeliverySeq(message: Message) {
        val seq = message.seq ?: return
        val conversationId = message.conversationId
        var gapDetected = false
        seqMutex.withLock {
            val last = deliveredSeqs[conversationId]
            if (last == null || seq > last) {
                if (last != null && seq > last + 1) {
                    gapDetected = true
                }
                deliveredSeqs[conversationId] = seq
            }
            // An older seq re-arriving after a repair is a duplicate.
        }
        if (gapDetected) {
            scheduleGapRepair(conversationId)
        }
    }

    /** Only the single event-consumption coroutine schedules repairs. */
    private fun scheduleGapRepair(conversationId: Uuid) {
        if (gapRepairs[conversationId]?.isActive == true) return
        gapRepairs[conversationId] = scope.launch {
            try {
                val fetched = syncConversationFromServer(conversationId)
                val highest = fetched.maxOfOrNull { it.seq ?: 0L }
                if (highest != null) {
                    seqMutex.withLock {
                        deliveredSeqs[conversationId] = maxOf(deliveredSeqs[conversationId] ?: 0L, highest)
                    }
                }
                log.info { "[gap-repair] repaired ${fetched.size} message(s) in $conversationId" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cursor stays behind, so the next received event
                // re-triggers the repair.
                log.error(e) { "[gap-repair] failed for $conversationId" }
            }
        }
    }

    /**
     * Pulls everything after the locally newest message of the conversation
     * and persists it. The server keeps rows for the whole history, so this
     * closes any delivery gap between the last stored message and now.
     */
    private suspend fun syncConversationFromServer(conversationId: Uuid): List<Message> {
        val lastMessageId = conversationDao.getConversationById(conversationId)
            .firstOrNull()?.last_message_id
            ?: return emptyList() // nothing stored yet: initial paging covers history
        val missed = conversationApi.syncMessages(
            conversationId = conversationId,
            afterId = lastMessageId,
        )
        missed.forEach { persistServerMessage(it, ownRequestId = null) }
        return missed
    }

    private suspend fun syncAllConversationsFromServer() {
        val conversations = conversationDao.getAllActiveConversations().firstOrNull().orEmpty()
        conversations.forEach { conversation ->
            runCatching { syncConversationFromServer(conversation.id) }
                .onFailure { log.warn(it) { "[reconnect-sync] failed for ${conversation.id}" } }
        }
    }

    /**
     * Persists a message that the server has already stored. Own messages
     * promote the local SENDING row (keyed by the requestId, which the server
     * adopted as the primary key); everything else upserts by id, so repairs
     * racing with the realtime stream stay idempotent. The conversation's
     * last-message cursor only ever moves forward — a repair can carry
     * messages older than the newest one already stored.
     */
    private suspend fun persistServerMessage(message: Message, ownRequestId: String?) {
        val messageEntity = message.toMessageEntity()
        val userEntity = message.toUserEntity()
        val participantEntity = message.toParticipantEntity()

        messageDao.transaction {
            val existing = messageDao.getMessageById(ownRequestId?.let(Uuid::parse) ?: message.id).firstOrNull()
            when {
                existing == null -> messageDao.insertMessage(messageEntity)
                ownRequestId != null -> messageDao.updateMessageStatus(
                    oldId = Uuid.parse(ownRequestId),
                    newId = message.id,
                    createdAt = message.createdAt,
                    status = MessageStatus.SENT
                )
                // A non-own duplicate (realtime race with a repair) — already
                // stored, nothing to do.
            }
            userEntity?.let { userDao.insertUser(it) }
            participantEntity?.let { participantDao.insertParticipant(it) }

            val currentLast = conversationDao.getConversationById(message.conversationId)
                .firstOrNull()?.last_message_id
            if (currentLast == null || message.id > currentLast) {
                conversationDao.updateLastMessage(
                    id = message.conversationId,
                    lastMessageId = message.id,
                    updatedAt = Clock.System.now()
                )
            }
        }
    }

    private suspend fun handleWithdrawnMessage(event: MessageEventResponse.Withdrawn) {
        try {
            messageDao.revokeMessage(event.messageId, event.timestamp)
//                .also {
//                    _invalidationEvents.tryEmit(Unit)
//                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error handling withdrawn message" }
        }
    }

    private suspend fun handleReadMessage(event: MessageEventResponse.Read) {
        try {
            log.debug { "Message read: ${event.messageId} by ${event.readerId}" }
            participantDao.updateLastReadMessage(
                conversationId = event.conversationId,
                userId = event.readerId,
                lastMessageId = event.messageId
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error handling read message" }
        }
    }

    private fun stopMessageConsumption() {
        messageConsumptionJob?.cancel()
        messageConsumptionJob = null
        outboxRetryJob?.cancel()
        outboxRetryJob = null
    }

    fun cleanup() {
        stopMessageConsumption()
    }

    /**
     * Outbox driver: `SENDING` rows double as the outbox. Retries trigger on
     * WS (re)connect and every [RETRY_POLL_INTERVAL]; messages still unacked
     * after [MESSAGE_TTL] are marked FAILED so bubbles stop spinning. Resends
     * reuse the original requestId, which the server uses as the message
     * primary key, making replays idempotent.
     */
    private fun startOutboxRetryLoop() {
        outboxRetryJob?.cancel()
        outboxRetryJob = scope.launch {
            // Resend as soon as the socket comes back up.
            messageApi.connectionState.collectLatest { state ->
                if (state is ConnectionState.Connected) {
                    retryPendingMessages()
                    // Outside collectLatest so a state flip cannot cancel the
                    // catch-up halfway through.
                    scope.launch { syncAllConversationsFromServer() }
                }
            }
        }
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(RETRY_POLL_INTERVAL)
                expireStaleMessages()
                retryPendingMessages()
            }
        }
    }

    override suspend fun retryPendingMessages() {
        if (messageApi.connectionState.value !is ConnectionState.Connected) return
        val retryCutoff = Clock.System.now() - RETRY_DELAY
        val pending = messageDao.getRetryableMessages(retryCutoff)
        if (pending.isEmpty()) return

        log.info { "[outbox] resending ${pending.size} pending message(s)" }
        for (entity in pending) {
            val request = MessageRequest.Send(
                senderId = entity.user_id,
                conversationId = entity.conversation_id,
                // The row keeps the reply target, and the server stores it on insert:
                // dropping it here would silently degrade a retried reply to a plain message.
                replyToMessageId = entity.reply_to_message_id,
                content = entity.content,
                requestId = entity.id.toString()
            )
            try {
                messageApi.send(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Socket died mid-retry: leave rows in SENDING for the next
                // trigger instead of failing the whole batch.
                log.warn(e) { "[outbox] resend interrupted" }
                break
            }
        }
    }

    private suspend fun expireStaleMessages() {
        val giveUpBefore = Clock.System.now() - MESSAGE_TTL
        messageDao.failStaleMessages(giveUpBefore)
    }

    private fun determineRenderType(content: MessageContent): MessageRenderType = when (content) {
        is TextContent -> MessageRenderType.TEXT
        is ImageContent -> MessageRenderType.IMAGE
        is VideoContent -> MessageRenderType.VIDEO
        is AudioContent -> MessageRenderType.AUDIO
        is FileContent -> MessageRenderType.FILE
        else -> MessageRenderType.OTHER
    }
}
