package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ConversationParticipant(
    @ProtoNumber(1) val conversationId: Uuid,
    @ProtoNumber(2) val userId: Uuid,
    @ProtoNumber(3) val role: ConversationRole,
    @ProtoNumber(4) val lastReadMessageId: Uuid?,
    @ProtoNumber(5) val joinedAt: Instant,
    @ProtoNumber(6) val mutedUntil: Instant? = null,
    @ProtoNumber(7) val settings: ParticipantSettings,
)

@Serializable
data class ParticipantSettings(
    @ProtoNumber(1) val nickname: String? = null,
    @ProtoNumber(2) val alias: String? = null,
    @ProtoNumber(3) val backgroundImage: String? = null,
    @ProtoNumber(4) val enableNotification: Boolean = true,
    @ProtoNumber(5) val bubbleColor: String? = null,
    @ProtoNumber(6) val pinnedAt: Instant? = null,
)

enum class ConversationRole {
    OWNER,
    ADMIN,
    MEMBER,

    PARTICIPANT,

    UNKNOWN,
}