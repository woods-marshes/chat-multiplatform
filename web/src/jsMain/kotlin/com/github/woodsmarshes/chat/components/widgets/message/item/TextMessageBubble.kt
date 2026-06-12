package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.woodsmarshes.chat.components.widgets.message.MessageStatusIndicator
import com.github.woodsmarshes.chat.components.widgets.message.ReplyPreview
import com.github.woodsmarshes.chat.components.widgets.message.messageColors
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.github.woodsmarshes.chat.model.TextMessage
import com.varabyte.kobweb.compose.css.AlignItems
import com.varabyte.kobweb.compose.css.CSSFloat
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.css.WordBreak
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.alignItems
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.bottom
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.flexWrap
import com.varabyte.kobweb.compose.ui.modifiers.float
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.onContextMenu
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.position
import com.varabyte.kobweb.compose.ui.modifiers.right
import com.varabyte.kobweb.compose.ui.modifiers.whiteSpace
import com.varabyte.kobweb.compose.ui.modifiers.wordBreak
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexWrap
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import kotlin.time.Instant

@Composable
fun TextMessageBubble(
    modifier: Modifier = Modifier,
    message: TextMessage,
    replyMessage: ReplyPreview? = null,
    onClick: () -> Unit = {},
    onContextMenu: () -> Unit = {},
    onClickQuotedMessage: (replyMessageId: Long) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    val formattedTime = remember(message.detail.timestamp) {
        formatDateTime(message.detail.timestamp)
    }

    val messageColors = messageColors(message.detail.isOwnMessage)

    // 1. 气泡的根容器
    Box(
        modifier = modifier
            .backgroundColor(messageColors.bubbleColor)
            .borderRadius(12.px)
            .padding(left = 10.px, right = 10.px, top = 6.px, bottom = 4.px)
            .onClick { onClick() }
            .onContextMenu { it.preventDefault(); onContextMenu() }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.px)) {
            // 2. 回复预览 (如果存在)
            replyMessage?.let {
                Box(
                    Modifier
                        .backgroundColor(messageColors.replyBackgroundColor)
                        .borderRadius(6.px)
                        .onClick { onClickQuotedMessage(replyMessage.msgId) }
                ) {
                    ReplyPreview(
                        replyToMessage = it,
                        contentColor = messageColors.replyContentColor
                    )
                }
            }
            Column {
                P(
                    attrs = Modifier
                        .margin(0.px)
                        .color(messageColors.textColor)
                        .whiteSpace(WhiteSpace.PreWrap)
                        .wordBreak(WordBreak.BreakAll)
                        .toAttrs()
                ) {
                    // 3a. 消息文本
                    SpanText(
                        text = message.formatMessage,
                    )

                }
                Row(
                    modifier = Modifier
                        .align(Alignment.End) // 关键：将自身对齐到父 Column 的右侧
                        .margin(top = 0.25.em),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.px)
                ) {
                    // 使用底层的 Span 元素来确保精确的行内对齐
                    SpanText(
                        text = formattedTime,
                        modifier = Modifier
                            .fontSize(0.7.em)
                            .color(messageColors.textColor.toRgb().copyf(alpha = 0.7f))
                            .wordBreak(WordBreak.BreakAll)
                    )
                    if (message.detail.isOwnMessage) {
                        MessageStatusIndicator(
                            state = message.detail.state,
                            color = messageColors.textColor.toRgb().copyf(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}