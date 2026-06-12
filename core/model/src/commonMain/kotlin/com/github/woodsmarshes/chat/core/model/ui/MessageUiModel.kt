package com.github.woodsmarshes.chat.core.model.ui

import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class MessageUiModel(
    val id: Uuid,
    val conversationId: Uuid,
    val sender: SenderUser? = null,
    val category: MessageCategory,
    val renderType: MessageRenderType,
    val createdAt: Instant,
    val revokedAt: Instant? = null,
    val replyTo: MessageUiModel? = null,
    val content: MessageContent,
    val bubbleColor: String? = null,
    val sendStatus: MessageState,
)

data class SenderUser(
    val id: Uuid,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val role: ConversationRole?,
)

sealed class MessageState {

    data object Sending : MessageState()

    data class SendFailed(val reason: String) : MessageState()

    data object Completed : MessageState()

}