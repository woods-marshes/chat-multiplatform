package com.github.woodsmarshes.chat.core.network.dto.events

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
sealed class ConversationEventResponse : RealtimeEvent {
    @Serializable
    data class ConversationCreated(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val type: ConversationType,
        @ProtoNumber(3) val creatorId: Uuid,
        @ProtoNumber(4) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class ConversationDeleted(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val deleterId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class UserJoinedConversation(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val userId: List<Uuid>,
        @ProtoNumber(3) val inviterId: Uuid?,
        @ProtoNumber(4) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class UserLeftConversation(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val userId: Uuid,
        @ProtoNumber(3) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class GroupProfileUpdated(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val updaterId: Uuid,
        @ProtoNumber(3) val profile: UpdateConversationSettingsRequest,
        @ProtoNumber(4) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class PersonalSettingsUpdated(
        @ProtoNumber(1) val conversationId: Uuid,
        @ProtoNumber(2) val userId: Uuid,
        @ProtoNumber(3) val settings: ParticipantSettings,
        @ProtoNumber(4) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class GroupJoinRequest(
        @ProtoNumber(1) val requestId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val applicantId: Uuid,
        @ProtoNumber(4) val message: String?,
        @ProtoNumber(5) val timestamp: Instant
    ) : ConversationEventResponse()

    @Serializable
    data class GroupJoinRequestHandled(
        @ProtoNumber(1) val requestId: Uuid,
        @ProtoNumber(2) val conversationId: Uuid,
        @ProtoNumber(3) val applicantId: Uuid,
        @ProtoNumber(4) val handlerId: Uuid,
        @ProtoNumber(5) val approved: Boolean,
        @ProtoNumber(6) val reason: String?,
        @ProtoNumber(7) val timestamp: Instant
    ) : ConversationEventResponse()
}