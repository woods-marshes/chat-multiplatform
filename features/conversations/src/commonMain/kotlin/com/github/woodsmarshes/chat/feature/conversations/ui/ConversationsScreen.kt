package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppFloatingActionButton
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.AppPullToRefreshBox
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.search.rememberFreshSearchBarState
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ConversationSkeleton
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.ListScreenScaffold
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (conversationId: String, isGroup: Boolean) -> Unit,
    onGroupInfoClick: (conversationId: String) -> Unit,
    modifier: Modifier = Modifier,
    selectedConversationId: String? = null,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchBarState = rememberFreshSearchBarState()
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val inMultiPane = isInListDetailScene()

    val avatarPickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    runCatching { file.readBytes() }
                        .onSuccess { bytes -> viewModel.onGroupAvatarSelected(bytes) }
                }
            }
        },
    )

    Scaffold(
        topBar = {
            AdaptiveSearchBar(
                onQueryChange = { searchQuery = it },
                onSearchQuery = viewModel::onSearchQuery,
                state = searchBarState,
                placeholder = strings.searchConversationsPlaceholder,
                navigationIcon = if (onMenuClick != null) Icons.Default.Menu else null,
                onNavigationIconClick = onMenuClick,
                trailingAffordance = LocalAccountAffordance.current,
                searchViewContent = {
                    when {
                        uiState.isSearching && uiState.searchResults.isEmpty() -> LoadingContent(
                            message = strings.loading,
                            modifier = Modifier.fillMaxSize(),
                        )
                        uiState.searchError != null && uiState.searchResults.isEmpty() -> ErrorContent(
                            message = uiState.searchError ?: strings.searchFailed,
                            modifier = Modifier.fillMaxSize(),
                            onRetry = viewModel::retrySearch,
                        )
                        uiState.searchResults.isEmpty() -> EmptyContent(
                            message = if (searchQuery.isNotBlank()) strings.searchNoResults else strings.searchPrompt,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = uiState.searchResults,
                                key = { it.id },
                            ) { conv ->
                                ConversationItem(
                                    conversation = conv,
                                    onClick = {
                                        scope.launch { searchBarState.animateToCollapsed() }
                                        onGroupInfoClick(conv.id.toString())
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = viewModel::showCreateGroup,
                icon = Icons.Default.Add,
                contentDescription = strings.createGroupTitle,
            )
        },
    ) { innerPadding ->
        ListScreenScaffold(
            isLoading = uiState.isLoading,
            error = uiState.error,
            isEmpty = uiState.conversations.isEmpty(),
            emptyMessage = strings.noConversations,
            onRetry = viewModel::refresh,
            loadingContent = {
                ListSkeleton(count = 8, skeleton = { ConversationSkeleton() })
            },
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppPullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = uiState.conversations,
                        key = { it.id },
                    ) { conv ->
                        ConversationItem(
                            conversation = conv,
                            selected = inMultiPane && conv.id.toString() == selectedConversationId,
                            onClick = {
                                onConversationClick(
                                    conv.id.toString(),
                                    conv.type == ConversationType.GROUP
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- Create group dialog ----
    val canConfirmCreate = !uiState.isCreating &&
        uiState.groupName.isNotBlank() &&
        !uiState.isCheckingHandle &&
        (uiState.groupHandle.isBlank() || uiState.isHandleAvailable != false)

    AppAlertDialog(
        show = uiState.showCreateGroup,
        onDismissRequest = viewModel::dismissCreateGroup,
        title = strings.createGroupTitle,
        confirmLabel = strings.create,
        onConfirm = {
            viewModel.createGroup { createdId ->
                onConversationClick(createdId, true)
            }
        },
        confirmEnabled = canConfirmCreate,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissCreateGroup,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val errorMsg = uiState.createError
            if (errorMsg != null) {
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Group Avatar selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { avatarPickerLauncher.launch() }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.avatarBytes != null) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (uiState.avatarBytes != null) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.PhotoCamera
                        },
                        contentDescription = strings.changeAvatar,
                        tint = if (uiState.avatarBytes != null) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (uiState.avatarBytes != null) strings.groupAvatarSelected else strings.changeAvatar,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (uiState.avatarBytes != null) {
                        Text(
                            text = "${uiState.avatarBytes!!.size / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = uiState.groupName,
                onValueChange = viewModel::onGroupNameChanged,
                label = strings.groupNameLabel,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            AppTextField(
                value = uiState.groupHandle,
                onValueChange = viewModel::onGroupHandleChanged,
                label = strings.groupHandleLabel,
                singleLine = true,
                isError = uiState.isHandleAvailable == false,
                errorText = if (uiState.isHandleAvailable == false) strings.groupHandleTaken else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.groupHandle.isNotBlank()) {
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
                value = uiState.groupDescription,
                onValueChange = viewModel::onGroupDescriptionChanged,
                label = strings.groupDescriptionHint,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            AppHorizontalDivider()
            SettingsItemWithSwitch(
                icon = Icons.Default.VerifiedUser,
                title = strings.groupJoinApprovalRequired,
                subtitle = strings.groupJoinApprovalRequiredDesc,
                checked = uiState.joinApprovalRequired,
                onCheckedChange = viewModel::onJoinApprovalRequiredChanged,
            )
            SettingsItemWithSwitch(
                icon = Icons.Default.PersonAdd,
                title = strings.groupAllowMemberInvite,
                subtitle = strings.groupAllowMemberInviteDesc,
                checked = uiState.allowMemberInvite,
                onCheckedChange = viewModel::onAllowMemberInviteChanged,
            )

            if (uiState.availableFriends.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AppHorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = strings.groupSelectMembersLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.selectedMemberIds.isNotEmpty()) {
                        Text(
                            text = strings.groupSelectedMembersFmt(uiState.selectedMemberIds.size.toString()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                uiState.availableFriends.forEach { friend ->
                    val isSelected = friend.id in uiState.selectedMemberIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { viewModel.onToggleMemberSelection(friend.id) }
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
                            onCheckedChange = { viewModel.onToggleMemberSelection(friend.id) },
                        )
                    }
                }
            }

            if (uiState.isCreating) {
                Spacer(Modifier.height(12.dp))
                AppProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    size = 24.dp,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

