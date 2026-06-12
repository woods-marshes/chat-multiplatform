package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}

@Serializable
data class Message(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val conversationId: Uuid,
    @ProtoNumber(3) val sender: SimpleUser? = null,
    @ProtoNumber(4) val category: MessageCategory,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val revokedAt: Instant? = null,
    @ProtoNumber(7) val replyTo: Message? = null,
    @ProtoNumber(8) val content: MessageContent,
    @ProtoNumber(9) val senderContext: MessageSenderContext? = null
)

@Serializable
data class SimpleUser(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val username: String,
    @ProtoNumber(3) val displayName: String?,
    @ProtoNumber(4) val avatarUrl: String?,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val updatedAt: Instant,
    @ProtoNumber(7) val deletedAt: Instant?,
    @ProtoNumber(8) val role: UserRole,
)

@Serializable
data class MessageSenderContext(
    @ProtoNumber(1) val conversationRole: ConversationRole,
    @ProtoNumber(2) val joinedAt: Instant,
    @ProtoNumber(3) val participantSettings: ParticipantSettings?,
    @ProtoNumber(4) val lastReadMessageId: Uuid? = null,
    @ProtoNumber(5) val mutedUntil: Instant? = null,
)


@Serializable
sealed interface MessageContent

@Serializable
sealed interface System : MessageContent

@Serializable
sealed interface Normal : MessageContent

@Serializable
@SerialName("TEXT")
data class TextContent(
    @ProtoNumber(1) val text: String,
    @ProtoNumber(2) val entities: List<FormattingEntity> = emptyList()
) : Normal

@Serializable
sealed interface MediaContent : Normal {
    val url: String
    val fileName: String
    val size: Long
    val mimeType: String?
}

@Serializable
@SerialName("IMAGE")
data class ImageContent(
    @ProtoNumber(1) override val url: String,
    @ProtoNumber(2) override val fileName: String,
    @ProtoNumber(3) val width: Int,
    @ProtoNumber(4) val height: Int,
    @ProtoNumber(5) override val size: Long,
    @ProtoNumber(6) val blurHash: String? = null,
    @ProtoNumber(7) val thumbnailUrl: String? = null,
    @ProtoNumber(8) override val mimeType: String = "image/jpeg",
) : MediaContent
@Serializable
@SerialName("VIDEO")
data class VideoContent(
    @ProtoNumber(1) override val url: String,
    @ProtoNumber(2) override val fileName: String,
    @ProtoNumber(3) val coverUrl: String? = null,
    @ProtoNumber(4) val width: Int,
    @ProtoNumber(5) val height: Int,
    @ProtoNumber(6) val duration: Long,
    @ProtoNumber(7) override val size: Long,
    @ProtoNumber(8) override val mimeType: String = "video/mp4"
) : MediaContent

@Serializable
@SerialName("AUDIO")
data class AudioContent(
    @ProtoNumber(1) override val url: String,
    @ProtoNumber(2) override val fileName: String,
    @ProtoNumber(3) val duration: Long,
    @ProtoNumber(4) val waveform: List<Int> = emptyList(),
    @ProtoNumber(5) override val size: Long,
    @ProtoNumber(6) override val mimeType: String = "audio/aac"
) : MediaContent

@Serializable
@SerialName("FILE")
data class FileContent(
    @ProtoNumber(1) override val url: String,
    @ProtoNumber(2) override val fileName: String,
    @ProtoNumber(3) override val mimeType: String? = null,
    @ProtoNumber(4) override val size: Long
) : MediaContent

@Serializable
@SerialName("JOIN_GROUP")
data class JoinGroupContent(
    @ProtoNumber(1) val userId: Uuid,
    @ProtoNumber(2) val userName: String,
    @ProtoNumber(3) val inviterId: Uuid? = null,
    @ProtoNumber(4) val inviterName: String? = null,
) : System

enum class MessageCategory {
    NORMAL,
    SYSTEM
}

enum class MessageRenderType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    OTHER,
}

enum class FileType {
    IMAGE,
    AUDIO,
    VIDEO,
    FILE,
    AVATAR
}

enum class FormattingType {
    MENTION, // @人
    URL,     // 链接
    BOLD,    // 加粗
    ITALIC,  // 斜体
    CODE     // 代码块
}
