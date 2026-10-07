package com.github.woodsmarshes.chat.core.network.api.websocket

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Owns the realtime WebSocket connection.
 *
 * Every connection attempt belongs to a monotonically increasing generation,
 * and every check-then-write against generation, connection state, or the
 * published socket is serialized under [lifecycleMutex] (never held across
 * network I/O or event emission).
 *
 * Each connection attempt owns the socket it opens and always closes it in a
 * [NonCancellable] `finally` block — covering normal server close, read errors,
 * cancellation, and handshakes that completed after their generation expired.
 *
 * [connect] waits for any in-flight [disconnect] to finish before starting a
 * new producer, so old and new producers never overlap on the event stream.
 * [disconnect] runs its teardown on a dedicated non-cancellable scope that
 * shares [scope]'s dispatcher: cancelling [scope] or the [disconnect] caller
 * never abandons socket cleanup or lets `await()` pretend cleanup succeeded,
 * while a cancelled caller still receives its [CancellationException]
 * immediately.
 */
class RealtimeApi(
    private val client: HttpClient,
    private val config: com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig,
    private val authTokenDataSource: AuthTokenDataSource,
    private val tokenRefresher: com.github.woodsmarshes.chat.core.network.ktor.TokenRefresher,
    private val scope: CoroutineScope
) {
    val log = KotlinLogging.logger {}

    private val lifecycleMutex = Mutex()
    private var generation = 0L
    private var connectionJob: Job? = null
    private var inFlightDisconnect: CompletableDeferred<Unit>? = null
    private var session: DefaultClientWebSocketSession? = null

    /**
     * Dedicated process-lived teardown owner using [scope]'s dispatcher but an
     * independent [SupervisorJob]: cancelling [scope] during a session change
     * must not silently cancel in-flight socket cleanup and let callers think
     * teardown succeeded. Process-exit shutdown ownership is tracked separately
     * from per-session logout.
     */
    private val teardownScope = CoroutineScope(
        SupervisorJob() + (scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Default)
    )

    // Buffering decouples the socket read loop from DB-writing consumers: a
    // slow consumer must not backpressure the TCP read side into a stall.
    private val _events = MutableSharedFlow<RealtimeEvent>(replay = 0, extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()

    // Test seams: replaceable transport steps so lifecycle tests need no ktor
    // static mocks. Not part of the public contract; production uses defaults.
    internal var openSession: suspend (token: String) -> DefaultClientWebSocketSession =
        { token ->
            client.webSocketSession(config.wsUrl) {
                url {
                    // 用 URL query param 而非 Header，因为浏览器 WebSocket API
                    // 不支持在握手阶段设置自定义 Header。WSS 下 query string 是 TLS 加密的。
                    parameters.append("access_token", token)
                }
            }
        }
    internal var readEvent: suspend (DefaultClientWebSocketSession) -> RealtimeEvent = { it.receiveDeserialized() }
    internal var closeSocket: suspend (DefaultClientWebSocketSession) -> Unit = { it.close() }
    internal var cancelSocket: (DefaultClientWebSocketSession) -> Unit = { (it as CoroutineScope).cancel() }
    internal var sendFrame: suspend (DefaultClientWebSocketSession, RealtimeEvent) -> Unit = { s, e -> s.sendSerialized(e) }

    /**
     * Waits for any in-flight [disconnect] to complete, then starts the
     * connection loop unless one is already running for the current generation.
     * Old and new producers never overlap.
     */
    suspend fun connect() {
        while (true) {
            val pendingDisconnect = lifecycleMutex.withLock {
                val disconnecting = inFlightDisconnect
                if (disconnecting != null && !disconnecting.isCompleted) {
                    disconnecting
                } else {
                    if (connectionJob?.isActive == true) {
                        log.info { "[RealtimeApi] connect() skipped: already active" }
                        return
                    }
                    check(scope.isActive) { "RealtimeApi owner scope is cancelled; cannot start connection" }
                    val gen = ++generation
                    log.info { "[RealtimeApi] connect() starting connection loop (generation $gen)" }
                    connectionJob = scope.launch { connectionLoop(gen) }
                    return
                }
            }
            pendingDisconnect.await()
        }
    }

    /**
     * Invalidates the current generation, waits for the connection loop to exit
     * and release its socket, and publishes [ConnectionState.Idle].
     *
     * Concurrent callers share the same [CompletableDeferred]; a cancelled
     * caller observes its [CancellationException] immediately while the
     * teardown continues to completion on [teardownScope].
     */
    suspend fun disconnect() {
        val completion = lifecycleMutex.withLock {
            generation += 1
            _connectionState.value = ConnectionState.Idle
            val job = connectionJob
            connectionJob = null
            val previous = inFlightDisconnect
            if (job == null) {
                previous
            } else {
                val done = CompletableDeferred<Unit>()
                inFlightDisconnect = done
                teardownScope.launch {
                    withContext(NonCancellable) {
                        try {
                            previous?.await()
                            job.cancel()
                            job.join()
                            lifecycleMutex.withLock {
                                if (inFlightDisconnect === done) {
                                    inFlightDisconnect = null
                                }
                            }
                            done.complete(Unit)
                        } catch (t: Throwable) {
                            lifecycleMutex.withLock {
                                if (inFlightDisconnect === done) {
                                    inFlightDisconnect = null
                                }
                            }
                            done.completeExceptionally(t)
                        }
                    }
                }
                done
            }
        }
        completion?.await()
    }

    suspend fun send(realtimeEvent: RealtimeEvent) {
        val target = lifecycleMutex.withLock {
            val state = _connectionState.value
            if (state !is ConnectionState.Connected) {
                log.warn { "[RealtimeApi] send() skipped: not connected (state=$state)" }
                null
            } else {
                session
            }
        } ?: return

        try {
            sendFrame(target, realtimeEvent)
            log.info { "[RealtimeApi] send() serialized OK" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "[RealtimeApi] send() error: ${e.message}" }
        }
    }

    private suspend fun updateState(gen: Long, state: ConnectionState) {
        lifecycleMutex.withLock {
            if (generation == gen && currentCoroutineContext().isActive) {
                _connectionState.value = state
            }
        }
    }

    private suspend fun isCurrentGeneration(gen: Long): Boolean = lifecycleMutex.withLock {
        generation == gen && currentCoroutineContext().isActive
    }

    private suspend fun tryPublishSocket(gen: Long, socket: DefaultClientWebSocketSession): Boolean =
        lifecycleMutex.withLock {
            if (generation != gen || !currentCoroutineContext().isActive) {
                false
            } else {
                session = socket
                _connectionState.value = ConnectionState.Connected
                true
            }
        }

    private suspend fun connectionLoop(gen: Long) {
        log.info { "Starting connection loop (generation $gen)..." }

        var currentDelay = INITIAL_RETRY_DELAY_MS
        val maxDelay = MAX_RETRY_DELAY_MS

        suspend fun backoff() {
            delay(currentDelay.milliseconds)
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
        }

        while (isCurrentGeneration(gen)) {
            var connectedAt: TimeMark? = null
            try {
                log.info { "[RealtimeApi] connecting..." }
                updateState(gen, ConnectionState.Connecting)

                // Circuit breaker: with dead credentials (refresh rejected
                // with 401) the stored session is cleared and the loop
                // stops — the logged-out state takes over from here.
                val token = tokenRefresher.currentOrRefreshed(client)
                if (token == null) {
                    updateState(gen, ConnectionState.Disconnected("Session expired"))
                    return
                }

                val socket = openSession(token)
                try {
                    if (!tryPublishSocket(gen, socket)) {
                        // The handshake completed after its generation was
                        // invalidated; finally closes the socket with the same
                        // bounded NonCancellable cleanup as every other exit.
                        return
                    }
                    connectedAt = TimeSource.Monotonic.markNow()
                    log.info { "[RealtimeApi] connected, starting observeMessages" }

                    observeMessages(gen, socket)
                } finally {
                    withContext(NonCancellable) {
                        lifecycleMutex.withLock {
                            if (session === socket) session = null
                        }
                        closeSocketBounded(socket)
                    }
                }

                // The server closes idle sessions cleanly, which makes observeMessages
                // return instead of throwing. Without the backoff below the loop would
                // reconnect immediately forever while the state stayed "Connected".
                val uptimeMs = connectedAt?.elapsedNow()?.inWholeMilliseconds
                if (uptimeMs != null && uptimeMs >= STABLE_CONNECTION_MS) {
                    currentDelay = INITIAL_RETRY_DELAY_MS
                }
                updateState(gen, ConnectionState.Disconnected("Connection closed by server"))
                log.info { "[RealtimeApi] connection closed, backing off ${currentDelay}ms" }
                backoff()

            } catch (e: CancellationException) {
                // Cancellation belongs to disconnect(), which owns the terminal
                // state: writing here would let a stale generation clobber a
                // newer connection's state.
                log.info { "Connection loop (generation $gen) cancelled." }
                throw e
            } catch (e: Exception) {
                log.error(e) { "Connection error/Interrupted" }
                updateState(gen, ConnectionState.Disconnected("Error: ${e.message}", e))
                log.info { "Retrying in ${currentDelay}ms..." }
                backoff()
            }
        }
    }

    private suspend fun observeMessages(gen: Long, currentSession: DefaultClientWebSocketSession) {
        log.info { "[RealtimeApi] observeMessages() started" }
        while (currentSession.isActive && isCurrentGeneration(gen)) {
            try {
                val event = readEvent(currentSession)
                log.info { "[RealtimeApi] received event: ${event::class.simpleName}" }
                if (isCurrentGeneration(gen)) {
                    _events.emit(event)
                }
            } catch (e: WebsocketDeserializeException) {
                // The converter raises these for frame-type mismatches;
                // skip the frame and keep the session alive.
                log.error(e) { "[RealtimeApi] deserialize error: ${e.message}" }
            } catch (e: SerializationException) {
                // Raw undecodable payloads (bad protobuf tags, unknown
                // event types from a newer server) escape unwrapped —
                // dropping the frame beats tearing the session down.
                log.error(e) { "[RealtimeApi] undecodable frame: ${e.message}" }
            }
        }
        log.info { "[RealtimeApi] observeMessages() session closed" }
    }

    private suspend fun closeSocketBounded(socket: DefaultClientWebSocketSession) {
        try {
            withTimeout(SOCKET_CLOSE_TIMEOUT) { closeSocket(socket) }
        } catch (e: TimeoutCancellationException) {
            log.warn { "[RealtimeApi] socket close timed out after $SOCKET_CLOSE_TIMEOUT; forcing cancel" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn(e) { "[RealtimeApi] socket close failed; forcing cancel" }
        } finally {
            // Cooperative withTimeout only stops waiting; request cancellation
            // of the WebSocket session scope so a stuck close handshake is
            // signalled to terminate, and log if that request itself fails.
            runCatching { cancelSocket(socket) }
                .onFailure { log.warn(it) { "[RealtimeApi] force socket cancel failed" } }
        }
    }

    private companion object {
        const val INITIAL_RETRY_DELAY_MS = 1000L
        const val MAX_RETRY_DELAY_MS = 10_000L

        /** A connection that lasted this long is treated as healthy, so the backoff resets. */
        const val STABLE_CONNECTION_MS = 30_000L

        /** Best-effort bound for the graceful close frame; cancelSocket enforces termination in finally. */
        val SOCKET_CLOSE_TIMEOUT = 2.seconds
    }
}
