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
import com.github.woodsmarshes.chat.core.data.paging.MessageMediatorFactory
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
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
import com.github.woodsmarshes.chat.core.common.session.SessionExecutor
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.woodsmarshes.chat.db.GroupJoinRequestEntity
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
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
    private val groupJoinRequestDao: GroupJoinRequestDao,
    private val userSettingDataSource: UserSettingDataSource,
    private val scope: CoroutineScope,
    private val mediatorFactory: MessageMediatorFactory,
    private val groupProfileDao: GroupProfileDao? = null,
) : MessageRepository {
    override val connectionState get() = messageApi.connectionState

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

        /** Server-side sync page size; a short page ends a repair walk. */
        const val SYNC_PAGE_SIZE = 50

        /** Hard cap so a pathological repair cannot loop forever. */
        const val MAX_SYNC_PAGES = 40
    }
    val ownUser = userSettingDataSource.user

    private val sendMutex = Mutex()
    private val syncMutex = Mutex()
    private val lifecycle = com.github.woodsmarshes.chat.core.common.session.ResourceSession(
        dispatcher = scope.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        create = { RunningSession(this, scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Default) },
        prepare = { session ->
            startMessageConsumption(session)
            startOutboxRetryLoop(session)
        },
        beforeStop = { it.job.cancel() },
        dispose = { session ->
            session.job.join()
            seqMutex.withLock {
                deliveredSeqs.clear()
                gapRepairs.clear()
            }
            _typingUsers.value = emptyMap()
        },
    )

    /**
     * In-memory delivery high-water mark used to trigger repair. A jump in the
     * stream means events were lost in flight (socket hiccup or a server-side
     * drop); the missing span is then repaired from REST history. The first
     * message observed per conversation in a session only establishes the
     * baseline — it is not a gap.
     */
    private val seqMutex = Mutex()
    private val deliveredSeqs = mutableMapOf<Uuid, Long>()
    private val gapRepairs = mutableMapOf<Uuid, Job>()

    private class SessionElement(
        val owner: OfflineFirstMessageRepositoryImpl,
    ) : AbstractCoroutineContextElement(SessionElement) {
        companion object Key : CoroutineContext.Key<SessionElement>
    }

    /**
     * One active session lifecycle. Its [job] is an independent [SupervisorJob]
     * controlled explicitly via [startSession] and [stopSession] (which cancels
     * and awaits [job] before returning), rather than an `invokeOnCompletion`
     * callback on [scope] that would neither propagate cancellation immediately
     * nor wait for session cleanup to finish.
     */
    private class RunningSession(
        repository: OfflineFirstMessageRepositoryImpl,
        dispatcher: CoroutineContext,
    ) {
        val job: CompletableJob = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher + SessionElement(repository))
    }

    private fun isActiveSession(session: RunningSession): Boolean = lifecycle.isCurrent(session)

    override suspend fun startSession() = lifecycle.start()

    override suspend fun stopSession() {
        check(currentCoroutineContext()[SessionElement]?.owner !== this) {
            "stopSession() must not be called from inside a message worker"
        }
        lifecycle.stop()
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getMessages(
        ownUserId: Uuid,
        conversationId: Uuid,
        isGroup: Boolean,
        limit: Int
    ): Flow<PagingData<MessageUiModel>> {
        log.info { "[getMessages] called, conversationId=$conversationId ownUserId=$ownUserId" }
        val pagingDao = (messageDao as? com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl)?.pinForPaging() ?: messageDao
        return Pager(
            config = PagingConfig(
                pageSize = limit,
                enablePlaceholders = false,
            ),
            remoteMediator = mediatorFactory.create(ownUserId, conversationId, isGroup),
            pagingSourceFactory = {
                pagingDao.pagingSource(
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
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull()
                    ?: return@execute Err(MessageError.PermissionDenied).also {
                        log.warn { "[sendMessage] PermissionDenied: ownUser is null" }
                    }

                val requestId = Uuid.generateV7()
                log.info { "[sendMessage] sending, requestId=$requestId conversationId=$conversationId" }

                val request = MessageRequest.Send(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    content = content,
                    requestId = requestId.toString(),
                    replyToMessageId = replyToMessageId,
                )

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
                    messageDao.startAttempt(requestId, Clock.System.now())
                    conversationDao.updateLastMessage(
                        id = conversationId,
                        lastMessageId = requestId,
                        updatedAt = Clock.System.now()
                    )
                }
                // The durable outbox row must exist before any acknowledgement.
                sendMutex.withLock {
                    if (messageApi.connectionState.value is ConnectionState.Connected) {
                        messageApi.send(request)
                    }
                }
                Ok(Unit)
            }
        } catch (e: SessionStoppedException) {
            log.warn { "[sendMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[sendMessage] exception: ${e.message}" }
            Err(MessageError.Unknown(e.message))
        }
    }

    override suspend fun revokeMessage(messageId: Uuid): Result<Unit, MessageError> {
        return try {
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull()
                    ?: return@execute Err(MessageError.RevokeFailed)

                val request = MessageRequest.Withdraw(
                    senderId = currentUser.id,
                    messageId = messageId
                )

                // Not retried automatically: the caller reports the failure
                // and the user can revoke again once the connection is back.
                if (messageApi.send(request)) Ok(Unit) else Err(MessageError.RevokeFailed)
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[revokeMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send realtime request" }
            Err(MessageError.RevokeFailed)
        }
    }

    override suspend fun markAsRead(conversationId: Uuid, messageId: Uuid) {
        try {
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull() ?: return@execute

                val request = MessageRequest.Read(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    messageId = messageId
                )

                // Receipts are not retried: re-entering the chat re-reports
                // the current last message, so a dropped one only costs a
                // stale unread state until then. Logged, not surfaced.
                if (!messageApi.send(request)) {
                    log.warn { "[markAsRead] receipt for $messageId was not delivered" }
                }
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[markAsRead] refused: session is not active" }
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
            lifecycle.execute {
                val currentUser = ownUser.firstOrNull() ?: return@execute

                val request = MessageRequest.Typing(
                    senderId = currentUser.id,
                    conversationId = conversationId,
                    isTyping = isTyping,
                )

                messageApi.send(request)
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[sendTyping] refused: session is not active" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Failed to send typing state" }
        }
    }

    private fun handleUserTyping(session: RunningSession, event: MessageEventResponse.UserTyping) {
        if (!session.job.isActive || !isActiveSession(session)) return
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

    private suspend fun startMessageConsumption(session: RunningSession) {
        val subscriptionReady = CompletableDeferred<Unit>()
        val job = session.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            log.info { "[ws-consume] starting event consumption" }
            try {
                messageApi.events
                    .onSubscription { subscriptionReady.complete(Unit) }
                    .collect { event ->
                        log.info { "[ws-consume] received event: ${event::class.simpleName}" }
                        when (event) {
                            is MessageEventResponse.Received -> {
                                log.info { "[ws-consume] handling Received: requestId=${event.requestId} senderId=${event.senderId} msgId=${event.message.id}" }
                                handleReceivedMessage(session, event)
                            }
                            is MessageEventResponse.Withdrawn -> {
                                handleWithdrawnMessage(event)
                            }
                            is MessageEventResponse.Read -> {
                                handleReadMessage(event)
                            }
                            is MessageEventResponse.UserTyping -> {
                                handleUserTyping(session, event)
                            }
                            is ConversationEventResponse.ConversationDeleted -> {
                                runCatching {
                                    conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                                    groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                                }
                            }
                            is ConversationEventResponse.UserLeftConversation -> {
                                runCatching {
                                    val meId = ownUser.firstOrNull()?.id
                                    participantDao.removeParticipant(event.conversationId, event.userId)
                                    if (meId != null && event.userId == meId) {
                                        conversationDao.softDeleteConversation(event.conversationId, event.timestamp)
                                        groupJoinRequestDao.deleteGroupJoinRequestsForConversation(event.conversationId)
                                    }
                                }
                            }
                            is ConversationEventResponse.ConversationCreated -> {
                                handleConversationSyncEvent(session, event.conversationId)
                            }
                            is ConversationEventResponse.UserJoinedConversation -> {
                                handleConversationSyncEvent(session, event.conversationId)
                            }
                            is ConversationEventResponse.GroupProfileUpdated -> {
                                handleConversationSyncEvent(session, event.conversationId)
                            }
                            is ConversationEventResponse.PersonalSettingsUpdated -> {
                                handleConversationSyncEvent(session, event.conversationId)
                            }
                            is ConversationEventResponse.GroupJoinRequest -> {
                                // The event payload is complete (id, applicant, message):
                                // record it directly so admin badges update without polling.
                                // Seed semantics: a replayed event must not downgrade an
                                // already-handled row or clobber its message.
                                runCatching {
                                    groupJoinRequestDao.seedGroupJoinRequest(
                                        GroupJoinRequestEntity(
                                            id = event.requestId,
                                            conversation_id = event.conversationId,
                                            applicant_id = event.applicantId,
                                            message = event.message,
                                            status = RequestStatus.PENDING,
                                            handled_by = null,
                                            created_at = event.timestamp,
                                            updated_at = event.timestamp,
                                        )
                                    )
                                }.onFailure { log.warn(it) { "[ws-consume] failed to record GroupJoinRequest ${event.requestId}" } }
                            }
                            is ConversationEventResponse.GroupJoinRequestHandled -> {
                                // Sent only to the applicant: refresh their pending
                                // state in place.
                                runCatching {
                                    groupJoinRequestDao.updateGroupJoinRequestStatus(
                                        id = event.requestId,
                                        status = if (event.approved) RequestStatus.ACCEPTED else RequestStatus.REJECTED,
                                        handledBy = event.handlerId,
                                        updatedAt = event.timestamp,
                                    )
                                }.onFailure { log.warn(it) { "[ws-consume] failed to record GroupJoinRequestHandled ${event.requestId}" } }
                            }
                            else -> {
                                log.debug { "[ws-consume] unknown event: ${event::class.simpleName}" }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                subscriptionReady.cancel(e)
                throw e
            } catch (e: Exception) {
                subscriptionReady.completeExceptionally(e)
                log.error(e) { "[ws-consume] error in event consumption: ${e.message}" }
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null && !subscriptionReady.isCompleted) {
                subscriptionReady.completeExceptionally(cause)
            }
        }
        subscriptionReady.await()
    }

    private fun handleConversationSyncEvent(session: RunningSession, conversationId: Uuid) {
        if (!session.job.isActive || !isActiveSession(session)) return
        session.scope.launch {
            runCatching {
                val detail = conversationApi.getDetail(conversationId)
                conversationDao.insertConversation(detail.toConversation().toEntity())
                detail.toGroupProfileEntity()?.let { groupProfileDao?.insertGroupProfile(it) }
                participantDao.insertParticipant(detail.toParticipantEntity())
                if (detail.type == com.github.woodsmarshes.chat.core.model.ConversationType.GROUP) {
                    val participants = conversationApi.getParticipants(conversationId)
                    userDao.insertUsers(participants.map { (_, u) -> u.toUserEntity() })
                    participantDao.insertParticipants(participants.map { (p, _) -> p.toEntity() })
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                log.debug(e) { "[ws-consume] failed to sync conversation $conversationId" }
            }
        }
    }

    private suspend fun handleReceivedMessage(session: RunningSession, event: MessageEventResponse.Received) {
        try {
            val message = event.message
            val currentUser = ownUser.firstOrNull()

            val isOwnMessage = currentUser != null && event.senderId == currentUser.id
            log.info { "[handleReceived] isOwnMessage=$isOwnMessage requestId=${event.requestId} serverMsgId=${message.id}" }

            persistServerMessage(message, ownRequestId = event.requestId.takeIf { isOwnMessage }, resolvedOwnUserId = currentUser?.id)
            trackDeliverySeq(session, message)
            log.info { "[handleReceived] db transaction done" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[handleReceived] error: ${e.message}" }
            scheduleGapRepair(session, event.message.conversationId)
        }
    }

    private suspend fun trackDeliverySeq(session: RunningSession, message: Message) {
        val seq = message.seq ?: return
        val conversationId = message.conversationId
        var gapDetected = false
        seqMutex.withLock {
            if (!session.job.isActive || !isActiveSession(session)) return
            val last = deliveredSeqs[conversationId]
            if (last == null) {
                // First message observed for this conversation within this
                // session: it only establishes the baseline. Anything older
                // is backfilled by the reconnect sync from the durable
                // cursor — treating "no baseline" as a gap used to fire a
                // pointless full repair per conversation on every launch.
                deliveredSeqs[conversationId] = seq
            } else if (seq > last) {
                if (seq > last + 1) {
                    gapDetected = true
                }
                if (!gapDetected) deliveredSeqs[conversationId] = seq
            }
            // An older seq re-arriving after a repair is a duplicate.
        }
        if (gapDetected) {
            scheduleGapRepair(session, conversationId)
        }
    }

    /** Schedules a gap repair as a child of [session] so [stopSession] cancels and awaits it. */
    private suspend fun scheduleGapRepair(session: RunningSession, conversationId: Uuid) {
        seqMutex.withLock {
            if (!session.job.isActive || !isActiveSession(session)) return
            if (gapRepairs[conversationId]?.isActive == true) return
            gapRepairs[conversationId] = session.scope.launch {
                try {
                    val fetched = syncConversationFromServer(conversationId)
                    val highest = fetched.maxOfOrNull { it.seq ?: 0L }
                    if (highest != null) {
                        seqMutex.withLock {
                            if (session.job.isActive && isActiveSession(session)) {
                                deliveredSeqs[conversationId] = maxOf(deliveredSeqs[conversationId] ?: 0L, highest)
                            }
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
    }

    /** Resume REST history from the last page committed atomically with its cursor. */
    private suspend fun syncConversationFromServer(conversationId: Uuid): List<Message> = syncMutex.withLock {
        // Preview ids may point past holes or belong to pending client messages.
        var afterId = messageDao.getSyncCursor(conversationId) ?: Uuid.NIL
        val fetched = mutableListOf<Message>()
        repeat(MAX_SYNC_PAGES) {
            val page = conversationApi.syncMessages(conversationId = conversationId, afterId = afterId)
            if (page.isEmpty()) return@withLock fetched
            val next = page.maxOf { it.id }
            check(next > afterId) { "Message sync cursor did not advance" }
            val ownId = ownUser.firstOrNull()?.id
            messageDao.transaction {
                page.forEach { persistServerMessage(it, ownRequestId = null, resolvedOwnUserId = ownId) }
                messageDao.setSyncCursor(conversationId, next)
            }
            fetched += page
            afterId = next
            if (page.size < SYNC_PAGE_SIZE) return@withLock fetched
        }
        error("Message sync page budget exhausted; resume on the next sweep")
    }
    private suspend fun syncAllConversationsFromServer() {
        val conversations = conversationDao.getAllActiveConversations().firstOrNull().orEmpty()
        conversations.forEach { conversation ->
            try {
                syncConversationFromServer(conversation.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn(e) { "[reconnect-sync] failed for ${conversation.id}" }
            }
        }
    }

    /** Merge server data and all local references within one transaction. */
    private suspend fun persistServerMessage(
        message: Message,
        ownRequestId: String?,
        resolvedOwnUserId: Uuid?,
    ) {
        val ownId = resolvedOwnUserId
        val candidate = if (message.sender?.id == ownId) {
            ownRequestId?.let(Uuid::parseOrNull) ?: message.clientRequestId
        } else null
        messageDao.transaction {
            message.replyTo?.let { persistServerMessage(it, ownRequestId = null, resolvedOwnUserId = ownId) }
            message.toUserEntity()?.let { userDao.insertUser(it) }
            message.toParticipantEntity()?.let { participantDao.insertParticipant(it) }
            val entity = message.toMessageEntity(MessageStatus.SENT)
            if (entity == null) {
                log.warn { "[message-sync] skipping server message ${message.id}: no sender" }
                return@transaction
            }
            val local = candidate?.let { messageDao.findMessageById(it) }
            val matchingId = candidate?.takeIf {
                local != null && local.user_id == entity.user_id &&
                    local.conversation_id == message.conversationId &&
                    local.local_send_status in listOf(MessageStatus.SENDING, MessageStatus.FAILED)
            }
            messageDao.mergeServerMessage(entity, matchingId)
            val currentLast = conversationDao.findConversationById(message.conversationId)?.last_message_id
            if (currentLast == null || message.id > currentLast) {
                conversationDao.updateLastMessage(message.conversationId, message.id, message.createdAt)
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

    suspend fun cleanup() {
        stopSession()
    }

    internal suspend fun inMemorySessionStateCountsForTest(): Triple<Int, Int, Int> =
        seqMutex.withLock {
            Triple(deliveredSeqs.size, gapRepairs.size, _typingUsers.value.size)
        }

    /**
     * Outbox driver: `SENDING` rows double as the outbox. `FAILED` rows are
     * deliberately excluded — a failure may be a permanent rejection (no
     * permission, removed from the group, unsupported content), so retrying it
     * automatically would loop, and only the user can decide to try again via
     * [retryMessage]. Retries trigger on WS (re)connect and every
     * [RETRY_POLL_INTERVAL]; messages still unacked after [MESSAGE_TTL] are
     * marked FAILED based on the attempt clock, not message creation time. A resend reuses the original
     * request id, which the server matches against `client_request_id` before
     * inserting, making replays idempotent.
     */
    private suspend fun startOutboxRetryLoop(session: RunningSession) {
        val connectionSubscribed = CompletableDeferred<Unit>()
        val retryJob = session.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Resend as soon as the socket comes back up.
                messageApi.connectionState
                    .onSubscription { connectionSubscribed.complete(Unit) }
                    .collectLatest { state ->
                        if (state is ConnectionState.Connected) {
                            retryPendingMessagesInternal()
                            syncAllConversationsFromServer()
                        }
                    }
            } catch (e: CancellationException) {
                connectionSubscribed.cancel(e)
                throw e
            } catch (t: Throwable) {
                connectionSubscribed.completeExceptionally(t)
                throw t
            }
        }
        retryJob.invokeOnCompletion { cause ->
            if (cause != null && !connectionSubscribed.isCompleted) {
                connectionSubscribed.completeExceptionally(cause)
            }
        }
        session.scope.launch {
            while (true) {
                kotlinx.coroutines.delay(RETRY_POLL_INTERVAL)
                try {
                    if (ownUser.firstOrNull() == null) continue
                    sendMutex.withLock { expireStaleMessages() }
                    retryPendingMessagesInternal()
                    if (messageApi.connectionState.value is ConnectionState.Connected) syncAllConversationsFromServer()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn(e) { "Outbox sweep failed; retrying on the next tick" }
                }
            }
        }
        connectionSubscribed.await()
    }

    override suspend fun retryPendingMessages() {
        try {
            lifecycle.execute { retryPendingMessagesInternal() }
        } catch (_: SessionStoppedException) {
            log.warn { "[retryPendingMessages] refused: session is not active" }
        }
    }

    private suspend fun retryPendingMessagesInternal() = sendMutex.withLock {
        if (messageApi.connectionState.value !is ConnectionState.Connected) return@withLock
        val retryCutoff = Clock.System.now() - RETRY_DELAY
        val currentUserId = ownUser.firstOrNull()?.id ?: return@withLock
        val pending = messageDao.getRetryableMessages(retryCutoff).filter { it.user_id == currentUserId }
        if (pending.isEmpty()) return@withLock

        log.info { "[outbox] resending ${pending.size} pending message(s)" }
        for (entity in pending) {
            val current = messageDao.findMessageById(entity.id) ?: continue
            if (current.local_send_status != MessageStatus.SENDING) continue
            val request = MessageRequest.Send(
                senderId = current.user_id,
                conversationId = current.conversation_id,
                // The row keeps the reply target, and the server stores it on insert:
                // dropping it here would silently degrade a retried reply to a plain message.
                replyToMessageId = current.reply_to_message_id,
                content = current.content,
                requestId = entity.id.toString()
            )
            try {
                messageDao.recordAttempt(entity.id, entity.created_at, Clock.System.now())
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

    /**
     * Re-sends a single failed message under its original request identity.
     *
     * The row moves back into the outbox before the socket write, so a retry
     * that is not acknowledged here is picked up by the periodic sweep and the
     * reconnect trigger rather than being lost. Id and timestamp are preserved:
     * the bubble keeps its place in the timeline, and the server matches the
     * original `client_request_id` instead of storing a second message.
     */
    override suspend fun retryMessage(messageId: Uuid): Result<Unit, MessageError> {
        return try {
            lifecycle.execute {
                sendMutex.withLock {
                    val message = messageDao.findMessageById(messageId)
                        ?: return@withLock Err(MessageError.MessageNotFound)

                    if (message.user_id != ownUser.firstOrNull()?.id) return@withLock Err(MessageError.PermissionDenied)
                    if (message.local_send_status != MessageStatus.FAILED) {
                        // A SENDING row is already owned by the outbox, and re-sending a
                        // SENT one would resurrect a delivered message.
                        log.warn { "[retryMessage] $messageId is ${message.local_send_status}, ignoring" }
                        return@withLock Ok(Unit)
                    }

                    if (!messageDao.claimFailedMessage(messageId, Clock.System.now())) return@withLock Ok(Unit)

                    if (messageApi.connectionState.value !is ConnectionState.Connected) {
                        // Accepted into the outbox; the UI shows waiting for a connection.
                        return@withLock Ok(Unit)
                    }

                    val request = MessageRequest.Send(
                        senderId = message.user_id,
                        conversationId = message.conversation_id,
                        content = message.content,
                        requestId = message.id.toString(),
                        replyToMessageId = message.reply_to_message_id,
                    )
                    messageApi.send(request)
                    Ok(Unit)
                }
            }
        } catch (_: SessionStoppedException) {
            log.warn { "[retryMessage] refused: session is not active" }
            Err(MessageError.PermissionDenied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[retryMessage] resend failed for $messageId" }
            Err(MessageError.Unknown(e.message))
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
