package com.github.woodsmarshes.chat.base

import com.github.woodsmarshes.chat.repository.UserRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Short-TTL cache of per-user ban state for the JWT validate hook.
 *
 * Access tokens are stateless, so without this every authenticated request
 * would need a database round-trip to learn whether the user was banned
 * since the token was issued. A 30-second TTL bounds both the DB load (one
 * lookup per user per window) and the ban-enforcement delay; ban/unban
 * operations call [invalidate] to close the window immediately for the
 * affected user.
 */
class UserAccessCache(
    private val userRepository: UserRepository,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private data class Entry(val bannedAt: Instant?, val checkedAtMillis: Long)

    private val entries = ConcurrentHashMap<Uuid, Entry>()

    suspend fun isBanned(userId: Uuid): Boolean {
        val now = System.currentTimeMillis()
        entries[userId]?.let { entry ->
            if (now - entry.checkedAtMillis < ttlMillis) return entry.bannedAt != null
        }
        val user = userRepository.getUserById(userId) ?: return false
        entries[userId] = Entry(user.bannedAt, now)
        return user.bannedAt != null
    }

    fun invalidate(userId: Uuid) {
        entries.remove(userId)
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 30_000L
    }
}