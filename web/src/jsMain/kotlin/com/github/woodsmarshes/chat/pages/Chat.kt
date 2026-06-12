package com.github.woodsmarshes.chat.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.paging.LoadState
import com.github.woodsmarshes.chat.common.utils.debug
import com.github.woodsmarshes.chat.components.layouts.PageLayoutData
import com.github.woodsmarshes.chat.components.widgets.CircularProgressIndicator
import com.github.woodsmarshes.chat.components.widgets.ConversationItem
import com.github.woodsmarshes.chat.components.widgets.TopBar
import com.github.woodsmarshes.chat.components.widgets.message.ChatInputBar
import com.github.woodsmarshes.chat.components.widgets.message.MessageList
import com.github.woodsmarshes.chat.koin
import com.github.woodsmarshes.chat.log
import com.github.woodsmarshes.chat.model.Conversation
import com.github.woodsmarshes.chat.model.ConversationType
import com.github.woodsmarshes.chat.model.MessageState
import com.github.woodsmarshes.chat.model.viewmodel.chat.ChatViewModelForWeb
import com.github.woodsmarshes.chat.model.viewmodel.conversation.ConversationEvent
import com.github.woodsmarshes.chat.model.viewmodel.conversation.ConversationUiState
import com.github.woodsmarshes.chat.model.viewmodel.conversation.ConversationViewModel
import com.github.woodsmarshes.chat.model.viewmodel.conversation.SheetContentType
import com.github.woodsmarshes.chat.network.api.websocket.WebSocketState
import com.github.woodsmarshes.chat.rememberKoinInstance
import com.github.woodsmarshes.chat.toSitePalette
import com.github.woodsmarshes.chat.utils.foundation.lazy.rememberLazyListState
import com.github.woodsmarshes.chat.utils.paging.collectAsLazyPagingItems
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.animation
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRight
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxHeight
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.flex
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onDragEnter
import com.varabyte.kobweb.compose.ui.modifiers.onDragOver
import com.varabyte.kobweb.compose.ui.modifiers.onDrop
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.scrollBehavior
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.translateX
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.data.add
import com.varabyte.kobweb.core.init.InitRoute
import com.varabyte.kobweb.core.init.InitRouteContext
import com.varabyte.kobweb.core.layout.Layout
import com.varabyte.kobweb.framework.annotations.DelicateApi
import com.varabyte.kobweb.silk.components.icons.fa.FaArrowLeft
import com.varabyte.kobweb.silk.components.icons.fa.FaEllipsis
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import com.varabyte.kobweb.silk.style.breakpoint.Breakpoint
import com.varabyte.kobweb.silk.style.breakpoint.displayIfAtLeast
import com.varabyte.kobweb.silk.style.breakpoint.displayUntil
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import kotlinx.browser.window
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.koin.core.parameter.parametersOf
import org.w3c.dom.HTMLElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

val SlideInFromRight = Keyframes {
    from { Modifier.translateX(100.percent) }
    to { Modifier.translateX(0.percent) }
}
@InitRoute
fun initChatPage(ctx: InitRouteContext) {
    ctx.data.add(PageLayoutData("Chat"))
}

@OptIn(DelicateApi::class)
@Page
@Layout(".components.layouts.ChatLayout")
@Composable
fun ChatPage() {
    val conversationViewModel = rememberKoinInstance<ConversationViewModel>()
    val conversationUiState by conversationViewModel.conversationUiState.collectAsState()

    val selectedConversationId by conversationViewModel.selectedConversationId.collectAsState()

    LaunchedEffect(conversationViewModel.events) {
        conversationViewModel.events.collect { event ->
            when (event) {
                is ConversationEvent.ShowMessage -> {

                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .displayUntil(Breakpoint.MD),
    ) {
        // 总是渲染对话列表，但通过 ChatScreen 的覆盖来隐藏它
        Column(
            Modifier
                .fillMaxSize()
                .backgroundColor(ColorMode.current.toSitePalette().subtle)
        ) {
            ConversationScreen(
                conversationUiState = conversationUiState,
                onConversationClick = { conversationId, _ ->
                    conversationViewModel.selectConversation(conversationId)
                    // showChatInMobile 会在 LaunchedEffect(selectedConversationId) 中被设置为 true
                },
                formatDateTime = conversationViewModel::formatDateTime,
                onConfirmCreateGroup = conversationViewModel::createGroup,
                onConfirmJoinGroup = conversationViewModel::joinGroup,
                selectedConversationId = selectedConversationId,
            )
        }

        if (selectedConversationId != null) {
            val selectedConversation = (conversationUiState as? ConversationUiState.Success)
                ?.conversations?.find { it.id == selectedConversationId }

            if (selectedConversation != null) {
                key(selectedConversation.id) {
                    ChatScreen(
                        modifier = Modifier.animation(
                            SlideInFromRight.toAnimation(duration = 250.ms, timingFunction = AnimationTimingFunction.EaseOut)
                        ),
                        conversationId = selectedConversation.id,
                        isGroup = selectedConversation.type == ConversationType.Group,
                        onBackClick = {
                            conversationViewModel.clearSelection()
                        },
                        showBackButton = true,
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .displayIfAtLeast(Breakpoint.MD)
            .fillMaxSize()
//            .alignItems(AlignItems.Stretch) ,
//        horizontalArrangement = Arrangement.SpaceBetween,
//        verticalAlignment = Alignment.FromStyle
    ) {

        Column(
            Modifier
                .flex(1)
                .fillMaxHeight()
                .borderRight(1.px, LineStyle.Solid, ColorMode.current.toPalette().border)
                .backgroundColor(ColorMode.current.toSitePalette().subtle)
                .minWidth(0.px),
        ) {
            ConversationScreen(
                conversationUiState = conversationUiState,
                onConversationClick = { conversationId, isGroup ->
                    conversationViewModel.selectConversation(conversationId)
                },
                formatDateTime = conversationViewModel::formatDateTime,
                onConfirmCreateGroup = conversationViewModel::createGroup,
                onConfirmJoinGroup = conversationViewModel::joinGroup,
                selectedConversationId = selectedConversationId,
            )
        }

        Column(
            Modifier
                .flex(3)
                .fillMaxHeight()
        ) {
            if (selectedConversationId != null) {
                val selectedConversation = (conversationUiState as? ConversationUiState.Success)
                    ?.conversations?.find { it.id == selectedConversationId }

                if (selectedConversation != null) {
                    key(selectedConversation.id) {
                        ChatScreen(
                            conversationId = selectedConversation.id,
                            isGroup = selectedConversation.type == ConversationType.Group,
                            onBackClick = { conversationViewModel.clearSelection() },
                            showBackButton = false
                        )
                    }
                }
            } else {
                WelcomeScreen()
            }
        }
    }
}

@Composable
fun WelcomeScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SpanText("Select a conversation to start chatting")
    }
}

@OptIn(ExperimentalTime::class)
@Composable
internal fun ConversationScreen(
    conversationUiState: ConversationUiState,
    selectedConversationId: Int?,
    onConversationClick: (conversationId: Int, isGroup: Boolean) -> Unit,
    formatDateTime: (instant: Instant) -> String,
    onConfirmCreateGroup: (name: String, avatar: String?, introduction: String?, isOpen: Boolean) -> Unit,
    onConfirmJoinGroup: (groupId: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentSheetContent by remember { mutableStateOf<SheetContentType?>(null) }

    Column(Modifier.fillMaxSize()) {
        when (conversationUiState) {
            is ConversationUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SpanText(
                        text = "Error: ${conversationUiState.exception.message ?: "Unknown"}",
                        modifier = Modifier.color(Colors.Red).padding(16.px)
                    )
                }
            }
            ConversationUiState.Loading -> {
                CircularProgressIndicator(Modifier.fillMaxSize())
            }
            is ConversationUiState.Success -> {
                ConversationList(
                    items = conversationUiState.conversations,
                    onClickConversation = onConversationClick,
                    formatDateTime = formatDateTime,
                    selectedConversationId = selectedConversationId,
                )
            }
        }
    }
}

@Composable
fun ConversationList(
    items: List<Conversation>,
    selectedConversationId: Int?,
    onClickConversation: (conversationId: Int, isGroup: Boolean) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    Column(
        Modifier
            .fillMaxSize()
            .overflow {
                x(Overflow.Hidden)
                y(Overflow.Auto)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEach { conv ->
            ConversationItem(
                conversation = conv,
                isSelected = conv.id == selectedConversationId,
                onConversationClick = onClickConversation,
                formatDateTime = formatDateTime
            )
        }
    }
}

@Composable
internal fun ChatScreen(
    modifier: Modifier = Modifier,
    conversationId: Int,
    isGroup: Boolean,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
) {
    val viewModel: ChatViewModelForWeb = remember(conversationId) {
        koin.get { parametersOf(conversationId, isGroup) }
    }

    val webSocketState by viewModel.websocketConnectionState.collectAsState()

    val messages = viewModel.messages.collectAsLazyPagingItems()
    val conversation by viewModel.conversation.collectAsState()

    val chatUiState by viewModel.chatUiState.collectAsState()

    val returnMessage by viewModel.returnMessage.collectAsState()

    val currentInputSelector = viewModel.currentInputSelector
    val textMessageInputted = viewModel.textMessageInputted

    val isSendEnabled = textMessageInputted.isNotBlank() && (webSocketState == WebSocketState.Connected)

    var listContainerRef by remember { mutableStateOf<HTMLElement?>(null) }
    var oldScrollHeight by remember { mutableStateOf<Double?>(null) }
    var isInitialLoad by remember { mutableStateOf(true) }

    // 首次加载或刷新后，立即滚动到底部
    LaunchedEffect(messages.itemCount) {
        val container = listContainerRef ?: return@LaunchedEffect

        if (isInitialLoad && messages.itemCount > 0) {
            // 将滚动操作推迟到下一次浏览器重绘之前
            window.requestAnimationFrame {
                container.style.setProperty("scroll-behavior", "auto")
                container.scrollTo(0.0, container.scrollHeight.toDouble())
                container.style.removeProperty("scroll-behavior")
                isInitialLoad = false
            }
        } else {
            // 新消息到达的逻辑
            val isAtBottom = (container.scrollHeight - container.scrollTop - container.clientHeight) < 150

            if (isAtBottom && oldScrollHeight == null) {
                // 对于新消息，也可以使用 requestAnimationFrame 来确保平滑滚动作用于最新的高度
                window.requestAnimationFrame {
                    container.scrollTo(0.0, container.scrollHeight.toDouble())
                }
            }
        }
    }

    // 向上加载历史消息时，保持滚动位置
    LaunchedEffect(messages.loadState.prepend) {
        val container = listContainerRef ?: return@LaunchedEffect
        when (messages.loadState.prepend) {
            is LoadState.Loading -> {
                oldScrollHeight = container.scrollHeight.toDouble()
            }
            is LoadState.NotLoading -> {
                oldScrollHeight?.let { oldHeight ->
                    val newHeight = container.scrollHeight.toDouble()
                    if (newHeight > oldHeight) {
                        container.style.setProperty("scroll-behavior", "auto")
                        container.scrollTop += (newHeight - oldHeight)
                        container.style.removeProperty("scroll-behavior")
                    }
                    oldScrollHeight = null
                }
            }
            else -> {
                oldScrollHeight = null
            }
        }
    }

    // 监听 refresh 状态，重置 isInitialLoad 标志
    LaunchedEffect(messages.loadState.refresh) {
        if (messages.loadState.refresh is LoadState.Loading) {
            isInitialLoad = true
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .backgroundColor(ColorMode.current.toPalette().background)
            .onDragEnter { it.preventDefault() }
            .onDragOver { it.preventDefault() }
            .onDrop {
                it.preventDefault()
                // 处理文件拖放
                val files = it.dataTransfer?.files?.let { fileList ->
                    (0 until fileList.length).mapNotNull { i -> fileList.item(i) }
                }
                if (!files.isNullOrEmpty()) {

                }
            }
    ) {
        TopBar(
            title = {
                key(webSocketState, chatUiState.messageState) {
                    var opacity by remember { mutableStateOf(0.0) }
                    LaunchedEffect(Unit) {
                        opacity = 1.0
                    }
                    val titleModifier = Modifier
                        .opacity(opacity)
                        .transition(
                            Transition.of("opacity", duration = 200.ms),
                            Transition.of("transform", duration = 200.ms)
                        )

                    when (webSocketState) {
                        WebSocketState.Connecting -> SpanText("Connecting...", titleModifier)
                        is WebSocketState.Error -> SpanText("Connection Failed", titleModifier.color(ColorMode.current.toSitePalette().brand.primary.inverted()))
                        WebSocketState.Disconnected -> SpanText("Disconnected", titleModifier)
                        WebSocketState.Connected -> {
                            when (chatUiState.messageState) {
                                MessageState.Sending -> SpanText("Sending...", titleModifier)
                                is MessageState.SendFailed -> SpanText("Failed to send", titleModifier.color(ColorMode.current.toSitePalette().brand.primary.inverted()))
                                MessageState.Completed -> SpanText(conversation?.name ?: "Chat", titleModifier)
                            }
                        }
                    }
                }
            },
            navigationIcon = if (showBackButton) { { FaArrowLeft() } } else { null },
            actionIcon = { FaEllipsis() },
            onNavigationClick = {
                onBackClick()
            },
            onActionClick = {

            }
        )

        MessageList(
            modifier = Modifier.weight(1f),
            items = messages,
            onClickQuotedMessage = {

            },
            onAvatarClick = {

            },
            onAvatarContextMenu = {

            },
            onMessageClick = {

            },
            onImageClick = {

            },
            formatDateTime = viewModel::formatDateTime,
            onRef = { listContainerRef = it },
        )

        ChatInputBar(
            modifier = Modifier,
            textMessageInputted = textMessageInputted,
            returnMessage = returnMessage,
            isSendEnabled = isSendEnabled,
            currentInputSelector = currentInputSelector,
            onUserInputChanged = viewModel::onUserInputChanged,
            onInputSelectorChanged = viewModel::onInputSelectorChanged,
            sendTextMessage = {
                viewModel.clearReturnMsg()
                viewModel.sendTextMessage()
            },
            appendEmoji = viewModel::appendEmoji,
            onClearReturnMessage = {
                viewModel.clearReturnMsg()
                viewModel.clearReturnMsgId()
            },
            sendImageMessage = {
                viewModel.clearReturnMsg()
                //viewModel.sendImageMessage()
            },
            sendVideoMessage = {
                viewModel.clearReturnMsg()
            },
            sendAudioMessage = {
                viewModel.clearReturnMsg()
            }
        )
    }

}