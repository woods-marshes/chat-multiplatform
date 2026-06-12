package com.github.woodsmarshes.chat.feature.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.profile.ui.ProfileScreen
import kotlinx.serialization.Serializable

@Serializable
data class ProfileNavKey(val userId: String) : NavKey

fun EntryProviderScope<NavKey>.profileEntry(
    onBack: () -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ProfileNavKey>(metadata = metadata) { key ->
        ProfileScreen(userId = key.userId, onBack = onBack)
    }
}
