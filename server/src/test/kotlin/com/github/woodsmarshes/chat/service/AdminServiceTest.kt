package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.base.UserAccessCache
import com.github.woodsmarshes.chat.base.hashing.HashingService
import com.github.woodsmarshes.chat.base.hashing.SaltedHash
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.PagedUsers
import com.github.woodsmarshes.chat.repository.UserAdminStats
import com.github.woodsmarshes.chat.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class AdminServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val authSessionRepository = mockk<AuthSessionRepository>()
    private val hashingService = mockk<HashingService>()
    private val userAccessCache = mockk<UserAccessCache>(relaxed = true)
    private val service = AdminService(userRepository, authSessionRepository, hashingService, userAccessCache)

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
    fun listUsersBatchesSessionCounts() = runBlocking {
        val adminId = Uuid.random()
        val member = user(UserRole.MEMBER)
        coEvery { userRepository.getUserById(adminId) } returns user(UserRole.ADMIN)
        coEvery {
            userRepository.listUsersPaged(null, null, null, 1, 20)
        } returns PagedUsers(items = listOf(member), total = 1)
        coEvery { authSessionRepository.countActiveByUsers(listOf(member.id)) } returns mapOf(member.id to 3)

        val result = service.listUsers(null, null, null, 1, 20)

        assertTrue(result.isOk)
        assertEquals(3, result.get()!!.items.single().activeSessionCount)
        assertEquals(1, result.get()!!.total)
    }
}