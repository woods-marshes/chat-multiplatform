package com.github.woodsmarshes.chat.feature.conversations.model

import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.ui.ContactUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import kotlin.uuid.Uuid

data class GroupInfoUiState(
    val conversationId: String? = null,
    val name: String = "",
    val handle: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val settings: GroupSettings = GroupSettings(),
    val members: List<SenderUser> = emptyList(),
    // Whether the signed-in user is a participant of this group.
    val isMember: Boolean = false,
    val myUserId: Uuid? = null,
    val myRole: ConversationRole? = null,
    val myParticipantSettings: ParticipantSettings = ParticipantSettings(),
    // Personal settings
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val myNickname: String? = null,
    // True until the first cached profile emission arrives.
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    // Refresh failed and nothing is cached to fall back to.
    val error: String? = null,
    // The navigation argument was not a valid UUID, or the member-only detail
    // endpoint answered NotParticipant (403) while nothing is cached locally.
    val notFound: Boolean = false,
    // One-shot feedback for actions.
    val actionError: String? = null,
    val actionMessage: String? = null,
    val isJoining: Boolean = false,

    // Join application dialog (when joinApprovalRequired == true)
    val showJoinDialog: Boolean = false,
    val joinMessage: String = "",

    // Personal in-group nickname dialog
    val showEditNicknameDialog: Boolean = false,
    val editNicknameValue: String = "",

    // OWNER: Edit group profile dialog
    val showEditGroupDialog: Boolean = false,
    val editGroupName: String = "",
    val editGroupHandle: String = "",
    val isCheckingHandle: Boolean = false,
    val isHandleAvailable: Boolean? = null,
    val editGroupDescription: String = "",
    val isSavingGroup: Boolean = false,

    // OWNER / ADMIN: Pending join requests
    val pendingJoinRequests: List<GroupJoinRequest> = emptyList(),
    val requestUsers: Map<Uuid, User> = emptyMap(),
    val showJoinRequestsSheet: Boolean = false,
    // While an approve/reject call is in flight; buttons stay disabled.
    val isHandlingJoinRequest: Boolean = false,

    // Invite friends sheet
    val showInviteSheet: Boolean = false,
    val invitableFriends: List<ContactUiModel> = emptyList(),
    val selectedInviteIds: Set<Uuid> = emptySet(),
    val isInviting: Boolean = false,

    // OWNER: Transfer ownership sheet
    val showTransferOwnerSheet: Boolean = false,
    // Two-step confirm: the member picked for transfer, before confirmation.
    val pendingTransferTarget: SenderUser? = null,

    // Confirm dialogs
    val showLeaveConfirmDialog: Boolean = false,
    val showDissolveConfirmDialog: Boolean = false,
)

