package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

/**
 * Records which conversations a private attachment (/v1/files/content/…)
 * was actually sent to. Download authorization is derived from this: a
 * leaked URL alone grants nothing, only membership in a conversation the
 * file reached does.
 */
object PrivateFiles : Table("private_files") {
    val fileName = varchar("file_name", 80)
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }

    // A file forwarded into several conversations gets one row per
    // conversation; lookups by file name alone are served by the PK prefix.
    override val primaryKey = PrimaryKey(fileName, conversationId)
}
