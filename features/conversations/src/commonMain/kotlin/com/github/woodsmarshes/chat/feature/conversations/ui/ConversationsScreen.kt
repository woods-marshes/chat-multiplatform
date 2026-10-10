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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppFloatingActionButton
import com.github.woodsmarshes.chat.core.ui.components.AppModalBottomSheet
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.AppPullToRefreshBox
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.search.rememberFreshSearchBarState
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ConversationSkeleton
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.ListScreenScaffold
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
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
                onClick = viewModel::showActions,
                icon = Icons.Default.Add,
                contentDescription = strings.newCd,
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

    // ---- FAB bottom sheet ----
    AppModalBottomSheet(
        show = uiState.showActions,
        onDismissRequest = viewModel::dismissActions,
    ) {
        BottomSheetOption(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = strings.createGroupTitle,
            description = strings.createGroupDescription,
            onClick = viewModel::showCreateGroup,
        )
        Spacer(Modifier.height(16.dp))
        BottomSheetOption(
            icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
            label = strings.joinGroupTitle,
            description = strings.joinGroupDescription,
            onClick = viewModel::showJoinGroup,
        )
        Spacer(Modifier.height(16.dp))
    }

    // ---- Create group dialog ----
    AppAlertDialog(
        show = uiState.showCreateGroup,
        onDismissRequest = viewModel::dismissCreateGroup,
        title = strings.createGroupTitle,
        confirmLabel = strings.create,
        onConfirm = viewModel::createGroup,
        confirmEnabled = !uiState.isCreating,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissCreateGroup,
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
        AppTextField(
            value = uiState.groupName,
            onValueChange = viewModel::onGroupNameChanged,
            label = strings.groupNameLabel,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppTextField(
            value = uiState.groupDescription,
            onValueChange = viewModel::onGroupDescriptionChanged,
            label = strings.groupDescriptionHint,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.isCreating) {
            Spacer(Modifier.height(12.dp))
            AppProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 24.dp,
                strokeWidth = 2.dp,
            )
        }
    }

    // ---- Join group dialog ----
    AppAlertDialog(
        show = uiState.showJoinGroup,
        onDismissRequest = viewModel::dismissJoinGroup,
        title = strings.joinGroupTitle,
        confirmLabel = strings.join,
        onConfirm = viewModel::joinGroup,
        confirmEnabled = !uiState.isJoining,
        dismissLabel = strings.cancel,
        onDismiss = viewModel::dismissJoinGroup,
    ) {
        val errorMsg = uiState.joinError
        if (errorMsg != null) {
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        AppTextField(
            value = uiState.joinGroupId,
            onValueChange = viewModel::onJoinGroupIdChanged,
            label = strings.groupIdLabel,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.isJoining) {
            Spacer(Modifier.height(12.dp))
            AppProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 24.dp,
                strokeWidth = 2.dp,
            )
        }
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
