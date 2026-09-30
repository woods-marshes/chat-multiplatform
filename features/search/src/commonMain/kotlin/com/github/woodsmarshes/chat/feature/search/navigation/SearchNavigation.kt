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
}

fun EntryProviderScope<NavKey>.searchEntry(
    onBack: () -> Unit,
    onOpenProfile: (userId: String) -> Unit,
    onOpenGroupInfo: (conversationId: String) -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SearchNavKey>(metadata = metadata) { key ->
        SearchScreen(
            onBack = onBack,
            type = key.type,
            onOpenProfile = onOpenProfile,
            onOpenGroupInfo = onOpenGroupInfo,
        )
    }
}
