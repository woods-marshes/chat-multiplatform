package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.AppAlertDialog
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.AppProgressIndicator
import com.github.woodsmarshes.chat.core.ui.components.AppTextField
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.motion.sharedSettingsProfileBounds
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.feature.settings.AppBuildInfo
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import com.github.woodsmarshes.chat.features.settings.resources.Res
import com.github.woodsmarshes.chat.resources.Res as CoreUiRes
import com.github.woodsmarshes.chat.resources.app_icon
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesVariant
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.github.woodsmarshes.chat.feature.settings.ui.detail.ProfileEditContent
import com.github.woodsmarshes.chat.feature.settings.ui.detail.AppearanceSettingsContent
import com.github.woodsmarshes.chat.feature.settings.ui.detail.RadioSettingRow
import com.github.woodsmarshes.chat.feature.settings.ui.detail.NotificationsPrivacySettingsContent
import com.github.woodsmarshes.chat.feature.settings.ui.detail.AboutSettingsContent
import com.github.woodsmarshes.chat.feature.settings.ui.detail.OpenSourceLicensesContent

@Composable
fun SettingsDetailScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    isSubPage: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val inMultiPane = isInListDetailScene()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.profileMessage, uiState.profileError) {
        val message = uiState.profileMessage ?: uiState.profileError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearProfileFeedback()
        }
    }

    val title = when (category) {
        SettingsCategory.PROFILE -> strings.editProfile
        SettingsCategory.APPEARANCE -> strings.settingsCategoryAppearance
        SettingsCategory.NOTIFICATIONS_PRIVACY -> strings.settingsCategoryNotifications
        SettingsCategory.ABOUT -> strings.settingsCategoryAbout
        SettingsCategory.LICENSES -> strings.openSourceLicenses
    }

    // Sub-pages (LICENSES under ABOUT, or PROFILE edit pushed from ProfileScreen)
    // always show a back button even in multi-pane mode so the user can pop back
    // to the parent detail/extra pane.
    val showBack = !inMultiPane || category == SettingsCategory.LICENSES || isSubPage

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = title,
                showBackButton = showBack,
                onBackClick = onBack,
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (category) {
                SettingsCategory.PROFILE -> ProfileEditContent(
                    uiState = uiState,
                    onDisplayNameChange = viewModel::onEditDisplayNameChange,
                    onBioChange = viewModel::onEditBioChange,
                    onSaveProfile = viewModel::saveProfile,
                    onUploadAvatar = viewModel::uploadAvatar,
                    onLogout = onLogout,
                    snackbarHostState = snackbarHostState,
                )
                SettingsCategory.APPEARANCE -> AppearanceSettingsContent(
                    uiState = uiState,
                    onThemeBrandChange = viewModel::setThemeBrand,
                    onDarkThemeConfigChange = viewModel::setDarkThemeConfig,
                )
                SettingsCategory.NOTIFICATIONS_PRIVACY -> NotificationsPrivacySettingsContent(
                    uiState = uiState,
                    onNotificationSoundChange = viewModel::setNotificationSound,
                    onShowOnlineStatusChange = viewModel::setShowOnlineStatus,
                    onAllowSearchChange = viewModel::setAllowSearch,
                    onAllowStrangerChatChange = viewModel::setAllowStrangerChat,
                    onFriendRequestPolicyChange = viewModel::setFriendRequestPolicy,
                    onProfileVisibilityChange = viewModel::setProfileVisibility,
                )
                SettingsCategory.ABOUT -> AboutSettingsContent(
                    onOpenLicenses = onOpenLicenses,
                    snackbarHostState = snackbarHostState,
                )
                SettingsCategory.LICENSES -> OpenSourceLicensesContent()
            }
        }
    }
}
