package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.UserRole.*
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val userSettingRepository: UserSettingRepository,

) {
    suspend fun checkExists(email: String?, username: String?): Result<Boolean, UserError> = coroutineBinding {
        userRepository.checkExists(email, username)
    }

    suspend fun searchUsers(keyword: String): Result<List<User>, UserError> = coroutineBinding {
        userRepository.searchUsers(keyword)
    }

    suspend fun getUserById(userId: Uuid): Result<User, UserError> = coroutineBinding {
        userRepository.getUserById(userId) ?: Err(UserError.NotFound).bind()
    }

    suspend fun getMyProfile(userId: Uuid): Result<User, UserError> = coroutineBinding {
        userRepository.getUserById(userId) ?: Err(UserError.NotFound).bind()
    }

    suspend fun updateProfile(userId: Uuid, req: UpdateProfileRequest): Result<User, UserError> = coroutineBinding {
        userRepository.updateUser(
            userId = userId,
            displayName = req.displayName,
            avatarUrl = req.avatarUrl,
            bio = req.bio
        ) ?: Err(UserError.UpdateFailed).bind()
    }
    suspend fun getUserProfileForViewer(targetUserId: Uuid, viewerId: Uuid): Result<User, UserError> = coroutineBinding {
        userRepository.getUserProfileForViewer(targetUserId, viewerId)
            ?: Err(UserError.NotFound).bind()
    }
    suspend fun changeUserRole(adminId: Uuid, id: Uuid, newRole: UserRole): Result<Unit, UserError> = coroutineBinding {
        val admin = userRepository.getUserById(adminId) ?: Err(UserError.NotFound).bind()

        if (admin.role != ADMIN) {
            Err(UserError.PermissionDenied).bind()
        }

        val success = userRepository.changeUserRole(id, newRole)
        if (!success) {
            Err(UserError.UpdateFailed).bind()
        }
    }

    suspend fun getUserSettings(userId: Uuid): Result<UserSetting, UserError> = coroutineBinding {
        userSettingRepository.getSettings(userId) ?: Err(UserError.NotFound).bind()
    }
    suspend fun updateUserSettings(userId: Uuid, req: UpdateUserSettingsRequest): Result<Unit, UserError> = coroutineBinding {
        val success = userSettingRepository.updateSettings(
            userId = userId,
            allowSearch = req.privacy?.allowSearch,
            allowStrangerChat = req.privacy?.allowStrangerChat,
            showOnlineStatus = req.privacy?.showOnlineStatus,
            profileVisibility = req.privacy?.profileVisibility,
            friendRequestPolicy = req.privacy?.friendRequestPolicy,
            preferences = req.preferences
        )

        if (!success) {
            Err(UserError.UpdateFailed).bind()
        }
    }
}