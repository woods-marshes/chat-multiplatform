package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Clock

object Conversations : UuidTable(name = "conversations", uuidVersion = UuidVersion.V7) {
    val type = enumerationByName("type", 32, ConversationType::class)
    val lastMessageId = uuid("last_message_id").nullable()
    val metadata = jsonb<ConversationMetadata>("metadata", ProjectJson).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").clientDefault{ Clock.System.now() }
    val deletedAt = timestamp("deleted_at").nullable()
}