package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand

data class ThemeConfig(
    val themeBrand: ThemeBrand = ThemeBrand.MIUIX,
    val darkThemeConfig: DarkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
)

val LocalThemeConfig = compositionLocalOf { ThemeConfig() }
