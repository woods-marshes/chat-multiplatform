package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleShapes
import com.github.woodsmarshes.chat.core.ui.utils.formatDuration
import com.github.woodsmarshes.chat.core.ui.utils.formatFileSize

@Composable
fun VideoBubble(
    content: VideoContent?,
    isOwnMessage: Boolean,
    isPlaying: Boolean = false,
    onPlayClick: ((VideoContent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    val bubbleColors = LocalBubbleColors.current
    val bubbleShapes = LocalBubbleShapes.current
    val shape = bubbleShapes.mediaBubble

    Column(
        modifier = modifier
            .widthIn(max = 260.dp)
            .clip(shape)
            .background(bubbleColors.otherBackground)
            .then(
                // Consume touch events to prevent pass-through
                if (onPlayClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = null,
                    ) { onPlayClick(content) }
                } else Modifier
            ),
    ) {
        // Cover / playback area
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                // Platform video surface placeholder — replace with real player per platform
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp),
                    )
                }
            } else {
                if (content.coverUrl != null) {
                    SubcomposeAsyncImage(
                        model = content.coverUrl,
                        contentDescription = content.fileName,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentScale = ContentScale.Crop,
                        error = {
                            VideoPlaceholder(bubbleColors.iconTint, size = 40.dp)
                        },
                    )
                } else {
                    VideoPlaceholder(bubbleColors.iconTint, size = 48.dp)
                }

                // Centered play button overlay
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // Info row
        VideoInfoRow(content)
    }
}

@Composable
private fun VideoPlaceholder(iconTint: Color, size: androidx.compose.ui.unit.Dp) {
    val bubbleColors = LocalBubbleColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp)
            .background(bubbleColors.otherBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun VideoInfoRow(content: VideoContent) {
    val bubbleColors = LocalBubbleColors.current
    val durationStr = formatDuration(content.duration)
    val sizeStr = formatFileSize(content.size)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = content.fileName,
            color = bubbleColors.otherContent,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = durationStr,
            color = bubbleColors.timestampColor,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = sizeStr,
            color = bubbleColors.timestampColor,
            fontSize = 11.sp,
        )
    }
}
