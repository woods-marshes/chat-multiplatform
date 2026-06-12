package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Clock
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

object ConversationParticipants : Table("conversation_participants") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName("role", 20, ConversationRole::class)
    val lastReadMessageId = uuid("last_read_message_id").nullable()
    val joinedAt = timestamp("joined_at").clientDefault { Clock.System.now() }
    val mutedUntil = timestamp("muted_until").nullable()
    val settings = jsonb<ParticipantSettings>("settings", ProjectJson)

    override val primaryKey = PrimaryKey(conversationId, userId)
}