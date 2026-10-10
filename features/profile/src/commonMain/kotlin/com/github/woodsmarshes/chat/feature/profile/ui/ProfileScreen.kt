package com.github.woodsmarshes.chat.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenChat: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    onEditProfile: (() -> Unit)? = null,
    isExtraPane: Boolean = false,
    viewModel: ProfileViewModel = koinViewModel(parameters = { parametersOf(userId) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val inMultiPane = isInListDetailScene()

    LaunchedEffect(uiState.actionMessage, uiState.actionError) {
        val msg = uiState.actionMessage ?: uiState.actionError
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = strings.profileTitle,
                showBackButton = !inMultiPane,
                showCloseButton = inMultiPane && isExtraPane,
                onBackClick = onBack,
                actions = {
                    if (uiState.isOwnProfile && onEditProfile != null) {
                        IconButton(onClick = onEditProfile) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = strings.editProfile,
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
                message = strings.userNotFound,
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
            uiState.error != null && uiState.userId == null -> ErrorContent(
                message = uiState.error ?: strings.profileLoadFailed,
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
                val primaryTitle = uiState.remark?.takeIf { it.isNotBlank() }
                    ?: uiState.displayName.ifEmpty { uiState.username }
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    UserAvatar(
                        name = primaryTitle,
                        avatarUrl = uiState.avatarUrl,
                        size = 120.dp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = primaryTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (!uiState.remark.isNullOrBlank() && uiState.displayName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uiState.username.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "@${uiState.username}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val bio = uiState.bio
                    if (!bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    if (uiState.isOwnProfile) {
                        if (onEditProfile != null) {
                            ChatAppButton(
                                onClick = onEditProfile,
                                label = strings.editProfile,
                                style = ButtonStyle.SECONDARY,
                                size = ButtonSize.MD,
                                fullWidth = true,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        ChatAppButton(
                            onClick = { viewModel.startChat(onChatReady = onOpenChat) },
                            label = strings.sendMessage,
                            style = ButtonStyle.PRIMARY,
                            size = ButtonSize.MD,
                            enabled = !uiState.isStartingChat,
                            isLoading = uiState.isStartingChat,
                            fullWidth = true,
                        )
                    } else if (uiState.contactStatus == ContactStatus.BLOCKED) {
                        Text(
                            text = strings.blockedBanner,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        ChatAppButton(
                            onClick = viewModel::toggleBlockUser,
                            label = strings.unblockUser,
                            style = ButtonStyle.SECONDARY,
                            size = ButtonSize.MD,
                            fullWidth = true,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ChatAppButton(
                                    onClick = { viewModel.startChat(onChatReady = onOpenChat) },
                                    label = strings.sendMessage,
                                    style = ButtonStyle.PRIMARY,
                                    size = ButtonSize.MD,
                                    enabled = !uiState.isStartingChat,
                                    isLoading = uiState.isStartingChat,
                                    fullWidth = true,
                                )
                            }
                            if (uiState.contactStatus != ContactStatus.FRIEND) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ChatAppButton(
                                        onClick = viewModel::showAddFriendDialog,
                                        label = if (uiState.isFriendRequestPending) {
                                            strings.friendRequestPending
                                        } else {
                                            strings.addFriend
                                        },
                                        style = ButtonStyle.SECONDARY,
                                        size = ButtonSize.MD,
                                        fullWidth = true,
                                        enabled = !uiState.isFriendRequestPending,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            val email = uiState.email
                            if (!email.isNullOrBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                )
                                AppHorizontalDivider()
                            }
                            val savedUserId = uiState.userId
                            if (savedUserId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = strings.userIdLabel(savedUserId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        clipboard.setText(AnnotatedString(savedUserId))
                                        scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = strings.copyIdCd,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Contact Management Card (for other users)
                    if (!uiState.isOwnProfile) {
                        Spacer(modifier = Modifier.height(20.dp))
                        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                if (uiState.contactStatus == ContactStatus.FRIEND) {
                                    SettingsItem(
                                        icon = Icons.Default.Badge,
                                        title = strings.editRemark,
                                        subtitle = uiState.remark?.takeIf { it.isNotBlank() }
                                            ?: strings.remarkPlaceholder,
                                        onClick = viewModel::showEditRemarkDialog,
                                    )
                                    AppHorizontalDivider()
                                }
                                val isBlocked = uiState.contactStatus == ContactStatus.BLOCKED
                                SettingsItem(
                                    icon = Icons.Default.Block,
                                    title = if (isBlocked) strings.unblockUser else strings.blockUser,
                                    danger = !isBlocked,
                                    onClick = if (isBlocked) {
                                        viewModel::toggleBlockUser
                                    } else {
                                        // Blocking is destructive enough to confirm.
                                        viewModel::showBlockConfirmDialog
                                    },
                                )
                                if (uiState.contactStatus == ContactStatus.FRIEND) {
                                    AppHorizontalDivider()
                                    SettingsItem(
                                        icon = Icons.Default.PersonRemove,
                                        title = strings.deleteFriend,
                                        danger = true,
                                        onClick = viewModel::showDeleteFriendConfirm,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // ---- Add Friend Dialog ----
    AppAlertDialog(
        show = uiState.showAddFriendDialog,
        onDismissRequest = viewModel::dismissAddFriendDialog,
        title = strings.addFriend,
        confirmLabel = strings.addFriend,
        onConfirm = viewModel::sendFriendRequest,
        confirmEnabled = !uiState.isSendingFriendRequest,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissAddFriendDialog,
    ) {
        AppTextField(
            value = uiState.addFriendMessage,
            onValueChange = viewModel::onAddFriendMessageChanged,
            label = strings.addFriendMessageLabel,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ---- Edit Remark Dialog ----
    AppAlertDialog(
        show = uiState.showEditRemarkDialog,
        onDismissRequest = viewModel::dismissEditRemarkDialog,
        title = strings.editRemark,
        confirmLabel = strings.save,
        onConfirm = viewModel::saveRemark,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissEditRemarkDialog,
    ) {
        AppTextField(
            value = uiState.editRemarkValue,
            onValueChange = viewModel::onEditRemarkChanged,
            label = strings.remarkLabel,
            placeholder = strings.remarkPlaceholder,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ---- Block Confirm Dialog ----
    AppAlertDialog(
        show = uiState.showBlockConfirmDialog,
        onDismissRequest = viewModel::dismissBlockConfirmDialog,
        title = strings.blockUser,
        summary = strings.blockConfirmMsg,
        confirmLabel = strings.blockUser,
        confirmDanger = true,
        onConfirm = {
            viewModel.dismissBlockConfirmDialog()
            viewModel.toggleBlockUser()
        },
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissBlockConfirmDialog,
    )

    // ---- Delete Friend Confirm Dialog ----
    AppAlertDialog(
        show = uiState.showDeleteFriendConfirmDialog,
        onDismissRequest = viewModel::dismissDeleteFriendConfirm,
        title = strings.deleteFriendConfirmTitle,
        summary = strings.deleteFriendConfirmMsg,
        confirmLabel = strings.deleteFriend,
        confirmDanger = true,
        onConfirm = viewModel::deleteFriend,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissDeleteFriendConfirm,
    )
}

