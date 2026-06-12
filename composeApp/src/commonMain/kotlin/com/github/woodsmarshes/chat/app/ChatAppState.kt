package com.github.woodsmarshes.chat.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun rememberChatAppState(
    authRepository: AuthRepository,
    realtimeApi: RealtimeApi,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): ChatAppState {
    return remember(
        authRepository,
        realtimeApi,
        coroutineScope,
    ) {
        ChatAppState(
            authRepository = authRepository,
            realtimeApi = realtimeApi,
            coroutineScope = coroutineScope
        )
    }
}

@Stable
class ChatAppState(
    authRepository: AuthRepository,
    realtimeApi: RealtimeApi,
    coroutineScope: CoroutineScope,
) {

    val isLoggedIn: StateFlow<Boolean?> = authRepository.observeIsLoggedIn()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Current real-time WebSocket connection state. */
    val connectionState: StateFlow<ConnectionState> = realtimeApi.connectionState

    init {
        coroutineScope.launch {
            isLoggedIn.collect { loggedIn ->
                when (loggedIn) {
                    true -> realtimeApi.connect()
                    false -> realtimeApi.disconnect()
                    null -> { /* Still loading auth state — wait */ }
                }
            }
        }
    }
}

