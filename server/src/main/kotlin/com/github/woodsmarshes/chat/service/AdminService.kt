package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.base.UserAccessCache
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListItem
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.ResetPasswordResponse
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.toAuthSessionInfo
import com.github.woodsmarshes.chat.utils.MAX_PAGE_SIZE
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Application-administrator operations: user listing/detail, ban/unban,
 * role changes, session revocation and password resets. Every public method
 * re-checks the caller's ADMIN role — the routes never trust the client.
 */
class AdminService(
    private val userRepository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val hashingService: HashingService,
    private val userAccessCache: UserAccessCache,
) {
    private val secureRandom = SecureRandom()

    suspend fun requireAdmin(adminId: Uuid): Result<Unit, UserError> = coroutineBinding {
        val admin = userRepository.getUserById(adminId) ?: Err(UserError.NotFound).bind()
        if (admin.role != UserRole.ADMIN) Err(UserError.PermissionDenied).bind()
    }

    suspend fun listUsers(
        keyword: String?,
        role: UserRole?,
        banned: Boolean?,
        page: Int,
        limit: Int,
    ): Result<AdminUserListResponse, UserError> = coroutineBinding {
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val paged = userRepository.listUsersPaged(keyword, role, banned, safePage, safeLimit)
        val sessionCounts = authSessionRepository.countActiveByUsers(paged.items.map { it.id })
        AdminUserListResponse(
            items = paged.items.map { AdminUserListItem(user = it, activeSessionCount = sessionCounts[it.id] ?: 0) },
            page = safePage,
            limit = safeLimit,
            total = paged.total,
        )
    }

    suspend fun getUserDetail(id: Uuid): Result<AdminUserDetailResponse, UserError> = coroutineBinding {
        val user = userRepository.getUserById(id) ?: Err(UserError.NotFound).bind()
        val stats = userRepository.getUserAdminStats(id)
        val sessionCount = authSessionRepository.countActiveByUsers(listOf(id))[id] ?: 0
        val lastActiveAt = authSessionRepository.getLastActiveAt(id)
        AdminUserDetailResponse(
            user = user,
            activeSessionCount = sessionCount,
            conversationCount = stats.conversationCount,
            messageCount = stats.messageCount,
            lastActiveAt = lastActiveAt,
        )
    }

    suspend fun setBanned(adminId: Uuid, targetId: Uuid, banned: Boolean): Result<Unit, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        if (banned && targetId == adminId) Err(UserError.InvalidRequest).bind()
        val target = userRepository.getUserById(targetId) ?: Err(UserError.NotFound).bind()
        // Demoting the last admin out of the admin pool by ban would lock
        // everyone out of administration; refuse while at least one remains.
        if (banned && target.role == UserRole.ADMIN && userRepository.countAdmins() <= 1) {
            Err(UserError.PermissionDenied).bind()
        }
        if (!userRepository.setBannedAt(targetId, if (banned) Clock.System.now() else null)) {
            Err(UserError.UpdateFailed).bind()
        }
        if (banned) {
            // Refresh sessions die immediately; access tokens expire within
            // their TTL or sooner via the UserAccessCache invalidation.
            authSessionRepository.revokeAllByUser(targetId)
            userAccessCache.invalidate(targetId)
        } else {
            userAccessCache.invalidate(targetId)
        }
    }

    suspend fun changeRole(adminId: Uuid, targetId: Uuid, newRole: UserRole): Result<Unit, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        val target = userRepository.getUserById(targetId) ?: Err(UserError.NotFound).bind()
        if (target.role == UserRole.ADMIN && newRole != UserRole.ADMIN && userRepository.countAdmins() <= 1) {
            Err(UserError.PermissionDenied).bind()
        }
        if (!userRepository.changeUserRole(targetId, newRole)) Err(UserError.UpdateFailed).bind()
    }

    suspend fun listUserSessions(adminId: Uuid, targetId: Uuid): Result<AuthSessionListResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        userRepository.getUserById(targetId) ?: Err(UserError.NotFound).bind()
        val sessions = authSessionRepository.listByUser(targetId)
        AuthSessionListResponse(items = sessions.map { it.toAuthSessionInfo() })
    }

    suspend fun revokeAllUserSessions(adminId: Uuid, targetId: Uuid): Result<Int, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        userRepository.getUserById(targetId) ?: Err(UserError.NotFound).bind()
        val revoked = authSessionRepository.revokeAllByUser(targetId)
        userAccessCache.invalidate(targetId)
        revoked
    }

    suspend fun resetPassword(adminId: Uuid, targetId: Uuid): Result<ResetPasswordResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        userRepository.getUserById(targetId) ?: Err(UserError.NotFound).bind()
        val temporaryPassword = generateTemporaryPassword()
        val salted = hashingService.generateSaltedHash(temporaryPassword)
        if (!userRepository.updatePassword(targetId, salted.hash, salted.salt)) {
            Err(UserError.UpdateFailed).bind()
        }
        // A password reset must also end every existing session, otherwise
        // the old credentials' sessions outlive the credential change.
        authSessionRepository.revokeAllByUser(targetId)
        userAccessCache.invalidate(targetId)
        ResetPasswordResponse(temporaryPassword = temporaryPassword)
    }

    /** 16 chars from an unambiguous alphabet — shown once, never stored. */
    private fun generateTemporaryPassword(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        return CharArray(TEMP_PASSWORD_LENGTH) { alphabet[secureRandom.nextInt(alphabet.length)] }.concatToString()
    }

    private companion object {
        const val TEMP_PASSWORD_LENGTH = 16
    }
}