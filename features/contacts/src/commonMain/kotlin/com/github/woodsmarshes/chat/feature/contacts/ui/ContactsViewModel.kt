package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.woodsmarshes.chat.core.data.repository.ContactRepository
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.feature.contacts.model.ContactsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

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
}
