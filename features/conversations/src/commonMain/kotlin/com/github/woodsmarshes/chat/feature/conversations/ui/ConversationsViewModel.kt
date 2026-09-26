package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.conversations.model.ConversationsUiState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
) : ViewModel() {
    private val log = KotlinLogging.logger {}

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(ConversationsUiState())
    val uiState: StateFlow<ConversationsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var lastSearchQuery: String = ""

    init {
        loadConversations()
        refresh()
    }

    // ---------------- Conversation list ----------------

    private fun loadConversations() {
        viewModelScope.launch {
            conversationRepository.getConversationListFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true) }
                .catch { e ->
                    // Keep the Ktor request/URL text out of the UI, it is not a user message.
                    log.error(e) { "[Conversations] loading the list failed" }
                    _uiState.update {
                        it.copy(
                            error = strings.loadFailed,
                            isLoading = false,
                        )
                    }
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversations = conversations,
                        isLoading = false,
                    )
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                conversationRepository.syncConversations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "[ConversationsViewModel] sync failed" }
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    // ---------------- In-place search ----------------

    /**
     * Runs (or clears) the group search. Called with the trimmed query by
     * [com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar]
     * after its debounce; an empty query means the text is below the minimum
     * length or was cleared, so stale results are dropped immediately.
     */
    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            lastSearchQuery = ""
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                isSearching = false,
                searchError = null,
            )
            return
        }
        lastSearchQuery = query
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
            conversationRepository.searchGroups(query).onOk { groups ->
                _uiState.value = _uiState.value.copy(
                    searchResults = groups.map { group ->
                        ConversationUiModel(
                            id = group.conversationId,
                            type = ConversationType.GROUP,
                            name = group.name,
                            avatarUrl = group.avatarUrl,
                            description = group.description,
                            handle = group.handle,
                        )
                    },
                    isSearching = false,
                )
            }.onErr { err ->
                log.error { "[ConversationsViewModel] group search failed: $err" }
                _uiState.value = _uiState.value.copy(isSearching = false, searchError = strings.searchFailed)
            }
        }
    }

    /** Re-runs the search for the last query (error retry affordance). */
    fun retrySearch() {
        if (lastSearchQuery.isNotBlank()) {
            onSearchQuery(lastSearchQuery)
        }
    }

    // ---------------- FAB bottom sheet ----------------

    fun showActions() {
        _uiState.value = _uiState.value.copy(showActions = true)
    }

    fun dismissActions() {
        _uiState.value = _uiState.value.copy(showActions = false)
    }

    // ---------------- Create group ----------------

    fun showCreateGroup() {
        _uiState.value = _uiState.value.copy(
            showActions = false,
            showCreateGroup = true,
            groupName = "",
            groupDescription = "",
            createError = null,
        )
    }

    fun dismissCreateGroup() {
        _uiState.value = _uiState.value.copy(showCreateGroup = false)
    }

    fun onGroupNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun onGroupDescriptionChanged(desc: String) {
        _uiState.value = _uiState.value.copy(groupDescription = desc)
    }

    fun createGroup() {
        val name = _uiState.value.groupName.trim()
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(createError = strings.groupNameRequired)
            return
        }

        _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
        viewModelScope.launch {
            conversationRepository.createGroup(
                name = name,
                description = _uiState.value.groupDescription.trim().ifEmpty { null },
            ).onErr {
                _uiState.value = _uiState.value.copy(isCreating = false, createError = strings.createGroupFailed)
            }.onOk {
                _uiState.value = _uiState.value.copy(showCreateGroup = false, isCreating = false)
                refresh()
            }
        }
    }

    // ---------------- Join group ----------------

    fun showJoinGroup() {
        _uiState.value = _uiState.value.copy(
            showActions = false,
            showJoinGroup = true,
            joinGroupId = "",
            joinError = null,
        )
    }

    fun dismissJoinGroup() {
        _uiState.value = _uiState.value.copy(showJoinGroup = false)
    }

    fun onJoinGroupIdChanged(id: String) {
        _uiState.value = _uiState.value.copy(joinGroupId = id)
    }

    fun joinGroup() {
        val idStr = _uiState.value.joinGroupId.trim()
        val id = try {
            Uuid.parse(idStr)
        } catch (_: Exception) {
            log.info { "[ConversationsViewModel]: joinGroup(), not parse" }
            _uiState.value = _uiState.value.copy(joinError = strings.invalidGroupId)
            return
        }

        _uiState.value = _uiState.value.copy(isJoining = true, joinError = null)
        viewModelScope.launch {
            conversationRepository.joinGroup(id).onErr {
                log.info { "[ConversationsViewModel]: joinGroup() => $it" }
                _uiState.value = _uiState.value.copy(isJoining = false, joinError = strings.joinGroupFailed)
            }.onOk {
                log.info { "[ConversationsViewModel]: joinGroup() => success" }
                _uiState.value = _uiState.value.copy(showJoinGroup = false, isJoining = false)
                refresh()
            }
        }
    }
}
