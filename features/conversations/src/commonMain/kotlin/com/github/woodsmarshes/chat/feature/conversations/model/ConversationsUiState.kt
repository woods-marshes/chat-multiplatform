package com.github.woodsmarshes.chat.feature.conversations.model

import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import kotlin.uuid.Uuid

data class ConversationsUiState(
    val conversations: List<ConversationUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,

    // Create group modal
    val showCreateGroup: Boolean = false,
    val groupName: String = "",
    val groupHandle: String = "",
    val isCheckingHandle: Boolean = false,
    val isHandleAvailable: Boolean? = null,
    val groupDescription: String = "",
    val joinApprovalRequired: Boolean = false,
    val allowMemberInvite: Boolean = true,
    val availableFriends: List<ContactUiModel> = emptyList(),
    val selectedMemberIds: Set<Uuid> = emptySet(),
    val avatarBytes: ByteArray? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,

    // In-place search (AdaptiveSearchBar)
    val searchResults: List<ConversationUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
)

