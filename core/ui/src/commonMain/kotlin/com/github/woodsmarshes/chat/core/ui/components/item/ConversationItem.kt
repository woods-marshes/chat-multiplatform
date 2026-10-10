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
import androidx.compose.material3.Badge as M3Badge
import androidx.compose.material3.MaterialTheme
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
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.core.ui.utils.formatRelativeTime
import top.yukonga.miuix.kmp.basic.Badge as MiuixBadge
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConversationItem(
    conversation: ConversationUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val bubbleColors = LocalBubbleColors.current
    val isMiuix = isMiuixTheme()
    val convName = conversation.name ?: LocalStrings.current.unnamed
    val lastMsg = conversation.lastMessage

    val selectedBg = if (isMiuix) {
        MiuixTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val selectedTextColor = if (isMiuix) {
        MiuixTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(if (isMiuix) 16.dp else 12.dp))
            .background(
                if (selected) selectedBg
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像 + 未读角标
        Box(modifier = Modifier.size(52.dp)) {
            if (conversation.type == ConversationType.GROUP) {
                ConversationAvatar(
                    participants = conversation.memberAvatars,
                    name = conversation.name,
                    avatarUrl = conversation.avatarUrl,
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp),
                ) {
                    if (isMiuix) {
                        MiuixBadge(
                            containerColor = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError,
                        ) {
                            MiuixText(text = badgeText)
                        }
                    } else {
                        M3Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 中间信息区
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = convName,
                color = if (selected) selectedTextColor else bubbleColors.onSurfaceColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            LastMessagePreview(
                lastMessage = lastMsg,
                fallbackSubtitle = conversation.description?.takeIf { it.isNotBlank() }
                    ?: conversation.handle?.takeIf { it.isNotBlank() }?.let { "@$it" },
                unreadCount = conversation.unreadCount,
                bubbleColors = bubbleColors,
            )
        }

        // 右侧时间 + 置顶标记
        Column(horizontalAlignment = Alignment.End) {
            if (lastMsg != null) {
                Text(
                    text = formatRelativeTime(lastMsg.createdAt, LocalStrings.current),
                    color = bubbleColors.timestampColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (conversation.isPinned) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = LocalStrings.current.pinned,
                    color = bubbleColors.inputSendIconTint,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LastMessagePreview(
    lastMessage: LastMessageInfo?,
    fallbackSubtitle: String? = null,
    unreadCount: Int,
    bubbleColors: com.github.woodsmarshes.chat.core.ui.theme.BubbleColorTokens,
) {
    if (lastMessage == null) {
        Text(
            text = fallbackSubtitle ?: LocalStrings.current.noMessages,
            color = bubbleColors.timestampColor,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val senderPrefix = if (lastMessage.senderName != null) "${lastMessage.senderName}: " else ""
    val strings = LocalStrings.current
    val contentPreview = when (lastMessage.renderType) {
        MessageRenderType.IMAGE -> strings.messagePreviewImage
        MessageRenderType.VIDEO -> strings.messagePreviewVideo
        MessageRenderType.AUDIO -> strings.messagePreviewAudio
        MessageRenderType.FILE -> strings.messagePreviewFile
        else -> lastMessage.contentTruncated(50)
    }
    val fullPreview = "$senderPrefix$contentPreview"

    Text(
        text = fullPreview,
        color = if (unreadCount > 0) bubbleColors.onSurfaceColor else bubbleColors.timestampColor,
        style = MaterialTheme.typography.bodyMedium,
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
