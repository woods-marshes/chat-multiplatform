package com.github.woodsmarshes.chat.feature.settings.model

import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand

data class SettingsUiState(
    val isLoading: Boolean = false,
    val themeBrand: ThemeBrand = ThemeBrand.MIUIX,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
    val notificationSound: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val allowSearch: Boolean = true,
)
