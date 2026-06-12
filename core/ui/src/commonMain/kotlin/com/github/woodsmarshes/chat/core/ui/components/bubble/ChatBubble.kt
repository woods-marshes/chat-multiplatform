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
    formatter: MessageFormatter = rememberFormatter(),
    audioState: KmpMediaPlaybackState = KmpMediaPlaybackState(),
    videoIsPlaying: Boolean = false,
    onRetry: (() -> Unit)?,
    onImageClick: ((ImageContent) -> Unit)?,
    onVideoPlayClick: ((VideoContent) -> Unit)?,
    onAudioPlayPauseClick: ((AudioContent) -> Unit)?,
    onFileClick: ((FileContent) -> Unit)?,
) {
    when (renderType) {
        MessageRenderType.TEXT -> TextBubble(
            content = content as? TextContent ?: TextContent(""),
            isOwnMessage = isOwnMessage,
            sendStatus = sendStatus,
            formatter = formatter,
            onRetry = onRetry,
        )
        MessageRenderType.IMAGE -> ImageBubble(
            content = content as? ImageContent,
            isOwnMessage = isOwnMessage,
            onImageClick = onImageClick,
        )
        MessageRenderType.VIDEO -> VideoBubble(
            content = content as? VideoContent,
            isOwnMessage = isOwnMessage,
            isPlaying = videoIsPlaying,
            onPlayClick = onVideoPlayClick,
        )
        MessageRenderType.AUDIO -> AudioBubble(
            content = content as? AudioContent,
            isOwnMessage = isOwnMessage,
            state = audioState,
            onPlayPauseToggle = if (onAudioPlayPauseClick != null)
                {{ onAudioPlayPauseClick(content as? AudioContent ?: return@AudioBubble) }}
            else null,
            onSeek = null, // wiring left to caller
        )
        MessageRenderType.FILE -> FileBubble(
            content = content as? FileContent,
            isOwnMessage = isOwnMessage,
            onFileClick = onFileClick,
        )
        MessageRenderType.OTHER -> TextBubble(
            content = TextContent("[Unsupported]"),
            isOwnMessage = isOwnMessage,
            sendStatus = sendStatus,
            onRetry = null,
        )
    }
}
