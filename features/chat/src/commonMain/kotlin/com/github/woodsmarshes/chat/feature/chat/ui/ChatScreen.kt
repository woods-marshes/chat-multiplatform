package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(lazyMessages.itemCount) {
        if (lazyMessages.itemCount > 0 && isAtBottom) {
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
            ChatInputBar(
                value = uiState.input,
                onValueChange = viewModel::onInputChanged,
                onSend = viewModel::sendMessage,
                onImageClick = { /* TODO: 打开系统图片选择器 */ },
                onFileClick = { /* TODO: 打开系统文件选择器 */ },
                onVoiceClick = { /* TODO: 开始录音 */ },
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = padding,
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
