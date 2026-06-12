package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface UserSettingRepository {
    suspend fun initSettings(userId: Uuid): UserSetting?

    suspend fun getSettings(userId: Uuid): UserSetting?
    suspend fun updateSettings(
        userId: Uuid,
        allowSearch: Boolean? = null,
        allowStrangerChat: Boolean? = null,
        showOnlineStatus: Boolean? = null,
        profileVisibility: ProfileVisibility? = null,
        friendRequestPolicy: FriendRequestPolicy? = null,
        preferences: UserPreference? = null,
    ): Boolean
}

class UserSettingDataSourceImpl : UserSettingRepository {
    override suspend fun initSettings(userId: Uuid): UserSetting? = dbQuery {
        UserSettings.insert {
            it[this.userId] = userId
            it[updatedAt] = Clock.System.now()
        }
            .resultedValues
            ?.singleOrNull()
            ?.toUserSetting()
    }

    override suspend fun getSettings(userId: Uuid): UserSetting? = dbQuery {
        UserSettings.selectAll()
            .where { UserSettings.userId eq userId }
            .map { it.toUserSetting() }
            .singleOrNull()
    }

    override suspend fun updateSettings(
        userId: Uuid,
        allowSearch: Boolean?,
        allowStrangerChat: Boolean?,
        showOnlineStatus: Boolean?,
        profileVisibility: ProfileVisibility?,
        friendRequestPolicy: FriendRequestPolicy?,
        preferences: UserPreference?
    ): Boolean = dbQuery {
        UserSettings.update(where = {
            UserSettings.userId eq userId
        }) {
            allowSearch?.let { allowSearch ->  it[UserSettings.allowSearch] = allowSearch }
            allowStrangerChat?.let { allowStrangerChat -> it[UserSettings.allowStrangerChat] = allowStrangerChat }
            showOnlineStatus?.let { showOnlineStatus -> it[UserSettings.showOnlineStatus] = showOnlineStatus }
            profileVisibility?.let { profileVisibility -> it[UserSettings.profileVisibility] = profileVisibility }
            friendRequestPolicy?.let { friendRequestPolicy -> it[UserSettings.friendRequestPolicy] = friendRequestPolicy }
            preferences?.let { preferences -> it[UserSettings.preferences] = preferences }
            it[updatedAt] = Clock.System.now()
        } > 0
    }
}