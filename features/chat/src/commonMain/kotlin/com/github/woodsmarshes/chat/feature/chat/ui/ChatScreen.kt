package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.components.ChatConversationTopBar
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.bubble.messageItems
import com.github.woodsmarshes.chat.core.ui.components.bubble.rememberFormatter
import com.github.woodsmarshes.chat.core.ui.components.input.ChatInputBar
import com.github.woodsmarshes.chat.core.ui.components.item.ConversationItem
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onProfileClick: (String) -> Unit,
    onGroupInfoClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyMessages = viewModel.messages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val formatter = rememberFormatter()
    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val forwardTargets by viewModel.forwardTargets.collectAsState(initial = emptyList<ConversationUiModel>())
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val strings = LocalStrings.current

    // 记录最新一条消息的 ID
    val latestMessage = if (lazyMessages.itemCount > 0) lazyMessages[0] else null
    val latestMessageId = latestMessage?.id?.toString()

    // 新消息未读计数
    val unreadCount = remember { mutableStateOf(0) }
    // 是否为初次进入页面
    val isFirstLoad = remember { mutableStateOf(true) }

    LaunchedEffect(latestMessageId) {
        if (latestMessageId != null) {
            val isOwn = latestMessage.sender?.id == uiState.ownUserId
            if (isFirstLoad.value) {
                // 初次加载，直接定位到最底部，不播放动画
                listState.scrollToItem(0)
                isFirstLoad.value = false
            } else {
                // 如果用户当前就在底部附近（小于5条消息），或者是自己发的消息，则自动滚动
                if (listState.firstVisibleItemIndex < 5 || isOwn) {
                    listState.animateScrollToItem(0)
                    unreadCount.value = 0
                } else {
                    // 用户正在往上浏览，只增加未读计数，不滚动
                    unreadCount.value += 1
                }
            }
        }
    }

    val showScrollToBottomFab by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 5
        }
    }

    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            unreadCount.value = 0
        }
    }

//    // 跟踪之前的消息数量，用于检测新消息
//    val previousMessageCount = remember { mutableStateOf(0) }
//
//    // 当消息数量变化时，自动滚动到最新消息（底部）
//    LaunchedEffect(lazyMessages.itemCount) {
//        if (lazyMessages.itemCount > 0) {
//            // reverseLayout = true 时，index 0 是最下面的最新消息
//            listState.animateScrollToItem(0)
//            previousMessageCount.value = lazyMessages.itemCount
//        }
//    }
//
//    // 发送消息后也滚动到底部
//    LaunchedEffect(uiState.isSending) {
//        if (!uiState.isSending && lazyMessages.itemCount > 0) {
//            listState.animateScrollToItem(0)
//        }
//    }

    var headerMenuExpanded by remember { mutableStateOf(false) }

    // Stable message-list callbacks: recreated only when their captures
    // actually change, so LazyColumn items can skip recomposition.
    val selectedIds = remember(uiState.selectedMessages) {
        uiState.selectedMessages.map { it.id }.toSet()
    }
    val onRetryCallback: (MessageUiModel) -> Unit = remember(viewModel) {
        { message: MessageUiModel -> viewModel.sendMessage() }
    }
    val onReplyCallback: (MessageUiModel) -> Unit = remember(viewModel) {
        { message: MessageUiModel -> viewModel.toggleReplyTo(message) }
    }
    val onAvatarClickCallback: (MessageUiModel) -> Unit = remember(onProfileClick) {
        { message: MessageUiModel ->
            message.sender?.id?.let { senderId ->
                onProfileClick(senderId.toString())
            }
        }
    }
    val menuContentCallback: @Composable (MessageUiModel, () -> Unit) -> Unit =
        remember(viewModel, clipboard, snackbarHostState, coroutineScope, strings) {
            { message: MessageUiModel, dismiss: () -> Unit ->
                MessageContextMenu(
                    isText = message.content is TextContent,
                    dismiss = dismiss,
                    onReply = { viewModel.setReplyTo(message) },
                    onCopy = {
                        (message.content as? TextContent)?.let { content ->
                            clipboard.setText(AnnotatedString(content.text))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(strings.copied)
                            }
                        }
                    },
                    onForward = { viewModel.startForward(listOf(message)) },
                    onMultiSelect = { viewModel.enterSelection(message) },
                )
            }
        }

    // Back gesture/button exits multi-select before leaving the chat.
    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled = uiState.selectionMode,
        onBackCompleted = viewModel::clearSelection,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.selectionMode) {
                ChatTopAppBar(
                    title = LocalStrings.current.selectedCountFmt(
                        uiState.selectedMessages.size.toString(),
                    ),
                    showBackButton = true,
                    onBackClick = viewModel::clearSelection,
                )
            } else {
                val strings = LocalStrings.current
                val header = uiState.header
            val typing = uiState.typingUsers
            // Subtitle: typing indicator wins over the member count.
            val subtitle = when {
                typing.isNotEmpty() && header?.isGroup == true ->
                    typing.map { it.displayName?.ifEmpty { null } ?: it.username }
                        .joinToString("、") + " " + strings.typingLabel
                typing.isNotEmpty() -> strings.typingLabel
                header?.isGroup == true && uiState.memberCount != null ->
                    strings.memberCountFmt(uiState.memberCount.toString())
                else -> null
            }
            // While someone types in a group chat, the header avatar swaps
            // to that member (Telegram behaviour).
            val typingUser = if (header?.isGroup == true) typing.firstOrNull() else null
            val openDetails: (() -> Unit)? = when {
                header == null -> null
                header.isGroup -> {
                    val conversationId = header.conversationId.toString()
                    val action: () -> Unit = { onGroupInfoClick(conversationId) }
                    action
                }
                header.peerUserId != null -> {
                    val peerId = header.peerUserId.toString()
                    val action: () -> Unit = { onProfileClick(peerId) }
                    action
                }
                else -> null
            }
            ChatConversationTopBar(
                title = header?.title ?: strings.chatTitle,
                subtitle = subtitle,
                avatarName = typingUser?.let { it.displayName?.ifEmpty { null } ?: it.username }
                    ?: (header?.title ?: strings.chatTitle),
                avatarUrl = typingUser?.avatarUrl ?: header?.avatarUrl,
                onBack = onBack,
                onHeaderClick = openDetails,
                actions = {
                    if (openDetails != null && header != null) {
                        IconButton(onClick = { headerMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = strings.menuCd,
                            )
                        }
                        DropdownMenu(
                            expanded = headerMenuExpanded,
                            onDismissRequest = { headerMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (header.isGroup) strings.groupInfoTitle
                                        else strings.profileTitle
                                    )
                                },
                                onClick = {
                                    headerMenuExpanded = false
                                    openDetails?.invoke()
                                },
                            )
                        }
                    }
                },
            )
            }
        },
        bottomBar = {
            if (uiState.selectionMode) {
                SelectionActionBar(
                    selectedCount = uiState.selectedMessages.size,
                    canReply = uiState.selectedMessages.size == 1,
                    canCopy = uiState.selectedMessages.any { it.content is TextContent },
                    onReply = {
                        uiState.selectedMessages.firstOrNull()?.let(
                            viewModel::setReplyToAndClearSelection,
                        )
                    },
                    onCopy = {
                        val text = uiState.selectedMessages
                            .mapNotNull { (it.content as? TextContent)?.text }
                            .joinToString("\n")
                        clipboard.setText(AnnotatedString(text))
                        viewModel.clearSelection()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(strings.copied)
                        }
                    },
                    onForward = { viewModel.startForward(uiState.selectedMessages) },
                )
            } else {
                // 使用 Box 包裹 ChatInputBar，设置背景色避免透明问题。
                // The draft input is collected HERE, in the bottom-bar scope:
                // keystrokes only recompose the input bar, never the message
                // list (ChatViewModel.input is a separate stream on purpose).
                val draftInput by viewModel.input.collectAsState()
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    ChatInputBar(
                        value = draftInput,
                        onValueChange = viewModel::onInputChanged,
                        onSend = viewModel::sendMessage,
                        replyTo = uiState.replyToMessage,
                        onClearReply = viewModel::clearReplyTo,
                        onImageClick = { /* TODO: 打开系统图片选择器 */ },
                        onFileClick = { /* TODO: 打开系统文件选择器 */ },
                        onVoiceClick = { /* TODO: 开始录音 */ },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    top = padding.calculateTopPadding(),
                    // 添加底部 padding，避免消息被输入框遮挡
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
            ) {
                messageItems(
                    itemCount = lazyMessages.itemCount,
                    itemProvider = { lazyMessages[it] },
                    formatter = formatter,
                    ownUserId = uiState.ownUserId,
                    onRetry = onRetryCallback,
                    onReply = onReplyCallback,
                    onAvatarClick = onAvatarClickCallback,
                    selectionActive = uiState.selectionMode,
                    selectedIds = selectedIds,
                    onToggleSelection = viewModel::toggleSelection,
                    menuContent = menuContentCallback,
                )
            }


            AnimatedVisibility(
                visible = showScrollToBottomFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // 动态计算底部的 Padding 避免遮挡输入框
                    .padding(
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp
                    )
            ) {
                Surface(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier.height(40.dp) // 比普通 FAB 略小
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = strings.scrollToBottomCd,
                            modifier = Modifier.size(20.dp)
                        )
                        if (unreadCount.value > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (unreadCount.value > 99) "99+" else unreadCount.value.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.forwardingMessages.isNotEmpty()) {
        ForwardDialog(
            targets = forwardTargets,
            isSending = uiState.isForwarding,
            onDismiss = viewModel::dismissForward,
            onTargetSelected = viewModel::forwardMessages,
        )
    }
}

/** Bottom action bar shown in multi-select mode. */
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    canReply: Boolean,
    canCopy: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.Reply,
                label = strings.reply,
                enabled = canReply,
                onClick = onReply,
                modifier = Modifier.weight(1f),
            )
            SelectionAction(
                icon = Icons.Default.ContentCopy,
                label = strings.copy,
                enabled = canCopy,
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            )
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.Forward,
                label = strings.forward,
                enabled = true,
                onClick = onForward,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

/** Long-press/tap message menu (reply / copy / forward / multi-select). */
@Composable
private fun MessageContextMenu(
    isText: Boolean,
    dismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    val strings = LocalStrings.current
    DropdownMenu(expanded = true, onDismissRequest = dismiss) {
        DropdownMenuItem(
            text = { Text(strings.reply) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
            },
            onClick = {
                dismiss()
                onReply()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.copy) },
            leadingIcon = {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
            },
            enabled = isText,
            onClick = {
                dismiss()
                onCopy()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.forward) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
            },
            onClick = {
                dismiss()
                onForward()
            },
        )
        DropdownMenuItem(
            text = { Text(strings.multiSelect) },
            leadingIcon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
            },
            onClick = {
                dismiss()
                onMultiSelect()
            },
        )
    }
}

/** Conversation picker shown when forwarding messages. */
@Composable
private fun ForwardDialog(
    targets: List<ConversationUiModel>,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onTargetSelected: (Uuid) -> Unit,
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.forwardTitle) },
        text = {
            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(targets, key = { it.id }) { conversation ->
                    ConversationItem(
                        conversation = conversation,
                        onClick = { onTargetSelected(conversation.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(strings.cancel) }
        },
    )
}
