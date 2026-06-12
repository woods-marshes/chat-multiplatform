package com.github.woodsmarshes.chat.feature.conversations.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ConversationSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (String) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.conversationsTitle,
                showMenuButton = onMenuClick != null,
                onMenuClick = onMenuClick,
                actions = {
                    IconButton(onClick = { /* TODO: search */ }) {
                        Icon(Icons.Default.Search, contentDescription = LocalStrings.current.searchCd)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showActions) {
                Icon(Icons.Default.Add, contentDescription = LocalStrings.current.newCd)
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.conversations.isEmpty() -> {
                ListSkeleton(
                    modifier = Modifier.padding(innerPadding),
                    count = 8, skeleton = { ConversationSkeleton() }
                )
            }
            uiState.error != null && uiState.conversations.isEmpty() -> {
                ErrorContent(
                    message = uiState.error ?: LocalStrings.current.loadFailed,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            uiState.conversations.isEmpty() && !uiState.isLoading -> {
                EmptyContent(
                    message = LocalStrings.current.noConversations,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            else -> {
                val state = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    state = state,
                    indicator = {
                        Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = uiState.isRefreshing,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            state = state
                        )
                    },
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
                                onClick = { onConversationClick(conv.id.toString()) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- FAB bottom sheet ----
    if (uiState.showActions) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissActions,
            sheetState = rememberModalBottomSheetState(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                BottomSheetOption(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = LocalStrings.current.createGroupTitle,
                    description = LocalStrings.current.createGroupDescription,
                    onClick = viewModel::showCreateGroup,
                )
                Spacer(Modifier.height(16.dp))
                BottomSheetOption(
                    icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                    label = LocalStrings.current.joinGroupTitle,
                    description = LocalStrings.current.joinGroupDescription,
                    onClick = viewModel::showJoinGroup,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ---- Create group dialog ----
    if (uiState.showCreateGroup) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateGroup,
            title = { Text(LocalStrings.current.createGroupTitle) },
            text = {
                Column {
                    val errorMsg = uiState.createError
                    if (errorMsg != null) {
                        Text(text = errorMsg, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    OutlinedTextField(
                        value = uiState.groupName,
                        onValueChange = viewModel::onGroupNameChanged,
                        label = { Text(LocalStrings.current.groupNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.groupDescription,
                        onValueChange = viewModel::onGroupDescriptionChanged,
                        label = { Text(LocalStrings.current.groupDescriptionHint) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.isCreating) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::createGroup, enabled = !uiState.isCreating) { Text(LocalStrings.current.create) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreateGroup) { Text(LocalStrings.current.cancel) }
            },
        )
    }

    // ---- Join group dialog ----
    if (uiState.showJoinGroup) {
        AlertDialog(
            onDismissRequest = viewModel::dismissJoinGroup,
            title = { Text(LocalStrings.current.joinGroupTitle) },
            text = {
                Column {
                    val errorMsg = uiState.joinError
                    if (errorMsg != null) {
                        Text(text = errorMsg, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    OutlinedTextField(
                        value = uiState.joinGroupId,
                        onValueChange = viewModel::onJoinGroupIdChanged,
                        label = { Text(LocalStrings.current.groupIdLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (uiState.isJoining) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::joinGroup, enabled = !uiState.isJoining) { Text(LocalStrings.current.join) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissJoinGroup) { Text(LocalStrings.current.cancel) }
            },
        )
    }
}

@Composable
private fun BottomSheetOption(
    icon: @Composable () -> Unit,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
