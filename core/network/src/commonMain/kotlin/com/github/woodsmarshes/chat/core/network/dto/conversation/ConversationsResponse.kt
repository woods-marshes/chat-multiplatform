package com.github.woodsmarshes.chat.core.network.dto.conversation

import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageSenderContext
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ConversationResponse(
    @ProtoNumber(1) val conversationId: Uuid,
    @ProtoNumber(2) val type: ConversationType,
    @ProtoNumber(3) val lastMessage: SimpleMessage?,
    @ProtoNumber(4) val metadata: ConversationMetadata?,
    @ProtoNumber(5) val participant: ConversationParticipant,
    @ProtoNumber(6) val conversationInfo: ConversationInfo,
)

@Serializable
data class SimpleMessage(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val sender: SimpleUser? = null,
    @ProtoNumber(4) val category: MessageCategory,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val revokedAt: Instant? = null,
    @ProtoNumber(7) val content: MessageContent,
    @ProtoNumber(8) val senderContext: MessageSenderContext? = null
)

@Serializable
sealed interface ConversationInfo

@Serializable
@SerialName("Group")
data class GroupInfo(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val handle: String?,
    @ProtoNumber(3) val description: String?,
    @ProtoNumber(4) val avatarUrl: String?,
    @ProtoNumber(5) val ownerId: Uuid,
    @ProtoNumber(6) val settings: GroupSettings,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val deletedAt: Instant?
) : ConversationInfo

@Serializable
@SerialName("User")
data class UserInfo(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val username: String,
    @ProtoNumber(3) val email: String? = null,
    @ProtoNumber(4) val displayName: String?,
    @ProtoNumber(5) val avatarUrl: String?,
    @ProtoNumber(6) val bio: String?,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val role: UserRole,
    @ProtoNumber(10) val deletedAt: Instant?
) : ConversationInfo

fun User.toUserInfo() = UserInfo(
    id = id,
    username = username,
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    role = role,
)

fun GroupProfile.toGroupInfo(deletedAt: Instant? = null) = GroupInfo(
    name = name,
    handle = handle,
    description = description,
    avatarUrl = avatarUrl,
    ownerId = ownerId,
    settings = settings,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

