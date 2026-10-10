package com.github.woodsmarshes.chat.feature.profile.model

import com.github.woodsmarshes.chat.core.model.ContactStatus

data class ProfileUiState(
    val userId: String? = null,
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val email: String? = null,
    val isOwnProfile: Boolean = false,
    val contactStatus: ContactStatus? = null,
    val remark: String? = null,
    // True until the first emission arrives (cached row or refresh result).
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    // Refresh failed and nothing is cached to fall back to.
    val error: String? = null,
    // The navigation argument was not a valid UUID, or the server answered
    // UserError.NotFound (404) for a well-formed one.
    val notFound: Boolean = false,
    // One-shot feedback for actions.
    val actionError: String? = null,
    val actionMessage: String? = null,
    val isStartingChat: Boolean = false,

    // Add friend dialog
    val showAddFriendDialog: Boolean = false,
    val addFriendMessage: String = "",
    val isSendingFriendRequest: Boolean = false,
    // A PENDING request towards this user already exists; the button turns
    // into a disabled state instead of allowing duplicate sends.
    val isFriendRequestPending: Boolean = false,

    // Edit remark dialog
    val showEditRemarkDialog: Boolean = false,
    val editRemarkValue: String = "",

    // Block confirm dialog
    val showBlockConfirmDialog: Boolean = false,

    // Delete friend confirm dialog
    val showDeleteFriendConfirmDialog: Boolean = false,
)

