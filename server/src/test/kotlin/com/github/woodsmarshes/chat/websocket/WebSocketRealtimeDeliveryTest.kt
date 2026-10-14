package com.github.woodsmarshes.chat.websocket

import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SocketErrorResponse
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.readReason
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketRealtimeDeliveryTest {

    private val sessions = SessionIndex()
    private val participants = mockk<ConversationParticipantRepository>()
    private val logger = mockk<io.ktor.util.logging.Logger>(relaxUnitFun = true)

    // Unconfined so the writer launch starts eagerly.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val sent = ConcurrentLinkedQueue<Pair<Uuid, RealtimeEvent>>()

    private val delivery = WebSocketRealtimeDelivery(
        sessions = sessions,
        participants = participants,
        logger = logger,
        scope = scope,
        sender = { session, event -> sent.add(session.coroutineContext[SessionIdKey]!!.id to event) },
    )

    /** Tags a fake session with an identity the fake sender can record. */
    class SessionIdKey(val id: Uuid) : AbstractCoroutineContextElement(SessionIdKey) {
        companion object Key : CoroutineContext.Key<SessionIdKey>
    }

    private class FakeSession(val id: Uuid = Uuid.random(), live: Boolean = true) : WebSocketServerSession {
        val job = Job().apply { if (!live) cancel() }
        var closedReason: CloseReason? = null
        var onSendCloseFrame: (suspend () -> Unit)? = null
        var onTerminate: (() -> Unit)? = null

        override val call: ApplicationCall get() = error("not needed")
        override val coroutineContext: CoroutineContext = job + SessionIdKey(id)
        override var masking: Boolean = false
        override var maxFrameSize: Long = Long.MAX_VALUE
        override val incoming: ReceiveChannel<Frame> = Channel(Channel.UNLIMITED)
        override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override suspend fun send(frame: Frame) {
            if (frame is Frame.Close) {
                closedReason = frame.readReason()
                onSendCloseFrame?.invoke()
                terminate()
            }
        }
        override suspend fun flush() {}
        override fun terminate() {
            onTerminate?.invoke()
            job.cancel()
        }
    }

    private class PausableDispatcher : CoroutineDispatcher() {
        val taskQueue = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            taskQueue.add(block)
        }
    }

    private fun participant(userId: Uuid, conversationId: Uuid) = ConversationParticipant(
        conversationId = conversationId,
        userId = userId,
        role = ConversationRole.PARTICIPANT,
        lastReadMessageId = null,
        joinedAt = Clock.System.now(),
        settings = ParticipantSettings(),
    )

    private fun event() = mockk<RealtimeEvent>()

    @Test
    fun conversationEventsReachEveryOnlineMemberSession() {
        val conversationId = Uuid.random()
        val userA = Uuid.random()
        val userB = Uuid.random()
        val sessionA1 = FakeSession(Uuid.random(), live = true)
        val sessionA2 = FakeSession(Uuid.random(), live = true)
        val sessionB = FakeSession(Uuid.random(), live = true)
        sessions.add(userA, sessionA1)
        sessions.add(userA, sessionA2)
        sessions.add(userB, sessionB)
        delivery.registerSession(sessionA1)
        delivery.registerSession(sessionA2)
        delivery.registerSession(sessionB)
        coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
            participant(userA, conversationId),
            participant(userB, conversationId),
        )
        val payload = event()

        runBlocking { delivery.sendToConversation(conversationId, payload) }

        assertEquals(
            setOf(sessionA1.id, sessionA2.id, sessionB.id),
            sent.map { it.first }.toSet(),
        )
        assertEquals(listOf(payload, payload, payload), sent.map { it.second })
    }

    @Test
    fun inactiveSessionsAreSkipped() {
        val conversationId = Uuid.random()
        val userId = Uuid.random()
        val deadSession = FakeSession(Uuid.random(), live = false)
        val liveSession = FakeSession(Uuid.random(), live = true)
        sessions.add(userId, deadSession)
        sessions.add(userId, liveSession)
        delivery.registerSession(deadSession)
        delivery.registerSession(liveSession)
        coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
            participant(userId, conversationId)
        )

        runBlocking { delivery.sendToConversation(conversationId, event()) }

        assertEquals(setOf(liveSession.id), sent.map { it.first }.toSet())
    }

    @Test
    fun sendToUserIgnoresUnknownUsers() {
        runBlocking { delivery.sendToUser(Uuid.random(), event()) }

        assertEquals(emptyList(), sent.toList())
    }

    // --- Scenarios for Bounded Outbound Per-Connection Queue & Lifecycle ---

    /**
     * Scenario 1: Multiple frames sent to the same connection are delivered in
     * identical order with strictly serialized sender execution (max concurrency = 1).
     */
    @Test
    fun multipleFramesOnSameConnectionSentInOrderWithMaxConcurrencyOne() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val activeConcurrency = AtomicInteger(0)
        val maxConcurrency = AtomicInteger(0)
        val received = mutableListOf<RealtimeEvent>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
            sender = { _, event ->
                val current = activeConcurrency.incrementAndGet()
                maxConcurrency.updateAndGet { maxOf(it, current) }
                delay(10)
                received.add(event)
                activeConcurrency.decrementAndGet()
            }
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            val events = List(5) { mockk<RealtimeEvent>() }
            events.forEach { customDelivery.sendToSession(session, it) }

            withTimeout(2000) {
                while (received.size < 5) delay(10)
            }
            assertEquals(events, received)
            assertEquals(1, maxConcurrency.get(), "Sender must execute strictly sequentially with max concurrency 1")
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 2: Artificially blocked sender triggers bounded queue overflow,
     * closing the connection with VIOLATED_POLICY without leaking coroutines.
     */
    @Test
    fun blockedSenderTriggersBoundedQueueOverflowAndClosesConnection() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val senderStarted = CompletableDeferred<Unit>()
        val blockGate = CompletableDeferred<Unit>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 2,
            sender = { _, _ ->
                senderStarted.complete(Unit)
                blockGate.await()
            }
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            val e1 = mockk<RealtimeEvent>()
            val e2 = mockk<RealtimeEvent>()
            val e3 = mockk<RealtimeEvent>()
            val overflow = mockk<RealtimeEvent>()

            // e1 is enqueued and immediately picked up by the concurrent writer
            customDelivery.sendToSession(session, e1)
            senderStarted.await()

            // e2 and e3 fill the channel buffer (capacity = 2)
            customDelivery.sendToSession(session, e2)
            customDelivery.sendToSession(session, e3)
            // 4th event overflows the queue
            assertFalse(customDelivery.sendToSession(session, overflow))

            // Overflow triggers asynchronous teardown: session is removed from SessionIndex,
            // closed with VIOLATED_POLICY, and writer channel is closed.
            withTimeout(2000) {
                while (sessions.getUser(session) != null || session.isActive) {
                    delay(10)
                }
            }
            assertNull(sessions.getUser(session), "Overflowed session must be removed from SessionIndex")
            assertFalse(session.isActive, "Overflowed session must be closed")
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, session.closedReason?.code)

            blockGate.complete(Unit)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 3: One slow connection does not block a fast connection in the same conversation.
     */
    @Test
    fun slowConnectionDoesNotBlockFastConnection() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val conversationId = Uuid.random()
        val userA = Uuid.random()
        val userB = Uuid.random()
        val sessionFast = FakeSession(live = true)
        val sessionSlow = FakeSession(live = true)
        sessions.add(userA, sessionFast)
        sessions.add(userB, sessionSlow)
        coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
            participant(userA, conversationId),
            participant(userB, conversationId),
        )
        val slowGate = CompletableDeferred<Unit>()
        val fastReceived = mutableListOf<RealtimeEvent>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 2,
            sender = { session, event ->
                if (session == sessionSlow) {
                    slowGate.await()
                } else {
                    fastReceived.add(event)
                }
            }
        )
        try {
            assertTrue(customDelivery.registerSession(sessionFast))
            assertTrue(customDelivery.registerSession(sessionSlow))
            val events = List(5) { mockk<RealtimeEvent>() }
            // Broadcast 5 events: fast session receives all 5 promptly; slow session overflows
            events.forEach {
                customDelivery.sendToConversation(conversationId, it)
                delay(5)
            }

            withTimeout(2000) {
                while (fastReceived.size < 5) delay(10)
            }
            assertEquals(events, fastReceived, "Fast session must receive all events promptly")
            assertNull(sessions.getUser(sessionSlow), "Slow session must overflow and be removed")

            slowGate.complete(Unit)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 4: Two devices for the same user; overflow on one does not close the other.
     */
    @Test
    fun overflowOnOneDeviceDoesNotCloseAnotherDeviceOfSameUser() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val user = Uuid.random()
        val mobile = FakeSession(live = true)
        val web = FakeSession(live = true)
        sessions.add(user, mobile)
        sessions.add(user, web)
        val webGate = CompletableDeferred<Unit>()
        val mobileReceived = mutableListOf<RealtimeEvent>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 2,
            sender = { session, event ->
                if (session == web) {
                    webGate.await()
                } else {
                    mobileReceived.add(event)
                }
            }
        )
        try {
            assertTrue(customDelivery.registerSession(mobile))
            assertTrue(customDelivery.registerSession(web))
            val events = List(5) { mockk<RealtimeEvent>() }
            events.forEach {
                customDelivery.sendToUser(user, it)
                delay(5)
            }

            withTimeout(2000) {
                while (mobileReceived.size < 5) delay(10)
            }
            assertEquals(events, mobileReceived, "Mobile session must receive all events")
            assertTrue(mobile.isActive, "Mobile session must remain active")
            assertEquals(listOf(mobile), sessions.getSessions(user), "Only mobile should remain in session index")
            assertFalse(web.isActive, "Web session must be closed on overflow")

            webGate.complete(Unit)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 5: ACK, error responses, initial heads, and broadcasts all go through
     * the unified per-connection writer.
     */
    @Test
    fun ackErrorAndBroadcastAllGoThroughUnifiedDelivery() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val conversationId = Uuid.random()
        val user = Uuid.random()
        val session = FakeSession(live = true)
        sessions.add(user, session)
        val enqueued = mutableListOf<RealtimeEvent>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
            sender = { _, event -> enqueued.add(event) }
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            val heads = mockk<SyncStateResponse.Heads>()
            val ack = mockk<MessageEventResponse.Received>()
            val error = mockk<SocketErrorResponse>()
            val broadcast = mockk<MessageEventResponse.Withdrawn>()

            customDelivery.sendToSession(session, heads)
            customDelivery.sendToSession(session, ack)
            customDelivery.sendToSession(session, error)
            coEvery { participants.getConversationParticipants(conversationId) } returns listOf(
                participant(user, conversationId)
            )
            customDelivery.sendToConversation(conversationId, broadcast)

            withTimeout(2000) {
                while (enqueued.size < 4) delay(10)
            }
            assertEquals(listOf(heads, ack, error, broadcast), enqueued)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 6: Disconnect, sender exceptions, and repeated cleanup release all resources cleanly.
     */
    @Test
    fun disconnectSendExceptionAndIdempotentCleanupReleaseResources() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val user = Uuid.random()
        val session = FakeSession(live = true)
        sessions.add(user, session)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
            sender = { _, _ -> throw IOException("connection reset by peer") }
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            // Send triggers sender exception -> writer terminates and cleans up
            customDelivery.sendToSession(session, mockk())
            withTimeout(2000) {
                while (sessions.getUser(session) != null || session.isActive) delay(10)
            }
            assertNull(sessions.getUser(session))
            assertFalse(session.isActive)
            assertEquals(CloseReason.Codes.GOING_AWAY.code, session.closedReason?.code)

            // Idempotent repeat cleanup
            customDelivery.closeSession(session)
            customDelivery.closeSession(session)
            assertEquals(0, customDelivery.getActiveWritersCount())
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 7: Application shutdown cleanly cancels all connection writers without residue.
     */
    @Test
    fun applicationShutdownStopsAllConnectionWritersWithoutResidue() = runBlocking {
        val userA = Uuid.random()
        val userB = Uuid.random()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val s1 = FakeSession(live = true)
        val s2 = FakeSession(live = true)
        val s3 = FakeSession(live = true)
        sessions.add(userA, s1)
        sessions.add(userA, s2)
        sessions.add(userB, s3)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = appScope,
            queueCapacity = 10,
            sender = { _, _ -> delay(500) }
        )
        assertTrue(customDelivery.registerSession(s1))
        assertTrue(customDelivery.registerSession(s2))
        assertTrue(customDelivery.registerSession(s3))

        customDelivery.sendToSession(s1, mockk())
        customDelivery.sendToSession(s2, mockk())
        customDelivery.sendToSession(s3, mockk())

        val w1 = customDelivery.getWriterForTest(s1)
        val w2 = customDelivery.getWriterForTest(s2)
        val w3 = customDelivery.getWriterForTest(s3)
        assertNotNull(w1)
        assertNotNull(w2)
        assertNotNull(w3)
        assertTrue(w1.job?.isActive == true)
        assertTrue(w2.job?.isActive == true)
        assertTrue(w3.job?.isActive == true)

        // Application shuts down: scope is cancelled
        appScope.cancel()

        withTimeout(2000) {
            while (w1.job?.isActive == true || w2.job?.isActive == true || w3.job?.isActive == true || customDelivery.getActiveWritersCount() > 0) delay(10)
        }
        assertTrue(w1.job?.isCancelled == true)
        assertTrue(w2.job?.isCancelled == true)
        assertTrue(w3.job?.isCancelled == true)
        assertTrue(w1.channel.isClosedForSend)
        assertTrue(w2.channel.isClosedForSend)
        assertTrue(w3.channel.isClosedForSend)
        assertEquals(0, customDelivery.getActiveWritersCount(), "All active writers must be removed from writers map")
        assertNull(sessions.getUser(s1), "s1 must be removed from SessionIndex on cancellation")
        assertNull(sessions.getUser(s2), "s2 must be removed from SessionIndex on cancellation")
        assertNull(sessions.getUser(s3), "s3 must be removed from SessionIndex on cancellation")
    }

    /**
     * Scenario 8: Delay socket close, send another frame after overflow; verify
     * that no second writer is created, the send is rejected, and the sender is
     * never invoked for the post-overflow send.
     */
    @Test
    fun delayedSocketCloseAfterOverflowRejectsSubsequentSendsWithoutCreatingNewWriter() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val blockSenderGate = CompletableDeferred<Unit>()
        val senderInvocations = AtomicInteger(0)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 2,
            sender = { _, _ ->
                senderInvocations.incrementAndGet()
                blockSenderGate.await()
            }
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            val e1 = mockk<RealtimeEvent>()
            val e2 = mockk<RealtimeEvent>()
            val e3 = mockk<RealtimeEvent>()
            val e4 = mockk<RealtimeEvent>()

            customDelivery.sendToSession(session, e1)
            withTimeout(2000) {
                while (senderInvocations.get() < 1) delay(5)
            }
            customDelivery.sendToSession(session, e2)
            customDelivery.sendToSession(session, e3)
            // e4 overflows and triggers teardown
            assertFalse(customDelivery.sendToSession(session, e4), "Overflow send must return false")

            // Socket close is asynchronous and sender is still blocked on blockSenderGate.
            // A concurrent or subsequent send arrives for the same session.
            val e5 = mockk<RealtimeEvent>()
            assertFalse(customDelivery.sendToSession(session, e5), "Send to overflowed/terminated session must be rejected")

            // Verify: exactly 0 active writers remain in the map (no second writer recreated!)
            assertEquals(0, customDelivery.getActiveWritersCount(), "Terminated session must not have active writer")
            // Verify: sender was only invoked once (for e1), never for e5!
            assertEquals(1, senderInvocations.get(), "Sender must not be invoked for post-overflow sends")

            blockSenderGate.complete(Unit)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    @Test
    fun queueCapacityMustBePositiveFiniteInteger() {
        assertFailsWith<IllegalArgumentException> {
            WebSocketRealtimeDelivery(sessions, participants, logger, scope, queueCapacity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            WebSocketRealtimeDelivery(sessions, participants, logger, scope, queueCapacity = Channel.UNLIMITED)
        }
    }

    /**
     * Scenario 9: Duplicate or concurrent registration of the same session must be
     * idempotent. Candidate / loser registration attempts must NEVER tear down the
     * live connection or writer owned by the winner.
     */
    @Test
    fun concurrentDuplicateRegistrationIsIdempotentAndDoesNotCloseLiveConnection() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val received = mutableListOf<RealtimeEvent>()
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
            sender = { _, event -> received.add(event) }
        )
        try {
            // First registration succeeds
            assertTrue(customDelivery.registerSession(session))
            // Duplicate registration returns false
            assertFalse(customDelivery.registerSession(session))

            // Concurrent duplicate registration attempts from multiple threads
            val latch = CountDownLatch(1)
            val threads = List(10) {
                Thread {
                    latch.await()
                    customDelivery.registerSession(session)
                }
            }
            threads.forEach { it.start() }
            latch.countDown()
            threads.forEach { it.join() }

            // Exactly 1 writer must be active, and the session must remain live
            assertEquals(1, customDelivery.getActiveWritersCount())
            assertTrue(session.isActive, "Session must not be closed by duplicate registration attempts")

            // The connection must still function normally
            val payload = mockk<RealtimeEvent>()
            assertTrue(customDelivery.sendToSession(session, payload))
            withTimeout(2000) {
                while (received.isEmpty()) delay(10)
            }
            assertEquals(listOf(payload), received)
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Scenario 10: Concurrent registration racing delivery.close() must never
     * leave a lingering writer registered in the map after close completes.
     */
    @Test
    fun concurrentRegistrationAndDeliveryCloseNeverLeavesLingerWriters() = runBlocking {
        repeat(50) { round ->
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val customDelivery = WebSocketRealtimeDelivery(
                sessions = sessions,
                participants = participants,
                logger = logger,
                scope = appScope,
                queueCapacity = 10,
            )
            val candidateSessions = List(20) { FakeSession(live = true) }
            val latch = CountDownLatch(1)
            val threads = candidateSessions.map { s ->
                Thread {
                    latch.await()
                    customDelivery.registerSession(s)
                }
            } + listOf(
                Thread {
                    latch.await()
                    customDelivery.close()
                }
            )
            threads.forEach { it.start() }
            latch.countDown()
            threads.forEach { it.join() }

            // Once close has settled, 0 active writers must linger
            assertEquals(0, customDelivery.getActiveWritersCount(), "Round $round leaked a writer during close race")
            appScope.cancel()
        }
    }

    /**
     * Scenario 11: Repeatedly connecting, sending, and disconnecting must not
     * leak memory in any internal container over the lifetime of the server.
     */
    @Test
    fun repeatedConnectAndDisconnectLeavesZeroResidueAcrossContainers() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
            sender = { _, _ -> }
        )
        try {
            val user = Uuid.random()
            repeat(500) {
                val session = FakeSession(live = true)
                sessions.add(user, session)
                customDelivery.registerSession(session)
                customDelivery.sendToSession(session, mockk())
                customDelivery.closeSession(session)
            }

            assertEquals(0, customDelivery.getActiveWritersCount(), "No writers should remain active")
            assertFalse(sessions.isOnline(user), "User must be offline in SessionIndex")
            assertEquals(0, sessions.size(), "SessionIndex must have 0 active users")
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Issue 1: Direct closeSession() must actively close the underlying socket with
     * the requested reason and evict from SessionIndex, without relying on scope.cancel().
     */
    @Test
    fun closeSessionActivelyClosesUnderlyingSocketWithoutRelyingOnScopeCancel() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            assertTrue(session.isActive)

            // Direct active close
            customDelivery.closeSession(session)

            withTimeout(2000) {
                while (session.isActive || sessions.getUser(session) != null) delay(10)
            }
            assertFalse(session.isActive, "Direct closeSession must actively close the socket")
            assertEquals(CloseReason.Codes.NORMAL.code, session.closedReason?.code)
            assertEquals(0, customDelivery.getActiveWritersCount(), "Writers map must be cleared")
            assertNull(sessions.getUser(session), "Session must be evicted from SessionIndex")
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Issue 1: Direct delivery.close() must actively close ALL underlying sockets
     * with GOING_AWAY without relying on scope.cancel().
     */
    @Test
    fun deliveryCloseActivelyClosesAllUnderlyingSockets() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val s1 = FakeSession(live = true)
        val s2 = FakeSession(live = true)
        val s3 = FakeSession(live = true)
        val userA = Uuid.random()
        val userB = Uuid.random()
        sessions.add(userA, s1)
        sessions.add(userA, s2)
        sessions.add(userB, s3)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
        )
        try {
            assertTrue(customDelivery.registerSession(s1))
            assertTrue(customDelivery.registerSession(s2))
            assertTrue(customDelivery.registerSession(s3))

            // Direct delivery shutdown
            customDelivery.close()

            withTimeout(2000) {
                while (s1.isActive || s2.isActive || s3.isActive || sessions.size() > 0) delay(10)
            }
            assertFalse(s1.isActive, "s1 must be actively closed by delivery.close()")
            assertFalse(s2.isActive, "s2 must be actively closed by delivery.close()")
            assertFalse(s3.isActive, "s3 must be actively closed by delivery.close()")
            assertEquals(CloseReason.Codes.GOING_AWAY.code, s1.closedReason?.code)
            assertEquals(CloseReason.Codes.GOING_AWAY.code, s2.closedReason?.code)
            assertEquals(CloseReason.Codes.GOING_AWAY.code, s3.closedReason?.code)
            assertEquals(0, customDelivery.getActiveWritersCount())
            assertEquals(0, sessions.size())
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Simulated test: Cleanup hook executes on initialization failure (e.g. getHeads throws),
     * releasing writer and SessionIndex without residue.
     */
    @Test
    fun simulatedRouteInitializationFailureCleansUpWriterAndSessionIndex() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
        )
        try {
            assertTrue(customDelivery.registerSession(session))
            assertEquals(1, customDelivery.getActiveWritersCount())
            assertEquals(user, sessions.getUser(session))

            // Simulate route finally block executing cleanup after exception in initialize
            customDelivery.closeSession(session)
            sessions.remove(session)

            withTimeout(2000) {
                while (session.isActive || sessions.getUser(session) != null) delay(10)
            }
            assertEquals(0, customDelivery.getActiveWritersCount(), "Writer must be evicted on initialization failure")
            assertNull(sessions.getUser(session), "SessionIndex must not hold failing session")
            assertFalse(session.isActive, "Socket must be closed on initialization failure")
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Lifecycle boundary: If the parent scope is already cancelled prior to registration,
     * registerSession must fail immediately without storing any writer in the map.
     */
    @Test
    fun registrationFailsWhenParentScopeIsAlreadyCancelled() = runBlocking {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope.cancel()
        val s = FakeSession(live = true)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = appScope,
            queueCapacity = 10,
        )
        assertFalse(customDelivery.registerSession(s), "Registration must fail on cancelled parent scope")
        assertEquals(0, customDelivery.getActiveWritersCount(), "No writer must be stored in map")
        assertFalse(customDelivery.sendToSession(s, mockk()), "Send must be rejected")
    }

    /**
     * Lifecycle boundary: Controlled deterministic dispatch where registration succeeds
     * but parent scope is cancelled BEFORE the lazy writer coroutine body executes.
     * The invokeOnCompletion handler must guarantee eviction from writers Map, SessionIndex,
     * and channel cancellation without leaving orphan state.
     */
    @Test
    fun cancellingScopeBeforeLazyWriterStartsCleansUpMapSessionIndexAndChannel() = runBlocking {
        val pausableDispatcher = PausableDispatcher()
        val parentJob = SupervisorJob()
        val appScope = CoroutineScope(parentJob + pausableDispatcher)
        val s = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, s)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = appScope,
            queueCapacity = 10,
        )
        // Registration establishes the writer mapping and installs completion handler
        assertTrue(customDelivery.registerSession(s))
        assertEquals(1, customDelivery.getActiveWritersCount())
        assertEquals(user, sessions.getUser(s))
        val writer = customDelivery.getWriterForTest(s)
        assertNotNull(writer)
        assertFalse(writer.channel.isClosedForSend)
        // Coroutine task is queued on pausableDispatcher but has NOT been executed
        assertEquals(1, pausableDispatcher.taskQueue.size)

        // Cancel parent scope before the coroutine body ever runs
        parentJob.cancel()
        // Drain any tasks dispatched to pausableDispatcher (e.g. cancellation dispatch)
        while (pausableDispatcher.taskQueue.isNotEmpty()) {
            pausableDispatcher.taskQueue.removeAt(0).run()
        }

        assertEquals(0, customDelivery.getActiveWritersCount(), "Writer must be evicted from map when scope cancels before start")
        assertNull(sessions.getUser(s), "Session must be evicted from SessionIndex")
        assertTrue(writer.channel.isClosedForSend, "Channel must be cancelled")
        assertTrue(writer.job?.isCancelled == true, "Writer job must be cancelled")
    }

    /**
     * Timeout fallback: When graceful socket close times out,
     * executeCloseWork must fallback to force-terminating the socket scope via session.cancel() to guarantee
     * no lingering connection remains.
     */
    @Test
    fun closeSessionForceTerminatesSocketWhenGracefulCloseTimesOut() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val terminateCalled = CompletableDeferred<Unit>()
        val user = Uuid.random()
        val hungSession = FakeSession(live = true).apply {
            onSendCloseFrame = {
                // Hang on close frame to force timeout in executeCloseWork
                awaitCancellation()
            }
            job.invokeOnCompletion {
                terminateCalled.complete(Unit)
            }
        }
        sessions.add(user, hungSession)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = testScope,
            queueCapacity = 10,
        )
        try {
            assertTrue(customDelivery.registerSession(hungSession))
            // Trigger active close
            customDelivery.closeSession(hungSession)

            // Within 4 seconds (timeout is 2000ms), cancel must be invoked as fallback
            withTimeout(4000) {
                terminateCalled.await()
            }
            assertFalse(hungSession.isActive, "Session must be terminated after graceful close timeout")
            assertEquals(0, customDelivery.getActiveWritersCount())
            assertNull(sessions.getUser(hungSession))
        } finally {
            testScope.cancel()
        }
        Unit
    }

    /**
     * Timeout/cancellation fallback: If the socket close task is queued on the
     * scope's dispatcher but the parent scope is cancelled BEFORE the close coroutine
     * body executes, closeJob.invokeOnCompletion must still ensure item.session.cancel()
     * is invoked as a fallback without needing the coroutine body to run.
     */
    @Test
    fun cancellingScopeBeforeCloseTaskExecutesCancelsSessionViaCompletionHandler() = runBlocking {
        val pausableDispatcher = PausableDispatcher()
        val parentJob = SupervisorJob()
        val appScope = CoroutineScope(parentJob + pausableDispatcher)
        val session = FakeSession(live = true)
        val user = Uuid.random()
        sessions.add(user, session)
        val customDelivery = WebSocketRealtimeDelivery(
            sessions = sessions,
            participants = participants,
            logger = logger,
            scope = appScope,
            queueCapacity = 10,
        )
        // Register session while appScope is active
        assertTrue(customDelivery.registerSession(session))
        assertTrue(session.isActive)

        // Calling closeSession will queue the executeCloseWork launch onto pausableDispatcher
        customDelivery.closeSession(session)
        // At this point, the close task is queued on pausableDispatcher but has NOT run
        assertTrue(pausableDispatcher.taskQueue.isNotEmpty())
        assertTrue(session.isActive, "Session must still be active before close task runs")

        // Cancel parent scope before running the close coroutine body
        parentJob.cancel()
        // Drain any tasks dispatched (e.g. cancellation dispatchers)
        while (pausableDispatcher.taskQueue.isNotEmpty()) {
            pausableDispatcher.taskQueue.removeAt(0).run()
        }

        // Session must be cancelled via closeJob's invokeOnCompletion fallback
        assertFalse(session.isActive, "Session must be cancelled when parent scope cancels before close task body runs")
        assertEquals(0, customDelivery.getActiveWritersCount())
        assertNull(sessions.getUser(session))
    }
}
