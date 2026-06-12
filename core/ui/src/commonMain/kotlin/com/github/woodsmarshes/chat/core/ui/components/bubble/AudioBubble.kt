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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.KmpMediaPlaybackState
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleShapes
import com.github.woodsmarshes.chat.core.ui.utils.formatDuration

@Composable
fun AudioBubble(
    content: AudioContent?,
    isOwnMessage: Boolean,
    state: KmpMediaPlaybackState = KmpMediaPlaybackState(),
    onPlayPauseToggle: (() -> Unit)? = null,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    val bubbleColors = LocalBubbleColors.current
    val bubbleShapes = LocalBubbleShapes.current

    val bgColor = if (isOwnMessage) bubbleColors.ownBackground else bubbleColors.otherBackground
    val contentColor = if (isOwnMessage) bubbleColors.ownContent else bubbleColors.otherContent
    val shape = if (isOwnMessage) bubbleShapes.ownBubble else bubbleShapes.otherBubble
    val durationStr = formatDuration(content.duration)

    Column(
        modifier = modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .clip(shape)
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Play/Pause button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f))
                    .then(
                        if (onPlayPauseToggle != null) {
                            Modifier.clickable { onPlayPauseToggle() }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // File info + progress
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.fileName,
                    color = contentColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))

                if (content.waveform.isNotEmpty()) {
                    WaveformBar(
                        waveform = content.waveform,
                        progress = state.progressFraction,
                        activeColor = contentColor,
                        inactiveColor = contentColor.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { state.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = durationStr,
                    color = bubbleColors.timestampColor,
                    fontSize = 11.sp,
                )
            }
        }

        // Seek slider
        if (onSeek != null && state.durationMillis > 0) {
            Spacer(Modifier.height(4.dp))
            Slider(
                value = state.progressFraction,
                onValueChange = { /* only commit on finished */ },
                onValueChangeFinished = { onSeek(state.progressFraction) },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = contentColor,
                    activeTrackColor = contentColor,
                    inactiveTrackColor = contentColor.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

@Composable
private fun WaveformBar(
    waveform: List<Int>,
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val maxValue = waveform.maxOrNull()?.toFloat() ?: 1f
        val playedIndex = (progress * waveform.size).toInt().coerceIn(0, waveform.size)

        waveform.forEachIndexed { index, value ->
            val heightFraction = (value.toFloat() / maxValue).coerceIn(0.05f, 1f)
            val barColor = if (index <= playedIndex) activeColor else inactiveColor

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp * heightFraction)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(barColor),
            )
        }
    }
}
