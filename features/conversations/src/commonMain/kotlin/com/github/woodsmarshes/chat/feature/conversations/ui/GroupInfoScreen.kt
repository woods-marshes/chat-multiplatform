package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.AppModalBottomSheet
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.bubble.RoleMicroBadge
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversationId: String,
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    isExtraPane: Boolean = false,
    viewModel: GroupInfoViewModel = koinViewModel(parameters = { parametersOf(conversationId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val inMultiPane = isInListDetailScene()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isOwner = uiState.myRole == ConversationRole.OWNER
    val isAdminOrOwner = isOwner || uiState.myRole == ConversationRole.ADMIN
    val canInvite = uiState.isMember && (isAdminOrOwner || uiState.settings.allowMemberInvite)

    LaunchedEffect(uiState.actionMessage, uiState.actionError) {
        val msg = uiState.actionMessage ?: uiState.actionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val avatarPickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    runCatching { file.readBytes() }
                        .onSuccess { bytes -> viewModel.uploadGroupAvatar(bytes) }
                }
            }
        },
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = strings.groupInfoTitle,
                showBackButton = !inMultiPane,
                showCloseButton = inMultiPane && isExtraPane,
                onBackClick = onBack,
                actions = {
                    if (isOwner) {
                        IconButton(onClick = viewModel::showEditGroup) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = strings.groupEditInfo,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.notFound -> EmptyContent(
                message = strings.groupNotFound,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            uiState.isLoading -> LoadingContent(
                message = strings.loading,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            uiState.error != null && uiState.conversationId == null -> ErrorContent(
                message = uiState.error ?: strings.groupLoadFailed,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRetry = viewModel::refresh,
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item(key = "header") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                UserAvatar(
                                    name = uiState.name,
                                    avatarUrl = uiState.avatarUrl,
                                    size = 120.dp,
                                )
                                if (isOwner) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { avatarPickerLauncher.launch() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = strings.changeAvatar,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = uiState.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            val handle = uiState.handle
                            if (!handle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "@$handle",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            val resolvedConvId = uiState.conversationId
                            if (uiState.isMember && resolvedConvId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (!(inMultiPane && isExtraPane)) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            ChatAppButton(
                                                onClick = { onOpenChat(resolvedConvId) },
                                                label = strings.openChat,
                                                style = ButtonStyle.PRIMARY,
                                                size = ButtonSize.MD,
                                                fullWidth = true,
                                            )
                                        }
                                    }
                                    if (canInvite) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            ChatAppButton(
                                                onClick = viewModel::showInviteFriends,
                                                label = strings.groupInviteMembers,
                                                style = ButtonStyle.SECONDARY,
                                                size = ButtonSize.MD,
                                                fullWidth = true,
                                            )
                                        }
                                    }
                                }
                            } else {
                                ChatAppButton(
                                    onClick = viewModel::onJoinButtonClick,
                                    label = strings.joinGroupTitle,
                                    style = ButtonStyle.PRIMARY,
                                    size = ButtonSize.MD,
                                    enabled = !uiState.isJoining,
                                    isLoading = uiState.isJoining,
                                    fullWidth = true,
                                )
                            }

                            val description = uiState.description
                            if (!description.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Personal Conversation Settings
                    if (uiState.isMember) {
                        item(key = "personal_settings") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                SectionHeader(title = strings.groupMySettingsSection)
                                ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        SettingsItemWithSwitch(
                                            icon = Icons.Default.PushPin,
                                            title = strings.groupPinConversation,
                                            checked = uiState.isPinned,
                                            onCheckedChange = viewModel::togglePin,
                                        )
                                        AppHorizontalDivider()
                                        SettingsItemWithSwitch(
                                            icon = Icons.Default.NotificationsOff,
                                            title = strings.groupMuteNotifications,
                                            checked = uiState.isMuted,
                                            onCheckedChange = viewModel::toggleMute,
                                        )
                                        AppHorizontalDivider()
                                        SettingsItem(
                                            icon = Icons.Default.Badge,
                                            title = strings.groupMyNicknameLabel,
                                            subtitle = uiState.myNickname?.takeIf { it.isNotBlank() }
                                                ?: strings.groupMyNicknamePlaceholder,
                                            onClick = viewModel::showEditNickname,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Group Management (OWNER / ADMIN)
                    if (isAdminOrOwner) {
                        item(key = "group_management") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                SectionHeader(title = strings.groupManagementSection)
                                ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        SettingsItem(
                                            icon = Icons.Default.HowToReg,
                                            title = strings.groupJoinRequestsTitle,
                                            subtitle = if (uiState.pendingJoinRequests.isEmpty()) {
                                                strings.groupJoinRequestsEmpty
                                            } else {
                                                "${uiState.pendingJoinRequests.size}"
                                            },
                                            onClick = viewModel::showJoinRequests,
                                        )
                                        if (isOwner) {
                                            AppHorizontalDivider()
                                            SettingsItem(
                                                icon = Icons.Default.Edit,
                                                title = strings.groupEditInfo,
                                                onClick = viewModel::showEditGroup,
                                            )
                                            AppHorizontalDivider()
                                            SettingsItemWithSwitch(
                                                icon = Icons.Default.VerifiedUser,
                                                title = strings.groupJoinApprovalRequired,
                                                subtitle = strings.groupJoinApprovalRequiredDesc,
                                                checked = uiState.settings.joinApprovalRequired,
                                                onCheckedChange = viewModel::toggleJoinApprovalRequired,
                                            )
                                            AppHorizontalDivider()
                                            SettingsItemWithSwitch(
                                                icon = Icons.Default.PersonAdd,
                                                title = strings.groupAllowMemberInvite,
                                                subtitle = strings.groupAllowMemberInviteDesc,
                                                checked = uiState.settings.allowMemberInvite,
                                                onCheckedChange = viewModel::toggleAllowMemberInvite,
                                            )
                                            AppHorizontalDivider()
                                            SettingsItemWithSwitch(
                                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                                title = strings.groupMuteAll,
                                                subtitle = strings.groupMuteAllDesc,
                                                checked = uiState.settings.muteAll,
                                                onCheckedChange = viewModel::toggleMuteAll,
                                            )
                                            if (uiState.members.size > 1) {
                                                AppHorizontalDivider()
                                                SettingsItem(
                                                    icon = Icons.Default.SwapHoriz,
                                                    title = strings.groupTransferOwnership,
                                                    subtitle = strings.groupTransferConfirmTitle,
                                                    onClick = viewModel::showTransferOwner,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Members header
                    if (uiState.members.isNotEmpty()) {
                        item(key = "members_header") {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "${strings.membersLabel} (${uiState.members.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                            )
                        }
                    }

                    items(
                        items = uiState.members,
                        key = { it.id },
                    ) { member ->
                        MemberRow(member = member)
                    }

                    // Leave / Dissolve actions
                    if (uiState.isMember) {
                        item(key = "danger_zone") {
                            Spacer(modifier = Modifier.height(24.dp))
                            ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    if (!isOwner) {
                                        SettingsItem(
                                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                                            title = strings.groupLeave,
                                            danger = true,
                                            onClick = viewModel::showLeaveConfirm,
                                        )
                                    }
                                    if (isAdminOrOwner) {
                                        if (!isOwner) {
                                            AppHorizontalDivider()
                                        }
                                        SettingsItem(
                                            icon = Icons.Default.DeleteForever,
                                            title = strings.groupDissolve,
                                            danger = true,
                                            onClick = viewModel::showDissolveConfirm,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Join Application Message Dialog (100 chars max) ----
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

    // ---- Edit Personal In-Group Nickname Dialog ----
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

    // ---- Edit Group Profile Dialog (OWNER) ----
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

    // ---- Pending Join Requests Bottom Sheet (OWNER / ADMIN) ----
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

    // ---- Invite Friends Dialog ----
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

    // ---- Transfer Ownership Dialog (OWNER) ----
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

    // ---- Leave Group Confirm Dialog ----
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

    // ---- Dissolve Group Confirm Dialog ----
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

@Composable
private fun MemberRow(member: SenderUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = member.displayName?.ifEmpty { null } ?: member.username,
            avatarUrl = member.avatarUrl,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName?.ifEmpty { null } ?: member.username,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (member.role != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    RoleMicroBadge(role = member.role)
                }
            }
            if (member.username.isNotEmpty()) {
                Text(
                    text = "@${member.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

