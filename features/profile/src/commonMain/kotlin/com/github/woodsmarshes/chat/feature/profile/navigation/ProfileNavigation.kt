package com.github.woodsmarshes.chat.feature.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.profile.ui.ProfileScreen
import kotlinx.serialization.Serializable

@Serializable
data class ProfileNavKey(val userId: String) : NavKey

/**
 * Contextual user-profile route opened from inside an active [ChatScreen];
 * bound to the rightmost `extraPane` in a three-pane layout, whereas
 * [ProfileNavKey] (opened from the contacts list or search) occupies the
 * primary `detailPane`.
 */
@Serializable
data class ChatProfileNavKey(val userId: String) : NavKey

fun EntryProviderScope<NavKey>.profileEntry(
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    onEditProfile: (() -> Unit)? = null,
    detailMetadata: Map<String, Any> = emptyMap(),
    extraMetadata: Map<String, Any> = detailMetadata,
) {
    entry<ProfileNavKey>(metadata = detailMetadata) { key ->
        ProfileScreen(
            userId = key.userId,
            onBack = onBack,
            onOpenChat = onOpenChat,
            onEditProfile = onEditProfile,
            isExtraPane = false,
        )
    }
    entry<ChatProfileNavKey>(metadata = extraMetadata) { key ->
        ProfileScreen(
            userId = key.userId,
            onBack = onBack,
            onOpenChat = onOpenChat,
            onEditProfile = onEditProfile,
            isExtraPane = true,
        )
    }
}
