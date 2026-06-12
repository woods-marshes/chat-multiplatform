package com.github.woodsmarshes.chat.core.network.api.websocket

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpHeaders
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RealtimeApi(
    private val client: HttpClient,
    private val config: com.github.woodsmarshes.chat.core.network.ktor.NetworkConfig,
    private val authTokenDataSource: AuthTokenDataSource,
    private val scope: CoroutineScope
) {
    val log = KotlinLogging.logger {}
    private var session: DefaultClientWebSocketSession? = null

    private var connectionJob: Job? = null
    private val _events = MutableSharedFlow<RealtimeEvent>(replay = 0)
    val events = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()

    fun connect() {
        if (connectionJob?.isActive == true) {
            log.info { "[RealtimeApi] connect() skipped: already active" }
            return
        }
        log.info { "[RealtimeApi] connect() starting connection loop" }
        connectionJob = scope.launch {
            log.info { "Starting connection loop..." }

            var currentDelay = 1000L
            val maxDelay = 10000L

            while (isActive) {
                try {
                    log.info { "[RealtimeApi] connecting..." }
                    _connectionState.value = ConnectionState.Connecting

                    val token = authTokenDataSource.jwtToken
                        .filterNotNull()
                        .first { it.isNotEmpty() }

                    session = client.webSocketSession(config.wsUrl) {
                        url {
                            // 用 URL query param 而非 Header，因为浏览器 WebSocket API
                            // 不支持在握手阶段设置自定义 Header。WSS 下 query string 是 TLS 加密的。
                            parameters.append("access_token", token)
                        }
                    }

                    _connectionState.value = ConnectionState.Connected
                    currentDelay = 1000L
                    log.info { "[RealtimeApi] connected, starting observeMessages" }

                    observeMessages()

                } catch (e: Exception) {
                    if (e is CancellationException) {
                        log.info { "Connection job cancelled." }
                        _connectionState.value = ConnectionState.Disconnected("User initiated disconnect")
                        throw e
                    }
                    log.error(e) { "Connection error/Interrupted" }

                    session = null

                    _connectionState.value = ConnectionState.Disconnected("Error: ${e.message}", e)

                    log.info { "Retrying in ${currentDelay}ms..." }
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
                }
            }
        }
    }

    fun disconnect() {
        log.info { "[RealtimeApi] disconnect() called" }
        connectionJob?.cancel()
        connectionJob = null

        val currentSession = session
        scope.launch {
            try {
                currentSession?.close()
            } catch (e: Exception) {

            }
        }
        session = null
        _connectionState.value = ConnectionState.Idle
    }

    suspend fun send(realtimeEvent: RealtimeEvent) {
        val state = _connectionState.value
        log.info { "[RealtimeApi] send() called, state=$state eventType=${realtimeEvent::class.simpleName}" }
        if (state !is ConnectionState.Connected) {
            log.warn { "[RealtimeApi] send() skipped: not connected (state=$state)" }
            return
        }
        try {
            session?.sendSerialized(realtimeEvent)
            log.info { "[RealtimeApi] send() serialized OK" }
        } catch (e: Exception) {
            log.error(e) { "[RealtimeApi] send() error: ${e.message}" }
        }
    }

    suspend fun observeMessages() {
        session?.let { currentSession ->
            log.info { "[RealtimeApi] observeMessages() started" }
            while (currentSession.isActive) {
                try {
                    val event = currentSession.receiveDeserialized<RealtimeEvent>()
                    log.info { "[RealtimeApi] received event: ${event::class.simpleName}" }
                    _events.emit(event)
                } catch (e: WebsocketDeserializeException) {
                    log.error(e) { "[RealtimeApi] deserialize error: ${e.message}" }
                }
            }
            log.info { "[RealtimeApi] observeMessages() session closed" }
        }
    }
}


