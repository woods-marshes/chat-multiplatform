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
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.Normal
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
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
    val ownUser = userSettingDataSource.user

    private var messageConsumptionJob: Job? = null

    private val _invalidationEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val invalidationEvents: Flow<Unit>
        get() = _invalidationEvents.asSharedFlow()

    init {
        startMessageConsumption()
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
                log.info { "[getMessages] pagingSourceFactory creating source for conversationId=$conversationId" }

                val source = messageDao.pagingSource(
                    conversationId = conversationId,
                    pageSize = limit.toLong()
                )

                val job = scope.launch {
                    invalidationEvents.collect {
                        if (!source.invalid) {
                            log.info { "[getMessages] invalidation event collected, invalidating source" }
                            source.invalidate()
                        }
                    }
                }

                source.registerInvalidatedCallback {
                    log.info { "[getMessages] source invalidated, cancelling collection job" }
                    job.cancel()
                }

                source
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
            log.info { "[sendMessage] sending, requestId=$requestId conversationId=$conversationId content=$content" }

            val request = MessageRequest.Send(
                senderId = currentUser.id,
                conversationId = conversationId,
                content = content,
                requestId = requestId.toString()
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
            _invalidationEvents.tryEmit(Unit)
            log.info { "[sendMessage] local insert done, requestId=$requestId" }
            Ok(Unit)
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
        } catch (e: Exception) {
            // Log error if needed
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
        } catch (e: Exception) {
            // Log error if needed
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
                            // Handle typing indicator if needed
                        }
                        else -> {
                            log.debug { "[ws-consume] unknown event: ${event::class.simpleName}" }
                        }
                    }
                }
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

            // Convert message to entities
            val messageEntity = message.toMessageEntity()
            val userEntity = message.toUserEntity()
            val participantEntity = message.toParticipantEntity()

            // Insert into database using transaction
            messageDao.transaction {
                if (isOwnMessage) {
                    log.info { "[handleReceived] updating own msg: oldId=${event.requestId} -> newId=${message.id}" }
                    messageDao.updateMessageStatus(
                        oldId = Uuid.parse(event.requestId),
                        newId = message.id,
                        createdAt = message.createdAt,
                        status = MessageStatus.SENT
                    )
                } else {
                    messageDao.insertMessage(messageEntity)
                }
                userEntity?.let { userDao.insertUser(it) }
                participantEntity?.let { participantDao.insertParticipant(it) }

                conversationDao.updateLastMessage(
                    id = message.conversationId,
                    lastMessageId = message.id,
                    updatedAt = Clock.System.now()
                )
            }
            log.info { "[handleReceived] db transaction done" }
            _invalidationEvents.tryEmit(Unit)
        } catch (e: Exception) {
            log.error(e) { "[handleReceived] error: ${e.message}" }
        }
    }

    private suspend fun handleWithdrawnMessage(event: MessageEventResponse.Withdrawn) {
        try {
            messageDao.revokeMessage(event.messageId, event.timestamp)
                .also {
                    _invalidationEvents.tryEmit(Unit)
                }
        } catch (e: Exception) {
            log.error(e) { "Error handling withdrawn message" }
        }
    }

    private suspend fun handleReadMessage(event: MessageEventResponse.Read) {
        try {
            // Update read status in database if needed
            log.debug { "Message read: ${event.messageId} by ${event.readerId}" }
            // TODO: Implement read status update in database
            // This might involve updating participant's last_read_message_id
        } catch (e: Exception) {
            log.error(e) { "Error handling read message" }
        }
    }

    private fun stopMessageConsumption() {
        messageConsumptionJob?.cancel()
        messageConsumptionJob = null
    }

    fun cleanup() {
        stopMessageConsumption()
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
