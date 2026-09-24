package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.data.repository.UserRepository
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.feature.contacts.model.ContactsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    // User-facing strings for ViewModel-produced messages (no CompositionLocal here).
    private val strings = getLocaleStrings()

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var lastSearchQuery: String = ""

    init {
        viewModelScope.launch {
            contactRepository.getFriendsFlow()
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error") }
                .collect { pairs ->
                    val contacts = pairs.map { (_, user) ->
                        ContactUiModel(
                            id = user.id,
                            username = user.username,
                            displayName = user.displayName,
                            avatarUrl = user.avatarUrl,
                            bio = user.bio,
                        )
                    }
                    _uiState.value = _uiState.value.copy(contacts = contacts, isLoading = false, error = null)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try { contactRepository.syncFriends() } catch (_: Exception) {}
        }
    }

    // ---------------- In-place search ----------------

    /**
     * Runs (or clears) the contact search. Called with the trimmed query by
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
            userRepository.searchUsers(query).onOk { users ->
                _uiState.value = _uiState.value.copy(
                    searchResults = users.map { user ->
                        ContactUiModel(
                            id = user.id,
                            username = user.username,
                            displayName = user.displayName,
                            avatarUrl = user.avatarUrl,
                            bio = user.bio,
                        )
                    },
                    isSearching = false,
                )
            }.onErr {
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
}
