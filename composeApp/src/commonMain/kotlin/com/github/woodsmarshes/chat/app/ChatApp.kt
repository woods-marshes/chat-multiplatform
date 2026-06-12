package com.github.woodsmarshes.chat.app

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.data.repository.AuthRepository
import com.github.woodsmarshes.chat.core.network.api.websocket.RealtimeApi
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarState
import com.github.woodsmarshes.chat.core.ui.components.feedback.LocalSnackbarState
import com.github.woodsmarshes.chat.core.ui.components.feedback.rememberAppSnackbarState
import com.github.woodsmarshes.chat.core.ui.resources.ProvideLyricistStrings
import com.github.woodsmarshes.chat.core.ui.theme.AppTheme
import com.github.woodsmarshes.chat.core.ui.theme.ThemeConfig
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChatApp() {
    val authRepo = koinInject<AuthRepository>()
    val realtimeRepo = koinInject<RealtimeApi>()
    val userSettingDataSource = koinInject<UserSettingDataSource>()

    val userPreference by userSettingDataSource.preference.collectAsState(null)
    val themeConfig = remember(userPreference) {
        ThemeConfig(
            themeBrand = userPreference?.themeBrand ?: ThemeBrand.MIUIX,
            darkThemeConfig = userPreference?.darkThemeConfig ?: DarkThemeConfig.FOLLOW_SYSTEM,
        )
    }

    val appState = rememberChatAppState(
        authRepository = authRepo,
        realtimeApi = realtimeRepo,
    )

    val snackbarState = rememberAppSnackbarState()
    ProvideLyricistStrings {
        CompositionLocalProvider(LocalSnackbarState provides snackbarState) {
            AppTheme(themeConfig = themeConfig) {
                MainApp(appState = appState, snackbarState = snackbarState)
            }
        }
    }
}
