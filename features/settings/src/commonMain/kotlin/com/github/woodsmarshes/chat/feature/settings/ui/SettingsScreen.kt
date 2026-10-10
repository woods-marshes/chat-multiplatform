package com.github.woodsmarshes.chat.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.AppHorizontalDivider
import com.github.woodsmarshes.chat.core.ui.components.ChatAppCard
import com.github.woodsmarshes.chat.core.ui.components.ChatTopAppBar
import com.github.woodsmarshes.chat.core.ui.components.avatar.UserAvatar
import com.github.woodsmarshes.chat.core.ui.components.isInListDetailScene
import com.github.woodsmarshes.chat.core.ui.resources.LocalStrings
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.model.SettingsUiState
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (SettingsCategory) -> Unit = {},
    selectedCategory: SettingsCategory? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val inMultiPane = isInListDetailScene()
    val normalizedSelected = when (selectedCategory) {
        SettingsCategory.LICENSES -> SettingsCategory.ABOUT
        else -> selectedCategory
    }
    val activeCategory = if (inMultiPane) (normalizedSelected ?: SettingsCategory.PROFILE) else null

    val themeSubtitle = buildString {
        append(
            when (uiState.themeBrand) {
                ThemeBrand.MATERIAL3 -> strings.themeMaterial3
                else -> strings.themeMiuix
            }
        )
        append(" · ")
        append(
            when (uiState.darkThemeConfig) {
                DarkThemeConfig.FOLLOW_SYSTEM -> strings.followSystem
                DarkThemeConfig.LIGHT -> strings.lightMode
                DarkThemeConfig.DARK -> strings.darkModeLabel
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                title = strings.settingsTitle,
                showBackButton = false,
                showAccountAffordance = true,
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
            PersonalProfileCard(
                uiState = uiState,
                selected = activeCategory == SettingsCategory.PROFILE,
                onClick = { onCategoryClick(SettingsCategory.PROFILE) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            )

            AppHorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            val categoryItems = @Composable {
                SettingsCategoryItem(
                    icon = Icons.Default.Palette,
                    title = strings.settingsCategoryAppearance,
                    subtitle = themeSubtitle,
                    selected = activeCategory == SettingsCategory.APPEARANCE,
                    showChevron = !inMultiPane,
                    onClick = { onCategoryClick(SettingsCategory.APPEARANCE) },
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Notifications,
                    title = strings.settingsCategoryNotifications,
                    subtitle = strings.settingsCategoryNotificationsDesc,
                    selected = activeCategory == SettingsCategory.NOTIFICATIONS_PRIVACY,
                    showChevron = !inMultiPane,
                    onClick = { onCategoryClick(SettingsCategory.NOTIFICATIONS_PRIVACY) },
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Info,
                    title = strings.settingsCategoryAbout,
                    subtitle = strings.settingsCategoryAboutDesc,
                    selected = activeCategory == SettingsCategory.ABOUT,
                    showChevron = !inMultiPane,
                    onClick = { onCategoryClick(SettingsCategory.ABOUT) },
                )
            }

            if (isMiuixTheme() && !inMultiPane) {
                ChatAppCard(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Column {
                        categoryItems()
                    }
                }
            } else {
                categoryItems()
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PersonalProfileCard(
    uiState: SettingsUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val displayTitle = uiState.displayName.ifEmpty {
        uiState.username.ifEmpty { strings.settingsCategoryProfile }
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryTextColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                name = displayTitle,
                avatarUrl = uiState.avatarUrl,
                size = 56.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (uiState.username.isNotEmpty()) {
                    Text(
                        text = "@${uiState.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.bio.ifBlank { strings.settingsCategoryProfileDesc },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = strings.editProfile,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    showChevron: Boolean = true,
) {
    val isMiuix = isMiuixTheme()
    val backgroundColor = when {
        selected && isMiuix -> MiuixTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val titleColor = when {
        danger -> MaterialTheme.colorScheme.error
        selected && isMiuix -> MiuixTheme.colorScheme.onTertiaryContainer
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = when {
        selected && isMiuix -> MiuixTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f)
        selected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = when {
        danger -> MaterialTheme.colorScheme.error
        selected && isMiuix -> MiuixTheme.colorScheme.onTertiaryContainer
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        isMiuix -> MiuixTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(if (isMiuix) 16.dp else 12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = iconTint,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = subtitleColor,
            )
        }
    }
}
