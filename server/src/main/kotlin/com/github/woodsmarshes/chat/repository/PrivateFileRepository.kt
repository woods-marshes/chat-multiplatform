package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.repository.database.schema.PrivateFiles
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

interface PrivateFileRepository {
    /** Records that [fileName] was sent into [conversationId]; idempotent. */
    suspend fun registerMapping(fileName: String, conversationId: Uuid)

    /** Conversations the file was sent to (drives download authorization). */
    suspend fun getConversationsForFile(fileName: String): List<Uuid>

    /** Whether the file was ever persisted as part of a message. */
    suspend fun hasMapping(fileName: String): Boolean

    /** Removes every conversation mapping for the file (file is gone). */
    suspend fun deleteMappings(fileName: String)

    suspend fun count(): Long
}

class PrivateFileSourceImpl : PrivateFileRepository {
    override suspend fun registerMapping(fileName: String, conversationId: Uuid) {
        dbQuery {
            PrivateFiles.upsert {
                it[this.fileName] = fileName
                it[this.conversationId] = conversationId
            }
        }
    }

    override suspend fun getConversationsForFile(fileName: String): List<Uuid> = dbQuery {
        PrivateFiles
            .selectAll()
            .where { PrivateFiles.fileName eq fileName }
            .map { it[PrivateFiles.conversationId].value }
    }

    override suspend fun hasMapping(fileName: String): Boolean = dbQuery {
        PrivateFiles
            .selectAll()
            .where { PrivateFiles.fileName eq fileName }
            .limit(1)
            .count() > 0
    }

    override suspend fun deleteMappings(fileName: String) {
        dbQuery {
            PrivateFiles.deleteWhere { PrivateFiles.fileName eq fileName }
        }
    }

    override suspend fun count(): Long = dbQuery {
        PrivateFiles.selectAll().count()
    }
}
