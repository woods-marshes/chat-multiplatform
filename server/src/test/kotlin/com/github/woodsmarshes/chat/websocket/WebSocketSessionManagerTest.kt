package com.github.woodsmarshes.chat.websocket

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `stats report distinct active users`() {
        manager.addUserSession(userA, FakeSession())
        manager.addUserSession(userA, FakeSession())
        manager.addUserSession(userB, FakeSession())

        assertEquals(2, manager.getSessionStats()["activeUsers"])
    }
}
