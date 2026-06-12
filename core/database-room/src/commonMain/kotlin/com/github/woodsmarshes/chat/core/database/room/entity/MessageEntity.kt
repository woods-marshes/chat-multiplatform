package com.github.woodsmarshes.chat.core.database.room.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: Uuid,
    val conversationId: Uuid,
    val senderId: Uuid,
    val category: MessageCategory,
    val renderType: MessageRenderType,
    val content: MessageContent,
    val searchText: String?,
    val replyToMessageId: Uuid?,
    val createdAt: Instant,
    val revokedAt: Instant?,
)
