package com.github.woodsmarshes.chat.feature.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.ui.components.AlphabetIndexBar
import com.github.woodsmarshes.chat.core.ui.components.AppFloatingActionButton
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.AppModalBottomSheet
import com.github.woodsmarshes.chat.core.ui.components.AppTabRow
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.ContactItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.search.rememberFreshSearchBarState
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ContactSkeleton
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.components.state.ErrorContent
import com.github.woodsmarshes.chat.core.ui.components.state.ListScreenScaffold
import com.github.woodsmarshes.chat.core.ui.components.state.LoadingContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val indexLetters = ('A'..'Z').map { it.toString() } + "#"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedUserId: String? = null,
    onMenuClick: (() -> Unit)? = null,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchBarState = rememberFreshSearchBarState()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalStrings.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val inMultiPane = isInListDetailScene()

    LaunchedEffect(uiState.actionMessage) {
        val msg = uiState.actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AdaptiveSearchBar(
                onQueryChange = { searchQuery = it },
                onSearchQuery = viewModel::onSearchQuery,
                state = searchBarState,
                placeholder = strings.searchContactsPlaceholder,
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
                            ) { contact ->
                                ContactItem(
                                    contact = contact,
                                    onClick = {
                                        scope.launch { searchBarState.animateToCollapsed() }
                                        onContactClick(contact.id.toString())
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
                onClick = {
                    scope.launch {
                        searchBarState.animateToExpanded()
                    }
                },
                icon = Icons.Default.Add,
                contentDescription = strings.addContactCd,
            )
        },
    ) { innerPadding ->
        ListScreenScaffold(
            isLoading = uiState.isLoading,
            error = uiState.error,
            isEmpty = false,
            emptyMessage = strings.noContacts,
            onRetry = viewModel::refresh,
            loadingContent = {
                ListSkeleton(count = 8, skeleton = { ContactSkeleton() })
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
                    item(key = "contact_management_entries") {
                        ChatAppCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Column {
                                SettingsItem(
                                    icon = Icons.Default.PersonAdd,
                                    title = strings.contactsNewFriends,
                                    subtitle = strings.contactsNewFriendsDesc,
                                    onClick = viewModel::showNewFriends,
                                    trailing = if (uiState.pendingReceivedCount > 0) {
                                        { CountBadge(count = uiState.pendingReceivedCount) }
                                    } else {
                                        null
                                    },
                                )
                                AppHorizontalDivider()
                                SettingsItem(
                                    icon = Icons.Default.Groups,
                                    title = strings.contactsGroupNotifications,
                                    subtitle = strings.contactsGroupNotificationsDesc,
                                    onClick = viewModel::showGroupNotifications,
                                    trailing = if (uiState.pendingGroupRequestCount > 0) {
                                        { CountBadge(count = uiState.pendingGroupRequestCount) }
                                    } else {
                                        null
                                    },
                                )
                                AppHorizontalDivider()
                                SettingsItem(
                                    icon = Icons.Default.Block,
                                    title = strings.contactsBlockedUsers,
                                    subtitle = if (uiState.blockedContacts.isNotEmpty()) {
                                        "${uiState.blockedContacts.size}"
                                    } else {
                                        strings.contactsBlockedUsersDesc
                                    },
                                    onClick = viewModel::showBlockedUsers,
                                )
                            }
                        }
                    }

                    if (uiState.contacts.isEmpty()) {
                        item(key = "empty_contacts") {
                            EmptyContent(
                                message = strings.noContacts,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    } else {
                        items(
                            items = uiState.contacts,
                            key = { it.id },
                        ) { contact ->
                            ContactItem(
                                contact = contact,
                                selected = inMultiPane && contact.id.toString() == selectedUserId,
                                onClick = { onContactClick(contact.id.toString()) },
                            )
                        }
                    }
                }
                if (uiState.contacts.isNotEmpty()) {
                    AlphabetIndexBar(
                        letters = indexLetters,
                        onLetterSelected = { letter ->
                            val idx = uiState.contacts.indexOfFirst {
                                (it.displayName?.firstOrNull() ?: it.username.firstOrNull())?.uppercaseChar() == letter.firstOrNull()
                            }
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx + 1) }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }

    // ---- New Friends Bottom Sheet ----
    AppModalBottomSheet(
        show = uiState.showNewFriendsSheet,
        onDismissRequest = viewModel::dismissNewFriends,
        title = strings.contactsNewFriends,
    ) {
        AppTabRow(
            tabs = listOf(strings.contactsTabReceived, strings.contactsTabSent),
            selectedTabIndex = uiState.newFriendsTabIndex,
            onTabSelected = viewModel::onNewFriendsTabSelected,
        )
        Spacer(Modifier.height(12.dp))
        val activeList = if (uiState.newFriendsTabIndex == 0) {
            uiState.receivedRequests
        } else {
            uiState.sentRequests
        }
        if (activeList.isEmpty()) {
            Text(
                text = strings.contactsNoRequests,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                activeList.forEach { req ->
                    val isReceived = uiState.newFriendsTabIndex == 0
                    val targetId = if (isReceived) req.senderId else req.receiverId
                    val user = uiState.requestUsers[targetId]
                    val name = user?.displayName?.ifEmpty { null }
                        ?: user?.username
                        ?: targetId.toString().take(8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                viewModel.dismissNewFriends()
                                onContactClick(targetId.toString())
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = name,
                            avatarUrl = user?.avatarUrl,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
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
                        when {
                            isReceived && req.status == RequestStatus.PENDING -> {
                                ChatAppButton(
                                    onClick = { viewModel.handleFriendRequest(req.id, approve = false) },
                                    label = strings.reject,
                                    style = ButtonStyle.SECONDARY,
                                    size = ButtonSize.SM,
                                )
                                Spacer(Modifier.width(8.dp))
                                ChatAppButton(
                                    onClick = { viewModel.handleFriendRequest(req.id, approve = true) },
                                    label = strings.approve,
                                    style = ButtonStyle.PRIMARY,
                                    size = ButtonSize.SM,
                                )
                            }
                            !isReceived && req.status == RequestStatus.PENDING -> {
                                ChatAppButton(
                                    onClick = { viewModel.cancelFriendRequest(req.id) },
                                    label = strings.cancelRequest,
                                    style = ButtonStyle.SECONDARY,
                                    size = ButtonSize.SM,
                                )
                            }
                            else -> {
                                val statusLabel = when (req.status) {
                                    RequestStatus.PENDING -> strings.requestStatusPending
                                    RequestStatus.ACCEPTED -> strings.requestStatusAccepted
                                    RequestStatus.REJECTED -> strings.requestStatusRejected
                                    RequestStatus.CANCELED -> strings.requestStatusCanceled
                                }
                                Text(
                                    text = statusLabel,
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

    // ---- Group Notifications Bottom Sheet ----
    AppModalBottomSheet(
        show = uiState.showGroupNotificationsSheet,
        onDismissRequest = viewModel::dismissGroupNotifications,
        title = strings.contactsGroupNotifications,
    ) {
        AppTabRow(
            tabs = listOf(strings.contactsTabReceived, strings.contactsTabSent),
            selectedTabIndex = uiState.groupNotificationsTabIndex,
            onTabSelected = viewModel::onGroupNotificationsTabSelected,
        )
        Spacer(Modifier.height(12.dp))
        if (uiState.isRefreshingGroupNotifications && uiState.incomingGroupRequests.isEmpty() && uiState.sentGroupRequests.isEmpty()) {
            LoadingContent(modifier = Modifier.fillMaxWidth())
            return@AppModalBottomSheet
        }
        if (uiState.groupNotificationsError != null && uiState.incomingGroupRequests.isEmpty() && uiState.sentGroupRequests.isEmpty()) {
            ErrorContent(
                message = uiState.groupNotificationsError!!,
                modifier = Modifier.fillMaxWidth(),
                onRetry = viewModel::refreshGroupNotifications,
            )
            return@AppModalBottomSheet
        }
        val isIncoming = uiState.groupNotificationsTabIndex == 0
        val activeList = if (isIncoming) {
            uiState.incomingGroupRequests
        } else {
            uiState.sentGroupRequests
        }
        if (activeList.isEmpty()) {
            Text(
                text = strings.contactsNoRequests,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                activeList.forEach { req ->
                    val applicant = uiState.requestUsers[req.applicantId]
                    val groupName = uiState.groupNames[req.conversationId]
                        ?: req.conversationId.toString().take(8)
                    val titleText = if (isIncoming) {
                        val userName = applicant?.displayName?.ifEmpty { null }
                            ?: applicant?.username
                            ?: req.applicantId.toString().take(8)
                        "$userName → $groupName"
                    } else {
                        groupName
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = titleText,
                            avatarUrl = if (isIncoming) applicant?.avatarUrl else null,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
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
                        if (isIncoming && req.status == RequestStatus.PENDING) {
                            ChatAppButton(
                                onClick = {
                                    viewModel.handleGroupJoinRequest(
                                        req.conversationId,
                                        req.id,
                                        approve = false,
                                    )
                                },
                                label = strings.reject,
                                style = ButtonStyle.SECONDARY,
                                size = ButtonSize.SM,
                            )
                            Spacer(Modifier.width(8.dp))
                            ChatAppButton(
                                onClick = {
                                    viewModel.handleGroupJoinRequest(
                                        req.conversationId,
                                        req.id,
                                        approve = true,
                                    )
                                },
                                label = strings.approve,
                                style = ButtonStyle.PRIMARY,
                                size = ButtonSize.SM,
                            )
                        } else {
                            val statusLabel = when (req.status) {
                                RequestStatus.PENDING -> strings.requestStatusPending
                                RequestStatus.ACCEPTED -> strings.requestStatusAccepted
                                RequestStatus.REJECTED -> strings.requestStatusRejected
                                RequestStatus.CANCELED -> strings.requestStatusCanceled
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Blocked Users Bottom Sheet ----
    AppModalBottomSheet(
        show = uiState.showBlockedUsersSheet,
        onDismissRequest = viewModel::dismissBlockedUsers,
        title = strings.contactsBlockedUsers,
    ) {
        if (uiState.blockedContacts.isEmpty()) {
            Text(
                text = strings.contactsNoBlocked,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                uiState.blockedContacts.forEach { blocked ->
                    val name = blocked.displayName ?: blocked.username
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            name = name,
                            avatarUrl = blocked.avatarUrl,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "@${blocked.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        ChatAppButton(
                            onClick = { viewModel.unblockUser(blocked.id) },
                            label = strings.unblockUser,
                            style = ButtonStyle.SECONDARY,
                            size = ButtonSize.SM,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
        )
    }
}

