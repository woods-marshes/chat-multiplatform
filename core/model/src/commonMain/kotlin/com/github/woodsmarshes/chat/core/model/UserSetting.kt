package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class UserSetting(
    @ProtoNumber(1) val userId: Uuid,
    @ProtoNumber(2) val privacy: PrivacySetting,
    @ProtoNumber(3) val preferences: UserPreference,
    @ProtoNumber(4) val updatedAt: Instant,
)

@Serializable
data class PrivacySetting(
    @ProtoNumber(1) val allowSearch: Boolean = true,
    @ProtoNumber(2) val friendRequestPolicy: FriendRequestPolicy = FriendRequestPolicy.NEED_APPROVAL,
    @ProtoNumber(3) val showOnlineStatus: Boolean = true,
    @ProtoNumber(4) val profileVisibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    @ProtoNumber(5) val allowStrangerChat: Boolean = true,
)

@Serializable
data class UserPreference(
    @ProtoNumber(1) val themeBrand: ThemeBrand = ThemeBrand.DEFAULT,
    @ProtoNumber(2) val notificationSound: Boolean = true,
    @ProtoNumber(3) val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    @ProtoNumber(4) val useDynamicColor: Boolean = true,
    @ProtoNumber(5) val shouldHideOnboarding: Boolean = true,
)

enum class ProfileVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE
}

enum class FriendRequestPolicy {
    NEED_APPROVAL,
    AUTO_ACCEPT,
    DENY_ANY
}

enum class ThemeBrand {
    DEFAULT,
    MIUIX,
    MATERIAL3,
    ANDROID,
    IOS,
    DESKTOP
}

enum class DarkThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}