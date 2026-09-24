package com.github.woodsmarshes.chat.feature.contacts.model

import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel

data class ContactsUiState(
    val contacts: List<ContactUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,

    // In-place search (AdaptiveSearchBar)
    val searchResults: List<ContactUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
)
