package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object GroupProfiles : Table("group_profiles") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 64)
    val handle = varchar("handle", 32).uniqueIndex().nullable()
    val description = varchar("description", 512).nullable()
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val ownerId = reference("owner_id", Users)

    val settings = jsonb<GroupSettings>("settings", ProjectJson)

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(conversationId)
}