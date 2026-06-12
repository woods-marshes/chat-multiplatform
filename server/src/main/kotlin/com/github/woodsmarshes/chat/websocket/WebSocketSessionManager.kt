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
        byUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        bySession[session] = userId
    }

    fun remove(session: WebSocketServerSession): Uuid? {
        val userId = bySession.remove(session) ?: return null
        byUser[userId]?.remove(session)
        return userId
    }

    fun getSessions(userId: Uuid): List<WebSocketServerSession> =
        byUser[userId]?.toList() ?: emptyList()

    fun getUser(session: WebSocketServerSession): Uuid? = bySession[session]

    fun getActiveUsers(): Set<Uuid> = byUser.keys.toSet()

    fun isOnline(userId: Uuid): Boolean = byUser[userId]?.isNotEmpty() == true

    fun size(): Int = byUser.size
}

/**
 * Tracks conversation ↔ user membership for real-time message routing.
 */
class RoomIndex {
    private val members = ConcurrentHashMap<Uuid, MutableSet<Uuid>>()
    private val subscriptions = ConcurrentHashMap<Uuid, MutableSet<Uuid>>()

    fun addUser(userId: Uuid, conversationId: Uuid) {
        members.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }.add(userId)
        subscriptions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(conversationId)
    }

    fun addUsers(userIds: Set<Uuid>, conversationId: Uuid) {
        members.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }.addAll(userIds)
        userIds.forEach { userId ->
            subscriptions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(conversationId)
        }
    }

    fun addUserToMany(userId: Uuid, conversationIds: Set<Uuid>) {
        conversationIds.forEach { id ->
            members.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }.add(userId)
        }
        subscriptions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.addAll(conversationIds)
    }

    fun removeUser(userId: Uuid, conversationId: Uuid) {
        members[conversationId]?.remove(userId)
        if (members[conversationId]?.isEmpty() == true) members.remove(conversationId)

        subscriptions[userId]?.remove(conversationId)
        if (subscriptions[userId]?.isEmpty() == true) subscriptions.remove(userId)
    }

    fun getMembers(conversationId: Uuid): Set<Uuid> =
        members[conversationId]?.toSet() ?: emptySet()

    fun getConversations(userId: Uuid): Set<Uuid> =
        subscriptions[userId]?.toSet() ?: emptySet()

    fun contains(userId: Uuid, conversationId: Uuid): Boolean =
        members[conversationId]?.contains(userId) == true

    fun removeConversation(conversationId: Uuid): MutableSet<Uuid>? =
        members.remove(conversationId)?.also { userIds ->
            userIds.forEach { subscriptions[it]?.remove(conversationId) }
        }
}

class WebSocketSessionManager(
    private val sessions: SessionIndex = SessionIndex(),
    private val rooms: RoomIndex = RoomIndex(),
) {

    fun addUserSession(userId: Uuid, session: WebSocketServerSession) {
        sessions.add(userId, session)
    }

    fun addUserToConversation(userId: Uuid, conversationId: Uuid) {
        rooms.addUser(userId, conversationId)
    }

    fun addUsersToConversation(userIds: Set<Uuid>, conversationId: Uuid) {
        rooms.addUsers(userIds, conversationId)
    }

    fun addUserToConversations(userId: Uuid, conversationIds: Set<Uuid>) {
        rooms.addUserToMany(userId, conversationIds)
    }

    fun removeUserFromConversation(userId: Uuid, conversationId: Uuid) {
        rooms.removeUser(userId, conversationId)
    }

    fun getConversationUsers(conversationId: Uuid): Set<Uuid> =
        rooms.getMembers(conversationId)

    fun getUserConversations(userId: Uuid): Set<Uuid> =
        rooms.getConversations(userId)

    fun getConversationSessions(conversationId: Uuid): List<WebSocketServerSession> =
        rooms.getMembers(conversationId).flatMap { userId -> getUserSessions(userId) }

    fun isUserInConversation(userId: Uuid, conversationId: Uuid): Boolean =
        rooms.contains(userId, conversationId)

    fun removeUserSession(session: WebSocketServerSession) {
        sessions.remove(session)
    }

    fun removeConversation(conversationId: Uuid): MutableSet<Uuid>? =
        rooms.removeConversation(conversationId)

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
