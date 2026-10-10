package com.github.woodsmarshes.chat.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.profile.model.ProfileUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class ProfileViewModel(
    userId: String,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val contactRepository: ContactRepository? = null,
) : ViewModel() {

    private val strings
        get() = getLocaleStrings()

    private val parsedId = runCatching { Uuid.parse(userId) }.getOrNull()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var hasCachedUser = false
    private var refreshJob: Job? = null
    private var chatJob: Job? = null

    init {
        if (parsedId == null) {
            _uiState.update { it.copy(notFound = true) }
        } else {
            observeUser(parsedId)
            refresh()
        }
    }

    private fun observeUser(userId: Uuid) {
        viewModelScope.launch {
            userRepository.getMeFlow().collect { me ->
                _uiState.update {
                    it.copy(isOwnProfile = me?.id == userId)
                }
            }
        }
        viewModelScope.launch {
            userRepository.getUserFlow(userId).collect { user ->
                if (user != null) {
                    hasCachedUser = true
                    _uiState.update {
                        it.copy(
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
        val repo = contactRepository
        if (repo != null) {
            viewModelScope.launch {
                repo.getContactFlow(userId)
                    .catch { }
                    .collect { contact ->
                        _uiState.update {
                            it.copy(
                                contactStatus = contact?.status,
                                remark = contact?.alias?.takeIf { s -> s.isNotBlank() }
                                    ?: contact?.nickname?.takeIf { s -> s.isNotBlank() },
                            )
                        }
                    }
            }
            viewModelScope.launch {
                combine(repo.observeAllRequests(), userRepository.getMeFlow()) { requests, me ->
                    requests to me
                }
                    .catch { }
                    .collect { (requests, me) ->
                        val myId = me?.id
                        _uiState.update {
                            it.copy(
                                isFriendRequestPending = myId != null && requests.any {
                                    it.senderId == myId &&
                                        it.receiverId == userId &&
                                        it.status == RequestStatus.PENDING
                                }
                            )
                        }
                    }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionError = null, actionMessage = null) }
    }

    /** Pulls the latest profile from the network into local storage. */
    fun refresh() {
        val userId = parsedId ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            userRepository.fetchUserDetail(userId).onErr { err ->
                when {
                    err is UserError.NotFound -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            notFound = true,
                        )
                    }
                    !hasCachedUser -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = strings.profileLoadFailed,
                        )
                    }
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun startChat(onChatReady: (conversationId: String) -> Unit) {
        val userId = parsedId ?: return
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            _uiState.update { it.copy(isStartingChat = true, actionError = null) }
            conversationRepository.createDirectChat(userId).onOk { conversation ->
                _uiState.update { it.copy(isStartingChat = false) }
                onChatReady(conversation.id.toString())
            }.onErr {
                _uiState.update {
                    it.copy(
                        isStartingChat = false,
                        actionError = strings.openChatFailed,
                    )
                }
            }
        }
    }

    // ---------------- Add Friend ----------------

    fun showAddFriendDialog() {
        _uiState.update { it.copy(showAddFriendDialog = true, addFriendMessage = "") }
    }

    fun dismissAddFriendDialog() {
        if (_uiState.value.isSendingFriendRequest) return
        _uiState.update { it.copy(showAddFriendDialog = false) }
    }

    fun onAddFriendMessageChanged(message: String) {
        _uiState.update { it.copy(addFriendMessage = message) }
    }

    fun sendFriendRequest() {
        val userId = parsedId ?: return
        val repo = contactRepository ?: return
        val msg = _uiState.value.addFriendMessage.trim().ifEmpty { null }
        _uiState.update { it.copy(isSendingFriendRequest = true) }
        viewModelScope.launch {
            repo.sendFriendRequest(userId, msg).onOk {
                _uiState.update {
                    it.copy(
                        isSendingFriendRequest = false,
                        showAddFriendDialog = false,
                        actionMessage = strings.addFriendSent,
                    )
                }
                repo.syncFriends()
            }.onErr {
                _uiState.update {
                    it.copy(
                        isSendingFriendRequest = false,
                        showAddFriendDialog = false,
                        actionError = strings.authOperationFailed,
                    )
                }
            }
        }
    }

    // ---------------- Edit Remark ----------------

    fun showEditRemarkDialog() {
        _uiState.update {
            it.copy(
                showEditRemarkDialog = true,
                editRemarkValue = it.remark.orEmpty(),
            )
        }
    }

    fun dismissEditRemarkDialog() {
        _uiState.update { it.copy(showEditRemarkDialog = false) }
    }

    fun onEditRemarkChanged(value: String) {
        _uiState.update { it.copy(editRemarkValue = value) }
    }

    fun saveRemark() {
        val userId = parsedId ?: return
        val repo = contactRepository ?: return
        val newRemark = _uiState.value.editRemarkValue.trim()
        _uiState.update { it.copy(showEditRemarkDialog = false, remark = newRemark.ifEmpty { null }) }
        viewModelScope.launch {
            // Alias only: nickname is a different column. An empty string
            // deliberately clears the remark — null would mean "no change"
            // server-side.
            repo.updateContactInfo(userId, nickname = null, alias = newRemark).onOk {
                _uiState.update { it.copy(actionMessage = strings.profileSaved) }
            }.onErr {
                _uiState.update { it.copy(actionError = strings.profileSaveFailed) }
            }
        }
    }

    // ---------------- Block / Unblock ----------------

    fun showBlockConfirmDialog() {
        _uiState.update { it.copy(showBlockConfirmDialog = true) }
    }

    fun dismissBlockConfirmDialog() {
        _uiState.update { it.copy(showBlockConfirmDialog = false) }
    }

    fun toggleBlockUser() {
        val userId = parsedId ?: return
        val repo = contactRepository ?: return
        val isBlocked = _uiState.value.contactStatus == ContactStatus.BLOCKED
        viewModelScope.launch {
            if (isBlocked) {
                repo.unblockUser(userId).onOk {
                    repo.syncFriends()
                }.onErr {
                    _uiState.update { it.copy(actionError = strings.authOperationFailed) }
                }
            } else {
                repo.blockUser(userId).onErr {
                    _uiState.update { it.copy(actionError = strings.authOperationFailed) }
                }
            }
        }
    }

    // ---------------- Delete Friend ----------------

    fun showDeleteFriendConfirm() {
        _uiState.update { it.copy(showDeleteFriendConfirmDialog = true) }
    }

    fun dismissDeleteFriendConfirm() {
        _uiState.update { it.copy(showDeleteFriendConfirmDialog = false) }
    }

    fun deleteFriend() {
        val userId = parsedId ?: return
        val repo = contactRepository ?: return
        _uiState.update { it.copy(showDeleteFriendConfirmDialog = false) }
        viewModelScope.launch {
            repo.removeFriend(userId).onErr {
                _uiState.update { it.copy(actionError = strings.authOperationFailed) }
            }
        }
    }
}

