package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionInfo
import com.github.woodsmarshes.chat.repository.database.schema.AuthSessions
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Server-side view of one issued refresh token. */
data class AuthSession(
    val id: Uuid,
    val userId: Uuid,
    val expiresAt: Instant,
    val deviceName: String?,
    val userAgent: String?,
    val ipAddress: String?,
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
    ): Uuid?

    /** The live session for [tokenHash] — revoked and expired rows never match. */
    suspend fun findActiveSession(tokenHash: String): AuthSession?

    suspend fun revokeSession(tokenHash: String): Boolean

    suspend fun deleteExpiredSessions(): Int

    /** All sessions (active and revoked) for [userId], newest first. */
    suspend fun listByUser(userId: Uuid): List<AuthSessionRecord>

    /** Revokes every live session of [userId], optionally sparing [exceptSessionId]; returns rows revoked. */
    suspend fun revokeAllByUser(
        userId: Uuid,
        exceptSessionId: Uuid? = null,
    ): Int

    /** Revokes one session owned by [userId]; false when not found/already revoked. */
    suspend fun revokeById(
        sessionId: Uuid,
        userId: Uuid,
    ): Boolean

    /** Active-session counts per user for a page of users. */
    suspend fun countActiveByUsers(userIds: List<Uuid>): Map<Uuid, Int>

    /** Most recent activity across the user's live sessions, if any. */
    suspend fun getLastActiveAt(userId: Uuid): Instant?

    /** Admin: platform-wide count of live (unrevoked, unexpired) sessions. */
    suspend fun countActiveSessions(): Long
}

class AuthSessionSourceImpl : AuthSessionRepository {
    override suspend fun createSession(
        userId: Uuid,
        tokenHash: String,
        expiresAt: Instant,
        deviceName: String?,
        userAgent: String?,
        ipAddress: String?,
    ): Uuid? =
        dbQuery {
            val sessionId = Uuid.random()
            val inserted =
                AuthSessions
                    .insert {
                        it[id] = sessionId
                        it[this.userId] = userId
                        it[this.tokenHash] = tokenHash
                        it[this.expiresAt] = expiresAt
                        it[this.deviceName] = deviceName
                        it[this.userAgent] = userAgent
                        it[this.ipAddress] = ipAddress
                        it[lastUsedAt] = Clock.System.now()
                    }.resultedValues
                    ?.isNotEmpty() == true
            sessionId.takeIf { inserted }
        }

    override suspend fun findActiveSession(tokenHash: String): AuthSession? =
        dbQuery {
            AuthSessions
                .selectAll()
                .where {
                    (AuthSessions.tokenHash eq tokenHash) and
                        AuthSessions.revokedAt.isNull() and
                        (AuthSessions.expiresAt greater Clock.System.now())
                }.singleOrNull()
                ?.let {
                    AuthSession(
                        id = it[AuthSessions.id],
                        userId = it[AuthSessions.userId].value,
                        expiresAt = it[AuthSessions.expiresAt],
                        deviceName = it[AuthSessions.deviceName],
                        userAgent = it[AuthSessions.userAgent],
                        ipAddress = it[AuthSessions.ipAddress],
                    )
                }
        }

    override suspend fun revokeSession(tokenHash: String): Boolean =
        dbQuery {
            AuthSessions.update(
                where = { (AuthSessions.tokenHash eq tokenHash) and AuthSessions.revokedAt.isNull() },
            ) {
                it[revokedAt] = Clock.System.now()
            } > 0
        }

    override suspend fun deleteExpiredSessions(): Int =
        dbQuery {
            AuthSessions.deleteWhere { AuthSessions.expiresAt less Clock.System.now() }
        }

    override suspend fun listByUser(userId: Uuid): List<AuthSessionRecord> =
        dbQuery {
            AuthSessions
                .selectAll()
                .where { AuthSessions.userId eq userId }
                .orderBy(AuthSessions.createdAt, SortOrder.DESC)
                .map { it.toAuthSessionRecord() }
        }

    override suspend fun revokeAllByUser(
        userId: Uuid,
        exceptSessionId: Uuid?,
    ): Int =
        dbQuery {
            var condition: Op<Boolean> = (AuthSessions.userId eq userId) and AuthSessions.revokedAt.isNull()
            exceptSessionId?.let { condition = condition and (AuthSessions.id neq it) }
            AuthSessions.update({ condition }) {
                it[revokedAt] = Clock.System.now()
            }
        }

    override suspend fun revokeById(
        sessionId: Uuid,
        userId: Uuid,
    ): Boolean =
        dbQuery {
            AuthSessions.update({
                (AuthSessions.id eq sessionId) and
                    (AuthSessions.userId eq userId) and
                    AuthSessions.revokedAt.isNull()
            }) {
                it[revokedAt] = Clock.System.now()
            } > 0
        }

    override suspend fun countActiveByUsers(userIds: List<Uuid>): Map<Uuid, Int> =
        dbQuery {
            if (userIds.isEmpty()) return@dbQuery emptyMap()
            AuthSessions
                .select(AuthSessions.userId)
                .where {
                    (AuthSessions.userId inList userIds) and
                        AuthSessions.revokedAt.isNull() and
                        (AuthSessions.expiresAt greater Clock.System.now())
                }.map { it[AuthSessions.userId].value }
                .groupingBy { it }
                .eachCount()
        }

    override suspend fun getLastActiveAt(userId: Uuid): Instant? =
        dbQuery {
            AuthSessions
                .selectAll()
                .where {
                    (AuthSessions.userId eq userId) and
                        AuthSessions.revokedAt.isNull() and
                        (AuthSessions.expiresAt greater Clock.System.now())
                }.map { it[AuthSessions.lastUsedAt] ?: it[AuthSessions.createdAt] }
                .maxOrNull()
        }

    override suspend fun countActiveSessions(): Long =
        dbQuery {
            AuthSessions
                .selectAll()
                .where {
                    AuthSessions.revokedAt.isNull() and
                        (AuthSessions.expiresAt greater Clock.System.now())
                }.count()
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

fun AuthSessionRecord.toAuthSessionInfo(isCurrent: Boolean = false): AuthSessionInfo =
    AuthSessionInfo(
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
