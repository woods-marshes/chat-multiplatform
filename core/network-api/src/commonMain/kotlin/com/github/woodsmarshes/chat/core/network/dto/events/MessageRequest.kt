package com.github.woodsmarshes.chat.core.network.dto.events

import com.github.woodsmarshes.chat.core.model.MessageContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
sealed class MessageRequest : RealtimeEvent {

    @Serializable
    data class Send(
        @ProtoNumber(1) val senderId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val replyToMessageId: Uuid? = null,
        @ProtoNumber(4) val content: MessageContent,
        @ProtoNumber(5) val requestId: String,
    ) : MessageRequest()

    @Serializable
    data class Withdraw(
        @ProtoNumber(1) val senderId: Uuid,
        @ProtoNumber(2) val messageId: Uuid,
    ) : MessageRequest()

    @Serializable
    data class Read(
        @ProtoNumber(1) val senderId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val messageId: Uuid,
    ) : MessageRequest()

    @Serializable
    data class Typing(
        @ProtoNumber(1) val senderId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val isTyping: Boolean,
    ) : MessageRequest()
}