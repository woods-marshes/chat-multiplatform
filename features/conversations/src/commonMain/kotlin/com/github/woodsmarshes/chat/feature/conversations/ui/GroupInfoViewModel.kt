package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.conversations.model.GroupInfoUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Offline-first group info: the profile and the member list both stream from
 * Room, while [refresh] pulls the latest detail from the network into local
 * storage. Group profiles found via search are cached by the search itself,
 * so the page opens instantly even before the refresh lands.
 */
class GroupInfoViewModel(
    conversationId: String,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val parsedId = runCatching { Uuid.parse(conversationId) }.getOrNull()

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var joinJob: Job? = null

    init {
        if (parsedId == null) {
            _uiState.value = _uiState.value.copy(notFound = true)
        } else {
            observeGroup(parsedId)
            refresh()
        }
    }

    private fun observeGroup(conversationId: Uuid) {
        viewModelScope.launch {
            combine(
                conversationRepository.getGroupProfileFlow(conversationId),
                conversationRepository.getGroupMembersFlow(conversationId),
                userRepository.getMeFlow(),
            ) { profile, members, me ->
                Triple(profile, members, me)
            }.collect { (profile, members, me) ->
                if (profile != null) {
                    _uiState.value = _uiState.value.copy(
                        conversationId = profile.conversationId.toString(),
                        name = profile.name,
                        handle = profile.handle,
                        avatarUrl = profile.avatarUrl,
                        description = profile.description,
                        members = members,
                        isMember = me != null && members.any { it.id == me.id },
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }

    /** Pulls the latest group detail from the network into local storage. */
    fun refresh() {
        val conversationId = parsedId ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            conversationRepository.refreshGroupDetail(conversationId).onErr { err ->
                when {
                    // The detail endpoint is member-only: for a group found via
                    // search the server answers NotParticipant (403). That is
                    // the expected non-member path — the profile cached by the
                    // search keeps the page rendered and the join button shown,
                    // so nothing is reported here.
                    err is ConversationError.NotParticipant -> {
                        // NotParticipant also covers a dead conversation id.
                        // With nothing cached the page would spin forever;
                        // surface the not-found state instead.
                        if (_uiState.value.conversationId == null) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                notFound = true,
                            )
                        }
                    }
                    // Generic failure: only surface it when there is no cached
                    // data to fall back to.
                    _uiState.value.conversationId == null -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = strings.groupLoadFailed,
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun joinGroup() {
        val conversationId = parsedId ?: return
        val current = _uiState.value
        if (current.isMember || current.isJoining) {
            return
        }
        joinJob?.cancel()
        joinJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isJoining = true, actionError = null)
            conversationRepository.joinGroup(conversationId).onOk {
                // joinGroup syncs conversations, so the member flow re-emits
                // and isMember flips without manual state writes here.
                _uiState.value = _uiState.value.copy(isJoining = false)
            }.onErr {
                _uiState.value = _uiState.value.copy(
                    isJoining = false,
                    actionError = strings.joinGroupFailed,
                )
            }
        }
    }
}
