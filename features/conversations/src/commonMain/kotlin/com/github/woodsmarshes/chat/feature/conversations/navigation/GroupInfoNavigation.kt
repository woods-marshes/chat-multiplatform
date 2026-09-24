package com.github.woodsmarshes.chat.feature.conversations.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.conversations.ui.GroupInfoScreen
import kotlinx.serialization.Serializable

@Serializable
data class GroupInfoNavKey(val conversationId: String) : NavKey

fun EntryProviderScope<NavKey>.groupInfoEntry(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<GroupInfoNavKey>(metadata = metadata) { key ->
        GroupInfoScreen(
            conversationId = key.conversationId,
            onBack = onBack,
            onOpenChat = onOpenChat,
        )
    }
}
