package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.woodsmarshes.chat.components.widgets.message.MessageStatusIndicator
import com.github.woodsmarshes.chat.components.widgets.message.messageColors
import com.github.woodsmarshes.chat.model.CodeMessage
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px
import kotlin.time.Instant

@Composable
fun CodeMessageBubble(
    modifier: Modifier = Modifier,
    message: CodeMessage,
    replyMessage: ReplyPreview? = null,
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    val formattedTime = remember(message.detail.timestamp) { formatDateTime(message.detail.timestamp) }
    val messageColors = messageColors(message.detail.isOwnMessage)

    // 气泡根容器
    Box(
        modifier = modifier
            .backgroundColor(messageColors.bubbleColor)
            .borderRadius(8.px)
            .padding(4.px)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.px)) {

            replyMessage?.let {
                Box(
                    Modifier
                        .backgroundColor(messageColors.replyBackgroundColor)
                        .borderRadius(6.px)
                        .onClick { onClickQuotedMessage(replyMessage.msgId) }
                        .padding(leftRight = 4.px) // 给回复预览一点内边距
                ) {
                    com.github.woodsmarshes.chat.components.widgets.message.ReplyPreview(
                        replyToMessage = it,
                        contentColor = messageColors.replyContentColor
                    )
                }
            }

            Box {

//                CodeSnippet(
//                    // 使用 trimIndent() 清理代码缩进
//                    code = message.formatMessage.trimIndent(),
//                    // 假设你的 CodeMessage 模型包含语言信息
//                    lang = message.language,
//                    modifier = Modifier
//                        .maxWidth(80.vw) // 限制最大宽度
//                        .overflowX(Overflow.Auto) // 允许水平滚动
//                )


                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .margin(8.px)
                        .backgroundColor(Colors.Black.copyf(alpha = 0.6f))
                        .borderRadius(12.px)
                        .padding(leftRight = 8.px, topBottom = 4.px),
                    horizontalArrangement = Arrangement.spacedBy(4.px),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpanText(
                        text = formattedTime,
                        modifier = Modifier.color(Colors.White.copyf(alpha = 0.95f)).fontSize(0.75.em)
                    )
                    if (message.detail.isOwnMessage) {
                        MessageStatusIndicator(state = message.detail.state, color = Colors.White.copyf(alpha = 0.95f))
                    }
                }
            }
        }
    }
}