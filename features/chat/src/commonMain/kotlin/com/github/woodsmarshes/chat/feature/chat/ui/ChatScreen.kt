package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.bubble.messageItems
import com.github.woodsmarshes.chat.core.ui.components.bubble.rememberFormatter
import com.github.woodsmarshes.chat.core.ui.components.input.ChatInputBar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onProfileClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyMessages = viewModel.messages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val formatter = rememberFormatter()
    val coroutineScope = rememberCoroutineScope()

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

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.chatTitle,
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        bottomBar = {
            // 使用 Box 包裹 ChatInputBar，设置背景色避免透明问题
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
//                   .navigationBarsPadding()
//                    .imePadding()
            ) {
                ChatInputBar(
                    value = uiState.input,
                    onValueChange = viewModel::onInputChanged,
                    onSend = viewModel::sendMessage,
                    onImageClick = { /* TODO: 打开系统图片选择器 */ },
                    onFileClick = { /* TODO: 打开系统文件选择器 */ },
                    onVoiceClick = { /* TODO: 开始录音 */ },
                )
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
                    onRetry = { viewModel.sendMessage() },
                    onReply = { viewModel.setReplyTo(it) },
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
                            contentDescription = "Scroll to bottom",
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
}
