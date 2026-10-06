package com.github.woodsmarshes.chat.feature.conversations.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.conversations.ui.GroupInfoScreen
import kotlinx.serialization.Serializable

@Serializable
data class GroupInfoNavKey(val conversationId: String) : NavKey

/**
 * Contextual group-info route opened from inside an active [ChatScreen];
 * bound to the rightmost `extraPane` in a three-pane layout, whereas
 * [GroupInfoNavKey] (opened from search) occupies the primary `detailPane`.
 */
@Serializable
data class ChatGroupInfoNavKey(val conversationId: String) : NavKey

fun EntryProviderScope<NavKey>.groupInfoEntry(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    detailMetadata: Map<String, Any> = emptyMap(),
    extraMetadata: Map<String, Any> = detailMetadata,
) {
    entry<GroupInfoNavKey>(metadata = detailMetadata) { key ->
        GroupInfoScreen(
            conversationId = key.conversationId,
            onBack = onBack,
            onOpenChat = onOpenChat,
            isExtraPane = false,
        )
    }
    entry<ChatGroupInfoNavKey>(metadata = extraMetadata) { key ->
        GroupInfoScreen(
            conversationId = key.conversationId,
            onBack = onBack,
            onOpenChat = onOpenChat,
            isExtraPane = true,
        )
    }
}
