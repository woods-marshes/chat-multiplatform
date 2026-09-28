package com.github.woodsmarshes.chat.websocket

import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Pushes realtime events to connected clients. Routing by conversation is
 * resolved against the database, not an in-memory room table, so a dropped
 * join/leave event can never corrupt who receives what — and any node with
 * database access derives the same recipients.
 */
interface RealtimeDelivery {
    suspend fun sendToUser(userId: Uuid, data: RealtimeEvent)
    suspend fun sendToUsers(userIds: List<Uuid>, data: RealtimeEvent)
    suspend fun sendToConversation(conversationId: Uuid, data: RealtimeEvent)
}

class WebSocketRealtimeDelivery(
    private val sessions: SessionIndex,
    private val participants: ConversationParticipantRepository,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val sender: suspend (WebSocketServerSession, RealtimeEvent) -> Unit =
        { session, event -> session.sendSerialized<RealtimeEvent>(event) },
) : RealtimeDelivery {

    override suspend fun sendToUser(userId: Uuid, data: RealtimeEvent) {
        sessions.getSessions(userId).forEach { session ->
            dispatch(session, data)
        }
    }

    override suspend fun sendToUsers(userIds: List<Uuid>, data: RealtimeEvent) {
        userIds.forEach { userId ->
            sendToUser(userId, data)
        }
    }

    override suspend fun sendToConversation(conversationId: Uuid, data: RealtimeEvent) {
        participants.getConversationParticipants(conversationId).forEach { participant ->
            sessions.getSessions(participant.userId).forEach { session ->
                dispatch(session, data)
            }
        }
    }

    private fun dispatch(session: WebSocketServerSession, data: RealtimeEvent) {
        scope.launch {
            try {
                if (session.isActive) {
                    sender(session, data)
                }
            } catch (e: Exception) {
                logger.error("Failed to deliver realtime event: ${e.message}")
                runCatching { session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "delivery failed")) }
            }
        }
    }
}
