package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.repository.database.schema.AuthSessions
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Server-side view of one issued refresh token. */
data class AuthSession(
    val userId: Uuid,
    val expiresAt: Instant,
)

interface AuthSessionRepository {
    suspend fun createSession(userId: Uuid, tokenHash: String, expiresAt: Instant): Boolean

    /** The live session for [tokenHash] — revoked and expired rows never match. */
    suspend fun findActiveSession(tokenHash: String): AuthSession?

    suspend fun revokeSession(tokenHash: String): Boolean

    suspend fun deleteExpiredSessions(): Int
}

class AuthSessionSourceImpl : AuthSessionRepository {

    override suspend fun createSession(userId: Uuid, tokenHash: String, expiresAt: Instant): Boolean =
        dbQuery {
            AuthSessions.insert {
                it[id] = Uuid.random()
                it[this.userId] = userId
                it[this.tokenHash] = tokenHash
                it[this.expiresAt] = expiresAt
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
}
