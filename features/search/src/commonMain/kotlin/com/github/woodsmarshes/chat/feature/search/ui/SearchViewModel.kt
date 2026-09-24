package com.github.woodsmarshes.chat.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ConversationRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.search.model.SearchResultUiModel
import com.github.woodsmarshes.chat.feature.search.model.SearchUiState
import com.github.woodsmarshes.chat.feature.search.navigation.SearchType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchType: SearchType,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    private val log = KotlinLogging.logger {}

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (searchType == SearchType.SETTING) {
            _uiState.value = _uiState.value.copy(notSupported = true)
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        // Below the minimum length: drop stale results immediately instead of
        // waiting for the debounced flow to catch up.
        if (query.trim().length < 2) {
            searchJob?.cancel()
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                isLoading = false,
                error = null,
            )
        }
    }

    /**
     * Runs (or clears) the search. Called by
     * [com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar]
     * after its debounce; an empty query means the text is below the minimum
     * length or was cleared.
     */
    fun onSearchQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                isLoading = false,
                error = null,
            )
            return
        }
        performSearch(query)
    }

    /** Re-runs the search for the current query (error retry affordance). */
    fun retry() {
        val query = _uiState.value.query.trim()
        if (query.length >= 2) {
            performSearch(query)
        }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (searchType) {
                SearchType.CONTACT -> runContactSearch(query)
                SearchType.CONVERSATION -> runConversationSearch(query)
                SearchType.SETTING -> Unit // surfaced via notSupported from init
            }
        }
    }

    private suspend fun runContactSearch(query: String) {
        userRepository.searchUsers(query).onOk { users ->
            _uiState.value = _uiState.value.copy(
                results = users.map { user ->
                    SearchResultUiModel.Contact(
                        ContactUiModel(
                            id = user.id,
                            username = user.username,
                            displayName = user.displayName,
                            avatarUrl = user.avatarUrl,
                            bio = user.bio,
                        )
                    )
                },
                isLoading = false,
            )
        }.onErr { err ->
            log.error { "[SearchVM] user search failed: $err" }
            _uiState.value = _uiState.value.copy(isLoading = false, error = strings.searchFailed)
        }
    }

    private suspend fun runConversationSearch(query: String) {
        conversationRepository.searchGroups(query).onOk { groups ->
            _uiState.value = _uiState.value.copy(
                results = groups.map { group ->
                    SearchResultUiModel.Conversation(
                        ConversationUiModel(
                            id = group.conversationId,
                            type = ConversationType.GROUP,
                            name = group.name,
                            avatarUrl = group.avatarUrl,
                            description = group.description,
                            handle = group.handle,
                        )
                    )
                },
                isLoading = false,
            )
        }.onErr { err ->
            log.error { "[SearchVM] group search failed: $err" }
            _uiState.value = _uiState.value.copy(isLoading = false, error = strings.searchFailed)
        }
    }
}
