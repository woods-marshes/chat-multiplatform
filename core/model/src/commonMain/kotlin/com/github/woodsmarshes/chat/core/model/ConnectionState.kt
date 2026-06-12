package com.github.woodsmarshes.chat.core.model

/**
 * Represents the lifecycle state of the real-time WebSocket connection.
 */
sealed interface ConnectionState {
    /** Connection has not been initiated. */
    data object Idle : ConnectionState

    /** Connection attempt is in progress. */
    data object Connecting : ConnectionState

    /** Connection is established and ready for sending/receiving. */
    data object Connected : ConnectionState

    /** Connection is lost or intentionally closed. */
    data class Disconnected(
        val reason: String,
        val throwable: Throwable? = null
    ) : ConnectionState
}
