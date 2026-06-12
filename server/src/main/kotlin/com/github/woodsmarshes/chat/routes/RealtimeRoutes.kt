package com.github.woodsmarshes.chat.routes

import com.github.michaelbull.result.mapBoth
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.network.dto.events.SocketErrorResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageRequest
import com.github.woodsmarshes.chat.service.MessageService
import com.github.woodsmarshes.chat.service.RealtimeService
import com.github.woodsmarshes.chat.utils.extractUserIdFromWebSocket
import com.github.woodsmarshes.chat.utils.mapToStatus
import io.ktor.serialization.WebsocketConverterNotFoundException
import io.ktor.serialization.WebsocketDeserializeException
import io.ktor.serialization.deserialize
import io.ktor.serialization.suitableCharset
import io.ktor.server.routing.Route
import io.ktor.server.websocket.converter
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.logging.Logger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.isActive
import org.koin.ktor.ext.inject

fun Route.realtimeRoutes() {
    val realtimeService by inject<RealtimeService>()
    val messageService by inject<MessageService>()
    val log by inject<Logger>()
    webSocket("/ws") {
        val userId = call.extractUserIdFromWebSocket()
        if (userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "UserId missing"))
            return@webSocket
        }

        realtimeService.initializeWebSocketConnection(
            userId = userId,
            session = this,
        )

        try {
            while (isActive) {
                try {
                    when (val result = receiveDeserialized<MessageRequest>()) {
                        is MessageRequest.Send -> {
                            messageService.sendMessage(
                                userId = userId,
                                conversationId = result.conversationId,
                                content = result.content,
                                replyToMessageId = result.replyToMessageId,
                                requestId = result.requestId
                            ).mapBoth(
                                success = {
                                    // ACK ?
                                },
                                failure = { error ->
                                    sendSerialized(
                                        SocketErrorResponse(
                                            requestId = result.requestId,
                                            code = error.mapToStatus().value,
                                            message = "Failed to send message"
                                        )
                                    )
                                }
                            )
                        }

                        is MessageRequest.Withdraw -> {
                            messageService.withdrawMessage(
                                userId = userId,
                                messageId = result.messageId
                            ).mapBoth({  }) { error ->
                                sendSerialized(
                                    SocketErrorResponse(
                                        requestId = null,
                                        code = error.mapToStatus().value,
                                        message = "Withdraw failed"
                                    )
                                )
                            }
                        }

                        is MessageRequest.Read -> {
                            messageService.markAsRead(
                                userId = userId,
                                conversationId = result.conversationId,
                                messageId = result.messageId
                            )
                        }

                        is MessageRequest.Typing -> {
                            messageService.onUserTyping(
                                senderId = userId,
                                conversationId = result.conversationId,
                                isTyping = result.isTyping
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("Exception while receiving message from user $userId", e)
                    break
                }
            }
        } catch (e: Exception) {
            realtimeService.handleWebSocketException(e, userId, this)
        } finally {
            realtimeService.cleanupWebSocketConnection(this)
        }
    }
}