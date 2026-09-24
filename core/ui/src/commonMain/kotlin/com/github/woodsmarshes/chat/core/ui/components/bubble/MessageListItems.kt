package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.KmpMediaPlaybackState
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import kotlin.uuid.Uuid

/**
 * Renders a message list into a [LazyListScope] with per-item keys and
 * content-type optimisation driven by [MessageUiModel.renderType] × own/other.
 *
 * Usage in a LazyColumn:
 * ```
 * LazyColumn(state = listState) {
 *     messageItems(
 *         itemCount = lazyMessages.itemCount,
 *         itemProvider = { lazyMessages[it] },
 *         formatter = rememberFormatter(),
 *         onReply = { viewModel.setReplyTo(it) },
 *     )
 * }
 * ```
 */
fun LazyListScope.messageItems(
    itemCount: Int,
    itemProvider: (Int) -> MessageUiModel?,
    formatter: MessageFormatter,
    ownUserId: Uuid? = null,
    audioState: KmpMediaPlaybackState = KmpMediaPlaybackState(),
    videoIsPlaying: Boolean = false,
    onImageClick: ((ImageContent) -> Unit)? = null,
    onVideoPlayClick: ((VideoContent) -> Unit)? = null,
    onAudioPlayPauseClick: ((AudioContent) -> Unit)? = null,
    onFileClick: ((FileContent) -> Unit)? = null,
    onRetry: ((MessageUiModel) -> Unit)? = null,
    onReply: ((MessageUiModel) -> Unit)? = null,
    onAvatarClick: ((MessageUiModel) -> Unit)? = null,
    selectionActive: Boolean = false,
    selectedIds: Set<Uuid> = emptySet(),
    onToggleSelection: ((MessageUiModel) -> Unit)? = null,
    menuContent: (@Composable (message: MessageUiModel, dismiss: () -> Unit) -> Unit)? = null,
) {
    items(
        count = itemCount,
        key = { index -> itemProvider(index)?.id?.toString() ?: index },
        contentType = { index ->
            val msg = itemProvider(index)
            val prefix = if (ownUserId != null && msg?.sender?.id == ownUserId) "own" else "other"
            "${prefix}_${msg?.renderType?.name ?: "unknown"}"
        },
    ) { index ->
        val message = itemProvider(index) ?: return@items
        val isOwn = ownUserId != null && message.sender?.id == ownUserId
        val selected = selectionActive && message.id in selectedIds

        val content: @Composable () -> Unit = {
            MessageBubbleContent(
                renderType = message.renderType,
                content = message.content,
                isOwnMessage = isOwn,
                sendStatus = message.sendStatus,
                formatter = formatter,
                audioState = audioState,
                videoIsPlaying = videoIsPlaying,
                onRetry = if (message.sendStatus is MessageState.SendFailed && onRetry != null) {
                    { onRetry(message) }
                } else null,
                onImageClick = onImageClick,
                onVideoPlayClick = onVideoPlayClick,
                onAudioPlayPauseClick = onAudioPlayPauseClick,
                onFileClick = onFileClick,
            )
        }

        if (isOwn) {
            OwnMessageContainer(
                message = message,
                onReply = onReply?.let { { onReply(message) } },
                selectionActive = selectionActive,
                selected = selected,
                onToggleSelection = onToggleSelection?.let { { it(message) } },
                menuContent = menuContent?.let { content ->
                    { dismiss -> content(message, dismiss) }
                },
                content = content,
            )
        } else {
            OtherMessageContainer(
                message = message,
                showAvatar = true,
                showSenderName = true,
                onReply = onReply?.let { { onReply(message) } },
                onAvatarClick = onAvatarClick?.let { click -> { click(message) } },
                selectionActive = selectionActive,
                selected = selected,
                onToggleSelection = onToggleSelection?.let { { it(message) } },
                menuContent = menuContent?.let { content ->
                    { dismiss -> content(message, dismiss) }
                },
                content = content,
            )
        }
    }
}
