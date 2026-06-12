package com.github.woodsmarshes.chat.core.model

/**
 * Platform-agnostic media playback state.
 *
 * Stability is declared via [stability_config.conf] —
 * no Compose runtime dependency needed in this module.
 */
data class KmpMediaPlaybackState(
    val isPlaying: Boolean = false,
    val progressFraction: Float = 0f,
    val isLoading: Boolean = false,
    val durationMillis: Long = 0L,
)
