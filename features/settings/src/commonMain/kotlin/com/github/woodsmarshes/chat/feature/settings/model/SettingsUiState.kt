package com.github.woodsmarshes.chat.feature.settings.model

import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import kotlinx.serialization.Serializable

@Serializable
enum class SettingsCategory {
    PROFILE,
    APPEARANCE,
    NOTIFICATIONS_PRIVACY,
    ABOUT,
    LICENSES,
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val editDisplayName: String = "",
    val editBio: String = "",
    val isSavingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val profileMessage: String? = null,
    val profileError: String? = null,
    val themeBrand: ThemeBrand = ThemeBrand.MIUIX,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    val notificationSound: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val allowSearch: Boolean = true,
) {
    val hasProfileChanges: Boolean
        get() = editDisplayName.trim() != displayName.trim() || editBio.trim() != bio.trim()
}
