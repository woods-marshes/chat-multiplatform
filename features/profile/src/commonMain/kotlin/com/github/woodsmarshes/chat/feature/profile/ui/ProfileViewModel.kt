package com.github.woodsmarshes.chat.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.profile.model.ProfileUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Offline-first profile: Room is the single source of truth and the UI only
 * observes [UserRepository.getUserFlow]. Entering the screen shows the cached
 * row immediately (if any) while [refresh] pulls the latest data from the
 * network and writes it into storage.
 */
class ProfileViewModel(
    userId: String,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val parsedId = runCatching { Uuid.parse(userId) }.getOrNull()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var hasCachedUser = false
    private var refreshJob: Job? = null
    private var chatJob: Job? = null

    init {
        if (parsedId == null) {
            _uiState.value = _uiState.value.copy(notFound = true)
        } else {
            observeUser(parsedId)
            refresh()
        }
    }

    private fun observeUser(userId: Uuid) {
        viewModelScope.launch {
            userRepository.getUserFlow(userId).collect { user ->
                if (user != null) {
                    hasCachedUser = true
                    _uiState.value = _uiState.value.copy(
                        userId = user.id.toString(),
                        displayName = user.displayName ?: "",
                        username = user.username,
                        avatarUrl = user.avatarUrl,
                        bio = user.bio,
                        email = user.email,
                        isLoading = false,
                        error = null,
                        notFound = false,
                    )
                }
            }
        }
    }

    /** Pulls the latest profile from the network into local storage. */
    fun refresh() {
        val userId = parsedId ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            userRepository.fetchUserDetail(userId).onErr { err ->
                when {
                    // The server answers a typed NotFound (404 body carries the
                    // serialized UserError); the user is gone, cached row or not.
                    // Network errors and unparseable bodies degrade to
                    // UserError.Unknown, so this branch never fires for those.
                    err is UserError.NotFound -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notFound = true,
                    )
                    // Generic failure: only surface it when there is no cached
                    // data to fall back to.
                    !hasCachedUser -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = strings.profileLoadFailed,
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    /**
     * Creates (or reuses) a direct chat with this user; the conversation is
     * idempotent on the server, so the button is safe for non-friends too.
     */
    fun startChat(onChatReady: (conversationId: String) -> Unit) {
        val userId = parsedId ?: return
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStartingChat = true, actionError = null)
            conversationRepository.createDirectChat(userId).onOk { conversation ->
                _uiState.value = _uiState.value.copy(isStartingChat = false)
                onChatReady(conversation.id.toString())
            }.onErr {
                _uiState.value = _uiState.value.copy(
                    isStartingChat = false,
                    actionError = strings.openChatFailed,
                )
            }
        }
    }
}
