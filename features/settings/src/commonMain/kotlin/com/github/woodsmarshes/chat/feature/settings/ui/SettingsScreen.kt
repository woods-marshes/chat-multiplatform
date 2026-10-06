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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.LocalAccountAffordance
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.components.search.AdaptiveSearchBar
import com.github.woodsmarshes.chat.core.ui.components.search.rememberFreshSearchBarState
import com.github.woodsmarshes.chat.core.ui.components.state.EmptyContent
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val searchBarState = rememberFreshSearchBarState()
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    val themeSubtitle = when (uiState.themeBrand) {
        ThemeBrand.MIUIX, ThemeBrand.DEFAULT -> strings.themeMiuix
        ThemeBrand.MATERIAL3 -> strings.themeMaterial3
        else -> strings.themeMiuix
    }
    val darkModeSubtitle = when (uiState.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> strings.followSystem
        DarkThemeConfig.LIGHT -> strings.lightMode
        DarkThemeConfig.DARK -> strings.darkModeLabel
    }

    Scaffold(
        topBar = {
            AdaptiveSearchBar(
                onQueryChange = { searchQuery = it },
                onSearchQuery = { searchQuery = it },
                state = searchBarState,
                placeholder = "${strings.searchTitle}${strings.settingsTitle}...",
                trailingAffordance = LocalAccountAffordance.current,
                searchViewContent = {
                    val q = searchQuery.trim().lowercase()
                    if (q.isEmpty()) {
                        EmptyContent(
                            message = strings.searchPrompt,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        val matchTheme = strings.themeBrand.lowercase().contains(q) ||
                            themeSubtitle.lowercase().contains(q) ||
                            strings.sectionAppearance.lowercase().contains(q)
                        val matchDark = strings.darkMode.lowercase().contains(q) ||
                            darkModeSubtitle.lowercase().contains(q) ||
                            strings.sectionAppearance.lowercase().contains(q)
                        val matchSound = strings.notificationSound.lowercase().contains(q) ||
                            strings.sectionNotifications.lowercase().contains(q)
                        val matchOnline = strings.showOnlineStatus.lowercase().contains(q) ||
                            strings.sectionNotifications.lowercase().contains(q)
                        val matchAllowSearch = strings.allowSearch.lowercase().contains(q) ||
                            strings.sectionNotifications.lowercase().contains(q)
                        val matchVersion = strings.version.lowercase().contains(q) ||
                            "v1.0.0".contains(q) ||
                            strings.sectionAbout.lowercase().contains(q)
                        val matchLogout = strings.logout.lowercase().contains(q) ||
                            strings.sectionAccount.lowercase().contains(q)

                        val anyMatch = matchTheme || matchDark || matchSound ||
                            matchOnline || matchAllowSearch || matchVersion || matchLogout

                        if (!anyMatch) {
                            EmptyContent(
                                message = strings.searchNoResults,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                if (matchTheme) {
                                    SettingsItem(
                                        icon = Icons.Default.Palette,
                                        title = strings.themeBrand,
                                        subtitle = themeSubtitle,
                                        onClick = {
                                            viewModel.setThemeBrand(
                                                if (uiState.themeBrand == ThemeBrand.MATERIAL3) ThemeBrand.MIUIX
                                                else ThemeBrand.MATERIAL3
                                            )
                                        },
                                    )
                                }
                                if (matchDark) {
                                    SettingsItem(
                                        icon = Icons.Default.DarkMode,
                                        title = strings.darkMode,
                                        subtitle = darkModeSubtitle,
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
                                }
                                if (matchSound) {
                                    SettingsItemWithSwitch(
                                        icon = Icons.Default.Notifications,
                                        title = strings.notificationSound,
                                        checked = uiState.notificationSound,
                                        onCheckedChange = viewModel::setNotificationSound,
                                    )
                                }
                                if (matchOnline) {
                                    SettingsItemWithSwitch(
                                        icon = Icons.Default.Visibility,
                                        title = strings.showOnlineStatus,
                                        checked = uiState.showOnlineStatus,
                                        onCheckedChange = viewModel::setShowOnlineStatus,
                                    )
                                }
                                if (matchAllowSearch) {
                                    SettingsItemWithSwitch(
                                        icon = Icons.Default.Search,
                                        title = strings.allowSearch,
                                        checked = uiState.allowSearch,
                                        onCheckedChange = viewModel::setAllowSearch,
                                    )
                                }
                                if (matchVersion) {
                                    SettingsItem(
                                        icon = Icons.Default.Info,
                                        title = strings.version,
                                        subtitle = "v1.0.0",
                                        onClick = null,
                                    )
                                }
                                if (matchLogout) {
                                    SettingsItem(
                                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                                        title = strings.logout,
                                        danger = true,
                                        onClick = {
                                            scope.launch { searchBarState.animateToCollapsed() }
                                            onLogout()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
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
                // Hoisted: the session owner (SessionManager) sequences socket
                // disconnect, auth teardown and the per-user database close.
                onClick = onLogout,
            )
        }
    }
}
