package com.github.woodsmarshes.chat.app

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.woodsmarshes.chat.app.session.SessionManager
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
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
    val sessionManager = koinInject<SessionManager>()
    val userSettingDataSource = koinInject<UserSettingDataSource>()

    val userPreference by userSettingDataSource.preference.collectAsStateWithLifecycle(null)
    val themeConfig = remember(userPreference) {
        ThemeConfig(
            themeBrand = userPreference?.themeBrand ?: ThemeBrand.MIUIX,
            darkThemeConfig = userPreference?.darkThemeConfig ?: DarkThemeConfig.FOLLOW_SYSTEM,
        )
    }

    val snackbarState = rememberAppSnackbarState()
    ProvideLyricistStrings {
        CompositionLocalProvider(LocalSnackbarState provides snackbarState) {
            AppTheme(themeConfig = themeConfig) {
                MainApp(sessionManager = sessionManager, snackbarState = snackbarState)
            }
        }
    }
}
