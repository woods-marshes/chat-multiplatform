package com.github.woodsmarshes.chat.core.ui.components.bubble

import com.github.woodsmarshes.chat.core.model.ConnectionState
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import kotlin.test.Test
import kotlin.test.assertEquals

class DeliveryPresentationTest {
    @Test
    fun pendingMessagesFollowConnectionWithoutChangingStoredStatus() {
        val pending = MessageState.Sending
        for (connection in listOf(ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Disconnected("test"))) {
            assertEquals(DeliveryPresentation.Waiting, deliveryPresentation(pending, connection, true))
        }
        assertEquals(DeliveryPresentation.Sending, deliveryPresentation(pending, ConnectionState.Connected, true))
        assertEquals(DeliveryPresentation.Waiting, deliveryPresentation(pending, ConnectionState.Disconnected("lost"), true))
    }

    @Test
    fun failureAndAcknowledgementTakePriorityOverConnectivity() {
        for (connection in listOf(ConnectionState.Idle, ConnectionState.Connecting, ConnectionState.Connected)) {
            assertEquals(DeliveryPresentation.Failed, deliveryPresentation(MessageState.SendFailed("private diagnostic"), connection, true))
            assertEquals(DeliveryPresentation.Sent, deliveryPresentation(MessageState.Completed, connection, true))
        }
    }

    @Test
    fun incomingMessagesNeverDisplayLocalDeliveryStatus() {
        for (status in listOf(MessageState.Sending, MessageState.Completed, MessageState.SendFailed("failure"))) {
            assertEquals(DeliveryPresentation.Hidden, deliveryPresentation(status, ConnectionState.Connected, false))
        }
    }
}
