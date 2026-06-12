package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.ui.MessageState
import com.github.woodsmarshes.chat.core.model.ui.MessageUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import io.github.woodsmarshes.chat.db.GetLatestMessage
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByPage
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.UserEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity

fun GetLatestMessage.toMessageUiModel(replyMessageUiModel: MessageUiModel) = MessageUiModel(
    id = this.id,
    conversationId = this.conversation_id,
    sender = SenderUser(
        id = this.user_id,
        username = this.senderUsername ?: "null",
        displayName = this.senderDisplayName,
        avatarUrl = this.senderAvatar,
        role = this.senderRole,
    ),
    category = this.category,
    renderType = this.render_type,
    createdAt = this.created_at,
    revokedAt = this.revoked_at,
    replyTo = replyMessageUiModel, // 需要的话在上层补
    content = this.content,
    bubbleColor = this.senderParticipantSettings?.bubbleColor,
    sendStatus = when (this.local_send_status) {
        MessageStatus.SENDING -> MessageState.Sending
        MessageStatus.FAILED -> MessageState.SendFailed("failed")
        MessageStatus.SENT, null -> MessageState.Completed
    }
)


fun MessageStatus?.toUiState(): MessageState {
    return when (this) {
        MessageStatus.SENDING -> MessageState.Sending
        MessageStatus.SENT, null -> MessageState.Completed
        MessageStatus.FAILED -> MessageState.SendFailed(reason = "Send failed")
    }
}

fun GetMessagesWithAllRelationsByPage.toUiModel(): MessageUiModel {
    val senderUser = SenderUser(
        id = user_id,
        username = senderUsername ?: "null",
        displayName = senderParticipantSettings?.nickname ?: senderDisplayName,
        avatarUrl = senderAvatar,
        role = senderRole,
    )

    val replyUi: MessageUiModel? =
        if (reply_id != null && reply_conversation_id != null && reply_user_id != null &&
            reply_category != null && reply_render_type != null && reply_content != null &&
            reply_created_at != null
        ) {
            val replySender = if (reply_senderUsername != null) {
                SenderUser(
                    id = reply_user_id!!,
                    username = reply_senderUsername!!,
                    displayName = reply_senderDisplayName,
                    avatarUrl = reply_senderAvatar,
                    role = reply_senderRole,
                )
            } else {
                null
            }

            MessageUiModel(
                id = reply_id!!,
                conversationId = reply_conversation_id!!,
                sender = replySender,
                category = reply_category!!,
                renderType = reply_render_type!!,
                createdAt = reply_created_at!!,
                revokedAt = reply_revoked_at,
                replyTo = null, // 这里只做一层回复，不再递归
                content = reply_content!!,
                bubbleColor = reply_senderParticipantSettings?.bubbleColor,
                sendStatus = reply_local_send_status.toUiState()
            )
        } else {
            null
        }

    return MessageUiModel(
        id = id,
        conversationId = conversation_id,
        sender = senderUser,
        category = category,
        renderType = render_type,
        createdAt = created_at,
        revokedAt = revoked_at,
        replyTo = replyUi,
        content = content,
        bubbleColor = senderParticipantSettings?.bubbleColor, // 你如果有气泡颜色规则，可以在这里算
        sendStatus = local_send_status.toUiState()
    )
}

fun List<GetMessagesWithAllRelationsByPage>.toUiModels(): List<MessageUiModel> {
    return map { it.toUiModel() }
}

/**
 * 将 Message 转换为 MessageEntity
 */
fun Message.toMessageEntity(localStatus: MessageStatus? = null): MessageEntity = MessageEntity(
    id = this.id,
    conversation_id = this.conversationId,
    user_id = this.sender?.id ?: error("Sender is required for MessageEntity"),
    category = this.category,
    render_type = this.getRenderType(),
    content = this.content,
    reply_to_message_id = this.replyTo?.id,
    created_at = this.createdAt,
    revoked_at = this.revokedAt,
    local_send_status = localStatus
)

/**
 * 将引用的回复消息转换为 MessageEntity
 */
fun Message.toReplyMessageEntity(): MessageEntity? {
    return this.replyTo?.toMessageEntity()
}

/**
 * 将消息发送者转换为 UserEntity
 */
fun Message.toUserEntity(): UserEntity? {
    val user = this.sender ?: return null
    return UserEntity(
        id = user.id,
        username = user.username,
        email = null, // SimpleUser 中没有 email，设为 null
        display_name = user.displayName,
        avatar = user.avatarUrl,
        bio = null, // SimpleUser 中没有 bio
        created_at = user.createdAt,
        updated_at = user.updatedAt,
        deleted_at = user.deletedAt,
        role = user.role
    )
}

/**
 * 将回复消息的发送者转换为 UserEntity
 */
fun Message.toReplyUserEntity(): UserEntity? {
    return this.replyTo?.toUserEntity()
}

/**
 * 将消息发送者的群聊上下文转换为 ParticipantEntity
 */
fun Message.toParticipantEntity(): ParticipantEntity? {
    val user = this.sender ?: return null
    val context = this.senderContext ?: return null
    return ParticipantEntity(
        conversation_id = this.conversationId,
        user_id = user.id,
        role = context.conversationRole,
        last_read_message_id = context.lastReadMessageId,
        joined_at = context.joinedAt,
        muted_until = context.mutedUntil,
        settings = context.participantSettings ?: ParticipantSettings() // 如果为空则使用默认配置
    )
}

/**
 * 将回复消息发送者的群聊上下文转换为 ParticipantEntity
 */
fun Message.toReplyParticipantEntity(): ParticipantEntity? {
    return this.replyTo?.toParticipantEntity()
}


private fun Message.getRenderType(): MessageRenderType {
    return when (this.content) {
        is TextContent -> MessageRenderType.TEXT
        is ImageContent -> MessageRenderType.IMAGE
        is VideoContent -> MessageRenderType.VIDEO
        is AudioContent -> MessageRenderType.AUDIO
        is FileContent -> MessageRenderType.FILE
        else -> MessageRenderType.OTHER
    }
}

fun KeyedMessagesWithRelations.toUiModel(): MessageUiModel {
    val senderUser = SenderUser(
        id = user_id,
        username = senderUsername ?: "null",
        displayName = senderParticipantSettings?.nickname ?: senderDisplayName,
        avatarUrl = senderAvatar,
        role = senderRole,
    )

    val replyUi: MessageUiModel? =
        if (reply_id != null && reply_conversation_id != null && reply_user_id != null &&
            reply_category != null && reply_render_type != null && reply_content != null &&
            reply_created_at != null
        ) {
            val replySender = if (reply_senderUsername != null) {
                SenderUser(
                    id = reply_user_id!!,
                    username = reply_senderUsername!!,
                    displayName = reply_senderDisplayName,
                    avatarUrl = reply_senderAvatar,
                    role = reply_senderRole,
                )
            } else {
                null
            }

            MessageUiModel(
                id = reply_id!!,
                conversationId = reply_conversation_id!!,
                sender = replySender,
                category = reply_category!!,
                renderType = reply_render_type!!,
                createdAt = reply_created_at!!,
                revokedAt = reply_revoked_at,
                replyTo = null, // 这里只做一层回复，不再递归
                content = reply_content!!,
                bubbleColor = reply_senderParticipantSettings?.bubbleColor,
                sendStatus = reply_local_send_status.toUiState()
            )
        } else {
            null
        }

    return MessageUiModel(
        id = id,
        conversationId = conversation_id,
        sender = senderUser,
        category = category,
        renderType = render_type,
        createdAt = created_at,
        revokedAt = revoked_at,
        replyTo = replyUi,
        content = content,
        bubbleColor = senderParticipantSettings?.bubbleColor, // 你如果有气泡颜色规则，可以在这里算
        sendStatus = local_send_status.toUiState()
    )
}