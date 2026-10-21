package com.github.woodsmarshes.chat.feature.conversations.navigation

import com.github.woodsmarshes.chat.core.model.GroupProfile
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.conversations.ui.ConversationsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ConversationsNavKey : NavKey

fun EntryProviderScope<NavKey>.conversationsEntry(
    onNavigateToChat: (conversationId: String, isGroup: Boolean, title: String?, avatarUrl: String?) -> Unit,
    onGroupInfoClick: (profile: GroupProfile) -> Unit,
    selectedConversationId: () -> String? = { null },
    onMenuClick: (() -> Unit)? = null,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ConversationsNavKey>(metadata = metadata) {
        ConversationsScreen(
            onConversationClick = onNavigateToChat,
            onGroupInfoClick = onGroupInfoClick,
            selectedConversationId = selectedConversationId(),
            onMenuClick = onMenuClick,
        )
    }
}
