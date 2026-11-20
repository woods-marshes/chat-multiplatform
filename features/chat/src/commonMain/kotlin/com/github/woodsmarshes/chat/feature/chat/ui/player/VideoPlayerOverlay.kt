package com.github.woodsmarshes.chat.feature.chat.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.ui.components.avatar.LocalServerBaseUrl
import com.github.woodsmarshes.chat.core.ui.components.avatar.resolveMediaUrl
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

/**
 * Full-screen video player opened from a [com.github.woodsmarshes.chat.core.model.VideoContent]
 * bubble. The player state is scoped to this overlay: leaving composition stops
 * playback and releases the native backend.
 */
@Composable
fun VideoPlayerOverlay(
    content: VideoContent,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val playerState = rememberVideoPlayerState()
    val resolvedUrl = resolveMediaUrl(content.url, LocalServerBaseUrl.current)

    LaunchedEffect(resolvedUrl) {
        resolvedUrl?.let { playerState.openUri(it, InitialPlayerState.PLAY) }
    }

    fun dismiss() {
        playerState.pause()
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose { playerState.pause() }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = ::dismiss,
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        VideoPlayerSurface(
            playerState = playerState,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            IconButton(onClick = ::dismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backCd,
                    tint = Color.White,
                )
            }

            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        if (playerState.isPlaying) playerState.pause() else playerState.play()
                    },
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription =
                            if (playerState.isPlaying) strings.pauseCd else strings.playCd,
                        tint = Color.White,
                    )
                }

                Slider(
                    value = playerState.sliderPos,
                    onValueChange = { playerState.seekStart(it) },
                    onValueChangeFinished = { playerState.seekFinished() },
                    valueRange = 0f..1000f,
                    modifier = Modifier.weight(1f),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                )

                Text(
                    text = "${playerState.positionText} / ${playerState.durationText}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
