package com.github.woodsmarshes.chat.feature.contacts.model

import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import kotlin.uuid.Uuid

data class ContactsUiState(
    val contacts: List<ContactUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val actionMessage: String? = null,

    // In-place search (AdaptiveSearchBar)
    val searchResults: List<ContactUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,

    // New Friends (received & sent requests)
    val showNewFriendsSheet: Boolean = false,
    val newFriendsTabIndex: Int = 0,
    val receivedRequests: List<ContactRequest> = emptyList(),
    val sentRequests: List<ContactRequest> = emptyList(),
    val pendingReceivedCount: Int = 0,
    val requestUsers: Map<Uuid, User> = emptyMap(),

    // Group Notifications (incoming & sent group requests)
    val showGroupNotificationsSheet: Boolean = false,
    val groupNotificationsTabIndex: Int = 0,
    val incomingGroupRequests: List<GroupJoinRequest> = emptyList(),
    val sentGroupRequests: List<GroupJoinRequest> = emptyList(),
    val pendingGroupRequestCount: Int = 0,
    val groupNames: Map<Uuid, String> = emptyMap(),
    val isRefreshingGroupNotifications: Boolean = false,
    val groupNotificationsError: String? = null,

    // Blocked Users
    val showBlockedUsersSheet: Boolean = false,
    val blockedContacts: List<ContactUiModel> = emptyList(),
)

