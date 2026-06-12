package com.github.woodsmarshes.chat.feature.contacts.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.contacts.ui.ContactsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ContactsNavKey : NavKey

fun EntryProviderScope<NavKey>.contactsEntry(
    onNavigateToProfile: (userId: String) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ContactsNavKey>(metadata = metadata) {
        ContactsScreen(
            onContactClick = onNavigateToProfile,
            onMenuClick = onMenuClick,
        )
    }
}
