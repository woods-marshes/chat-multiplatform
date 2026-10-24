package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.UserRole.ADMIN
import com.github.woodsmarshes.chat.core.model.UserRole.MEMBER
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val userSettingRepository = mockk<UserSettingRepository>()
    private val service = UserService(userRepository, userSettingRepository)

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
    fun changeUserRoleSucceedsForAdminRequester() = runBlocking {
        val target = Uuid.random()
        coEvery { userRepository.getUserById(any()) } returns user(ADMIN)
        coEvery { userRepository.changeUserRole(target, MEMBER) } returns true

        val result = service.changeUserRole(Uuid.random(), target, MEMBER)

        assertTrue(result.isOk)
        coVerify(exactly = 1) { userRepository.changeUserRole(target, MEMBER) }
    }

    @Test
    fun changeUserRoleIsDeniedForNonAdminRequester() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(MEMBER)

        val result = service.changeUserRole(Uuid.random(), Uuid.random(), ADMIN)

        assertEquals(UserError.PermissionDenied, result.getError())
        coVerify(exactly = 0) { userRepository.changeUserRole(any(), any()) }
    }

    @Test
    fun changeUserRoleFailsWhenRequesterIsUnknown() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns null

        val result = service.changeUserRole(Uuid.random(), Uuid.random(), MEMBER)

        assertEquals(UserError.NotFound, result.getError())
        coVerify(exactly = 0) { userRepository.changeUserRole(any(), any()) }
    }

    @Test
    fun changeUserRoleMapsRepositoryFailureToUpdateFailed() = runBlocking {
        coEvery { userRepository.getUserById(any()) } returns user(ADMIN)
        coEvery { userRepository.changeUserRole(any(), any()) } returns false

        val result = service.changeUserRole(Uuid.random(), Uuid.random(), MEMBER)

        assertEquals(UserError.UpdateFailed, result.getError())
    }

    @Test
    fun updateProfileReturnsThePersistedUser() = runBlocking {
        val updated = user(MEMBER)
        coEvery {
            userRepository.updateUser(userId = any(), displayName = any(), avatarUrl = any(), bio = any())
        } returns updated

        val result = service.updateProfile(updated.id, UpdateProfileRequest(displayName = "New Name"))

        assertTrue(result.isOk)
        assertEquals(updated, result.get())
    }

    @Test
    fun updateProfileMapsMissingRowToUpdateFailed() = runBlocking {
        coEvery {
            userRepository.updateUser(userId = any(), displayName = any(), avatarUrl = any(), bio = any())
        } returns null

        val result = service.updateProfile(Uuid.random(), UpdateProfileRequest())

        assertEquals(UserError.UpdateFailed, result.getError())
    }

    @Test
    fun updateUserSettingsMapsRepositoryFailureToUpdateFailed() = runBlocking {
        coEvery { userSettingRepository.updateSettings(any(), any(), any(), any(), any(), any(), any()) } returns false

        val result = service.updateUserSettings(Uuid.random(), UpdateUserSettingsRequest())

        assertEquals(UserError.UpdateFailed, result.getError())
    }

    @Test
    fun getUserSettingsMapsMissingRowToNotFound() = runBlocking {
        coEvery { userSettingRepository.getSettings(any()) } returns null

        val result = service.getUserSettings(Uuid.random())

        assertEquals(UserError.NotFound, result.getError())
    }
}
