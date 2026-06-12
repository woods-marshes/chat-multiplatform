package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.repository.database.UuidV7Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object Messages : UuidV7Table("messages") {
    val conversationId = reference("conversation_id", Conversations)
    val senderId = reference("user_id", Users)
    val category = enumerationByName("category", 32, MessageCategory::class)
    val renderType = enumerationByName("render_type", 20, MessageRenderType::class)

    val content = jsonb<MessageContent>("content", ProjectJson)

    val searchText = text("search_text").nullable()
    val replyToMessageId = reference("reply_to_message_id", Messages).nullable()
    val createdAt = timestamp("created_at")
    val revokedAt = timestamp("revoked_at").nullable()

    init {
        uniqueIndex(conversationId, id)
    }

}