package com.github.woodsmarshes.chat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsNavKey : NavKey

fun EntryProviderScope<NavKey>.settingsEntry(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSearchClick: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<SettingsNavKey>(metadata = metadata) {
        SettingsScreen(onBack = onBack, onLogout = onLogout, onSearchClick = onSearchClick)
    }
}
