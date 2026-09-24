package com.github.woodsmarshes.chat.feature.conversations.model

import com.github.woodsmarshes.chat.core.model.ui.SenderUser

data class GroupInfoUiState(
    val conversationId: String? = null,
    val name: String = "",
    val handle: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val members: List<SenderUser> = emptyList(),
    // Whether the signed-in user is a participant of this group.
    val isMember: Boolean = false,
    // True until the first cached profile emission arrives.
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    // Refresh failed and nothing is cached to fall back to.
    val error: String? = null,
    // The navigation argument was not a valid UUID, or the member-only detail
    // endpoint answered NotParticipant (403) while nothing is cached locally.
    val notFound: Boolean = false,
    // One-shot error for actions (e.g. joining failed).
    val actionError: String? = null,
    val isJoining: Boolean = false,
)
