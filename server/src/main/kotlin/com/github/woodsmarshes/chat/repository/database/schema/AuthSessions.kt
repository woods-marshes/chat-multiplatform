package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

/**
 * One row per issued refresh token. Only the SHA-256 hash of the opaque
 * token is stored, so a database leak does not leak usable credentials;
 * rotation marks the old row revoked and mints a fresh one.
 */
object AuthSessions : Table("auth_sessions") {
    val id = uuid("id")
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
