package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.PrivacySetting
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.UserError
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface UserRepository {
    fun getMeFlow(): Flow<User?>

    suspend fun syncMe(): Result<User, UserError>

    suspend fun updateMyProfile(
        displayName: String? = null,
        avatarUrl: String? = null,
        bio: String? = null
    ): Result<User, UserError>

    fun getGlobalSettingsFlow(): Flow<UserSetting?>

    suspend fun syncGlobalSettings(): Result<UserSetting, UserError>

    suspend fun updateGlobalSettings(
        privacy: PrivacySetting? = null,
        preferences: UserPreference? = null
    ): Result<Boolean, UserError>

    suspend fun fetchUserDetail(userId: Uuid): Result<User, UserError>
}