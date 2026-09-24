package com.github.woodsmarshes.chat.core.network.dto.events

import com.github.woodsmarshes.chat.core.model.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
sealed class MessageEventResponse : RealtimeEvent {
    @Serializable
    data class Received(
        @ProtoNumber(1) val message: Message,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val senderId: Uuid,
        @ProtoNumber(4) val requestId: String,
    ) : MessageEventResponse()

    @Serializable
    data class Withdrawn(
        @ProtoNumber(1) val messageId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val senderId: Uuid,
        @ProtoNumber(4) val timestamp: Instant,
    ) : MessageEventResponse()

    @Serializable
    data class Read(
        @ProtoNumber(1) val messageId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val readerId: Uuid,
        @ProtoNumber(4) val timestamp: Instant,
    ) : MessageEventResponse()

    @Serializable
    data class UserTyping(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val userId: Uuid,
        @ProtoNumber(3) val isTyping: Boolean,
        @ProtoNumber(4) val timestamp: Instant,
    ) : MessageEventResponse()
}