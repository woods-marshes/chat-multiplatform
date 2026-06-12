package com.github.woodsmarshes.chat.feature.auth.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.auth.ui.AuthScreen
import kotlinx.serialization.Serializable

@Serializable
data object AuthNavKey : NavKey

fun EntryProviderScope<NavKey>.authEntry(
    onAuthSuccess: () -> Unit,
) {
    entry<AuthNavKey> {
        AuthScreen(onAuthSuccess = onAuthSuccess)
    }
}
