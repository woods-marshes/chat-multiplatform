package com.github.woodsmarshes.chat.feature.conversations.model

import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel

data class ConversationsUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,

    // FAB bottom sheet
    val showActions: Boolean = false,

    // Create group
    val showCreateGroup: Boolean = false,
    val groupName: String = "",
    val groupDescription: String = "",
    val isCreating: Boolean = false,
    val createError: String? = null,

    // Join group
    val showJoinGroup: Boolean = false,
    val joinGroupId: String = "",
    val isJoining: Boolean = false,
    val joinError: String? = null,
)
