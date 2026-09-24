package com.github.woodsmarshes.chat.websocket

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Minimal stand-in for [WebSocketServerSession]: the session index only uses
 * sessions as map keys, so every member stays inert.
 */
private class FakeSession : WebSocketServerSession {
    override val call: ApplicationCall get() = error("ApplicationCall is not needed for index tests")
    override val coroutineContext: CoroutineContext = Job()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming: ReceiveChannel<Frame> = Channel()
    override val outgoing: SendChannel<Frame> = Channel()
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override suspend fun send(frame: Frame) {}
    override suspend fun flush() {}
    override fun terminate() {}
}

class WebSocketSessionManagerTest {

    private val manager = WebSocketSessionManager()

    private val userA = Uuid.random()
    private val userB = Uuid.random()
    private val conversation1 = Uuid.random()
    private val conversation2 = Uuid.random()

    // ── SessionIndex behaviour ──────────────────────────────────────────

    @Test
    fun `registered session maps back to its user`() {
        val session = FakeSession()

        manager.addUserSession(userA, session)

        assertEquals(userA, manager.getUserIdBySession(session))
        assertEquals(listOf(session), manager.getUserSessions(userA))
    }

    @Test
    fun `unknown session resolves to null`() {
        assertNull(manager.getUserIdBySession(FakeSession()))
    }

    @Test
    fun `one user can hold multiple concurrent sessions`() {
        val web = FakeSession()
        val mobile = FakeSession()

        manager.addUserSession(userA, web)
        manager.addUserSession(userA, mobile)

        assertEquals(2, manager.getUserSessions(userA).size)
        assertTrue(manager.isUserOnline(userA))
    }

    @Test
    fun `same session registered twice is stored once`() {
        val session = FakeSession()

        manager.addUserSession(userA, session)
        manager.addUserSession(userA, session)

        assertEquals(1, manager.getUserSessions(userA).size)
    }

    @Test
    fun `removed session no longer resolves`() {
        val session = FakeSession()
        manager.addUserSession(userA, session)

        manager.removeUserSession(session)

        assertNull(manager.getUserIdBySession(session))
        assertTrue(manager.getUserSessions(userA).isEmpty())
        assertFalse(manager.isUserOnline(userA))
    }

    @Test
    fun `removing an unknown session is a no-op`() {
        manager.removeUserSession(FakeSession())

        assertEquals(0, manager.getSessionStats()["activeUsers"])
    }

    @Test
    fun `other sessions of a user survive one session being removed`() {
        val first = FakeSession()
        val second = FakeSession()
        manager.addUserSession(userA, first)
        manager.addUserSession(userA, second)

        manager.removeUserSession(first)

        assertEquals(listOf(second), manager.getUserSessions(userA))
        assertTrue(manager.isUserOnline(userA))
    }

    @Test
    fun `removing a session drops the empty per-user entry from the index`() {
        val session = FakeSession()
        manager.addUserSession(userA, session)

        manager.removeUserSession(session)

        assertFalse(manager.getActiveUsers().contains(userA))
        assertEquals(0, manager.getSessionStats()["activeUsers"])
        assertFalse(manager.isUserOnline(userA))
    }

    // ── RoomIndex behaviour ─────────────────────────────────────────────

    @Test
    fun `user joined to a conversation is a member of it`() {
        manager.addUserToConversation(userA, conversation1)

        assertTrue(manager.isUserInConversation(userA, conversation1))
        assertEquals(setOf(userA), manager.getConversationUsers(conversation1))
        assertEquals(setOf(conversation1), manager.getUserConversations(userA))
    }

    @Test
    fun `membership lookups for unknown ids are empty`() {
        assertTrue(manager.getConversationUsers(conversation1).isEmpty())
        assertTrue(manager.getUserConversations(userA).isEmpty())
        assertFalse(manager.isUserInConversation(userA, conversation1))
    }

    @Test
    fun `addUsersToConversation registers every user in one room`() {
        manager.addUsersToConversation(setOf(userA, userB), conversation1)

        assertEquals(setOf(userA, userB), manager.getConversationUsers(conversation1))
        assertEquals(setOf(conversation1), manager.getUserConversations(userA))
        assertEquals(setOf(conversation1), manager.getUserConversations(userB))
    }

    @Test
    fun `addUserToConversations registers one user in many rooms`() {
        manager.addUserToConversations(userA, setOf(conversation1, conversation2))

        assertEquals(setOf(userA), manager.getConversationUsers(conversation1))
        assertEquals(setOf(userA), manager.getConversationUsers(conversation2))
        assertEquals(setOf(conversation1, conversation2), manager.getUserConversations(userA))
    }

    @Test
    fun `leaving a conversation removes both membership directions`() {
        manager.addUserToConversation(userA, conversation1)
        manager.addUserToConversation(userB, conversation1)

        manager.removeUserFromConversation(userA, conversation1)

        assertEquals(setOf(userB), manager.getConversationUsers(conversation1))
        assertTrue(manager.getUserConversations(userA).isEmpty())
    }

    @Test
    fun `leaving a conversation the user never joined is a no-op`() {
        manager.removeUserFromConversation(userA, conversation1)

        assertTrue(manager.getConversationUsers(conversation1).isEmpty())
    }

    @Test
    fun `removing the last member cleans up the conversation entry`() {
        manager.addUserToConversation(userA, conversation1)

        manager.removeUserFromConversation(userA, conversation1)

        assertEquals(emptySet(), manager.getConversationUsers(conversation1))
    }

    @Test
    fun `removeConversation detaches every member in one call`() {
        manager.addUsersToConversation(setOf(userA, userB), conversation1)

        val removed = manager.removeConversation(conversation1)

        assertNotNull(removed)
        assertEquals(setOf(userA, userB), removed.toSet())
        assertTrue(manager.getConversationUsers(conversation1).isEmpty())
        assertTrue(manager.getUserConversations(userA).isEmpty())
        assertTrue(manager.getUserConversations(userB).isEmpty())
    }

    @Test
    fun `removing an unknown conversation returns null`() {
        assertNull(manager.removeConversation(conversation1))
    }

    @Test
    fun `memberships are scoped per conversation`() {
        manager.addUserToConversation(userA, conversation1)

        assertFalse(manager.isUserInConversation(userA, conversation2))
    }

    // ── Cross-index routing ─────────────────────────────────────────────

    @Test
    fun `conversation sessions flatten sessions of all members`() {
        val sessionA = FakeSession()
        val sessionB = FakeSession()
        manager.addUserSession(userA, sessionA)
        manager.addUserSession(userB, sessionB)
        manager.addUsersToConversation(setOf(userA, userB), conversation1)

        val sessions = manager.getConversationSessions(conversation1)

        assertEquals(setOf(sessionA, sessionB), sessions.toSet())
    }

    @Test
    fun `offline members contribute no sessions`() {
        val sessionB = FakeSession()
        manager.addUserSession(userB, sessionB)
        manager.addUsersToConversation(setOf(userA, userB), conversation1)

        assertEquals(listOf(sessionB), manager.getConversationSessions(conversation1))
    }

    @Test
    fun `stats report distinct active users`() {
        manager.addUserSession(userA, FakeSession())
        manager.addUserSession(userA, FakeSession())
        manager.addUserSession(userB, FakeSession())

        assertEquals(2, manager.getSessionStats()["activeUsers"])
    }
}
