package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.bubble.messageItems
import com.github.woodsmarshes.chat.core.ui.components.bubble.rememberFormatter
import com.github.woodsmarshes.chat.core.ui.components.input.ChatInputBar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
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

    // 跟踪之前的消息数量，用于检测新消息
    val previousMessageCount = remember { mutableStateOf(0) }

    // 当消息数量变化时，自动滚动到最新消息（底部）
    LaunchedEffect(lazyMessages.itemCount) {
        if (lazyMessages.itemCount > 0) {
            // reverseLayout = true 时，index 0 是最下面的最新消息
            listState.animateScrollToItem(0)
            previousMessageCount.value = lazyMessages.itemCount
        }
    }

    // 发送消息后也滚动到底部
    LaunchedEffect(uiState.isSending) {
        if (!uiState.isSending && lazyMessages.itemCount > 0) {
            listState.animateScrollToItem(0)
        }
    }

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
                    .navigationBarsPadding()
                    .imePadding()
                    .background(MaterialTheme.colorScheme.surface)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding),
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
    }
}
