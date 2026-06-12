package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val type: ConversationType,
    @ProtoNumber(3) val metadata: ConversationMetadata?,
    @ProtoNumber(4) val createdAt: Instant,
    @ProtoNumber(5) val updatedAt: Instant,
    @ProtoNumber(6) val deletedAt: Instant?,
    @ProtoNumber(7) val lastMessageId: Uuid?,
)

enum class ConversationType {
    GROUP, PRIVATE
}

@Serializable
sealed interface ConversationMetadata

@Serializable
@SerialName("GROUP")
data class GroupMetadata(
    @ProtoNumber(1) val announcement: List<String> = emptyList(),
) : ConversationMetadata

@Serializable
@SerialName("PRIVATE")
data class PrivateMetadata(
    @ProtoNumber(1) val encryptionKey: String? = null,
) : ConversationMetadata