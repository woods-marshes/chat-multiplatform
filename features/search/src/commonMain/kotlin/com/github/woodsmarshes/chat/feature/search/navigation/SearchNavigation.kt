package com.github.woodsmarshes.chat.feature.search.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.search.ui.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object SearchNavKey : NavKey

fun EntryProviderScope<NavKey>.searchEntry(
    onBack: () -> Unit,
) {
    entry<SearchNavKey> {
        SearchScreen(onBack = onBack)
    }
}
