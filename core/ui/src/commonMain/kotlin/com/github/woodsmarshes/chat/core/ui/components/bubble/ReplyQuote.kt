package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

/**
 * Compact quote of the message a bubble replies to, rendered above the bubble.
 * Excerpt rules mirror the composer's reply preview in ChatInputBar.
 */
@Composable
fun ReplyQuote(
    reply: MessageUiModel,
    modifier: Modifier = Modifier,
) {
    val bubbleColors = LocalBubbleColors.current
    val strings = LocalStrings.current
    val senderName = reply.sender?.displayName ?: reply.sender?.username ?: ""
    val excerpt = if (reply.revokedAt != null) {
        strings.messageRevoked
    } else {
        when (val content = reply.content) {
            is TextContent -> content.text
            is ImageContent -> strings.messagePreviewImage
            is VideoContent -> strings.messagePreviewVideo
            is AudioContent -> strings.messagePreviewAudio
            is FileContent -> strings.messagePreviewFile
            else -> ""
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bubbleColors.inputFieldBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = senderName,
            style = LocalTextStyle.current.copy(
                color = bubbleColors.senderNameColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = excerpt,
            style = LocalTextStyle.current.copy(
                color = bubbleColors.timestampColor,
                fontSize = 12.sp,
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
