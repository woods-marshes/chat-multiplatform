package com.github.woodsmarshes.chat.feature.chat.ui.bubble

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.presentation.MessageState
import com.github.woodsmarshes.chat.core.model.presentation.MessageUiModel
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings

/**
 * Dispatches the correct bubble composable based on [MessageRenderType].
 * Used by [MessageListItems] as the reusable content renderer.
 */
@Composable
fun MessageBubbleContent(
    message: MessageUiModel,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    formatter: MessageFormatter = rememberFormatter(),
    onRetry: (() -> Unit)?,
    onImageClick: ((ImageContent) -> Unit)?,
    onVideoPlayClick: ((VideoContent) -> Unit)?,
    onFileClick: ((FileContent) -> Unit)?,
) {
    val strings = LocalStrings.current
    if (message.revokedAt != null) {
        BasicText(
            text = strings.messageRevoked,
            style =
                LocalTextStyle.current.copy(
                    fontStyle = FontStyle.Italic,
                    textDecoration = TextDecoration.LineThrough,
                ),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier) {
        message.replyTo?.let { reply ->
            ReplyQuote(
                reply = reply,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        when (message.renderType) {
            MessageRenderType.TEXT -> {
                TextBubble(
                    content = message.content as? TextContent ?: TextContent(""),
                    isOwnMessage = isOwnMessage,
                    sendStatus = message.sendStatus,
                    modifier = Modifier,
                    formatter = formatter,
                    onRetry = onRetry,
                )
            }

            MessageRenderType.IMAGE -> {
                ImageBubble(
                    content = message.content as? ImageContent,
                    isOwnMessage = isOwnMessage,
                    modifier = Modifier,
                    onImageClick = onImageClick,
                )
            }

            MessageRenderType.VIDEO -> {
                VideoBubble(
                    content = message.content as? VideoContent,
                    isOwnMessage = isOwnMessage,
                    modifier = Modifier,
                    onPlayClick = onVideoPlayClick,
                )
            }

            MessageRenderType.AUDIO -> {
                AudioBubblePlayer(
                    content = message.content as? AudioContent,
                    isOwnMessage = isOwnMessage,
                    modifier = Modifier,
                )
            }

            MessageRenderType.FILE -> {
                FileBubble(
                    content = message.content as? FileContent,
                    isOwnMessage = isOwnMessage,
                    modifier = Modifier,
                    onFileClick = onFileClick,
                )
            }

            MessageRenderType.OTHER -> {
                TextBubble(
                    content = TextContent(strings.unsupportedMessage),
                    isOwnMessage = isOwnMessage,
                    sendStatus = message.sendStatus,
                    modifier = Modifier,
                    onRetry = null,
                )
            }
        }
    }
}
