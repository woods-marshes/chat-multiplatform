package com.github.woodsmarshes.chat.feature.settings.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
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
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.ButtonSize
import com.github.woodsmarshes.chat.core.ui.components.ButtonStyle
import com.github.woodsmarshes.chat.core.ui.components.ChatAppButton
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.feedback.AppSnackbarHost
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.components.item.SectionHeader
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItem
import com.github.woodsmarshes.chat.core.ui.components.item.SettingsItemWithSwitch
import com.github.woodsmarshes.chat.core.ui.components.motion.sharedSettingsProfileBounds
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.feature.settings.AppBuildInfo
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import com.github.woodsmarshes.chat.features.settings.resources.Res
import com.github.woodsmarshes.chat.resources.app_icon
import com.github.woodsmarshes.chat.resources.Res as CoreUiRes
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesVariant
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProfileEditContent(
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
        onError = { _ ->
            scope.launch { snackbarHostState.showSnackbar(strings.avatarUploadFailed) }
        },
        onResult = { file ->
            if (file != null) {
                scope.launch {
                    runCatching { file.readBytes() }
                        .onSuccess { bytes -> onUploadAvatar(bytes) }
                        .onFailure { snackbarHostState.showSnackbar(strings.avatarUploadFailed) }
                }
            }
        },
    )

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Keep the edit badge and upload indicator attached throughout the shared transition.
        Box(
            modifier = Modifier.sharedSettingsProfileBounds("avatar"),
            contentAlignment = Alignment.BottomEnd,
        ) {
            UserAvatar(
                name = uiState.editDisplayName.ifEmpty {
                    uiState.displayName.ifEmpty { uiState.username }
                },
                avatarUrl = uiState.avatarUrl,
                size = 96.dp,
                onClick = {
                    if (!uiState.isUploadingAvatar) {
                        imagePickerLauncher.launch()
                    }
                },
            )
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !uiState.isUploadingAvatar) {
                        imagePickerLauncher.launch()
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (uiState.isUploadingAvatar) {
                        AppProgressIndicator(
                            size = 15.dp,
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = strings.changeAvatar,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = uiState.displayName.ifEmpty {
                uiState.username.ifEmpty { strings.settingsCategoryProfile }
            },
            modifier = Modifier.sharedSettingsProfileBounds("title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (uiState.username.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "@${uiState.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Editable Profile Form Card
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = strings.displayNameLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                AppTextField(
                    value = uiState.editDisplayName,
                    onValueChange = onDisplayNameChange,
                    label = strings.displayNamePlaceholder,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.bioLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                AppTextField(
                    value = uiState.editBio,
                    onValueChange = onBioChange,
                    label = strings.bioPlaceholder,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (uiState.hasProfileChanges || uiState.isSavingProfile) {
                    Spacer(modifier = Modifier.height(18.dp))
                    ChatAppButton(
                        onClick = onSaveProfile,
                        label = if (uiState.isSavingProfile) strings.saving else strings.saveChanges,
                        style = ButtonStyle.PRIMARY,
                        size = ButtonSize.MD,
                        enabled = uiState.hasProfileChanges && !uiState.isSavingProfile,
                        isLoading = uiState.isSavingProfile,
                        fullWidth = true,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Account Info Section
        SectionHeader(
            title = strings.sectionAccount,
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.username.isNotEmpty()) {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = strings.usernameLabel,
                        subtitle = "@${uiState.username}",
                    )
                }
                if (uiState.email.isNotEmpty()) {
                    if (uiState.username.isNotEmpty()) {
                        AppHorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = strings.emailLabel,
                        subtitle = uiState.email,
                    )
                }
                if (uiState.userId.isNotEmpty()) {
                    if (uiState.username.isNotEmpty() || uiState.email.isNotEmpty()) {
                        AppHorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    SettingsItem(
                        icon = Icons.Default.Tag,
                        title = "UID",
                        subtitle = uiState.userId,
                        onClick = {
                            clipboard.setText(AnnotatedString(uiState.userId))
                            scope.launch { snackbarHostState.showSnackbar(strings.copied) }
                        },
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = strings.copyIdCd,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Standalone Logout Card
        ChatAppCard(modifier = Modifier.fillMaxWidth()) {
            SettingsItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = strings.logout,
                subtitle = strings.logoutDesc,
                danger = true,
                onClick = { showLogoutDialog = true },
            )
        }
    }

    AppAlertDialog(
        show = showLogoutDialog,
        onDismissRequest = { showLogoutDialog = false },
        title = strings.logoutConfirmTitle,
        summary = strings.logoutConfirmMessage,
        confirmLabel = strings.logout,
        onConfirm = {
            showLogoutDialog = false
            onLogout()
        },
        confirmDanger = true,
        dismissLabel = strings.cancel,
        onDismiss = { showLogoutDialog = false },
    )
}
