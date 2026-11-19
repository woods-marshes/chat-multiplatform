package com.github.woodsmarshes.chat.app

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.app.session.SessionManager
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.network.ktor.ServerEndpointProvider
import com.github.woodsmarshes.chat.core.ui.components.avatar.LocalServerBaseUrl
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
    val sessionManager = koinInject<SessionManager>()
    val userSettingDataSource = koinInject<UserSettingDataSource>()
    val serverEndpointProvider = koinInject<ServerEndpointProvider>()

    val userPreference by userSettingDataSource.preference.collectAsStateWithLifecycle(null)
    val themeConfig = remember(userPreference) {
        ThemeConfig(
            themeBrand = userPreference?.themeBrand ?: ThemeBrand.MIUIX,
            darkThemeConfig = userPreference?.darkThemeConfig ?: DarkThemeConfig.FOLLOW_SYSTEM,
        )
    }

    // State-backed so a logged-out server switch recomposes media readers
    // (LocalServerBaseUrl is a compositionLocalOf, not static).
    val serverConfig by serverEndpointProvider.config.collectAsStateWithLifecycle()

    val snackbarState = rememberAppSnackbarState()
    ProvideLyricistStrings {
        CompositionLocalProvider(
            LocalSnackbarState provides snackbarState,
            LocalServerBaseUrl provides serverConfig.baseUrl,
        ) {
            AppTheme(themeConfig = themeConfig) {
                MainApp(sessionManager = sessionManager, snackbarState = snackbarState)
            }
        }
    }
}