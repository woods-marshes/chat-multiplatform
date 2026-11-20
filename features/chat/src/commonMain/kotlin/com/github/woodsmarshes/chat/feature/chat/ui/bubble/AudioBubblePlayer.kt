package com.github.woodsmarshes.chat.feature.chat.ui.bubble

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.KmpMediaPlaybackState
import com.github.woodsmarshes.chat.core.ui.components.avatar.LocalServerBaseUrl
import com.github.woodsmarshes.chat.core.ui.components.avatar.resolveMediaUrl
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayer
import io.github.kdroidfilter.composemediaplayer.audio.AudioPlayerState
import io.github.kdroidfilter.composemediaplayer.audio.rememberAudioPlayerLiveState

/**
 * Tracks the single audio playback allowed at a time across all chat bubbles.
 * Claiming pauses whichever bubble was playing before.
 */
internal object ChatAudioCoordinator {
    var activePlayer: AudioPlayer? = null
}

/**
 * Self-contained audio bubble: owns a ComposeMediaPlayer audio player and maps
 * its live state onto the dumb [AudioBubble] UI.
 */
@Composable
fun AudioBubblePlayer(
    content: AudioContent?,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
) {
    if (content == null) return

    val live = rememberAudioPlayerLiveState()
    val resolvedUrl = resolveMediaUrl(content.url, LocalServerBaseUrl.current)

    DisposableEffect(live.player) {
        onDispose {
            if (ChatAudioCoordinator.activePlayer === live.player) {
                ChatAudioCoordinator.activePlayer = null
            }
        }
    }

    val isActive = live.state == AudioPlayerState.PLAYING || live.state == AudioPlayerState.BUFFERING
    val state =
        KmpMediaPlaybackState(
            isPlaying = isActive,
            isLoading = live.state == AudioPlayerState.BUFFERING,
            progressFraction =
                if (live.duration > 0) (live.position.toFloat() / live.duration).coerceIn(0f, 1f) else 0f,
            durationMillis = live.duration,
        )

    AudioBubble(
        content = content,
        isOwnMessage = isOwnMessage,
        modifier = modifier,
        state = state,
        onPlayPauseToggle = {
            when (live.state) {
                AudioPlayerState.PLAYING, AudioPlayerState.BUFFERING -> {
                    live.player.pause()
                }

                AudioPlayerState.PAUSED -> {
                    live.player.play()
                }

                AudioPlayerState.IDLE -> {
                    resolvedUrl?.let { url ->
                        ChatAudioCoordinator.activePlayer
                            ?.takeIf { it !== live.player }
                            ?.pause()
                        ChatAudioCoordinator.activePlayer = live.player
                        live.player.play(url)
                    }
                }
            }
        },
        onSeek = { fraction ->
            if (live.duration > 0) {
                live.player.seekTo((fraction * live.duration).toLong())
            }
        },
    )
}
