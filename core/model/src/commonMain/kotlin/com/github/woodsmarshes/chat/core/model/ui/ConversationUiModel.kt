package com.github.woodsmarshes.chat.core.model.ui

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class ConversationUiModel(
    val id: Uuid,
    val type: ConversationType,
    val name: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val handle: String? = null,
    val lastMessage: LastMessageInfo? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
)

data class LastMessageInfo(
    val id: Uuid,
    val content: MessageContent,
    val renderType: MessageRenderType,
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val createdAt: Instant,
    val isOwnMessage: Boolean = false
)

