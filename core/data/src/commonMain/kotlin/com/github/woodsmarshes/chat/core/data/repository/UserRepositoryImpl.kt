package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.UserError
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateProfileRequest
import com.github.woodsmarshes.chat.core.network.dto.user.UpdateUserSettingsRequest
import com.github.woodsmarshes.chat.core.network.ktor.HttpEventBus
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import kotlin.uuid.Uuid

class UserRepositoryImpl(
    private val userSettingDataSource: UserSettingDataSource,
    private val userDao: UserDao,
    private val userApi: UserApi,
) : UserRepository {
    override fun getMeFlow(): Flow<User?> {
        return userSettingDataSource.user
    }

    override suspend fun syncMe(): Result<User, UserError> = coroutineBinding {
        bindApi(UserError::Unknown) {
            userApi.getMe()
        }.also { user ->
            userSettingDataSource.setUser(user)
            userDao.insertUser(user.toUserEntity())
        }
    }

    override suspend fun updateMyProfile(
        displayName: String?,
        avatarUrl: String?,
        bio: String?
    ): Result<User, UserError> = coroutineBinding {
        bindApi(UserError::Unknown) {
            userApi.updateProfile(UpdateProfileRequest(displayName, avatarUrl, bio))
        }.also { updatedUser ->
            userSettingDataSource.setUser(updatedUser)
            userDao.insertUser(updatedUser.toUserEntity())
        }
    }

    override fun getGlobalSettingsFlow(): Flow<UserSetting?> {
        // Combine preference and privacySetting flows to create UserSetting
        return combine(
            userSettingDataSource.user,
            userSettingDataSource.preference,
            userSettingDataSource.privacySetting,
            userSettingDataSource.updatedAt
        ) { user, preference, privacy, updateAt ->
            if (user!= null && preference != null && privacy != null && updateAt != null) {
                UserSetting(
                    userId = user.id,
                    privacy = privacy,
                    preferences = preference,
                    updatedAt = updateAt
                )
            } else null
        }
    }

    override suspend fun syncGlobalSettings(): Result<UserSetting, UserError> = coroutineBinding {
        bindApi(UserError::Unknown) {
            userApi.getSettings()
        }.also { settings ->
            userSettingDataSource.setPreference(settings.preferences)
            userSettingDataSource.setPrivacySetting(settings.privacy)
            userSettingDataSource.setUpdatedAt(settings.updatedAt)
        }
    }

    override suspend fun updateGlobalSettings(
        privacy: PrivacySetting?,
        preferences: UserPreference?
    ): Result<Boolean, UserError> = coroutineBinding {
        val success = bindApi(UserError::Unknown) {
            userApi.updateSettings(UpdateUserSettingsRequest(privacy, preferences))
        }

        if (success) {
            // Update local cache if API call succeeded
            preferences?.let { userSettingDataSource.setPreference(it) }
            privacy?.let { userSettingDataSource.setPrivacySetting(it) }
            userSettingDataSource.setUpdatedAt(Clock.System.now())
        }

        success
    }

    override suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError> = coroutineBinding {
        bindApi(UserError::Unknown) {
            userApi.getUserById(userId)
        }.also { user ->
            userDao.insertUser(user.toUserEntity())
        }
    }

}