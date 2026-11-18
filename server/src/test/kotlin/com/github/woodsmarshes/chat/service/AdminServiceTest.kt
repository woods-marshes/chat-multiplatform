package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.base.UserAccessCache
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.SaltedHash
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.AdminConversationRow
import com.github.woodsmarshes.chat.repository.AuthSessionRecord
import com.github.woodsmarshes.chat.repository.ConversationAdminStats
import com.github.woodsmarshes.chat.repository.ConversationCounts
import com.github.woodsmarshes.chat.repository.PagedConversations
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.PagedUsers
import com.github.woodsmarshes.chat.repository.UserAdminStats
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserStats
import com.github.woodsmarshes.chat.utils.connectToH2Database
import com.github.woodsmarshes.chat.logging.AuditLogBuffer
import com.github.woodsmarshes.chat.websocket.RealtimeDelivery
import com.github.woodsmarshes.chat.websocket.SessionIndex
import io.ktor.server.websocket.WebSocketServerSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class AdminServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val authSessionRepository = mockk<AuthSessionRepository>()
    private val hashingService = mockk<HashingService>()
    private val userAccessCache = mockk<UserAccessCache>(relaxed = true)
    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val sessionIndex = SessionIndex()
    private val realtimeDelivery = mockk<RealtimeDelivery>(relaxed = true)

    // Real H2 connection so inTransaction blocks have a transaction context.
    private val database = connectToH2Database()

    private val service = AdminService(
        userRepository, authSessionRepository, hashingService, userAccessCache,
        conversationRepository, messageRepository, sessionIndex, realtimeDelivery,
    )

    private fun user(role: UserRole) = User(
        id = Uuid.random(),
        username = "alice",
        email = null,
        displayName = "Alice",
        avatarUrl = null,
        bio = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
        deletedAt = null,
        role = role,
    )

    @Test
    fun requireAdminDeniesNonAdmin() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(UserRole.MEMBER)

        val result = service.requireAdmin(Uuid.random())

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun setBannedRejectsSelfBan() = runBlocking {
        val adminId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)

        val result = service.setBanned(adminId, adminId, banned = true)

        assertEquals(UserError.InvalidRequest, result.getError())
    }

    @Test
    fun setBannedRejectsBanningLastAdmin() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.countAdmins() } returns 1L

        val result = service.setBanned(adminId, targetId, banned = true)

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun setBannedRevokesSessionsAndInvalidatesCache() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.MEMBER)
        coEvery { userRepository.setBannedAt(targetId, any()) } returns true
        coEvery { authSessionRepository.revokeAllByUser(targetId) } returns 2

        val result = service.setBanned(adminId, targetId, banned = true)

        assertTrue(result.isOk)
        coVerify(exactly = 1) { authSessionRepository.revokeAllByUser(targetId) }
        coVerify(exactly = 1) { userAccessCache.invalidate(targetId) }
    }

    @Test
    fun changeRoleRejectsDemotingLastAdmin() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.countAdmins() } returns 1L

        val result = service.changeRole(adminId, targetId, UserRole.MEMBER)

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun resetPasswordRevokesAllSessions() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.MEMBER)
        every { hashingService.generateSaltedHash(any()) } returns SaltedHash(hash = "h", salt = "s")
        coEvery { userRepository.updatePassword(targetId, "h", "s") } returns true
        coEvery { authSessionRepository.revokeAllByUser(targetId) } returns 1

        val result = service.resetPassword(adminId, targetId)

        assertTrue(result.isOk)
        assertEquals(16, result.get()!!.temporaryPassword.length)
        coVerify(exactly = 1) { authSessionRepository.revokeAllByUser(targetId) }
    }

    @Test
    fun revokeMessageRejectsNonAdmin() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(UserRole.MEMBER)

        val result = service.revokeMessage(Uuid.random(), Uuid.random())

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun revokeMessageReturnsNotFoundWhenAlreadyRevoked() = runBlocking {
        val adminId = Uuid.random()
        val messageId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { messageRepository.revokeMessage(messageId) } returns false

        val result = service.revokeMessage(adminId, messageId)

        assertEquals(UserError.NotFound, result.getError())
    }

    @Test
    fun getStatsAggregatesAllCounters() = runBlocking {
        val adminId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserStats() } returns UserStats(total = 10, admins = 2, banned = 1)
        coEvery { conversationRepository.countConversations() } returns ConversationCounts(total = 5, groups = 3, privates = 2)
        coEvery { messageRepository.countMessages() } returns 100L
        coEvery { authSessionRepository.countActiveSessions() } returns 7L

        val result = service.getStats(adminId)

        assertTrue(result.isOk)
        val stats = result.get()!!
        assertEquals(10, stats.userCount)
        assertEquals(2, stats.adminCount)
        assertEquals(1, stats.bannedUserCount)
        assertEquals(5, stats.conversationCount)
        assertEquals(3, stats.groupConversationCount)
        assertEquals(2, stats.privateConversationCount)
        assertEquals(100, stats.messageCount)
        assertEquals(7, stats.activeSessionCount)
    }
    @Test
    fun listConversationsBatchesStats() = runBlocking {
        val adminId = Uuid.random()
        val conversation = Conversation(
            id = Uuid.random(),
            type = ConversationType.GROUP,
            metadata = GroupMetadata(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            deletedAt = null,
            lastMessageId = null,
        )
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.listConversationsPaged(null, 1, 20) } returns
            PagedConversations(items = listOf(AdminConversationRow(conversation, "team-alpha")), total = 1)
        coEvery { conversationRepository.getConversationAdminStatsBatch(listOf(conversation.id)) } returns
            mapOf(conversation.id to ConversationAdminStats(participantCount = 3, messageCount = 42))

        val result = service.listConversations(adminId, null, 1, 20)

        assertTrue(result.isOk)
        val item = result.get()!!.items.single()
        assertEquals(3, item.participantCount)
        assertEquals(42, item.messageCount)
        assertEquals("team-alpha", item.groupName)
        assertEquals(1, result.get()!!.total)
    }

    @Test
    fun getConversationDetailReturnsParticipants() = runBlocking {
        val adminId = Uuid.random()
        val conversation = Conversation(
            id = Uuid.random(),
            type = ConversationType.PRIVATE,
            metadata = PrivateMetadata(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            deletedAt = null,
            lastMessageId = null,
        )
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.getConversationIncludeDeleted(conversation.id) } returns conversation
        coEvery { conversationRepository.getConversationAdminStats(conversation.id) } returns
            ConversationAdminStats(participantCount = 2, messageCount = 7)
        coEvery { conversationRepository.listConversationParticipants(conversation.id) } returns emptyList()

        val result = service.getConversationDetail(adminId, conversation.id)

        assertTrue(result.isOk)
        val detail = result.get()!!
        assertEquals(2, detail.participantCount)
        assertEquals(7, detail.messageCount)
        assertEquals(null, detail.groupName)
    }

    @Test
    fun listConversationMessagesComputesHasMore() = runBlocking {
        val adminId = Uuid.random()
        val conversation = Conversation(
            id = Uuid.random(),
            type = ConversationType.PRIVATE,
            metadata = PrivateMetadata(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            deletedAt = null,
            lastMessageId = null,
        )
        val messages = (1..3).map { index ->
            Message(
                id = Uuid.random(),
                conversationId = conversation.id,
                category = MessageCategory.NORMAL,
                createdAt = Clock.System.now(),
                content = TextContent("msg-$index"),
            )
        }
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.getConversationIncludeDeleted(conversation.id) } returns conversation
        // limit = 2 -> service fetches 3 rows; 3 > 2 means another page exists.
        coEvery { messageRepository.getHistory(conversation.id, 3, null) } returns messages

        val result = service.listConversationMessages(adminId, conversation.id, beforeId = null, limit = 2)

        assertTrue(result.isOk)
        val page = result.get()!!
        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun listConversationMessagesReturnsEmptyWhenNoMore() = runBlocking {
        val adminId = Uuid.random()
        val conversation = Conversation(
            id = Uuid.random(),
            type = ConversationType.PRIVATE,
            metadata = PrivateMetadata(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            deletedAt = null,
            lastMessageId = null,
        )
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.getConversationIncludeDeleted(conversation.id) } returns conversation
        coEvery { messageRepository.getHistory(conversation.id, 51, null) } returns emptyList()

        val result = service.listConversationMessages(adminId, conversation.id, beforeId = null, limit = 50)

        assertTrue(result.isOk)
        assertEquals(false, result.get()!!.hasMore)
        assertTrue(result.get()!!.items.isEmpty())
    }

    @Test
    fun deleteConversationSoftDeletes() = runBlocking {
        val adminId = Uuid.random()
        val conversation = Conversation(
            id = Uuid.random(),
            type = ConversationType.PRIVATE,
            metadata = PrivateMetadata(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            deletedAt = null,
            lastMessageId = null,
        )
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.getConversationIncludeDeleted(conversation.id) } returns conversation
        coEvery { conversationRepository.softDeleteConversation(conversation.id, adminId) } returns true

        val result = service.deleteConversation(adminId, conversation.id)

        assertTrue(result.isOk)
        coVerify(exactly = 1) { conversationRepository.softDeleteConversation(conversation.id, adminId) }
    }

    @Test
    fun deleteConversationReturnsNotFound() = runBlocking {
        val adminId = Uuid.random()
        val conversationId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { conversationRepository.getConversationIncludeDeleted(conversationId) } returns null

        val result = service.deleteConversation(adminId, conversationId)

        assertEquals(UserError.NotFound, result.getError())
    }

    @Test
    fun listUserSessionsFiltersRevokedAndExpired() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        val now = Clock.System.now()
        val active = AuthSessionRecord(
            id = Uuid.random(), userId = targetId, createdAt = now, expiresAt = now + 1.hours,
            revokedAt = null, deviceName = "phone", userAgent = null, ipAddress = null, lastUsedAt = null,
        )
        val revoked = AuthSessionRecord(
            id = Uuid.random(), userId = targetId, createdAt = now, expiresAt = now + 1.hours,
            revokedAt = now, deviceName = "old", userAgent = null, ipAddress = null, lastUsedAt = null,
        )
        val expired = AuthSessionRecord(
            id = Uuid.random(), userId = targetId, createdAt = now - 2.hours, expiresAt = now - 1.hours,
            revokedAt = null, deviceName = "stale", userAgent = null, ipAddress = null, lastUsedAt = null,
        )
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.MEMBER)
        coEvery { authSessionRepository.listByUser(targetId) } returns listOf(active, revoked, expired)

        val result = service.listUserSessions(adminId, targetId)

        assertTrue(result.isOk)
        assertEquals(1, result.get()!!.items.size)
        assertEquals("phone", result.get()!!.items.single().deviceName)
    }

    @Test
    fun setBannedDisconnectsLiveWebSocketSessions() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        val session = mockk<WebSocketServerSession>()
        sessionIndex.add(targetId, session)
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.MEMBER)
        coEvery { userRepository.setBannedAt(targetId, any()) } returns true
        coEvery { authSessionRepository.revokeAllByUser(targetId) } returns 1

        val result = service.setBanned(adminId, targetId, banned = true)

        assertTrue(result.isOk)
        verify(exactly = 1) { realtimeDelivery.closeSession(session) }
    }

    @Test
    fun setBannedUnbanKeepsWebSocketSessions() = runBlocking {
        val adminId = Uuid.random()
        val targetId = Uuid.random()
        val session = mockk<WebSocketServerSession>()
        sessionIndex.add(targetId, session)
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery { userRepository.getUserById(targetId) } returns user(UserRole.MEMBER)
        coEvery { userRepository.setBannedAt(targetId, any()) } returns true

        val result = service.setBanned(adminId, targetId, banned = false)

        assertTrue(result.isOk)
        verify(exactly = 0) { realtimeDelivery.closeSession(any()) }
    }

    @Test
    fun listUsersRejectsNonAdmin() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(UserRole.MEMBER)

        val result = service.listUsers(Uuid.random(), null, null, null, 1, 20)

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun getUserDetailRejectsNonAdmin() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(UserRole.MEMBER)

        val result = service.getUserDetail(Uuid.random(), Uuid.random())

        assertEquals(UserError.PermissionDenied, result.getError())
    }

    @Test
    fun listLogsRejectsUnknownLevel() = runBlocking {
        val adminId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)

        val result = service.listLogs(adminId, "DEBUG", 10)

        assertEquals(UserError.InvalidRequest, result.getError())
    }

    @Test
    fun listLogsAcceptsWarnAndError() = runBlocking {
        val adminId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)

        val warn = service.listLogs(adminId, "warn", 10)
        val error = service.listLogs(adminId, "ERROR", 10)

        assertTrue(warn.isOk)
        assertTrue(error.isOk)
    }
    @Test
    fun listUsersBatchesSessionCounts() = runBlocking {
        val adminId = Uuid.random()
        val member = user(UserRole.MEMBER)
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery {
            userRepository.listUsersPaged(null, null, null, 1, 20)
        } returns PagedUsers(items = listOf(member), total = 1)
        coEvery { authSessionRepository.countActiveByUsers(listOf(member.id)) } returns mapOf(member.id to 3)

        val result = service.listUsers(adminId, null, null, null, 1, 20)

        assertTrue(result.isOk)
        assertEquals(3, result.get()!!.items.single().activeSessionCount)
        assertEquals(1, result.get()!!.total)
    }

    @Test
    fun listAuditLogsReturnsAuditChannelEntries() = runBlocking {
        AuditLogBuffer.clear()
        AuditLogBuffer.add("INFO", "admin.audit", "action=ban admin=x target=y banned=true")
        val adminId = Uuid.random()
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)

        val result = service.listAuditLogs(adminId, 10)

        assertTrue(result.isOk)
        assertEquals(1, result.get()!!.items.size)
        assertEquals("admin.audit", result.get()!!.items.single().logger)
        AuditLogBuffer.clear()
    }
}