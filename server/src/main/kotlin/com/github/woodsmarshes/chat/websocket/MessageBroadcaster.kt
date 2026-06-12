package com.github.woodsmarshes.chat.websocket

import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.logging.Logger
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MessageBroadcaster(
    val sessionManager: WebSocketSessionManager,
    val logger: Logger,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("Broadcaster"))

    // --- RealtimeEvent-specific methods that force polymorphic serialization ---
    // Without the explicit RealtimeEvent type, kotlinx.serialization serializes
    // the concrete subclass without a discriminator, and the client fails to
    // deserialize as the polymorphic base type.

    fun sendRealtimeEventToUser(userId: Uuid, data: RealtimeEvent) {
        val sessions = sessionManager.getUserSessions(userId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.sendSerialized<RealtimeEvent>(data)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to user $userId: ${e.message}")
                }
            }
        }
    }

    fun sendRealtimeEventToConversation(conversationId: Uuid, data: RealtimeEvent) {
        val sessions = sessionManager.getConversationSessions(conversationId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.sendSerialized<RealtimeEvent>(data)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to conversation $conversationId: ${e.message}")
                }
            }
        }
    }

    fun sendRealtimeEventToUsers(userIds: List<Uuid>, data: RealtimeEvent) {
        userIds.forEach { userId ->
            sendRealtimeEventToUser(userId, data)
        }
    }

    fun sendRealtimeEventToSession(session: WebSocketServerSession, data: RealtimeEvent) {
        scope.launch {
            try {
                if (session.isActive) {
                    session.sendSerialized<RealtimeEvent>(data)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to session $session: ${e.message}")
            }
        }
    }

    // --- Generic methods for non-polymorphic data ---

    /**
     * 向特定用户的所有会话广播消息
     */
    fun broadcastToUser(userId: Uuid, frame: Frame) {
        val sessions = sessionManager.getUserSessions(userId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.send(frame)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to user $userId: ${e.message}")
                }
            }
        }
    }

    inline fun <reified T> broadcastToUser(userId: Uuid, data: T) {
        val sessions = sessionManager.getUserSessions(userId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.sendSerialized(data)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to user $userId: ${e.message}")
                }
            }
        }
    }

    fun broadcastToConversation(conversationId: Uuid, frame: Frame) {
        val sessions = sessionManager.getConversationSessions(conversationId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.send(frame)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to conversation $conversationId: ${e.message}")
                }
            }
        }
    }
    inline fun <reified T> broadcastToConversation(conversationId: Uuid, data: T) {
        val sessions = sessionManager.getConversationSessions(conversationId)
        if (sessions.isEmpty()) return
        sessions.forEach { session ->
            scope.launch {
                try {
                    if (session.isActive) {
                        session.sendSerialized(data)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to send to conversation $conversationId: ${e.message}")
                }
            }
        }
    }

    /**
     * 向多个用户广播消息
     */
    inline fun <reified T> broadcastToUsers(userIds: List<Uuid>, data: T) {
        userIds.forEach { userId ->
            broadcastToUser(userId, data)
        }
    }

    fun broadcastToUsers(userIds: List<Uuid>, frame: Frame) {
        userIds.forEach { userId ->
            broadcastToUser(userId, frame)
        }
    }

    /**
     * 向所有活跃用户广播消息
     */
    fun broadcastToAll(frame: Frame) {
        val activeUsers = sessionManager.getActiveUsers()
        broadcastToUsers(activeUsers.toList(), frame)
    }

    /**
     * 向特定会话发送消息
     */
    fun sendToSession(session: WebSocketServerSession, frame: Frame) {
        scope.launch {
            try {
                if (session.isActive) {
                    session.send(frame)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to session $session: ${e.message}")
            }
        }
    }

    inline fun <reified T> sendToSession(session: WebSocketServerSession, data: T) {
        scope.launch {
            try {
                if (session.isActive) {
                    session.sendSerialized(data)
                }
            } catch (e: Exception) {
                logger.error("Failed to send to session $session: ${e.message}")
            }
        }
    }
}
