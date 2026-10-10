package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.KmpMediaPlaybackState
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
/**
 * Dispatches the correct bubble composable based on [MessageRenderType].
 * Used by [MessageListItems] as the reusable content renderer.
 */
@Composable
fun MessageBubbleContent(
    renderType: MessageRenderType,
    content: MessageContent,
    isOwnMessage: Boolean,
    sendStatus: MessageState,
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
    when (renderType) {
        MessageRenderType.TEXT -> TextBubble(
            content = content as? TextContent ?: TextContent(""),
            isOwnMessage = isOwnMessage,
            sendStatus = sendStatus,
            modifier = modifier,
            formatter = formatter,
            onRetry = onRetry,
        )
        MessageRenderType.IMAGE -> ImageBubble(
            content = content as? ImageContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            onImageClick = onImageClick,
        )
        MessageRenderType.VIDEO -> VideoBubble(
            content = content as? VideoContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            isPlaying = videoIsPlaying,
            onPlayClick = onVideoPlayClick,
        )
        MessageRenderType.AUDIO -> {
            val audioContent = content as? AudioContent
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
            content = content as? FileContent,
            isOwnMessage = isOwnMessage,
            modifier = modifier,
            onFileClick = onFileClick,
        )
        MessageRenderType.OTHER -> TextBubble(
            content = TextContent(LocalStrings.current.unsupportedMessage),
            isOwnMessage = isOwnMessage,
            sendStatus = sendStatus,
            modifier = modifier,
            onRetry = null,
        )
    }
}
