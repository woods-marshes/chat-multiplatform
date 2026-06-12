package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ChatTopAppBar(
                title = LocalStrings.current.settingsTitle,
                showBackButton = true,
                onBackClick = onBack,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(LocalStrings.current.sectionAppearance)
            SettingsItem(
                icon = Icons.Default.Palette,
                title = LocalStrings.current.themeBrand,
                subtitle = when (uiState.themeBrand) {
                    ThemeBrand.MIUIX, ThemeBrand.DEFAULT -> LocalStrings.current.themeMiuix
                    ThemeBrand.MATERIAL3 -> LocalStrings.current.themeMaterial3
                    else -> LocalStrings.current.themeMiuix
                },
                onClick = {
                    viewModel.setThemeBrand(
                        if (uiState.themeBrand == ThemeBrand.MATERIAL3) ThemeBrand.MIUIX
                        else ThemeBrand.MATERIAL3
                    )
                },
            )
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = LocalStrings.current.darkMode,
                subtitle = when (uiState.darkThemeConfig) {
                    DarkThemeConfig.FOLLOW_SYSTEM -> LocalStrings.current.followSystem
                    DarkThemeConfig.LIGHT -> LocalStrings.current.lightMode
                    DarkThemeConfig.DARK -> LocalStrings.current.darkModeLabel
                },
                onClick = {
                    viewModel.setDarkThemeConfig(
                        when (uiState.darkThemeConfig) {
                            DarkThemeConfig.FOLLOW_SYSTEM -> DarkThemeConfig.DARK
                            DarkThemeConfig.DARK -> DarkThemeConfig.LIGHT
                            DarkThemeConfig.LIGHT -> DarkThemeConfig.FOLLOW_SYSTEM
                        }
                    )
                },
            )

            SectionHeader(LocalStrings.current.sectionNotifications)
            SettingsItemWithSwitch(
                icon = Icons.Default.Notifications,
                title = LocalStrings.current.notificationSound,
                checked = uiState.notificationSound,
                onCheckedChange = viewModel::setNotificationSound,
            )
            SettingsItemWithSwitch(
                icon = Icons.Default.Visibility,
                title = LocalStrings.current.showOnlineStatus,
                checked = uiState.showOnlineStatus,
                onCheckedChange = viewModel::setShowOnlineStatus,
            )
            SettingsItemWithSwitch(
                icon = Icons.Default.Search,
                title = LocalStrings.current.allowSearch,
                checked = uiState.allowSearch,
                onCheckedChange = viewModel::setAllowSearch,
            )

            SectionHeader(LocalStrings.current.sectionAbout)
            SettingsItem(
                icon = Icons.Default.Info,
                title = LocalStrings.current.version,
                subtitle = "v1.0.0",
                onClick = null,
            )

            SectionHeader(LocalStrings.current.sectionAccount)
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = LocalStrings.current.logout,
                danger = true,
                onClick = { viewModel.logout() },
            )
        }
    }
}
