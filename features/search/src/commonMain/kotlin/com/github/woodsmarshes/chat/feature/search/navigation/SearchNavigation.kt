package com.github.woodsmarshes.chat.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.search.ui.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data class SearchNavKey(
    val type: SearchType,
) : NavKey

enum class SearchType {
    CONVERSATION,
    CONTACT,
    SETTING,
}

fun EntryProviderScope<NavKey>.searchEntry(
    onBack: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SearchNavKey>(metadata = metadata) { key ->
        SearchScreen(
            onBack = onBack,
            type = key.type
        )
    }
}
