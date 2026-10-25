package com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppModalBottomSheet
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.conversations.model.GroupInfoUiState
import com.github.woodsmarshes.chat.feature.conversations.ui.GroupInfoViewModel

/**
 * The eight overlay surfaces GroupInfoScreen layers on top of its content.
 * Extracted verbatim; each is a pure function of the screen's
 * [GroupInfoUiState] plus its [GroupInfoViewModel] callbacks.
 */

@Composable
internal fun JoinMessageDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showJoinDialog,
        onDismissRequest = viewModel::dismissJoinDialog,
        title = strings.groupApplyJoin,
        confirmLabel = strings.join,
        onConfirm = viewModel::confirmJoinWithMessage,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissJoinDialog,
    ) {
        AppTextField(
            value = uiState.joinMessage,
            onValueChange = viewModel::onJoinMessageChanged,
            label = strings.groupApplyMessageLabel,
            placeholder = strings.groupApplyMessagePlaceholder,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${uiState.joinMessage.length}/100",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp),
        )
    }
}

@Composable
internal fun EditNicknameDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showEditNicknameDialog,
        onDismissRequest = viewModel::dismissEditNickname,
        title = strings.groupMyNicknameLabel,
        confirmLabel = strings.save,
        onConfirm = viewModel::saveMyNickname,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissEditNickname,
    ) {
        AppTextField(
            value = uiState.editNicknameValue,
            onValueChange = viewModel::onEditNicknameChanged,
            label = strings.groupMyNicknameLabel,
            placeholder = strings.groupMyNicknamePlaceholder,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun EditGroupDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    val canSaveGroup = !uiState.isSavingGroup &&
        uiState.editGroupName.isNotBlank() &&
        !uiState.isCheckingHandle &&
        (uiState.editGroupHandle.isBlank() || uiState.isHandleAvailable != false)

    AppAlertDialog(
        show = uiState.showEditGroupDialog,
        onDismissRequest = viewModel::dismissEditGroup,
        title = strings.groupEditInfo,
        confirmLabel = strings.save,
        onConfirm = viewModel::saveGroupProfile,
        confirmEnabled = canSaveGroup,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissEditGroup,
    ) {
        AppTextField(
            value = uiState.editGroupName,
            onValueChange = viewModel::onEditGroupNameChanged,
            label = strings.groupNameLabel,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppTextField(
            value = uiState.editGroupHandle,
            onValueChange = viewModel::onEditGroupHandleChanged,
            label = strings.groupHandleLabel,
            singleLine = true,
            isError = uiState.isHandleAvailable == false,
            errorText = if (uiState.isHandleAvailable == false) strings.groupHandleTaken else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.editGroupHandle.isNotBlank() && uiState.editGroupHandle != uiState.handle.orEmpty()) {
            val statusText = when {
                uiState.isCheckingHandle -> strings.groupHandleChecking
                uiState.isHandleAvailable == true -> strings.groupHandleAvailable
                else -> null
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.isHandleAvailable == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AppTextField(
            value = uiState.editGroupDescription,
            onValueChange = viewModel::onEditGroupDescriptionChanged,
            label = strings.groupDescriptionHint,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun JoinRequestsSheet(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    AppModalBottomSheet(
        show = uiState.showJoinRequestsSheet,
        onDismissRequest = viewModel::dismissJoinRequests,
        title = strings.groupJoinRequestsTitle,
    ) {
        if (uiState.pendingJoinRequests.isEmpty()) {
            Text(
                text = strings.groupJoinRequestsEmpty,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.pendingJoinRequests.forEach { req ->
                    val applicant = uiState.requestUsers[req.applicantId]
                    val displayName = applicant?.displayName?.ifEmpty { null }
                        ?: applicant?.username
                        ?: req.applicantId.toString().take(8)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = displayName,
                            avatarUrl = applicant?.avatarUrl,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            if (!req.message.isNullOrBlank()) {
                                Text(
                                    text = req.message!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        ChatAppButton(
                            onClick = { viewModel.handleJoinRequest(req.id, approve = false) },
                            label = strings.reject,
                            style = ButtonStyle.SECONDARY,
                            size = ButtonSize.SM,
                            enabled = !uiState.isHandlingJoinRequest,
                        )
                        Spacer(Modifier.width(8.dp))
                        ChatAppButton(
                            onClick = { viewModel.handleJoinRequest(req.id, approve = true) },
                            label = strings.approve,
                            style = ButtonStyle.PRIMARY,
                            size = ButtonSize.SM,
                            enabled = !uiState.isHandlingJoinRequest,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InviteFriendsDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showInviteSheet,
        onDismissRequest = viewModel::dismissInviteFriends,
        title = strings.groupInviteMembers,
        confirmLabel = strings.groupInviteMembers,
        onConfirm = viewModel::confirmInviteFriends,
        confirmEnabled = !uiState.isInviting && uiState.selectedInviteIds.isNotEmpty(),
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissInviteFriends,
    ) {
        if (uiState.invitableFriends.isEmpty()) {
            Text(
                text = strings.groupNoInvitableFriends,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                uiState.invitableFriends.forEach { friend ->
                    val isSelected = friend.id in uiState.selectedInviteIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.toggleInviteSelection(friend.id) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = friend.displayName ?: friend.username,
                            avatarUrl = friend.avatarUrl,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = friend.displayName ?: friend.username,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "@${friend.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { viewModel.toggleInviteSelection(friend.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransferOwnershipDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showTransferOwnerSheet,
        onDismissRequest = viewModel::dismissTransferOwner,
        title = strings.groupTransferOwnership,
        summary = strings.groupTransferConfirmTitle,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissTransferOwner,
    ) {
        val candidates = uiState.members.filter { it.id != uiState.myUserId }
        val transferTarget = uiState.pendingTransferTarget
        if (transferTarget != null) {
            // Two-step confirm: the transfer is irreversible, so a bare tap on a
            // member row must not trigger it.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = strings.groupTransferConfirmFmt(
                        transferTarget.displayName?.ifEmpty { null } ?: transferTarget.username
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChatAppButton(
                        onClick = viewModel::dismissTransferCandidate,
                        label = strings.cancel,
                        style = ButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                    ChatAppButton(
                        onClick = viewModel::confirmTransferOwnership,
                        label = strings.confirm,
                        style = ButtonStyle.PRIMARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.onTransferCandidateSelected(candidate) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = candidate.displayName?.ifEmpty { null } ?: candidate.username,
                            avatarUrl = candidate.avatarUrl,
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = candidate.displayName?.ifEmpty { null } ?: candidate.username,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "@${candidate.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LeaveGroupDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel, onBack: () -> Unit) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showLeaveConfirmDialog,
        onDismissRequest = viewModel::dismissLeaveConfirm,
        title = strings.groupLeaveConfirmTitle,
        summary = strings.groupLeaveConfirmMsg,
        confirmLabel = strings.groupLeave,
        confirmDanger = true,
        onConfirm = { viewModel.leaveGroup(onLeft = onBack) },
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissLeaveConfirm,
    )
}

@Composable
internal fun DissolveGroupDialog(uiState: GroupInfoUiState, viewModel: GroupInfoViewModel, onBack: () -> Unit) {
    val strings = LocalStrings.current
    AppAlertDialog(
        show = uiState.showDissolveConfirmDialog,
        onDismissRequest = viewModel::dismissDissolveConfirm,
        title = strings.groupDissolveConfirmTitle,
        summary = strings.groupDissolveConfirmMsg,
        confirmLabel = strings.groupDissolve,
        confirmDanger = true,
        onConfirm = { viewModel.dissolveGroup(onDissolved = onBack) },
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissDissolveConfirm,
    )
}
