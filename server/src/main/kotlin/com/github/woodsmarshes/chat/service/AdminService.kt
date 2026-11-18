package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.base.UserAccessCache
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListItem
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationParticipant
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminLogEntry
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminLogListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminMessageListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminStatsResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListItem
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.ResetPasswordResponse
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.logging.AdminLogBuffer
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
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
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
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

    suspend fun listConversations(
        adminId: Uuid,
        type: ConversationType?,
        page: Int,
        limit: Int,
    ): Result<AdminConversationListResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val paged = conversationRepository.listConversationsPaged(type, safePage, safeLimit)
        // Counts are fetched per conversation; a page is at most MAX_PAGE_SIZE
        // rows, so this stays a bounded number of small queries.
        val items = paged.items.map { row ->
            val stats = conversationRepository.getConversationAdminStats(row.conversation.id)
            AdminConversationListItem(
                conversation = row.conversation,
                participantCount = stats.participantCount,
                messageCount = stats.messageCount,
                groupName = row.groupName,
            )
        }
        AdminConversationListResponse(items = items, page = safePage, limit = safeLimit, total = paged.total)
    }

    suspend fun getConversationDetail(adminId: Uuid, conversationId: Uuid): Result<AdminConversationDetailResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        val conversation = conversationRepository.getConversationIncludeDeleted(conversationId)
            ?: Err(UserError.NotFound).bind()
        val stats = conversationRepository.getConversationAdminStats(conversationId)
        val participants = conversationRepository.listConversationParticipants(conversationId)
            .map { AdminConversationParticipant(user = it.user, role = it.role, joinedAt = it.joinedAt) }
        val groupName = if (conversation.type == ConversationType.GROUP) {
            conversationRepository.getGroupName(conversationId)
        } else null
        AdminConversationDetailResponse(
            conversation = conversation,
            participantCount = stats.participantCount,
            messageCount = stats.messageCount,
            groupName = groupName,
            participants = participants,
        )
    }

    suspend fun listConversationMessages(
        adminId: Uuid,
        conversationId: Uuid,
        page: Int,
        limit: Int,
    ): Result<AdminMessageListResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        conversationRepository.getConversationIncludeDeleted(conversationId) ?: Err(UserError.NotFound).bind()
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val paged = messageRepository.listMessagesPaged(conversationId, safePage, safeLimit)
        AdminMessageListResponse(items = paged.items, page = safePage, limit = safeLimit, total = paged.total)
    }

    /**
     * Moderator revoke: same irreversible blanking as a user-initiated
     * revoke, including the sync event that tells clients to withdraw the
     * message. No membership check — the admin is not a participant.
     */
    suspend fun revokeMessage(adminId: Uuid, messageId: Uuid): Result<Unit, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        if (!messageRepository.revokeMessage(messageId)) Err(UserError.NotFound).bind()
    }

    suspend fun getStats(adminId: Uuid): Result<AdminStatsResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        val userStats = userRepository.getUserStats()
        val conversationCounts = conversationRepository.countConversations()
        val messageCount = messageRepository.countMessages()
        val activeSessionCount = authSessionRepository.countActiveSessions()
        AdminStatsResponse(
            userCount = userStats.total,
            adminCount = userStats.admins,
            bannedUserCount = userStats.banned,
            conversationCount = conversationCounts.total,
            groupConversationCount = conversationCounts.groups,
            privateConversationCount = conversationCounts.privates,
            messageCount = messageCount,
            activeSessionCount = activeSessionCount,
        )
    }

    suspend fun listLogs(adminId: Uuid, level: String?, limit: Int): Result<AdminLogListResponse, UserError> = coroutineBinding {
        requireAdmin(adminId).bind()
        val safeLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        AdminLogListResponse(
            items = AdminLogBuffer.list(level, safeLimit).map {
                AdminLogEntry(timestamp = it.timestamp, level = it.level, logger = it.logger, message = it.message)
            }
        )
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