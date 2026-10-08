package com.github.woodsmarshes.chat.core.ui.components.bubble

import androidx.compose.runtime.staticCompositionLocalOf
import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.ui.MessageState

/** Presentation only: reconnecting must not rewrite durable outbox state. */
internal val LocalMessageConnection = staticCompositionLocalOf<ConnectionState> { ConnectionState.Idle }

internal enum class DeliveryPresentation { Waiting, Sending, Failed, Sent, Hidden }

internal fun deliveryPresentation(status: MessageState, connection: ConnectionState, own: Boolean): DeliveryPresentation =
    when {
        !own -> DeliveryPresentation.Hidden
        status is MessageState.SendFailed -> DeliveryPresentation.Failed
        status is MessageState.Completed -> DeliveryPresentation.Sent
        connection is ConnectionState.Connected -> DeliveryPresentation.Sending
        else -> DeliveryPresentation.Waiting
    }
