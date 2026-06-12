package com.github.woodsmarshes.chat.core.ui.components.item
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.LastMessageInfo
import com.github.woodsmarshes.chat.core.ui.components.avatar.ConversationAvatar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.utils.formatRelativeTime

@Composable
fun ConversationItem(
    conversation: ConversationUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current
    val convName = conversation.name ?: LocalStrings.current.unnamed
    val lastMsg = conversation.lastMessage

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像 + 未读角标
        Box(modifier = Modifier.size(52.dp)) {
            if (conversation.type == ConversationType.GROUP) {
                ConversationAvatar(
                    participants = emptyList(),
                    size = 52.dp,
                )
            } else {
                UserAvatar(
                    name = conversation.name,
                    avatarUrl = conversation.avatarUrl,
                    size = 52.dp,
                )
            }
            if (conversation.unreadCount > 0) {
                val badgeText = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
                val badgeWidth = if (conversation.unreadCount > 99) 36.dp else if (conversation.unreadCount > 9) 24.dp else 20.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(badgeWidth, 18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(bubbleColors.errorColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 中间信息区
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = convName,
                color = bubbleColors.onSurfaceColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            LastMessagePreview(
                lastMessage = lastMsg,
                unreadCount = conversation.unreadCount,
                bubbleColors = bubbleColors,
            )
        }

        // 右侧时间 + 置顶标记
        Column(horizontalAlignment = Alignment.End) {
            if (lastMsg != null) {
                Text(
                    text = formatRelativeTime(lastMsg.createdAt),
                    color = bubbleColors.timestampColor,
                    fontSize = 12.sp,
                )
            }
            if (conversation.isPinned) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = LocalStrings.current.pinned,
                    color = bubbleColors.inputSendIconTint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LastMessagePreview(
    lastMessage: LastMessageInfo?,
    unreadCount: Int,
    bubbleColors: com.github.woodsmarshes.chat.core.ui.theme.BubbleColorTokens,
) {
    if (lastMessage == null) {
        Text(
            text = LocalStrings.current.noMessages,
            color = bubbleColors.timestampColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val senderPrefix = if (lastMessage.senderName != null) "${lastMessage.senderName}: " else ""
    val contentPreview = when (lastMessage.renderType) {
        MessageRenderType.IMAGE -> "[图片]"
        MessageRenderType.VIDEO -> "[视频]"
        MessageRenderType.AUDIO -> "[语音]"
        MessageRenderType.FILE -> "[文件]"
        else -> lastMessage.contentTruncated(50)
    }
    val fullPreview = "$senderPrefix$contentPreview"

    Text(
        text = fullPreview,
        color = if (unreadCount > 0) bubbleColors.onSurfaceColor else bubbleColors.timestampColor,
        fontSize = 13.sp,
        fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun LastMessageInfo.contentTruncated(max: Int): String {
    return when (renderType) {
        MessageRenderType.TEXT -> {
            val text = (content as? com.github.woodsmarshes.chat.core.model.TextContent)?.text ?: ""
            text.take(max).replace("\n", " ")
        }
        else -> ""
    }
}
