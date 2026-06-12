package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.paging.LoadState
import com.github.woodsmarshes.chat.components.widgets.CircularProgressIndicator
import com.github.woodsmarshes.chat.components.widgets.message.item.AudioMessageBubble
import com.github.woodsmarshes.chat.components.widgets.message.item.CodeMessageBubble
import com.github.woodsmarshes.chat.components.widgets.message.item.ImageMessageBubble
import com.github.woodsmarshes.chat.components.widgets.message.item.TextMessageBubble
import com.github.woodsmarshes.chat.components.widgets.message.item.TimeMessageItem
import com.github.woodsmarshes.chat.components.widgets.message.item.VideoMessageBubble
import com.github.woodsmarshes.chat.model.AudioMessage
import com.github.woodsmarshes.chat.model.CodeMessage
import com.github.woodsmarshes.chat.model.GroupProfile
import com.github.woodsmarshes.chat.model.ImageMessage
import com.github.woodsmarshes.chat.model.Message
import com.github.woodsmarshes.chat.model.MessageDetail
import com.github.woodsmarshes.chat.model.MessageState
import com.github.woodsmarshes.chat.model.SystemMessage
import com.github.woodsmarshes.chat.model.TextMessage
import com.github.woodsmarshes.chat.model.TimeMessage
import com.github.woodsmarshes.chat.model.UserProfile
import com.github.woodsmarshes.chat.model.VideoMessage
import com.github.woodsmarshes.chat.utils.foundation.lazy.LazyColumn
import com.github.woodsmarshes.chat.utils.foundation.lazy.LazyListState
import com.github.woodsmarshes.chat.utils.foundation.lazy.rememberLazyListState
import com.github.woodsmarshes.chat.utils.paging.LazyPagingItems
import com.github.woodsmarshes.chat.utils.paging.itemKey
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.ScrollBehavior
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.animation
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexDirection
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.scrollBehavior
import com.varabyte.kobweb.compose.ui.modifiers.translateY
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.px
import org.w3c.dom.HTMLElement
import kotlin.collections.get
import kotlin.time.Clock
import kotlin.time.Instant

val SlideInFromBottomAndFadeIn = Keyframes {
    from {
        Modifier
            .opacity(0)
            .translateY(20.px)
    }
    to {
        Modifier
            .opacity(1)
            .translateY(0.px)
    }
}

@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    items: LazyPagingItems<Message>,
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    onAvatarClick: (Message) -> Unit,
    onAvatarContextMenu: (Message) -> Unit,
    onMessageClick: (Message) -> Unit,
    onImageClick: (imageUrl: String) -> Unit,
    formatDateTime: (instant: Instant) -> String,
    onRef: (HTMLElement) -> Unit,
) {
//    if (items.loadState.prepend is LoadState.Loading) {
//        Box(Modifier.fillMaxWidth().padding(16.px), contentAlignment = Alignment.Center) {
//            CircularProgressIndicator()
//        }
//    }
//    LazyColumn(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(leftRight = 8.px)
//            .scrollBehavior(ScrollBehavior.Smooth),
//        state = state,
//        reverseLayout = true,
//        spacedBy = 4.px,
//        ref = ref { element ->
//            onRef(element)
//        }
//    ) {
//        items(
//            count = items.itemCount,
//            key = items.itemKey { it.detail.msgId })
//        { index ->
//            when (val message = items[index]) {
//                is AudioMessage -> {
//                    val audioMessageBubble = @Composable {
//                        AudioMessageBubble(
//                            message = message,
//                            replyMessage = message.detail.replyPreview,
//                            onClickQuotedMessage = onClickQuotedMessage,
//                            formatDateTime = formatDateTime,
//                        )
//                    }
//                    if (message.detail.isOwnMessage) {
//                        OwnMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = audioMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    } else {
//                        OtherMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = audioMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    }
//                }
//                is CodeMessage -> {
//                    val codeMessageBubble = @Composable {
//                        CodeMessageBubble(
//                            message = message,
//                            replyMessage = message.replyPreview,
//                            onClickQuotedMessage = onClickQuotedMessage,
//                            formatDateTime = formatDateTime,
//                        )
//                    }
//                    if (message.detail.isOwnMessage) {
//                        OwnMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = codeMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    } else {
//                        OtherMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = codeMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    }
//                }
//                is ImageMessage -> {
//                    val imageMessageBubble = @Composable {
//                        ImageMessageBubble(
//                            message = message,
//                            replyMessage = message.detail.replyPreview,
//                            onImageClick = onImageClick,
//                            onContextMenu = {},
//                            onClickQuotedMessage = onClickQuotedMessage,
//                            formatDateTime = formatDateTime,
//                        )
//                    }
//                    if (message.detail.isOwnMessage) {
//                        OwnMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = imageMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    } else {
//                        OtherMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = imageMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    }
//                }
//                is TextMessage -> {
//                    val textMessageBubble = @Composable {
//                        TextMessageBubble(
//                            message = message,
//                            replyMessage = message.detail.replyPreview,
//                            onClick = { onMessageClick(message) },
//                            onClickQuotedMessage = onClickQuotedMessage,
//                            formatDateTime = formatDateTime,
//                        )
//                    }
//                    if (message.detail.isOwnMessage) {
//                        OwnMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = textMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    } else {
//                        OtherMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = textMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    }
//                }
//                is VideoMessage -> {
//                    val videoMessageBubble = @Composable {
//                        VideoMessageBubble(
//                            message = message,
//                            replyMessage = message.detail.replyPreview,
//                            onClick = { onMessageClick(message) },
//                            onClickQuotedMessage = onClickQuotedMessage,
//                            formatDateTime = formatDateTime,
//                            onNavigateToFullScreen = { _, _ ->},
//                        )
//                    }
//                    if (message.detail.isOwnMessage) {
//                        OwnMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = videoMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    } else {
//                        OtherMessageContainer(
//                            modifier = Modifier.animation(
//                                SlideInFromBottomAndFadeIn.toAnimation(
//                                    duration = 300.ms,
//                                    timingFunction = AnimationTimingFunction.EaseOut
//                                )
//                            ),
//                            message = message,
//                            onClickAvatar = onAvatarClick,
//                            messageContent = videoMessageBubble,
//                            onAvatarContextMenu = onAvatarContextMenu
//                        )
//                    }
//                }
//                is SystemMessage -> {
//
//                }
//                is TimeMessage -> {
//                    TimeMessageItem(
//                        modifier = Modifier.animation(
//                            SlideInFromBottomAndFadeIn.toAnimation(
//                                duration = 300.ms,
//                                timingFunction = AnimationTimingFunction.EaseOut
//                            )
//                        ),
//                        message = message,
//                        formatDateTime = formatDateTime,
//                    )
//                }
//
//                else -> {}
//            }
//        }
//    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(leftRight = 8.px)
            .overflow { y(Overflow.Auto) }
            .flexDirection(FlexDirection.ColumnReverse)
        ,
        ref = ref { element ->
            onRef(element)
        }
    ) {
        if (items.loadState.prepend is LoadState.Loading) {
            Box(Modifier.fillMaxWidth().padding(16.px), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        for (index in 0 until items.itemCount) {
            val message = items.peek(index)
//            log.debug(tag = "MessageList", message = "message -> ${message.toString()}")
            if (message != null) {
                key(message.detail.msgId) {
                    when (message) {
                        is AudioMessage -> {
                            val audioMessageBubble = @Composable {
                                AudioMessageBubble(
                                    message = message,
                                    replyMessage = message.detail.replyPreview,
                                    onClickQuotedMessage = onClickQuotedMessage,
                                    formatDateTime = formatDateTime,
                                )
                            }
                            if (message.detail.isOwnMessage) {
                                OwnMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = audioMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            } else {
                                OtherMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = audioMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            }
                        }
                        is CodeMessage -> {
                            val codeMessageBubble = @Composable {
                                CodeMessageBubble(
                                    message = message,
                                    replyMessage = message.replyPreview,
                                    onClickQuotedMessage = onClickQuotedMessage,
                                    formatDateTime = formatDateTime,
                                )
                            }
                            if (message.detail.isOwnMessage) {
                                OwnMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = codeMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            } else {
                                OtherMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = codeMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            }
                        }
                        is ImageMessage -> {
                            val imageMessageBubble = @Composable {
                                ImageMessageBubble(
                                    message = message,
                                    replyMessage = message.detail.replyPreview,
                                    onImageClick = onImageClick,
                                    onContextMenu = {},
                                    onClickQuotedMessage = onClickQuotedMessage,
                                    formatDateTime = formatDateTime,
                                )
                            }
                            if (message.detail.isOwnMessage) {
                                OwnMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = imageMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            } else {
                                OtherMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = imageMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            }
                        }
                        is TextMessage -> {
                            val textMessageBubble = @Composable {
                                TextMessageBubble(
                                    message = message,
                                    replyMessage = message.detail.replyPreview,
                                    onClick = { onMessageClick(message) },
                                    onClickQuotedMessage = onClickQuotedMessage,
                                    formatDateTime = formatDateTime,
                                )
                            }
                            if (message.detail.isOwnMessage) {
                                OwnMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = textMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            } else {
                                OtherMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = textMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            }
                        }
                        is VideoMessage -> {
                            val videoMessageBubble = @Composable {
                                VideoMessageBubble(
                                    message = message,
                                    replyMessage = message.detail.replyPreview,
                                    onClick = { onMessageClick(message) },
                                    onClickQuotedMessage = onClickQuotedMessage,
                                    formatDateTime = formatDateTime,
                                    onNavigateToFullScreen = { _, _ ->},
                                )
                            }
                            if (message.detail.isOwnMessage) {
                                OwnMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = videoMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            } else {
                                OtherMessageContainer(
                                    modifier = Modifier.animation(
                                        SlideInFromBottomAndFadeIn.toAnimation(
                                            duration = 300.ms,
                                            timingFunction = AnimationTimingFunction.EaseOut
                                        )
                                    ),
                                    message = message,
                                    onClickAvatar = onAvatarClick,
                                    messageContent = videoMessageBubble,
                                    onAvatarContextMenu = onAvatarContextMenu
                                )
                            }
                        }
                        is SystemMessage -> {

                        }
                        is TimeMessage -> {
                            TimeMessageItem(
                                modifier = Modifier.animation(
                                    SlideInFromBottomAndFadeIn.toAnimation(
                                        duration = 300.ms,
                                        timingFunction = AnimationTimingFunction.EaseOut
                                    )
                                ),
                                message = message,
                                formatDateTime = formatDateTime,
                            )
                        }
                    }
                }
            } else {
                // SkeletonMessageItem()
            }
        }
    }
}

val testMessage =
    TextMessage(
        messageDetail = MessageDetail(
            msgId = 1,
            sessionId = 1,
            timestamp = Clock.System.now(),
            state = MessageState.Completed,
            sender = UserProfile(
                userId = 1,
                userName = "测试",
                nickname = "测试",
                email = "测试",
                avatar = null,
                signature = null,
                createdOn = Clock.System.now(),
                updateOn = Clock.System.now(),
            ),
            isOwnMessage = true,
            groupProfile = GroupProfile(
                id = "",
                avatarUrl = "",
                name = "",
                introduction = "",
                createTime = Clock.System.now(),
                updateOn = Clock.System.now(),
                owner = 1,
                isOpen = true
            ),
            returnMessageId = null,
        ),
        text = "测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试测试"
    )

val messages = listOf(testMessage, )