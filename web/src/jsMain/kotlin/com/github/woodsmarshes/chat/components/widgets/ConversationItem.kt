package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.model.Conversation
import com.github.woodsmarshes.chat.model.ConversationType
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.TextOverflow
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.graphics.lightened
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderBottom
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.cursor
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.flexShrink
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.onContextMenu
import com.varabyte.kobweb.compose.ui.modifiers.onMouseMove
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textOverflow
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.whiteSpace
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.icons.fa.FaThumbtack
import com.varabyte.kobweb.silk.components.layout.HorizontalDivider
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.selectors.hover
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.background
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import kotlin.time.Instant

val ConversationItemStyle = CssStyle {
    base {
        val palette = colorMode.toPalette()
        Modifier
            .fillMaxWidth()
            .height(5.cssRem)
            .padding(leftRight = 1.cssRem, topBottom = 0.75.cssRem)
            .borderBottom(1.px, LineStyle.Solid, palette.border)
            .transition(Transition.of("background-color", 150.ms))
            .cursor(Cursor.Pointer)
    }

    hover {
        val sitePalette = colorMode.toSitePalette()
        Modifier.backgroundColor(sitePalette.subtle)
    }
}

@Composable
fun ConversationItem(
    modifier: Modifier = Modifier,
    conversation: Conversation,
    isSelected: Boolean,
    onConversationClick: (conversationId: Int, isGroup: Boolean) -> Unit,
    formatDateTime: (instant: Instant) -> String,
) {
    val sitePalette = ColorMode.current.toSitePalette()
    val palette = ColorMode.current.toPalette()

    val finalModifier = ConversationItemStyle.toModifier()
        .thenIf(isSelected) {
            Modifier
                .backgroundColor(sitePalette.brand.primary.toRgb().copyf(alpha = 0.1f))
                .color(sitePalette.brand.primary)
        }
        .onClick {
            onConversationClick(conversation.id, conversation.type == ConversationType.Group)
        }
        .onContextMenu {
            it.preventDefault()
            // TODO: 在这里实现右键菜单逻辑
        }
        .then(modifier)

    var menuExpanded by remember {
        mutableStateOf(value = false)
    }

    Row(
        modifier = finalModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.75.cssRem)
    ) {
        ConversationAvatar(
            modifier = Modifier.flexShrink(0),
            avatarUrl = conversation.avatar,
            isGroup = conversation.type == ConversationType.Group,
        )
        // Conversation details
        Column(
            modifier = Modifier.weight(1f).minWidth(0.px),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpanText(
                    text = conversation.name,
                    modifier = Modifier
                        .minWidth(0.px)
                        .whiteSpace(WhiteSpace.NoWrap)
                        .textOverflow(TextOverflow.Ellipsis)
                        .fontSize(1.em)
                        .fontWeight(FontWeight.Bold),
                )

                Row(
                    modifier = Modifier
                        .flexShrink(0)
                        .margin(left = 0.75.cssRem),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
                ) {
                    SpanText(
                        text = conversation.lastMessage?.detail?.timestamp?.let(formatDateTime) ?: "",
                        modifier = Modifier
                            .fontSize(0.8.em)
                            .color(if (isSelected) sitePalette.brand.primary.toRgb().copyf(alpha = 0.8f) else sitePalette.subtle.inverted().toRgb().copyf(alpha = 0.6f))
                    )

                    if (conversation.isPinned) {
                        FaThumbtack(
                            Modifier
                                .fontSize(0.8.em)
                                .color(if (isSelected) sitePalette.brand.primary else Colors.GoldenRod)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().margin(top = 0.25.cssRem),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SpanText(
                    text = conversation.formatMsg,
                    modifier = Modifier
                        .weight(1f)
                        .minWidth(0.px)
                        .whiteSpace(WhiteSpace.NoWrap)
                        .textOverflow(TextOverflow.Ellipsis)
                        .fontSize(0.875.em)
                        .color(if (isSelected) sitePalette.brand.primary.toRgb().copyf(alpha = 0.9f) else palette.color.toRgb().copyf(alpha = 0.8f))
                )
                if (conversation.unreadMessageCount > 0) {
                    UnreadBadge(
                        count = conversation.unreadMessageCount,
                        modifier = Modifier
                            .flexShrink(0)
                            .margin(left = 0.5.cssRem)
                    )
                }
            }

        }
//        if (showContextMenu) {
//            Popup(
//                placement = PopupPlacement.BottomStart, // 可以根据鼠标点击位置动态设置
//                onDismissRequest = { showContextMenu = false }
//            ) {
//                // 这里是你的菜单项
//                Column(Modifier.backgroundColor(Colors.White).padding(8.px).borderRadius(4.px)) {
//                    Button(onClick = { /* 置顶逻辑 */ showContextMenu = false }) { Text("Pin") }
//                    Button(onClick = { /* 删除逻辑 */ showContextMenu = false }) { Text("Delete") }
//                }
//            }
//        }
    }
}


@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val sitePalette = ColorMode.current.toSitePalette()
    val palette = ColorMode.current.toPalette()
    Box(
        modifier = modifier
            .minWidth(1.25.cssRem) // 20px
            .height(1.25.cssRem)
            .borderRadius(50.percent)
            .backgroundColor(sitePalette.brand.primary)
            .padding(leftRight = 0.4.cssRem), // 约 6px
        contentAlignment = Alignment.Center
    ) {
        SpanText(
            text = count.toString(),
            modifier = Modifier.color(palette.color.inverted()).fontSize(0.75.em)
        )
    }
}