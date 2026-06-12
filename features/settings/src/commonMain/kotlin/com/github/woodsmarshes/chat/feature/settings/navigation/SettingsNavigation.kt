package com.github.woodsmarshes.chat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsNavKey : NavKey

fun EntryProviderScope<NavKey>.settingsEntry(
    onBack: () -> Unit,
) {
    entry<SettingsNavKey> {
        SettingsScreen(onBack = onBack)
    }
}
