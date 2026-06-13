package com.github.woodsmarshes.chat.feature.chat.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.chat.ui.ChatScreen
import com.github.woodsmarshes.chat.feature.chat.ui.ChatViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class ChatNavKey(
    val conversationId: String,
    val isGroup: Boolean,
) : NavKey

fun EntryProviderScope<NavKey>.chatEntry(
    onBack: () -> Unit,
    onNavigateToProfile: (userId: String) -> Unit,
    metadata: Map<String, Any> = emptyMap(),
) {
    entry<ChatNavKey>(metadata = metadata) { key ->
        ChatScreen(
            viewModel = koinViewModel(
                key = "chat_${key.conversationId}",
                parameters = { parametersOf(key.conversationId, key.isGroup) },
            ),
            onProfileClick = onNavigateToProfile,
            onBack = onBack,
        )
    }
}
