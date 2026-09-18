package com.github.woodsmarshes.chat.feature.search.model

import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel

/** A rendered row of the search result list, keyed by search type. */
sealed interface SearchResultUiModel {
    data class Contact(val contact: ContactUiModel) : SearchResultUiModel
    data class Conversation(val conversation: ConversationUiModel) : SearchResultUiModel
}

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** True for search types that have no data source yet (e.g. settings). */
    val notSupported: Boolean = false,
)
