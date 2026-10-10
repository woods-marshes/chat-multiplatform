package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.conversations.model.ConversationsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class ConversationsViewModel(
    private val conversationRepository: ConversationRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    private val strings
        get() = getLocaleStrings()

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var handleCheckJob: Job? = null
    private var lastSearchQuery: String = ""

    init {
        loadConversations()
        observeFriendsForPicker()
        refresh()
    }

    // ---------------- Conversation list ----------------

    private fun loadConversations() {
        viewModelScope.launch {
            conversationRepository.getConversationListFlow()
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e ->
                    log.error(e) { "[Conversations] loading the list failed" }
                    _uiState.update {
                        it.copy(
                            error = strings.loadFailed,
                            isLoading = false,
                        )
                    }
                }
                .collect { conversations ->
                    // Mirrors the SQL ordering (COALESCE(last message time,
                    // conversation creation time)): message-less conversations
                    // must not sink below every messaged one.
                    val sorted = conversations.sortedWith(
                        compareByDescending<ConversationUiModel> { it.isPinned }
                            .thenByDescending { it.lastMessage?.createdAt ?: it.createdAt }
                    )
                    _uiState.update {
                        it.copy(
                            conversations = sorted,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun observeFriendsForPicker() {
        viewModelScope.launch {
            contactRepository.getFriendsFlow()
                .catch { e -> log.warn(e) { "[ConversationsViewModel] observing friends failed" } }
                .collect { pairs ->
                    val friends = pairs.map { (contact, user) ->
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
                    _uiState.update { it.copy(availableFriends = friends) }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                conversationRepository.syncConversations()
                contactRepository.syncFriends()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "[ConversationsViewModel] sync failed" }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    // ---------------- In-place search ----------------

    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            lastSearchQuery = ""
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchError = null,
                )
            }
            return
        }
        lastSearchQuery = query
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            conversationRepository.searchGroups(query).onOk { groups ->
                val existingById = _uiState.value.conversations.associateBy { it.id }
                _uiState.update {
                    it.copy(
                        searchResults = groups.map { group ->
                            val existing = existingById[group.conversationId]
                            ConversationUiModel(
                                id = group.conversationId,
                                type = ConversationType.GROUP,
                                name = group.name,
                                avatarUrl = group.avatarUrl,
                                description = group.description,
                                handle = group.handle,
                                lastMessage = existing?.lastMessage,
                                memberAvatars = existing?.memberAvatars.orEmpty(),
                            )
                        },
                        isSearching = false,
                    )
                }
            }.onErr { err ->
                log.error { "[ConversationsViewModel] group search failed: $err" }
                _uiState.update { it.copy(isSearching = false, searchError = strings.searchFailed) }
            }
        }
    }

    fun retrySearch() {
        if (lastSearchQuery.isNotBlank()) {
            onSearchQuery(lastSearchQuery)
        }
    }

    // ---------------- Create group ----------------

    fun showCreateGroup() {
        handleCheckJob?.cancel()
        _uiState.update {
            it.copy(
                showCreateGroup = true,
                groupName = "",
                groupHandle = "",
                isCheckingHandle = false,
                isHandleAvailable = null,
                groupDescription = "",
                joinApprovalRequired = false,
                allowMemberInvite = true,
                selectedMemberIds = emptySet(),
                avatarBytes = null,
                createError = null,
            )
        }
        viewModelScope.launch {
            runCatching { contactRepository.syncFriends() }
        }
    }

    fun dismissCreateGroup() {
        if (_uiState.value.isCreating) return
        handleCheckJob?.cancel()
        _uiState.update { it.copy(showCreateGroup = false) }
    }

    fun onGroupNameChanged(name: String) {
        _uiState.update { it.copy(groupName = name, createError = null) }
    }

    fun onGroupHandleChanged(rawHandle: String) {
        val cleaned = rawHandle.trim().removePrefix("@")
        _uiState.update {
            it.copy(
                groupHandle = cleaned,
                isCheckingHandle = cleaned.isNotEmpty(),
                isHandleAvailable = null,
                createError = null,
            )
        }
        handleCheckJob?.cancel()
        if (cleaned.isEmpty()) return
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

    fun onGroupDescriptionChanged(desc: String) {
        _uiState.update { it.copy(groupDescription = desc) }
    }

    fun onJoinApprovalRequiredChanged(required: Boolean) {
        _uiState.update { it.copy(joinApprovalRequired = required) }
    }

    fun onAllowMemberInviteChanged(allowed: Boolean) {
        _uiState.update { it.copy(allowMemberInvite = allowed) }
    }

    fun onToggleMemberSelection(userId: Uuid) {
        _uiState.update { state ->
            val updated = if (userId in state.selectedMemberIds) {
                state.selectedMemberIds - userId
            } else {
                state.selectedMemberIds + userId
            }
            state.copy(selectedMemberIds = updated)
        }
    }

    fun onGroupAvatarSelected(bytes: ByteArray) {
        _uiState.update { it.copy(avatarBytes = bytes) }
    }

    fun createGroup(onCreated: ((conversationId: String) -> Unit)? = null) {
        val state = _uiState.value
        if (state.isCreating) return
        val name = state.groupName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(createError = strings.groupNameRequired) }
            return
        }
        if (state.groupHandle.isNotBlank() && state.isHandleAvailable == false) {
            _uiState.update { it.copy(createError = strings.groupHandleTaken) }
            return
        }

        _uiState.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            val settings = GroupSettings(
                joinApprovalRequired = state.joinApprovalRequired,
                allowMemberInvite = state.allowMemberInvite,
            )
            conversationRepository.createGroup(
                name = name,
                handle = state.groupHandle.trim().ifEmpty { null },
                description = state.groupDescription.trim().ifEmpty { null },
                settings = settings,
                memberIds = state.selectedMemberIds.toList(),
            ).onErr {
                _uiState.update { it.copy(isCreating = false, createError = strings.createGroupFailed) }
            }.onOk { created ->
                val avatarBytes = state.avatarBytes
                if (avatarBytes != null) {
                    // Recoverable: the group exists and the owner can re-upload
                    // from GroupInfoScreen. The conversations screen has no
                    // snackbar plumbing, so surface the failure via the log.
                    conversationRepository.uploadGroupAvatar(created.id, avatarBytes)
                        .onErr { err ->
                            log.warn { "[Conversations] group avatar upload failed for ${created.id}: $err" }
                        }
                }
                _uiState.update { it.copy(showCreateGroup = false, isCreating = false) }
                refresh()
                onCreated?.invoke(created.id.toString())
            }
        }
    }
}

