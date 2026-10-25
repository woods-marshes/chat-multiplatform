package com.github.woodsmarshes.chat.feature.conversations.ui

import com.github.woodsmarshes.chat.core.ui.components.motion.sharedConversationBounds
import com.github.woodsmarshes.chat.core.model.GroupProfile
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
import com.github.woodsmarshes.chat.core.ui.components.RoleMicroBadge
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.DissolveGroupDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.EditGroupDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.EditNicknameDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.InviteFriendsDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.JoinMessageDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.JoinRequestsSheet
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.LeaveGroupDialog
import com.github.woodsmarshes.chat.feature.conversations.ui.groupinfo.TransferOwnershipDialog
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
    searchProfile: GroupProfile? = null,
    viewModel: GroupInfoViewModel = koinViewModel(parameters = { parametersOf(conversationId, searchProfile) }),
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
                            // Keep the edit badge in the avatar's shared overlay and coordinate space.
                            Box(
                                modifier = Modifier.sharedConversationBounds(conversationId, "avatar"),
                                contentAlignment = Alignment.BottomEnd,
                            ) {
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
                                modifier = Modifier.sharedConversationBounds(conversationId, "title"),
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

    JoinMessageDialog(uiState = uiState, viewModel = viewModel)
    EditNicknameDialog(uiState = uiState, viewModel = viewModel)
    EditGroupDialog(uiState = uiState, viewModel = viewModel)
    JoinRequestsSheet(uiState = uiState, viewModel = viewModel)
    InviteFriendsDialog(uiState = uiState, viewModel = viewModel)
    TransferOwnershipDialog(uiState = uiState, viewModel = viewModel)
    LeaveGroupDialog(uiState = uiState, viewModel = viewModel, onBack = onBack)
    DissolveGroupDialog(uiState = uiState, viewModel = viewModel, onBack = onBack)
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

