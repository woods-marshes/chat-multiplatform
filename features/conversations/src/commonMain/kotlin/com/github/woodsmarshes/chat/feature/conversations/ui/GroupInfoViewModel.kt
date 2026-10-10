package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.conversations.model.GroupInfoUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.Uuid

class GroupInfoViewModel(
    conversationId: String,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val strings
        get() = getLocaleStrings()

    private val parsedId = runCatching { Uuid.parse(conversationId) }.getOrNull()

    private val _uiState = MutableStateFlow(GroupInfoUiState())
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var joinJob: Job? = null
    private var handleCheckJob: Job? = null

    /** One network seed per screen lifetime; afterwards the local ledger drives updates. */
    private var joinRequestsSeeded = false

    init {
        if (parsedId == null) {
            _uiState.update { it.copy(notFound = true) }
        } else {
            observeGroup(parsedId)
            observeFriendsForInvite()
            observePendingJoinRequests(parsedId)
            refresh()
        }
    }

    private fun observeGroup(conversationId: Uuid) {
        viewModelScope.launch {
            combine(
                conversationRepository.getGroupProfileFlow(conversationId),
                conversationRepository.getGroupMembersFlow(conversationId),
                userRepository.getMeFlow(),
                conversationRepository.getMyParticipantFlow(conversationId),
            ) { profile, members, me, myParticipant ->
                 {
                    if (profile != null) {
                        val meMember = me?.let { u -> members.find { it.id == u.id } }
                        val resolvedRole = myParticipant?.role ?: meMember?.role
                        val isMember = me != null && (myParticipant != null || meMember != null)
                        val memberIds = members.map { it.id }.toSet()
                        val participantSettings = myParticipant?.settings ?: _uiState.value.myParticipantSettings
                        _uiState.update { s ->
                            s.copy(
                                conversationId = profile.conversationId.toString(),
                                name = profile.name,
                                handle = profile.handle,
                                avatarUrl = profile.avatarUrl,
                                description = profile.description,
                                settings = profile.settings,
                                members = members,
                                isMember = isMember,
                                myUserId = me?.id,
                                myRole = resolvedRole,
                                myParticipantSettings = participantSettings,
                                isPinned = participantSettings.pinnedAt != null,
                                isMuted = !participantSettings.enableNotification,
                                myNickname = participantSettings.nickname,
                                invitableFriends = s.invitableFriends.filter { it.id !in memberIds },
                                isLoading = false,
                                error = null,
                            )
                        }
                        if ((resolvedRole == ConversationRole.OWNER || resolvedRole == ConversationRole.ADMIN) &&
                            !joinRequestsSeeded
                        ) {
                            joinRequestsSeeded = true
                            loadJoinRequests(conversationId)
                        }
                    }
                }
            }.collect { updater -> updater() }
        }
    }

    private fun observeFriendsForInvite() {
        viewModelScope.launch {
            contactRepository.getFriendsFlow()
                .catch { }
                .collect { pairs ->
                    val memberIds = _uiState.value.members.map { it.id }.toSet()
                    val friends = pairs
                        .filter { (_, user) -> user.id !in memberIds }
                        .map { (contact, user) ->
                            ContactUiModel(
                                id = user.id,
                                username = user.username,
                                displayName = contact.alias?.takeIf { it.isNotBlank() }
                                    ?: contact.nickname?.takeIf { it.isNotBlank() }
                                    ?: user.displayName,
                                avatarUrl = user.avatarUrl,
                                bio = user.bio,
                            )
                        }
                    _uiState.update { it.copy(invitableFriends = friends) }
                }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(actionError = null, actionMessage = null) }
    }

    /** Pulls the latest group detail from the network into local storage. */
    fun refresh() {
        val conversationId = parsedId ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            conversationRepository.refreshGroupDetail(conversationId).onErr { err ->
                when {
                    err is ConversationError.NotParticipant -> {
                        if (_uiState.value.conversationId == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    notFound = true,
                                )
                            }
                        }
                    }
                    _uiState.value.conversationId == null -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = strings.groupLoadFailed,
                        )
                    }
                }
            }
            val role = _uiState.value.myRole
            if (role == ConversationRole.OWNER || role == ConversationRole.ADMIN) {
                loadJoinRequests(conversationId)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun loadJoinRequests(conversationId: Uuid) {
        // Network seed for the local ledger only; the pending list itself is
        // rendered from observeGroupJoinRequests, which updates live through
        // realtime events and the local approve/reject mirrors.
        viewModelScope.launch {
            conversationRepository.getGroupJoinRequests(conversationId)
        }
    }

    private fun observePendingJoinRequests(conversationId: Uuid) {
        viewModelScope.launch {
            conversationRepository.observeGroupJoinRequests(conversationId, RequestStatus.PENDING)
                .collect { pending ->
                    _uiState.update { it.copy(pendingJoinRequests = pending) }
                    pending.forEach { req ->
                        // Fetch applicant details only once per applicant.
                        if (_uiState.value.requestUsers[req.applicantId] == null) {
                            viewModelScope.launch {
                                userRepository.fetchUserDetail(req.applicantId).onOk { u ->
                                    _uiState.update {
                                        it.copy(requestUsers = it.requestUsers + (req.applicantId to u))
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    // ---------------- Join group (with optional approval message) ----------------

    fun onJoinButtonClick() {
        val state = _uiState.value
        if (state.isMember || state.isJoining) return
        if (state.settings.joinApprovalRequired) {
            _uiState.update { it.copy(showJoinDialog = true, joinMessage = "", actionError = null) }
        } else {
            joinGroup(message = null)
        }
    }

    fun dismissJoinDialog() {
        _uiState.update { it.copy(showJoinDialog = false) }
    }

    fun onJoinMessageChanged(message: String) {
        _uiState.update { it.copy(joinMessage = message.take(100)) }
    }

    fun confirmJoinWithMessage() {
        val msg = _uiState.value.joinMessage.trim().ifEmpty { null }
        _uiState.update { it.copy(showJoinDialog = false) }
        joinGroup(message = msg)
    }

    fun joinGroup(message: String? = null) {
        val conversationId = parsedId ?: return
        val current = _uiState.value
        if (current.isMember || current.isJoining) return
        joinJob?.cancel()
        joinJob = viewModelScope.launch {
            _uiState.update { it.copy(isJoining = true, actionError = null) }
            conversationRepository.joinGroup(conversationId, message).onOk {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        actionMessage = if (it.settings.joinApprovalRequired) strings.groupApplySubmitted else null,
                    )
                }
            }.onErr {
                _uiState.update {
                    it.copy(
                        isJoining = false,
                        actionError = strings.joinGroupFailed,
                    )
                }
            }
        }
    }

    // ---------------- Personal settings ----------------

    fun togglePin(pinned: Boolean) {
        val conversationId = parsedId ?: return
        val oldSettings = _uiState.value.myParticipantSettings
        val newSettings = oldSettings.copy(pinnedAt = if (pinned) Clock.System.now() else null)
        _uiState.update { it.copy(isPinned = pinned, myParticipantSettings = newSettings) }
        viewModelScope.launch {
            conversationRepository.updateMyParticipantSettings(
                conversationId = conversationId,
                settings = newSettings,
            ).onErr {
                _uiState.update {
                    it.copy(
                        isPinned = !pinned,
                        myParticipantSettings = oldSettings,
                        actionError = strings.authOperationFailed,
                    )
                }
            }
        }
    }

    fun toggleMute(muted: Boolean) {
        val conversationId = parsedId ?: return
        val oldSettings = _uiState.value.myParticipantSettings
        val newSettings = oldSettings.copy(enableNotification = !muted)
        _uiState.update { it.copy(isMuted = muted, myParticipantSettings = newSettings) }
        viewModelScope.launch {
            conversationRepository.updateMyParticipantSettings(
                conversationId = conversationId,
                settings = newSettings,
            ).onErr {
                _uiState.update {
                    it.copy(
                        isMuted = !muted,
                        myParticipantSettings = oldSettings,
                        actionError = strings.authOperationFailed,
                    )
                }
            }
        }
    }

    fun showEditNickname() {
        _uiState.update {
            it.copy(
                showEditNicknameDialog = true,
                editNicknameValue = it.myNickname.orEmpty(),
            )
        }
    }

    fun dismissEditNickname() {
        _uiState.update { it.copy(showEditNicknameDialog = false) }
    }

    fun onEditNicknameChanged(value: String) {
        _uiState.update { it.copy(editNicknameValue = value) }
    }

    fun saveMyNickname() {
        val conversationId = parsedId ?: return
        val newNickname = _uiState.value.editNicknameValue.trim().ifEmpty { null }
        val oldSettings = _uiState.value.myParticipantSettings
        val newSettings = oldSettings.copy(nickname = newNickname)
        _uiState.update {
            it.copy(
                showEditNicknameDialog = false,
                myNickname = newNickname,
                myParticipantSettings = newSettings,
            )
        }
        viewModelScope.launch {
            conversationRepository.updateMyParticipantSettings(
                conversationId = conversationId,
                settings = newSettings,
            ).onOk {
                refresh()
            }.onErr {
                _uiState.update {
                    it.copy(
                        myNickname = oldSettings.nickname,
                        myParticipantSettings = oldSettings,
                        actionError = strings.authOperationFailed,
                    )
                }
            }
        }
    }

    // ---------------- OWNER: Group Profile & Settings ----------------

    fun uploadGroupAvatar(bytes: ByteArray) {
        val conversationId = parsedId ?: return
        viewModelScope.launch {
            conversationRepository.uploadGroupAvatar(conversationId, bytes).onOk {
                _uiState.update { it.copy(actionMessage = strings.avatarUploadSuccess) }
            }.onErr {
                _uiState.update { it.copy(actionError = strings.avatarUploadFailed) }
            }
        }
    }

    fun showEditGroup() {
        handleCheckJob?.cancel()
        _uiState.update {
            it.copy(
                showEditGroupDialog = true,
                editGroupName = it.name,
                editGroupHandle = it.handle.orEmpty(),
                isCheckingHandle = false,
                isHandleAvailable = true,
                editGroupDescription = it.description.orEmpty(),
            )
        }
    }

    fun dismissEditGroup() {
        if (_uiState.value.isSavingGroup) return
        handleCheckJob?.cancel()
        _uiState.update { it.copy(showEditGroupDialog = false) }
    }

    fun onEditGroupNameChanged(name: String) {
        _uiState.update { it.copy(editGroupName = name) }
    }

    fun onEditGroupHandleChanged(rawHandle: String) {
        val cleaned = rawHandle.trim().removePrefix("@")
        val currentHandle = _uiState.value.handle.orEmpty()
        handleCheckJob?.cancel()
        if (cleaned.isEmpty() || cleaned == currentHandle) {
            _uiState.update {
                it.copy(
                    editGroupHandle = cleaned,
                    isCheckingHandle = false,
                    isHandleAvailable = true,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                editGroupHandle = cleaned,
                isCheckingHandle = true,
                isHandleAvailable = null,
            )
        }
        handleCheckJob = viewModelScope.launch {
            delay(400)
            conversationRepository.checkHandleExists(cleaned).onOk { exists ->
                _uiState.update {
                    it.copy(
                        isCheckingHandle = false,
                        isHandleAvailable = !exists,
                    )
                }
            }.onErr {
                _uiState.update {
                    it.copy(
                        isCheckingHandle = false,
                        isHandleAvailable = null,
                    )
                }
            }
        }
    }

    fun onEditGroupDescriptionChanged(desc: String) {
        _uiState.update { it.copy(editGroupDescription = desc) }
    }

    fun saveGroupProfile() {
        val conversationId = parsedId ?: return
        val state = _uiState.value
        val name = state.editGroupName.trim()
        if (name.isBlank()) return
        if (state.editGroupHandle.isNotBlank() && state.isHandleAvailable == false) return

        _uiState.update { it.copy(isSavingGroup = true) }
        viewModelScope.launch {
            conversationRepository.updateGroupProfile(
                conversationId = conversationId,
                name = name,
                handle = state.editGroupHandle.trim().ifEmpty { null },
                description = state.editGroupDescription.trim(),
            ).onOk {
                _uiState.update {
                    it.copy(
                        isSavingGroup = false,
                        showEditGroupDialog = false,
                        actionMessage = strings.profileSaved,
                    )
                }
                refresh()
            }.onErr {
                _uiState.update {
                    it.copy(
                        isSavingGroup = false,
                        actionError = strings.profileSaveFailed,
                    )
                }
            }
        }
    }

    fun toggleJoinApprovalRequired(required: Boolean) {
        val conversationId = parsedId ?: return
        val oldSettings = _uiState.value.settings
        val newSettings = oldSettings.copy(joinApprovalRequired = required)
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            conversationRepository.updateGroupProfile(conversationId, settings = newSettings).onOk {
                refresh()
            }.onErr {
                _uiState.update { it.copy(settings = oldSettings, actionError = strings.authOperationFailed) }
            }
        }
    }

    fun toggleAllowMemberInvite(allowed: Boolean) {
        val conversationId = parsedId ?: return
        val oldSettings = _uiState.value.settings
        val newSettings = oldSettings.copy(allowMemberInvite = allowed)
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            conversationRepository.updateGroupProfile(conversationId, settings = newSettings).onOk {
                refresh()
            }.onErr {
                _uiState.update { it.copy(settings = oldSettings, actionError = strings.authOperationFailed) }
            }
        }
    }

    fun toggleMuteAll(muteAll: Boolean) {
        val conversationId = parsedId ?: return
        val oldSettings = _uiState.value.settings
        val newSettings = oldSettings.copy(muteAll = muteAll)
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch {
            conversationRepository.updateGroupProfile(conversationId, settings = newSettings).onOk {
                refresh()
            }.onErr {
                _uiState.update { it.copy(settings = oldSettings, actionError = strings.authOperationFailed) }
            }
        }
    }

    // ---------------- OWNER / ADMIN: Join Requests ----------------

    fun showJoinRequests() {
        val conversationId = parsedId ?: return
        _uiState.update { it.copy(showJoinRequestsSheet = true) }
        loadJoinRequests(conversationId)
    }

    fun dismissJoinRequests() {
        _uiState.update { it.copy(showJoinRequestsSheet = false) }
    }

    fun handleJoinRequest(requestId: Uuid, approve: Boolean) {
        val conversationId = parsedId ?: return
        if (_uiState.value.isHandlingJoinRequest) return
        _uiState.update { it.copy(isHandlingJoinRequest = true) }
        viewModelScope.launch {
            conversationRepository.handleGroupJoinRequest(conversationId, requestId, approve).onOk {
                // The local ledger mirror in the repository updates
                // pendingJoinRequests through the observed flow.
                refresh()
            }.onErr {
                _uiState.update { it.copy(actionError = strings.authOperationFailed) }
            }
            _uiState.update { it.copy(isHandlingJoinRequest = false) }
        }
    }

    // ---------------- Invite Friends ----------------

    fun showInviteFriends() {
        _uiState.update { it.copy(showInviteSheet = true, selectedInviteIds = emptySet()) }
        viewModelScope.launch { runCatching { contactRepository.syncFriends() } }
    }

    fun dismissInviteFriends() {
        if (_uiState.value.isInviting) return
        _uiState.update { it.copy(showInviteSheet = false) }
    }

    fun toggleInviteSelection(userId: Uuid) {
        _uiState.update { state ->
            val updated = if (userId in state.selectedInviteIds) {
                state.selectedInviteIds - userId
            } else {
                state.selectedInviteIds + userId
            }
            state.copy(selectedInviteIds = updated)
        }
    }

    fun confirmInviteFriends() {
        val conversationId = parsedId ?: return
        val ids = _uiState.value.selectedInviteIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(isInviting = true) }
        viewModelScope.launch {
            conversationRepository.inviteUsers(conversationId, ids).onOk {
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        showInviteSheet = false,
                        actionMessage = strings.profileSaved,
                    )
                }
            }.onErr {
                _uiState.update {
                    it.copy(
                        isInviting = false,
                        actionError = strings.authOperationFailed,
                    )
                }
            }
        }
    }

    // ---------------- OWNER: Transfer Ownership ----------------

    fun showTransferOwner() {
        _uiState.update { it.copy(showTransferOwnerSheet = true, pendingTransferTarget = null) }
    }

    fun dismissTransferOwner() {
        _uiState.update { it.copy(showTransferOwnerSheet = false, pendingTransferTarget = null) }
    }

    fun onTransferCandidateSelected(candidate: SenderUser) {
        _uiState.update { it.copy(pendingTransferTarget = candidate) }
    }

    fun dismissTransferCandidate() {
        _uiState.update { it.copy(pendingTransferTarget = null) }
    }

    fun confirmTransferOwnership() {
        val target = _uiState.value.pendingTransferTarget ?: return
        transferOwnership(target.id)
    }

    fun transferOwnership(newOwnerId: Uuid) {
        val conversationId = parsedId ?: return
        _uiState.update { it.copy(showTransferOwnerSheet = false, pendingTransferTarget = null) }
        viewModelScope.launch {
            conversationRepository.updateGroupProfile(conversationId, ownerId = newOwnerId).onOk {
                _uiState.update { it.copy(actionMessage = strings.profileSaved) }
                refresh()
            }.onErr {
                _uiState.update { it.copy(actionError = strings.authOperationFailed) }
            }
        }
    }

    // ---------------- Leave / Dissolve Group ----------------

    fun showLeaveConfirm() {
        _uiState.update { it.copy(showLeaveConfirmDialog = true) }
    }

    fun dismissLeaveConfirm() {
        _uiState.update { it.copy(showLeaveConfirmDialog = false) }
    }

    fun leaveGroup(onLeft: () -> Unit) {
        val conversationId = parsedId ?: return
        _uiState.update { it.copy(showLeaveConfirmDialog = false) }
        viewModelScope.launch {
            conversationRepository.leaveGroup(conversationId).onOk {
                onLeft()
            }.onErr {
                _uiState.update { it.copy(actionError = strings.authOperationFailed) }
            }
        }
    }

    fun showDissolveConfirm() {
        _uiState.update { it.copy(showDissolveConfirmDialog = true) }
    }

    fun dismissDissolveConfirm() {
        _uiState.update { it.copy(showDissolveConfirmDialog = false) }
    }

    fun dissolveGroup(onDissolved: () -> Unit) {
        val conversationId = parsedId ?: return
        _uiState.update { it.copy(showDissolveConfirmDialog = false) }
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId).onOk {
                onDissolved()
            }.onErr {
                _uiState.update { it.copy(actionError = strings.authOperationFailed) }
            }
        }
    }
}

