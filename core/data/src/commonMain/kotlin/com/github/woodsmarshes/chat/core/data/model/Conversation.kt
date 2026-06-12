package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.GroupInfo
import com.github.woodsmarshes.chat.core.network.dto.conversation.SimpleMessage
import com.github.woodsmarshes.chat.core.network.dto.conversation.UserInfo
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GetConversationListView
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity

fun Conversation.toEntity() = ConversationEntity(
    id = this.id,
    type = this.type,
    last_message_id = this.lastMessageId,
    metadata = this.metadata,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
    deleted_at = this.deletedAt,
)

fun ConversationEntity.toConversation() = Conversation(
    id = this.id,
    type = this.type,
    metadata = this.metadata,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    deletedAt = this.deleted_at,
    lastMessageId = this.last_message_id,
)

fun ConversationResponse.toConversation() = Conversation(
    id = this.conversationId,
    type = this.type,
    metadata = this.metadata,
    createdAt = when (val info = conversationInfo) {
        is GroupInfo -> info.createdAt
        is UserInfo -> info.createdAt
    },
    updatedAt = when (val info = conversationInfo) {
        is GroupInfo -> info.updatedAt
        is UserInfo -> info.updatedAt
    },
    deletedAt = when (val info = conversationInfo) {
        is GroupInfo -> info.deletedAt
        is UserInfo -> info.deletedAt
    },
    lastMessageId = this.lastMessage?.id,
)

fun ConversationResponse.toParticipantEntity() = ParticipantEntity(
    conversation_id = this.conversationId,
    user_id = this.participant.userId,
    role = this.participant.role,
    last_read_message_id = this.participant.lastReadMessageId,
    joined_at = this.participant.joinedAt,
    muted_until = this.participant.mutedUntil,
    settings = this.participant.settings,
)

fun ConversationResponse.toMessageEntity(): MessageEntity? {
    val message = this.lastMessage
    return if (message != null) {
        MessageEntity(
            id = message.id,
            conversation_id = this.conversationId,
            user_id = message.sender?.id ?: error("Sender is required for MessageEntity"),
            category = message.category,
            render_type = message.getRenderType(),
            content = message.content,
            reply_to_message_id = null,
            created_at = message.createdAt,
            revoked_at = message.revokedAt,
            local_send_status = null
        )
    } else null
}

fun ConversationResponse.toUserEntity(): UserEntity? {
    val userInfo = this.conversationInfo as? UserInfo ?: return null
    return UserEntity(
        id = userInfo.id,
        username = userInfo.username,
        email = userInfo.email,
        display_name = userInfo.displayName,
        avatar = userInfo.avatarUrl,
        bio = userInfo.bio,
        created_at = userInfo.createdAt,
        updated_at = userInfo.updatedAt,
        deleted_at = userInfo.deletedAt,
        role = userInfo.role
    )
}

// 将 ConversationResponse 转换为 GroupProfileEntity（仅当 conversationInfo 是 GroupInfo 时）
fun ConversationResponse.toGroupProfileEntity(): GroupProfileEntity? {
    val groupInfo = this.conversationInfo as? GroupInfo ?: return null
    return GroupProfileEntity(
        conversation_id = this.conversationId,
        name = groupInfo.name,
        handle = groupInfo.handle,
        description = groupInfo.description,
        avatar_url = groupInfo.avatarUrl,
        owner_id = groupInfo.ownerId,
        settings = groupInfo.settings,
        created_at = groupInfo.createdAt,
        updated_at = groupInfo.updatedAt
    )
}

private fun SimpleMessage.getRenderType(): MessageRenderType {
    return when (this.content) {
        is TextContent -> MessageRenderType.TEXT
        is ImageContent -> MessageRenderType.IMAGE
        is VideoContent -> MessageRenderType.VIDEO
        is AudioContent -> MessageRenderType.AUDIO
        is FileContent -> MessageRenderType.FILE
        else -> MessageRenderType.OTHER
    }
}