package com.github.woodsmarshes.chat.core.database.utils

import app.cash.sqldelight.ColumnAdapter
import com.github.woodsmarshes.chat.core.model.ArticleStats
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant
import kotlin.uuid.Uuid

val instantAdapter =
    object : ColumnAdapter<Instant, Long> {
        override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)

        override fun encode(value: Instant): Long = value.toEpochMilliseconds()
    }

val uuidAdapter =
    object : ColumnAdapter<Uuid, ByteArray> {
        override fun decode(databaseValue: ByteArray): Uuid = Uuid.fromByteArray(databaseValue)

        override fun encode(value: Uuid): ByteArray = value.toByteArray()
    }

/**
 * DB-side mirror of the wire format (core:network ProjectJson) with the same
 * class discriminator. `ignoreUnknownKeys` is essential: a model field added in
 * a newer app version must not make previously persisted rows undecodable.
 * Kept local so core:database does not depend on core:network.
 */
private val DbJson =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        isLenient = true
    }

val participantSettingsAdapter =
    object : ColumnAdapter<ParticipantSettings, String> {
        override fun decode(databaseValue: String): ParticipantSettings = DbJson.decodeFromString(databaseValue)

        override fun encode(value: ParticipantSettings): String = DbJson.encodeToString(value)
    }

val groupSettingsAdapter =
    object : ColumnAdapter<GroupSettings, String> {
        override fun decode(databaseValue: String): GroupSettings = DbJson.decodeFromString(databaseValue)

        override fun encode(value: GroupSettings): String = DbJson.encodeToString(value)
    }

val conversationMetadataAdapter =
    object : ColumnAdapter<ConversationMetadata, String> {
        override fun decode(databaseValue: String): ConversationMetadata = DbJson.decodeFromString(databaseValue)

        override fun encode(value: ConversationMetadata): String = DbJson.encodeToString(value)
    }

val messageContentAdapter =
    object : ColumnAdapter<MessageContent, String> {
        override fun decode(databaseValue: String): MessageContent = DbJson.decodeFromString(databaseValue)

        override fun encode(value: MessageContent): String = DbJson.encodeToString(value)
    }

val jsonElementAdapter =
    object : ColumnAdapter<JsonElement, String> {
        override fun decode(databaseValue: String): JsonElement = DbJson.decodeFromString(databaseValue)

        override fun encode(value: JsonElement): String = DbJson.encodeToString(value)
    }

val articleStatsAdapter =
    object : ColumnAdapter<ArticleStats, String> {
        override fun decode(databaseValue: String): ArticleStats = DbJson.decodeFromString(databaseValue)

        override fun encode(value: ArticleStats): String = DbJson.encodeToString(value)
    }
