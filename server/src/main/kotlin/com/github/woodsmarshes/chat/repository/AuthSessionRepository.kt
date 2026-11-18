package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.repository.database.schema.AuthSessions
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionInfo
import kotlin.uuid.Uuid

/** Server-side view of one issued refresh token. */
data class AuthSession(
    val userId: Uuid,
    val expiresAt: Instant,
)

/** Full row view for admin/user session listings. */
data class AuthSessionRecord(
    val id: Uuid,
    val userId: Uuid,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val deviceName: String?,
    val userAgent: String?,
    val ipAddress: String?,
    val lastUsedAt: Instant?,
)

interface AuthSessionRepository {
    suspend fun createSession(
        userId: Uuid,
        tokenHash: String,
        expiresAt: Instant,
        deviceName: String? = null,
        userAgent: String? = null,
        ipAddress: String? = null,
    ): Boolean

    /** The live session for [tokenHash] — revoked and expired rows never match. */
    suspend fun findActiveSession(tokenHash: String): AuthSession?

    suspend fun revokeSession(tokenHash: String): Boolean

    suspend fun deleteExpiredSessions(): Int

    /** All sessions (active and revoked) for [userId], newest first. */
    suspend fun listByUser(userId: Uuid): List<AuthSessionRecord>

    /** Revokes every live session of [userId]; returns rows revoked. */
    suspend fun revokeAllByUser(userId: Uuid): Int

    /** Revokes one session owned by [userId]; false when not found/already revoked. */
    suspend fun revokeById(sessionId: Uuid, userId: Uuid): Boolean

    /** Active-session counts per user for a page of users. */
    suspend fun countActiveByUsers(userIds: List<Uuid>): Map<Uuid, Int>

    /** Most recent activity across the user's live sessions, if any. */
    suspend fun getLastActiveAt(userId: Uuid): Instant?
}

class AuthSessionSourceImpl : AuthSessionRepository {

    override suspend fun createSession(
        userId: Uuid,
        tokenHash: String,
        expiresAt: Instant,
        deviceName: String?,
        userAgent: String?,
        ipAddress: String?,
    ): Boolean = dbQuery {
        AuthSessions.insert {
            it[id] = Uuid.random()
            it[this.userId] = userId
            it[this.tokenHash] = tokenHash
            it[this.expiresAt] = expiresAt
            it[this.deviceName] = deviceName
            it[this.userAgent] = userAgent
            it[this.ipAddress] = ipAddress
            it[lastUsedAt] = Clock.System.now()
        }.resultedValues?.isNotEmpty() == true
    }

    override suspend fun findActiveSession(tokenHash: String): AuthSession? = dbQuery {
        AuthSessions.selectAll()
            .where {
                (AuthSessions.tokenHash eq tokenHash) and
                    AuthSessions.revokedAt.isNull() and
                    (AuthSessions.expiresAt greater Clock.System.now())
            }
            .singleOrNull()
            ?.let {
                AuthSession(
                    userId = it[AuthSessions.userId].value,
                    expiresAt = it[AuthSessions.expiresAt],
                )
        }
    }

    override suspend fun revokeSession(tokenHash: String): Boolean = dbQuery {
        AuthSessions.update(
            where = { (AuthSessions.tokenHash eq tokenHash) and AuthSessions.revokedAt.isNull() }
        ) {
            it[revokedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun deleteExpiredSessions(): Int = dbQuery {
        AuthSessions.deleteWhere { AuthSessions.expiresAt less Clock.System.now() }
    }

    override suspend fun listByUser(userId: Uuid): List<AuthSessionRecord> = dbQuery {
        AuthSessions.selectAll()
            .where { AuthSessions.userId eq userId }
            .orderBy(AuthSessions.createdAt, SortOrder.DESC)
            .map { it.toAuthSessionRecord() }
    }

    override suspend fun revokeAllByUser(userId: Uuid): Int = dbQuery {
        AuthSessions.update({
            (AuthSessions.userId eq userId) and AuthSessions.revokedAt.isNull()
        }) {
            it[revokedAt] = Clock.System.now()
        }
    }

    override suspend fun revokeById(sessionId: Uuid, userId: Uuid): Boolean = dbQuery {
        AuthSessions.update({
            (AuthSessions.id eq sessionId) and
                (AuthSessions.userId eq userId) and
                AuthSessions.revokedAt.isNull()
        }) {
            it[revokedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun countActiveByUsers(userIds: List<Uuid>): Map<Uuid, Int> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        AuthSessions.select(AuthSessions.userId)
            .where {
                (AuthSessions.userId inList userIds) and
                    AuthSessions.revokedAt.isNull() and
                    (AuthSessions.expiresAt greater Clock.System.now())
            }
            .map { it[AuthSessions.userId].value }
            .groupingBy { it }
            .eachCount()
    }

    override suspend fun getLastActiveAt(userId: Uuid): Instant? = dbQuery {
        AuthSessions.selectAll()
            .where {
                (AuthSessions.userId eq userId) and
                    AuthSessions.revokedAt.isNull() and
                    (AuthSessions.expiresAt greater Clock.System.now())
            }
            .map { it[AuthSessions.lastUsedAt] ?: it[AuthSessions.createdAt] }
            .maxOrNull()
    }
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toAuthSessionRecord(): AuthSessionRecord =
    AuthSessionRecord(
        id = this[AuthSessions.id],
        userId = this[AuthSessions.userId].value,
        createdAt = this[AuthSessions.createdAt],
        expiresAt = this[AuthSessions.expiresAt],
        revokedAt = this[AuthSessions.revokedAt],
        deviceName = this[AuthSessions.deviceName],
        userAgent = this[AuthSessions.userAgent],
        ipAddress = this[AuthSessions.ipAddress],
        lastUsedAt = this[AuthSessions.lastUsedAt],
    )

fun AuthSessionRecord.toAuthSessionInfo(isCurrent: Boolean = false): AuthSessionInfo = AuthSessionInfo(
    id = id,
    deviceName = deviceName,
    userAgent = userAgent,
    ipAddress = ipAddress,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    isCurrent = isCurrent,
)
