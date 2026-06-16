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

val instantAdapter = object : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant {
        return Instant.fromEpochMilliseconds(databaseValue)
    }

    override fun encode(value: Instant): Long {
        return value.toEpochMilliseconds()
    }
}

val uuidAdapter = object : ColumnAdapter<Uuid, ByteArray> {
    override fun decode(databaseValue: ByteArray): Uuid {
        return Uuid.fromByteArray(databaseValue)
    }

    override fun encode(value: Uuid): ByteArray {
        return value.toByteArray()
    }
}

val participantSettingsAdapter = object : ColumnAdapter<ParticipantSettings, String> {
    override fun decode(databaseValue: String): ParticipantSettings {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: ParticipantSettings): String {
        return Json.encodeToString(value)
    }
}

val groupSettingsAdapter = object : ColumnAdapter<GroupSettings, String> {
    override fun decode(databaseValue: String): GroupSettings {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: GroupSettings): String {
        return Json.encodeToString(value)
    }
}

val conversationMetadataAdapter = object : ColumnAdapter<ConversationMetadata, String> {
    override fun decode(databaseValue: String): ConversationMetadata {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: ConversationMetadata): String {
        return Json.encodeToString(value)
    }
}

val messageContentAdapter = object : ColumnAdapter<MessageContent, String> {
    override fun decode(databaseValue: String): MessageContent {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: MessageContent): String {
        return Json.encodeToString(value)
    }
}

val jsonElementAdapter = object : ColumnAdapter<JsonElement, String> {
    override fun decode(databaseValue: String): JsonElement {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: JsonElement): String {
        return Json.encodeToString(value)
    }
}

val articleStatsAdapter = object : ColumnAdapter<ArticleStats, String> {
    override fun decode(databaseValue: String): ArticleStats {
        return Json.decodeFromString(databaseValue)
    }

    override fun encode(value: ArticleStats): String {
        return Json.encodeToString(value)
    }
}