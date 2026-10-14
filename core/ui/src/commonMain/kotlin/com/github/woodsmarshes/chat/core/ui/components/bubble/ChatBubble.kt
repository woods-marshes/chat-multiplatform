package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.KmpMediaPlaybackState
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
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
    audioState: KmpMediaPlaybackState = KmpMediaPlaybackState(),
    videoIsPlaying: Boolean = false,
    onRetry: (() -> Unit)?,
    onImageClick: ((ImageContent) -> Unit)?,
    onVideoPlayClick: ((VideoContent) -> Unit)?,
    onAudioPlayPauseClick: ((AudioContent) -> Unit)?,
    onAudioSeek: ((AudioContent, Float) -> Unit)? = null,
    onFileClick: ((FileContent) -> Unit)?,
) {
    val strings = LocalStrings.current
    if (message.revokedAt != null) {
        BasicText(
            text = strings.messageRevoked,
            style = LocalTextStyle.current.copy(
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.LineThrough,
            ),
            modifier = modifier,
        )
        return
    }

    when (message.renderType) {
        MessageRenderType.TEXT -> TextBubble(
            content = message.content as? TextContent ?: TextContent(""),
            isOwnMessage = isOwnMessage,
            sendStatus = message.sendStatus,
            modifier = modifier,
            formatter = formatter,
            onRetry = onRetry,
        )
        MessageRenderType.IMAGE -> ImageBubble(
            content = message.content as? ImageContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            onImageClick = onImageClick,
        )
        MessageRenderType.VIDEO -> VideoBubble(
            content = message.content as? VideoContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            isPlaying = videoIsPlaying,
            onPlayClick = onVideoPlayClick,
        )
        MessageRenderType.AUDIO -> {
            val audioContent = message.content as? AudioContent
            AudioBubble(
                content = audioContent,
                isOwnMessage = isOwnMessage,
                modifier = modifier,
                state = audioState,
                onPlayPauseToggle = if (audioContent == null) {
                    null
                } else {
                    onAudioPlayPauseClick?.let { click -> { click(audioContent) } }
                },
                onSeek = if (audioContent == null) null else onAudioSeek?.let { seek -> { frac -> seek(audioContent, frac) } },
            )
        }
        MessageRenderType.FILE -> FileBubble(
            content = message.content as? FileContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            onFileClick = onFileClick,
        )
        MessageRenderType.OTHER -> TextBubble(
            content = TextContent(strings.unsupportedMessage),
            isOwnMessage = isOwnMessage,
            sendStatus = message.sendStatus,
            modifier = modifier,
            onRetry = null,
        )
    }
}
