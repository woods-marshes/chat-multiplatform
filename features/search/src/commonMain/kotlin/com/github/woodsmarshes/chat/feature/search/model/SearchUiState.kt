package com.github.woodsmarshes.chat.feature.search.model

import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel

data class SearchUiState(
    val query: String = "",
    val results: List<ContactUiModel> = emptyList(),
    val isLoading: Boolean = false,
)
