package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.woodsmarshes.chat.components.widgets.message.MessageStatusIndicator
import com.github.woodsmarshes.chat.components.widgets.message.messageColors
import com.github.woodsmarshes.chat.model.ImageMessage
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.github.woodsmarshes.chat.network.api.Endpoints.toFullUrl
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.functions.clamp
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.aspectRatio
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.maxHeight
import com.varabyte.kobweb.compose.ui.modifiers.maxWidth
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.objectFit
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.onContextMenu
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.vw
import kotlin.time.Instant

@Composable
fun ImageMessageBubble(
    modifier: Modifier = Modifier,
    message: ImageMessage,
    replyMessage: ReplyPreview? = null,
    onImageClick: (imageUrl: String) -> Unit,
    onContextMenu: () -> Unit = {},
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    val formattedTime = remember(message.detail.timestamp) {
        formatDateTime(message.detail.timestamp)
    }
    val messageColors = messageColors(message.detail.isOwnMessage)
    val imageUrl = remember(message.previewImageUrl) { message.previewImageUrl.toFullUrl() }

    // 气泡的根容器
    Box(
        modifier = modifier
            .borderRadius(12.px) // 图片气泡通常更圆润
            .onContextMenu { it.preventDefault(); onContextMenu() }
            .overflow(Overflow.Hidden)
    ) {
        Column(
            modifier = Modifier.padding(4.px), // 内部留出一点空间
            verticalArrangement = Arrangement.spacedBy(4.px)
        ) {
            // 1. 回复预览 (如果存在) - 这部分逻辑和 TextMessageBubble 相同
            replyMessage?.let {
                Box(
                    Modifier
                        .backgroundColor(messageColors.replyBackgroundColor.toRgb().copyf(alpha = 0.85f))
                        .borderRadius(6.px)
                        .onClick { onClickQuotedMessage(replyMessage.msgId) }
                        .padding(leftRight = 8.px, topBottom = 4.px)
                ) {
                    com.github.woodsmarshes.chat.components.widgets.message.ReplyPreview(
                        replyToMessage = it,
                        contentColor = messageColors.replyContentColor
                    )
                }
            }

            // 2. 图片和覆盖层的主容器
            // 使用 Box 来堆叠图片和右下角的时间/状态
            Box(
                modifier = Modifier
                    .width(clamp(120.px, 65.vw, 400.px))
                    .maxHeight(50.vh)
                    .aspectRatio(
                        (message.previewImage.width?.toFloat() ?: 1f) / (message.previewImage.height?.toFloat() ?: 1f)
                    )
                    .borderRadius(8.px) // 给图片本身也加一点圆角
                    .cursor(Cursor.Pointer)
                    .onClick { onImageClick(imageUrl) }
                    .overflow(Overflow.Hidden) // 确保子元素不会超出圆角范围
            ) {
                // 2a. 图片本身 (作为背景)
                Image(
                    src = imageUrl,
                    description = "Image message content",
                    modifier = Modifier
                        .fillMaxSize()
                        .objectFit(ObjectFit.Cover) // 确保图片填满容器并裁剪
                )

                // 2b. 右下角的半透明覆盖层 ("小尾巴")
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd) // 关键：将它定位到父 Box 的右下角
                        .margin(6.px) // 与边角保持一点距离
                        .backgroundColor(Colors.Black.copyf(alpha = 0.5f))
                        .borderRadius(10.px) // 让覆盖层本身也圆润
                        .padding(topBottom = 2.px, leftRight = 6.px),
                    horizontalArrangement = Arrangement.spacedBy(4.px),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 时间文本
                    SpanText(
                        text = formattedTime,
                        modifier = Modifier
                            .color(Colors.White.copyf(alpha = 0.95f))
                            .fontSize(0.7.em)
                    )
                    // 状态指示器
                    if (message.detail.isOwnMessage) {
                        MessageStatusIndicator(
                            state = message.detail.state,
                            color = Colors.White.copyf(alpha = 0.95f)
                        )
                    }
                }
            }
        }
    }
}