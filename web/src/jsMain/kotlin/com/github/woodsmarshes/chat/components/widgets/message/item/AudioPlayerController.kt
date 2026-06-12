package com.github.woodsmarshes.chat.components.widgets.message.item

import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event
import kotlin.math.roundToLong

data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val progressFraction: Float = 0f,
    val durationMillis: Long = 0L
)

// 控制器类
class AudioPlayerController(private val onStateChange: (AudioPlaybackState) -> Unit) {
    private var audioElement: HTMLAudioElement? = null
    private var currentState = AudioPlaybackState()

    // --- 事件监听器 ---
    private val onPlay: (Event) -> Unit = { updateState { copy(isPlaying = true, isLoading = false) } }
    private val onPause: (Event) -> Unit = { updateState { copy(isPlaying = false) } }
    private val onEnded: (Event) -> Unit = { updateState { copy(isPlaying = false, progressFraction = 1f) } }
    private val onWaiting: (Event) -> Unit = { updateState { copy(isLoading = true) } }
    private val onPlaying: (Event) -> Unit = { updateState { copy(isLoading = false) } }
    private val onLoadedData: (Event) -> Unit = {
        updateState { copy(durationMillis = (audioElement?.duration?.times(1000))?.roundToLong() ?: 0L, isLoading = false) }
    }
    private val onTimeUpdate: (Event) -> Unit = {
        audioElement?.let { audio ->
            if (audio.duration > 0) {
                updateState { copy(progressFraction = (audio.currentTime / audio.duration).toFloat()) }
            }
        }
    }

    // --- 公共 API ---
    fun attach(element: HTMLAudioElement) {
        detach() // 先清理旧的
        audioElement = element
        element.addEventListener("play", onPlay)
        element.addEventListener("pause", onPause)
        element.addEventListener("ended", onEnded)
        element.addEventListener("waiting", onWaiting)
        element.addEventListener("playing", onPlaying)
        element.addEventListener("loadeddata", onLoadedData)
        element.addEventListener("timeupdate", onTimeUpdate)
    }

    fun detach() {
        audioElement?.let { audio ->
            audio.pause()
            audio.removeEventListener("play", onPlay)
        }
        audioElement = null
    }

    fun play() { audioElement?.play() }
    fun pause() { audioElement?.pause() }
    fun seekTo(progress: Float) {
        audioElement?.let { it.currentTime = it.duration * progress }
    }

    private fun updateState(builder: AudioPlaybackState.() -> AudioPlaybackState) {
        val newState = currentState.builder()
        if (newState != currentState) {
            currentState = newState
            onStateChange(newState)
        }
    }
}