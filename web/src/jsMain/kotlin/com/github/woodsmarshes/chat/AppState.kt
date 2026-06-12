package com.github.woodsmarshes.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.woodsmarshes.chat.common.utils.NetworkMonitor
import com.github.woodsmarshes.chat.common.utils.debug
import com.github.woodsmarshes.chat.common.utils.error
import com.github.woodsmarshes.chat.data.repository.MessageRepository
import com.github.woodsmarshes.chat.data.repository.UserRepository
import com.github.woodsmarshes.chat.network.api.websocket.WebSocketState
import com.github.woodsmarshes.chat.network.error.HttpErrorEvent
import com.github.woodsmarshes.chat.network.error.HttpErrorEventChannel
import com.varabyte.kobweb.core.PageContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val LocalAppState = compositionLocalOf<AppState> {
    error("No AppState provided")
}
@Composable
fun rememberAppState(
    networkMonitor: NetworkMonitor,
    httpErrorEventChannel: HttpErrorEventChannel,
    userRepository: UserRepository,
    messageRepository: MessageRepository,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): AppState = remember(
    coroutineScope,
    httpErrorEventChannel,
    userRepository,
    messageRepository,
) {
    AppState(
        coroutineScope = coroutineScope,
        httpErrorEventChannel = httpErrorEventChannel,
        userRepository = userRepository,
        messageRepository = messageRepository,
        networkMonitor = networkMonitor,
    )
}

@OptIn(FlowPreview::class)
@Stable
class AppState(
    val coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
    httpErrorEventChannel: HttpErrorEventChannel,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
) {
    private val log = KotlinLogging.logger {}

    val isOffline = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
    val isLogIn: StateFlow<Boolean?> = userRepository.authToken
        .map { it.jwtToken?.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
//        httpErrorEventChannel.errorEvents
//            .filterIsInstance<HttpErrorEvent.Unauthorized>()
//            .onEach { event ->
//                val currentIsLoggedIn = isLoggedIn.value
//
//                log.debug(
//                    tag = "AppState",
//                    message = "Unauthorized event received: $event, $currentIsLoggedIn, $isLoggedIn"
//                )
//
//                coroutineScope.launch {
//                    userRepository.logout()
//                    messageRepository.disconnectWebSocket()
//                }
//
//            }
//            .catch { e -> log.error(tag = "AppState", message = "监听 HTTP 错误事件时出错", throwable = e) }
//            .launchIn(coroutineScope)
//
//        isLoggedIn
//            .filterNotNull()
//            .distinctUntilChanged()
//            .onEach { loggedIn ->
//                if (!loggedIn && !isLoginPage) {
//                    Log.info(tag = "AppState", message = "检测到未登录且不在登录页，导航到登录页")
//                    navigateToLogin()
//                }
//            }
//            .catch { e -> log.error(tag = "AppState", message = "监听登录状态时出错", throwable = e) }
//            .launchIn(coroutineScope)


        combine(
            isLogIn,
            isOffline,
            messageRepository.websocketConnectionState,
            ::Triple
        )
            .distinctUntilChanged()
            .debounce(500)
            .onEach { (loggedIn, offline, wsState) ->
                if (loggedIn == true && !offline) {

                    when (wsState) {
                        WebSocketState.Connected -> {
                            log.debug(tag = "AppState", message = "WebSocket is Connected")
                        }
                        WebSocketState.Connecting -> {
                            log.debug(tag = "AppState", message = "WebSocket is Connecting")
                        }
                        WebSocketState.Disconnected -> {
                            log.debug(tag = "AppState", message = "WebSocket is Disconnected")
                            messageRepository.connectWebSocket()
                        }
                        is WebSocketState.Error -> {
                            log.debug(tag = "AppState", message = wsState.message)
                            messageRepository.connectWebSocket()
                        }
                    }

                } else {
                    log.debug(tag = "AppState", message = "not login or offline")
                    if (wsState is WebSocketState.Connected) {
                        messageRepository.disconnectWebSocket()
                    }
                }
            }
            .catch { e -> log.error(tag = "AppState", message = "WebSocket state observation error", throwable = e) }
            .launchIn(coroutineScope)
    }
}