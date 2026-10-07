package com.github.woodsmarshes.chat.websocket

import io.ktor.server.websocket.WebSocketServerSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * Tracks bidirectional user ↔ session mappings.
 * Internally uses two [ConcurrentHashMap]s kept in sync.
 */
class SessionIndex {
    private val byUser = ConcurrentHashMap<Uuid, MutableSet<WebSocketServerSession>>()
    private val bySession = ConcurrentHashMap<WebSocketServerSession, Uuid>()

    fun add(userId: Uuid, session: WebSocketServerSession) {
        // Register in the session index first so remove() always finds the
        // owner once byUser knows about the session.
        bySession[session] = userId
        // All per-user set mutations and the empty-entry removal must run
        // inside compute: CHM serializes computes per key, so a reconnection
        // racing the last old connection's removal can no longer drop the
        // set that already contains the new session.
        byUser.compute(userId) { _, existing ->
            (existing ?: ConcurrentHashMap.newKeySet<WebSocketServerSession>()).apply {
                add(session)
            }
        }
    }

    fun remove(session: WebSocketServerSession): Uuid? {
        val userId = bySession.remove(session) ?: return null
        byUser.compute(userId) { _, userSessions ->
            when (userSessions) {
                null -> null
                else -> {
                    userSessions.remove(session)
                    // Drop the per-user entry once its last session is gone so
                    // getActiveUsers()/size() never report disconnected users.
                    if (userSessions.isEmpty()) null else userSessions
                }
            }
        }
        return userId
    }

    fun getSessions(userId: Uuid): List<WebSocketServerSession> =
        byUser[userId]?.toList() ?: emptyList()

    fun getUser(session: WebSocketServerSession): Uuid? = bySession[session]

    fun getActiveUsers(): Set<Uuid> = byUser.keys.toSet()

    fun isOnline(userId: Uuid): Boolean = byUser[userId]?.isNotEmpty() == true

    fun size(): Int = byUser.size
}

class WebSocketSessionManager(
    private val sessions: SessionIndex = SessionIndex(),
) {
    fun addUserSession(userId: Uuid, session: WebSocketServerSession) {
        sessions.add(userId, session)
    }

    fun removeUserSession(session: WebSocketServerSession) {
        sessions.remove(session)
    }

    fun getUserSessions(userId: Uuid): List<WebSocketServerSession> =
        sessions.getSessions(userId)

    fun getUserIdBySession(session: WebSocketServerSession): Uuid? =
        sessions.getUser(session)

    fun getActiveUsers(): Set<Uuid> = sessions.getActiveUsers()

    fun isUserOnline(userId: Uuid): Boolean = sessions.isOnline(userId)

    fun getSessionStats(): Map<String, Int> = buildMap {
        put("activeUsers", sessions.size())
    }
}
