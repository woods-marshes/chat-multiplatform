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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
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

@Composable
fun SettingsDetailScreen(
    category: SettingsCategory,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    isSubPage: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

@Composable
private fun ProfileEditContent(
    uiState: SettingsUiState,
    onDisplayNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onUploadAvatar: (ByteArray) -> Unit,
    onLogout: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
        onError = { _ -> },
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    runCatching { file.readBytes() }
                        .onSuccess { bytes -> onUploadAvatar(bytes) }
                }
            }
        },
    )

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(
                        name = uiState.editDisplayName.ifEmpty {
                            uiState.displayName.ifEmpty { uiState.username }
                        },
                        avatarUrl = uiState.avatarUrl,
                        size = 76.dp,
                        onClick = {
                            if (!uiState.isUploadingAvatar) {
                                imagePickerLauncher.launch()
                            }
                        },
                    )
                    Surface(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !uiState.isUploadingAvatar) {
                                imagePickerLauncher.launch()
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isUploadingAvatar) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = strings.changeAvatar,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.displayName.ifEmpty {
                            uiState.username.ifEmpty { strings.settingsCategoryProfile }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (uiState.username.isNotEmpty()) {
                        Text(
                            text = "@${uiState.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { imagePickerLauncher.launch() },
                        enabled = !uiState.isUploadingAvatar,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isUploadingAvatar) {
                                strings.uploadingAvatar
                            } else {
                                strings.changeAvatar
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.editDisplayName,
                    onValueChange = onDisplayNameChange,
                    label = { Text(strings.displayNameLabel) },
                    placeholder = { Text(strings.displayNamePlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.editBio,
                    onValueChange = onBioChange,
                    label = { Text(strings.bioLabel) },
                    placeholder = { Text(strings.bioPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onSaveProfile,
                    enabled = uiState.hasProfileChanges && !uiState.isSavingProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    if (uiState.isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.saving)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.saveChanges)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SectionHeader(
            title = strings.sectionAccount,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.username.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = strings.usernameLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "@${uiState.username}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (uiState.email.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = strings.emailLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = uiState.email,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (uiState.userId.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = strings.userIdLabel(uiState.userId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(uiState.userId))
                                scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = strings.copyIdCd,
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = strings.logout,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.logout,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.logoutDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(strings.logoutConfirmTitle) },
            text = { Text(strings.logoutConfirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(strings.logout)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun AppearanceSettingsContent(
    uiState: SettingsUiState,
    onThemeBrandChange: (ThemeBrand) -> Unit,
    onDarkThemeConfigChange: (DarkThemeConfig) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        SectionHeader(
            title = strings.themeBrand,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                RadioSettingRow(
                    icon = Icons.Default.AutoAwesome,
                    title = strings.themeMiuix,
                    subtitle = strings.themeMiuixDesc,
                    selected = uiState.themeBrand == ThemeBrand.MIUIX || uiState.themeBrand == ThemeBrand.DEFAULT,
                    onClick = { onThemeBrandChange(ThemeBrand.MIUIX) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                RadioSettingRow(
                    icon = Icons.Default.Palette,
                    title = strings.themeMaterial3,
                    subtitle = strings.themeMaterial3Desc,
                    selected = uiState.themeBrand == ThemeBrand.MATERIAL3,
                    onClick = { onThemeBrandChange(ThemeBrand.MATERIAL3) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(
            title = strings.darkMode,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                RadioSettingRow(
                    icon = Icons.Default.BrightnessAuto,
                    title = strings.followSystem,
                    subtitle = strings.followSystemDesc,
                    selected = uiState.darkThemeConfig == DarkThemeConfig.FOLLOW_SYSTEM,
                    onClick = { onDarkThemeConfigChange(DarkThemeConfig.FOLLOW_SYSTEM) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                RadioSettingRow(
                    icon = Icons.Default.LightMode,
                    title = strings.lightMode,
                    subtitle = strings.lightModeDesc,
                    selected = uiState.darkThemeConfig == DarkThemeConfig.LIGHT,
                    onClick = { onDarkThemeConfigChange(DarkThemeConfig.LIGHT) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                RadioSettingRow(
                    icon = Icons.Default.DarkMode,
                    title = strings.darkModeLabel,
                    subtitle = strings.darkModeDesc,
                    selected = uiState.darkThemeConfig == DarkThemeConfig.DARK,
                    onClick = { onDarkThemeConfigChange(DarkThemeConfig.DARK) },
                )
            }
        }
    }
}

@Composable
private fun RadioSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotificationsPrivacySettingsContent(
    uiState: SettingsUiState,
    onNotificationSoundChange: (Boolean) -> Unit,
    onShowOnlineStatusChange: (Boolean) -> Unit,
    onAllowSearchChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        SectionHeader(
            title = strings.sectionNotificationPrefs,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            SettingsItemWithSwitch(
                icon = Icons.Default.Notifications,
                title = strings.notificationSound,
                subtitle = strings.notificationSoundDesc,
                checked = uiState.notificationSound,
                onCheckedChange = onNotificationSoundChange,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(
            title = strings.sectionPrivacyPrefs,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsItemWithSwitch(
                    icon = Icons.Default.Visibility,
                    title = strings.showOnlineStatus,
                    subtitle = strings.showOnlineStatusDesc,
                    checked = uiState.showOnlineStatus,
                    onCheckedChange = onShowOnlineStatusChange,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingsItemWithSwitch(
                    icon = Icons.Default.Search,
                    title = strings.allowSearch,
                    subtitle = strings.allowSearchDesc,
                    checked = uiState.allowSearch,
                    onCheckedChange = onAllowSearchChange,
                )
            }
        }
    }
}

@Composable
private fun AboutSettingsContent(
    onOpenLicenses: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val strings = LocalStrings.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    val libraryCount = libraries?.libraries?.size ?: 0
    val fullVersionText = "v${AppBuildInfo.versionName} (${AppBuildInfo.versionCode} · ${AppBuildInfo.gitRevision})"

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App Hero Banner Card
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(72.dp),
                ) {
                    Image(
                        painter = painterResource(CoreUiRes.drawable.app_icon),
                        contentDescription = strings.appName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    clipboard.setText(AnnotatedString(fullVersionText))
                                    scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                                },
                        ) {
                            Text(
                                text = fullVersionText,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = strings.aboutAppDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Version & Build Environment Card
        SectionHeader(
            title = strings.aboutSectionVersion,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AboutInfoRow(
                    icon = Icons.Default.Info,
                    title = strings.aboutVersionNameLabel,
                    value = "v${AppBuildInfo.versionName}",
                    onClick = {
                        clipboard.setText(AnnotatedString(AppBuildInfo.versionName))
                        scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                    },
                    trailingIcon = Icons.Default.ContentCopy,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.Tag,
                    title = strings.aboutBuildVersionLabel,
                    value = "Build ${AppBuildInfo.versionCode} (${AppBuildInfo.gitRevision})",
                    onClick = {
                        clipboard.setText(AnnotatedString("${AppBuildInfo.versionCode} (${AppBuildInfo.gitRevision})"))
                        scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                    },
                    trailingIcon = Icons.Default.ContentCopy,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.Memory,
                    title = strings.aboutRuntimeLabel,
                    value = "Kotlin ${AppBuildInfo.kotlinVersion} · Compose Multiplatform ${AppBuildInfo.composeVersion}",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.Gavel,
                    title = strings.aboutLicenseLabel,
                    value = AppBuildInfo.licenseName,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Developer & Open Source Project Card
        SectionHeader(
            title = strings.aboutSectionProject,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AboutInfoRow(
                    icon = Icons.Default.Person,
                    title = strings.aboutDeveloperLabel,
                    value = "@${AppBuildInfo.developerName}",
                    onClick = {
                        runCatching { uriHandler.openUri(AppBuildInfo.developerUrl) }
                    },
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.Code,
                    title = strings.aboutGithubRepo,
                    value = AppBuildInfo.githubUrl.removePrefix("https://"),
                    onClick = {
                        runCatching { uriHandler.openUri(AppBuildInfo.githubUrl) }
                    },
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.BugReport,
                    title = strings.aboutIssueTracker,
                    value = AppBuildInfo.issuesUrl.removePrefix("https://"),
                    onClick = {
                        runCatching { uriHandler.openUri(AppBuildInfo.issuesUrl) }
                    },
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                AboutInfoRow(
                    icon = Icons.Default.Description,
                    title = strings.openSourceLicenses,
                    value = if (libraryCount > 0) {
                        strings.openSourceLicensesCountFmt(libraryCount.toString())
                    } else {
                        strings.openSourceLicensesDesc
                    },
                    onClick = onOpenLicenses,
                    trailingIcon = Icons.Default.ChevronRight,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OpenSourceLicensesContent() {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }

    LibrariesContainer(
        libraries = libraries,
        modifier = Modifier.fillMaxSize(),
        variant = LibrariesVariant.Refined,
        detailMode = LibraryDetailMode.Dialog,
        contentPadding = PaddingValues(bottom = 24.dp),
    )
}
