package com.github.woodsmarshes.chat.core.network.dto.events

import kotlinx.serialization.Serializable

@Serializable
sealed interface RealtimeEvent

/**
 * Fallback event representing an unrecognised polymorphic event received from
 * a newer server or peer. Allows older clients to advance their sync cursor
 * without crashing or wedge-locking the journal.
 */
@Serializable
data object UnknownRealtimeEvent : RealtimeEvent