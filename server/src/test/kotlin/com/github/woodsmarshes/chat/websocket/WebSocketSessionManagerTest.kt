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
import java.util.concurrent.CountDownLatch

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

    @Test
    fun `reconnection racing the last old connection's removal keeps the new session`() {
        // The pre-compute implementation lost the new session here: remove()
        // saw an empty set between the isEmpty() check and the map deletion
        // while add() was already putting the new session into that same set.
        repeat(500) { round ->
            val oldSession = FakeSession()
            val newSession = FakeSession()
            manager.addUserSession(userA, oldSession)

            val start = CountDownLatch(1)
            val threads = listOf(
                Thread { start.await(); manager.removeUserSession(oldSession) },
                Thread { start.await(); manager.addUserSession(userA, newSession) },
            )
            threads.forEach { it.start() }
            start.countDown()
            threads.forEach { it.join() }

            assertTrue(manager.isUserOnline(userA), "round $round: user went offline")
            assertEquals(userA, manager.getUserIdBySession(newSession), "round $round: new session unindexed")
            assertTrue(manager.getUserSessions(userA).contains(newSession), "round $round: new session undeliverable")
            assertNull(manager.getUserIdBySession(oldSession), "round $round: old session still indexed")

            manager.removeUserSession(newSession)
        }
    }

    @Test
    fun `churn from many threads leaves no index residue`() {
        val users = List(4) { Uuid.random() }
        val threads = 8
        val rounds = 200
        val start = CountDownLatch(1)
        val workers = (0 until threads).map { t ->
            Thread {
                start.await()
                repeat(rounds) { r ->
                    val user = users[(t + r) % users.size]
                    val session = FakeSession()
                    manager.addUserSession(user, session)
                    check(manager.getUserIdBySession(session) == user) { "session lost its owner" }
                    manager.removeUserSession(session)
                }
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join() }

        // Every session was added and removed again: nothing may remain.
        assertEquals(0, manager.getSessionStats()["activeUsers"])
        users.forEach { assertFalse(manager.isUserOnline(it)) }
    }
}
