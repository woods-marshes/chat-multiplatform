package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.events.ContactEvent
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.MessageEvent
import com.github.woodsmarshes.chat.websocket.RealtimeDelivery
import com.github.woodsmarshes.chat.websocket.WebSocketSessionManager
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

class RealtimeService(
    private val log: Logger,
    private val eventBus: EventBus,
    private val delivery: RealtimeDelivery,
    private val sessionManager: WebSocketSessionManager,
    private val scope: CoroutineScope,
    private val typingDebounceMs: Long = 1_000L,
) {
    private val typingDebounceMap = ConcurrentHashMap<Uuid, Job>()

    init {
        startEventConsumers()
    }

    private fun startEventConsumers() {
        scope.launch {
            eventBus.messageEvents.collect { event ->
                safeExecute {
                    when (event) {
                        is MessageEvent.SendMessage -> handleSendMessage(event)
                        is MessageEvent.WithdrawMessage -> handleWithdrawMessage(event)
                        is MessageEvent.ReadMessage -> handleReadMessage(event)
                        is MessageEvent.UserTyping -> handleUserTyping(event)
                    }
                }
            }
        }

        scope.launch {
            eventBus.conversationEvents.collect { event ->
                safeExecute { handleConversationEvent(event) }
            }
        }

        scope.launch {
            eventBus.contactEvents.collect { event ->
                safeExecute { handleContactEvent(event) }
            }
        }
    }

    private suspend fun safeExecute(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.error("Error handling realtime event", e)
        }
    }

    suspend fun initializeWebSocketConnection(
        userId: Uuid,
        session: WebSocketServerSession,
    ) {
        // Nothing to preload: delivery routes by the participant table, so a
        // connection only registers itself.
        sessionManager.addUserSession(userId, session)
    }

    /**
     * 清理 WebSocket 连接
     */
    fun cleanupWebSocketConnection(session: WebSocketServerSession) {
        val userId = sessionManager.getUserIdBySession(session)
        if (userId != null) {
            typingDebounceMap[userId]?.cancel()
            typingDebounceMap.remove(userId)
        }
        sessionManager.removeUserSession(session)
    }

    /**
     * 处理 WebSocket 异常
     */
    fun handleWebSocketException(
        exception: Exception,
        userId: Uuid,
        session: WebSocketServerSession,
    ) {
        when (exception) {
            is ClosedReceiveChannelException -> {
                log.info("WebSocket closed for user $userId")
            }
            else -> {
                log.error("WebSocket exception for user $userId", exception)
            }
        }
        // cleanup is handled by finally block in RealtimeRoutes
    }

    private suspend fun handleUserTyping(event: MessageEvent.UserTyping) {
        typingDebounceMap[event.userId]?.cancel()

        typingDebounceMap[event.userId] = scope.launch {
            delay(typingDebounceMs)
            val response = MessageEventResponse.UserTyping(
                conversationId = event.conversationId,
                userId = event.userId,
                isTyping = event.isTyping,
                timestamp = event.timestamp
            )
            delivery.sendToConversation(event.conversationId, response)
        }
    }

    private suspend fun handleReadMessage(event: MessageEvent.ReadMessage) {
        delivery.sendToConversation(
            conversationId = event.conversationId,
            data = MessageEventResponse.Read(
                messageId = event.messageId,
                conversationId = event.conversationId,
                readerId = event.readerId,
                timestamp = event.timestamp
            )
        )
    }

    private suspend fun handleSendMessage(event: MessageEvent.SendMessage) {
        delivery.sendToConversation(
            conversationId = event.conversationId,
            data = MessageEventResponse.Received(
                message = event.message,
                conversationId = event.conversationId,
                senderId = event.senderId,
                requestId = event.requestId,
            )
        )
    }

    private suspend fun handleWithdrawMessage(event: MessageEvent.WithdrawMessage) {
        delivery.sendToConversation(
            conversationId = event.conversationId,
            data = MessageEventResponse.Withdrawn(
                messageId = event.messageId,
                conversationId = event.conversationId,
                senderId = event.senderId,
                timestamp = event.timestamp
            )
        )
    }


    private suspend fun handleConversationEvent(event: ConversationEvent) {
        when (event) {
            is ConversationEvent.ConversationCreated -> {
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.ConversationCreated(
                        conversationId = event.conversationId,
                        type = event.type,
                        creatorId = event.creatorId,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.ConversationDeleted -> {
                // Participant rows outlive the soft-deleted conversation, so
                // routing still reaches every former member.
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.ConversationDeleted(
                        conversationId = event.conversationId,
                        deleterId = event.deleterId,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.GroupJoinRequest -> {
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.GroupJoinRequest(
                        requestId = event.requestId,
                        conversationId = event.conversationId,
                        applicantId = event.applicantId,
                        message = event.message,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.GroupJoinRequestHandled -> {
                delivery.sendToUser(
                    userId = event.applicantId,
                    data = ConversationEventResponse.GroupJoinRequestHandled(
                        requestId = event.requestId,
                        conversationId = event.conversationId,
                        applicantId = event.applicantId,
                        handlerId = event.handlerId,
                        approved = event.approved,
                        reason = event.reason,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.GroupProfileUpdated -> {
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.GroupProfileUpdated(
                        conversationId = event.conversationId,
                        updaterId = event.updaterId,
                        profile = event.profile,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.PersonalSettingsUpdated -> {
                delivery.sendToUser(
                    userId = event.userId,
                    data = ConversationEventResponse.PersonalSettingsUpdated(
                        conversationId = event.conversationId,
                        userId = event.userId,
                        settings = event.settings,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.UserJoinedConversation -> {
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.UserJoinedConversation(
                        conversationId = event.conversationId,
                        userId = event.userId,
                        inviterId = event.inviterId,
                        timestamp = event.timestamp
                    )
                )
            }

            is ConversationEvent.UserLeftConversation -> {
                delivery.sendToConversation(
                    conversationId = event.conversationId,
                    data = ConversationEventResponse.UserLeftConversation(
                        conversationId = event.conversationId,
                        userId = event.userId,
                        timestamp = event.timestamp
                    )
                )
            }
        }
    }

    private suspend fun handleContactEvent(event: ContactEvent) {
        val response = when (event) {
            is ContactEvent.FriendRequestSent ->
                ContactEventResponse.FriendRequestSent(
                    requestId = event.requestId,
                    senderId = event.senderId,
                    receiverId = event.receiverId,
                    timestamp = event.timestamp
                )
            is ContactEvent.FriendRequestAccepted ->
                ContactEventResponse.FriendRequestAccepted(
                    requestId = event.requestId,
                    senderId = event.senderId,
                    receiverId = event.receiverId,
                    timestamp = event.timestamp
                )
            is ContactEvent.FriendRequestRejected ->
                ContactEventResponse.FriendRequestRejected(
                    requestId = event.requestId,
                    senderId = event.senderId,
                    receiverId = event.receiverId,
                    reason = event.reason,
                    timestamp = event.timestamp
                )
            is ContactEvent.ContactAdded ->
                ContactEventResponse.ContactAdded(
                    userId = event.userId,
                    contactId = event.contactId,
                    timestamp = event.timestamp
                )
            is ContactEvent.ContactDeleted ->
                ContactEventResponse.ContactDeleted(
                    userId = event.userId,
                    contactId = event.contactId,
                    timestamp = event.timestamp
                )
            is ContactEvent.UserBlocked ->
                ContactEventResponse.UserBlocked(
                    userId = event.userId,
                    blockedUserId = event.blockedUserId,
                    timestamp = event.timestamp
                )
            is ContactEvent.UserUnblocked ->
                ContactEventResponse.UserUnblocked(
                    userId = event.userId,
                    unblockedUserId = event.unblockedUserId,
                    timestamp = event.timestamp
                )
            is ContactEvent.ContactUpdated ->
                ContactEventResponse.ContactUpdated(
                    userId = event.userId,
                    contactId = event.contactId,
                    nickname = event.nickname,
                    alias = event.alias,
                    timestamp = event.timestamp
                )
        }

        // For contact events, broadcast to the specific users involved
        val targetUsers = when (event) {
            is ContactEvent.FriendRequestSent -> listOf(event.senderId, event.receiverId)
            is ContactEvent.FriendRequestAccepted -> listOf(event.senderId, event.receiverId)
            is ContactEvent.FriendRequestRejected -> listOf(event.senderId, event.receiverId)
            is ContactEvent.ContactAdded -> listOf(event.userId, event.contactId)
            is ContactEvent.ContactDeleted -> listOf(event.userId, event.contactId)
            is ContactEvent.UserBlocked -> listOf(event.userId, event.blockedUserId)
            is ContactEvent.UserUnblocked -> listOf(event.userId, event.unblockedUserId)
            is ContactEvent.ContactUpdated -> listOf(event.userId, event.contactId)
        }

        delivery.sendToUsers(targetUsers, response)
    }
}
