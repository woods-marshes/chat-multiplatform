package com.github.woodsmarshes.chat.core.network.api.websocket

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig
import com.github.woodsmarshes.chat.core.network.ktor.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Lifecycle contract of the realtime connection: one producer per generation,
 * a disconnect that waits for the producer and socket cleanup to finish even if
 * the owner scope is cancelled, generation-guarded atomic state/socket updates,
 * per-attempt socket ownership across read errors / stale handshakes /
 * cancellation, bounded graceful close with forced scope cancel fallback, and
 * no overlap between old disconnect and new connect.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeApiLifecycleTest {

    private val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val messageId = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val sender = SimpleUser(userId, "user", null, null, now, now, null, UserRole.MEMBER)

    private fun receivedEvent(requestId: String) = MessageEventResponse.Received(
        Message(
            id = messageId,
            conversationId = conversationId,
            sender = sender,
            category = MessageCategory.NORMAL,
            createdAt = now,
            content = TextContent("hello"),
            seq = 1L,
            clientRequestId = Uuid.parse(requestId),
        ),
        conversationId,
        userId,
        requestId,
    )

    /** A controllable socket: the reader parks on a channel, so late feeds are delivered. */
    private class FakeConnection {
        val started = CompletableDeferred<Unit>()
        var closed = false
        var forceCanceled = false
        var readerExited = false
        var nextBehavior: (suspend () -> RealtimeEvent)? = null
        var closeBehavior: (suspend () -> Unit)? = null
        private val inbox = Channel<RealtimeEvent>(Channel.UNLIMITED)
        val session: DefaultClientWebSocketSession = mockk()

        init {
            every { session.isActive } answers { !closed && !forceCanceled }
        }

        fun feed(event: RealtimeEvent) {
            inbox.trySend(event)
        }

        suspend fun next(): RealtimeEvent {
            started.complete(Unit)
            try {
                nextBehavior?.let { return it() }
                return inbox.receive()
            } finally {
                readerExited = true
            }
        }

        suspend fun doClose() {
            closed = true
            closeBehavior?.invoke()
        }

        fun doForceCancel() {
            forceCanceled = true
            closed = true
        }
    }

    private class Harness(
        val api: RealtimeApi,
        val tokenRefresher: TokenRefresher,
        val ownerScope: CoroutineScope,
    ) {
        val connections = mutableListOf<FakeConnection>()
        var opener: (suspend (String) -> DefaultClientWebSocketSession)? = null

        fun register(connection: FakeConnection) {
            connections += connection
        }

        fun bySession(session: DefaultClientWebSocketSession): FakeConnection? =
            connections.firstOrNull { it.session === session }
    }

    private fun TestScope.newApi(): Harness {
        val client = mockk<HttpClient>()
        val authTokenDataSource = mockk<AuthTokenDataSource>(relaxed = true)
        val tokenRefresher = mockk<TokenRefresher>()
        coEvery { tokenRefresher.currentOrRefreshed(any()) } returns "token"
        val ownerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val api = RealtimeApi(
            client = client,
            config = NetworkConfig(host = "127.0.0.1"),
            authTokenDataSource = authTokenDataSource,
            tokenRefresher = tokenRefresher,
            scope = ownerScope,
        )
        val harness = Harness(api, tokenRefresher, ownerScope)
        api.openSession = { token -> harness.opener?.invoke(token) ?: FakeConnection().also { harness.register(it) }.session }
        api.readEvent = { session -> harness.bySession(session)?.next() ?: awaitCancellation() }
        api.closeSocket = { session -> harness.bySession(session)?.doClose() ?: Unit }
        api.cancelSocket = { session -> harness.bySession(session)?.doForceCancel() ?: Unit }
        return harness
    }

    @Test
    fun repeatedConnectsShareOneConnectionLoop() = runTest {
        val harness = newApi()

        harness.api.connect()
        harness.api.connect()
        testScheduler.advanceUntilIdle()

        assertEquals(1, harness.connections.size, "same-generation connects must not spawn a second producer")
        coVerify(exactly = 1) { harness.tokenRefresher.currentOrRefreshed(any()) }
        assertEquals(ConnectionState.Connected, harness.api.connectionState.value)

        harness.api.disconnect()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun disconnectReturnsOnlyAfterTheReaderStopped() = runTest {
        val harness = newApi()
        val api = harness.api
        api.connect()
        testScheduler.advanceUntilIdle()
        val connection = harness.connections.last()
        connection.started.await()
        testScheduler.advanceUntilIdle()

        var disconnectReturned = false
        val stopCaller = launch {
            api.disconnect()
            disconnectReturned = true
        }
        stopCaller.join()
        testScheduler.advanceUntilIdle()

        assertTrue(disconnectReturned)
        assertTrue(connection.closed, "disconnect must close the socket after the reader exited")
        assertTrue(connection.readerExited, "the reader must have exited before disconnect returns")
        assertEquals(ConnectionState.Idle, api.connectionState.value)
    }

    @Test
    fun oldGenerationCannotOverwriteTheTerminalState() = runTest {
        val harness = newApi()
        val api = harness.api
        // A reader whose cancellation surfaces as a plain IOException: the old
        // generation's error handling then runs AFTER disconnect claimed the
        // terminal state — exactly the overwrite the generation guard forbids.
        val connection = FakeConnection().apply {
            nextBehavior = {
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    throw java.io.IOException("injected on cancel", e)
                }
            }
        }
        harness.register(connection)
        harness.opener = { _ -> connection.session }

        api.connect()
        testScheduler.advanceUntilIdle()
        connection.started.await()
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionState.Connected, harness.api.connectionState.value)

        api.disconnect()
        testScheduler.advanceUntilIdle()
        assertEquals(
            ConnectionState.Idle,
            api.connectionState.value,
            "stale teardown must not overwrite the terminal state",
        )

        harness.opener = null
        api.connect()
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionState.Connected, harness.api.connectionState.value, "reconnect works after disconnect")
        assertEquals(2, harness.connections.size)

        api.disconnect()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun disconnectWhileConnectingCancelsWithoutLeakingASocket() = runTest {
        val harness = newApi()
        val api = harness.api
        val openerStarted = CompletableDeferred<Unit>()
        val releaseOpener = CompletableDeferred<Unit>()
        harness.opener = { _ ->
            openerStarted.complete(Unit)
            releaseOpener.await()
            FakeConnection().also { harness.register(it) }.session
        }

        api.connect()
        testScheduler.runCurrent()
        openerStarted.await()
        testScheduler.advanceUntilIdle()

        api.disconnect()
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionState.Idle, api.connectionState.value)
        assertEquals(0, harness.connections.size, "a cancelled handshake must not leak a socket")

        harness.opener = null
        api.connect()
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionState.Connected, api.connectionState.value)

        api.disconnect()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun hangingSocketCloseCannotStallDisconnectAndForcesCancel() = runTest {
        val harness = newApi()
        val api = harness.api
        api.connect()
        testScheduler.advanceUntilIdle()
        val connection = harness.connections.last()
        connection.started.await()
        testScheduler.advanceUntilIdle()
        connection.closeBehavior = { awaitCancellation() }

        var disconnectReturned = false
        val stopCaller = launch {
            api.disconnect()
            disconnectReturned = true
        }
        testScheduler.advanceUntilIdle()

        assertTrue(disconnectReturned, "a hung socket close must not stall disconnect beyond its bound")
        assertEquals(ConnectionState.Idle, api.connectionState.value)
        assertTrue(connection.readerExited, "the reader still stops even when the close hangs")
        assertTrue(connection.forceCanceled, "timeout must fall back to forcing socket scope cancellation")
        stopCaller.join()
    }

    @Test
    fun eventsStopFlowingAfterDisconnect() = runTest {
        val harness = newApi()
        val api = harness.api
        val received = mutableListOf<RealtimeEvent>()
        val collector = launch { api.events.collect { received += it } }
        testScheduler.runCurrent()

        api.connect()
        testScheduler.advanceUntilIdle()
        val connection = harness.connections.last()
        connection.started.await()
        testScheduler.advanceUntilIdle()

        connection.feed(receivedEvent("00000000-0000-0000-0000-0000000000a1"))
        testScheduler.advanceUntilIdle()
        connection.feed(receivedEvent("00000000-0000-0000-0000-0000000000a2"))
        testScheduler.advanceUntilIdle()
        assertEquals(2, received.size)

        api.disconnect()
        testScheduler.advanceUntilIdle()
        connection.feed(receivedEvent("00000000-0000-0000-0000-0000000000a3"))
        testScheduler.advanceUntilIdle()
        assertEquals(2, received.size, "no events may be produced after disconnect")
        collector.cancel()
    }

    @Test
    fun disconnectCallerCancellationPropagatesButTheProducerStillStops() = runTest {
        val harness = newApi()
        val api = harness.api
        val cleanupGate = CompletableDeferred<Unit>()
        var readerFinished = false
        val connection = FakeConnection().apply {
            nextBehavior = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupGate.await()
                        readerFinished = true
                    }
                }
            }
        }
        harness.register(connection)
        harness.opener = { _ -> connection.session }

        api.connect()
        testScheduler.advanceUntilIdle()
        connection.started.await()
        testScheduler.advanceUntilIdle()

        var callerCancellation: CancellationException? = null
        val disconnectCaller = launch {
            try {
                api.disconnect()
            } catch (e: CancellationException) {
                callerCancellation = e
            }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionState.Idle, api.connectionState.value)

        var secondReturned = false
        val secondCaller = launch {
            api.disconnect()
            secondReturned = true
        }
        testScheduler.advanceUntilIdle()

        disconnectCaller.cancel()
        testScheduler.advanceUntilIdle()
        assertNotNull(callerCancellation, "disconnect must not swallow the caller's cancellation")
        assertFalse(readerFinished, "the gated cleanup has not been released yet")
        assertFalse(secondReturned, "a concurrent disconnect must still wait for the reader to exit")

        cleanupGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        secondCaller.join()
        assertTrue(readerFinished)
        assertTrue(connection.closed, "the socket must still close even after the first disconnect caller was cancelled")
        assertTrue(secondReturned)
        assertEquals(ConnectionState.Idle, api.connectionState.value)
    }

    @Test
    fun readErrorClosesFailedSocketBeforeReconnecting() = runTest {
        val harness = newApi()
        val api = harness.api
        val first = FakeConnection().apply {
            nextBehavior = { throw java.io.IOException("read failed") }
        }
        val second = FakeConnection()
        var attempt = 0
        harness.opener = { _ ->
            val picked = if (attempt++ == 0) first else second
            harness.register(picked)
            picked.session
        }

        api.connect()
        // Run the first attempt up to the retry backoff delay without advancing virtual time yet.
        testScheduler.runCurrent()

        assertTrue(first.closed, "a read failure must close its own socket before backing off")
        assertTrue(first.forceCanceled, "closeSocketBounded also cancels the failed socket scope")
        assertTrue(
            api.connectionState.value is ConnectionState.Disconnected,
            "state must reflect the read failure while waiting to retry",
        )

        // Advance past the retry backoff so the second attempt connects and parks on its inbox.
        testScheduler.advanceTimeBy(1_000L)
        testScheduler.runCurrent()
        second.started.await()
        assertEquals(2, harness.connections.size)
        assertEquals(ConnectionState.Connected, api.connectionState.value)

        api.disconnect()
        testScheduler.advanceUntilIdle()
        assertTrue(second.closed)
    }

    @Test
    fun connectWaitsForInFlightDisconnectBeforeStartingNewProducer() = runTest {
        val harness = newApi()
        val api = harness.api
        val cleanupGate = CompletableDeferred<Unit>()
        val first = FakeConnection().apply {
            nextBehavior = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupGate.await()
                    }
                }
            }
        }
        val second = FakeConnection()
        var attempt = 0
        harness.opener = { _ ->
            val picked = if (attempt++ == 0) first else second
            harness.register(picked)
            picked.session
        }

        api.connect()
        testScheduler.advanceUntilIdle()
        first.started.await()

        val disconnectJob: Job = launch { api.disconnect() }
        testScheduler.runCurrent()

        var connectReturned = false
        val reconnectJob: Job = launch {
            api.connect()
            connectReturned = true
        }
        testScheduler.runCurrent()

        assertFalse(connectReturned, "connect must wait while the previous disconnect is still in flight")
        assertEquals(1, harness.connections.size, "no new producer may start before old teardown finishes")
        assertEquals(ConnectionState.Idle, api.connectionState.value)

        cleanupGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        disconnectJob.join()
        reconnectJob.join()

        assertTrue(connectReturned)
        assertEquals(2, harness.connections.size)
        assertEquals(ConnectionState.Connected, api.connectionState.value)

        api.disconnect()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun staleHandshakeClosesItsSocketWithBoundedTimeoutAndForceCancel() = runTest {
        val harness = newApi()
        val api = harness.api
        val handshakeStarted = CompletableDeferred<Unit>()
        val finishHandshake = CompletableDeferred<Unit>()
        val stale = FakeConnection().apply {
            // Even if graceful close hangs on the stale socket, the bounded
            // close + force cancel must terminate it within SOCKET_CLOSE_TIMEOUT.
            closeBehavior = { awaitCancellation() }
        }
        harness.opener = { _ ->
            withContext(NonCancellable) {
                handshakeStarted.complete(Unit)
                finishHandshake.await()
                harness.register(stale)
                stale.session
            }
        }

        api.connect()
        testScheduler.runCurrent()
        handshakeStarted.await()

        var disconnectReturned = false
        val disconnectCaller = launch {
            api.disconnect()
            disconnectReturned = true
        }
        testScheduler.runCurrent()
        assertFalse(disconnectReturned, "disconnect waits for the in-flight handshake to finish and clean up")

        finishHandshake.complete(Unit)
        testScheduler.advanceUntilIdle()
        disconnectCaller.join()

        assertTrue(disconnectReturned)
        assertTrue(stale.closed, "the stale handshake socket must be closed via closeSocketBounded")
        assertTrue(stale.forceCanceled, "a hung close on a stale handshake must still force-cancel the socket")
        assertFalse(stale.readerExited, "the stale socket must never enter observeMessages")
        assertEquals(ConnectionState.Idle, api.connectionState.value)
    }

    @Test
    fun cancellingOwnerScopeDoesNotMakeDisconnectReturnBeforeCleanupFinishes() = runTest {
        val harness = newApi()
        val api = harness.api
        val cleanupGate = CompletableDeferred<Unit>()
        var cleanupCompleted = false
        val connection = FakeConnection().apply {
            nextBehavior = {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupGate.await()
                        cleanupCompleted = true
                    }
                }
            }
        }
        harness.register(connection)
        harness.opener = { _ -> connection.session }

        api.connect()
        testScheduler.advanceUntilIdle()
        connection.started.await()

        // Cancel the injected owner scope BEFORE disconnect completes: disconnect
        // must still wait for the real socket cleanup rather than returning early.
        harness.ownerScope.cancel()
        var disconnectReturned = false
        val disconnectCaller = launch {
            api.disconnect()
            disconnectReturned = true
        }
        testScheduler.runCurrent()

        assertFalse(cleanupCompleted)
        assertFalse(
            disconnectReturned,
            "disconnect must not pretend cleanup succeeded while the socket teardown is still running",
        )

        cleanupGate.complete(Unit)
        testScheduler.advanceUntilIdle()
        disconnectCaller.join()

        assertTrue(cleanupCompleted)
        assertTrue(connection.closed)
        assertTrue(connection.forceCanceled)
        assertTrue(disconnectReturned)
    }
}
