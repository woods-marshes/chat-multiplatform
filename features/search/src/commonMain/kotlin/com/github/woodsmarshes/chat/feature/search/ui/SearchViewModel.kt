package com.github.woodsmarshes.chat.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.feature.search.model.SearchUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        _queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .filter { it.length >= 2 }
            .onEach { query -> performSearch(query) }
            .launchIn(viewModelScope)
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        _queryFlow.value = query
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val result = contactRepository.searchContacts(query)
        result.onOk { pairs ->
            val users = pairs.map { (_, user) ->
                ContactUiModel(
                    id = user.id,
                    username = user.username,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    bio = user.bio,
                )
            }
            _uiState.value = _uiState.value.copy(results = users, isLoading = false)
        }.onErr {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}
