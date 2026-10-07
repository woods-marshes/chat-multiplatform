package com.github.woodsmarshes.chat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.github.woodsmarshes.chat.feature.settings.model.SettingsCategory
import com.github.woodsmarshes.chat.feature.settings.ui.SettingsDetailScreen
import com.github.woodsmarshes.chat.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsNavKey : NavKey

@Serializable
data class SettingsDetailNavKey(
    val category: SettingsCategory = SettingsCategory.PROFILE,
) : NavKey

@Serializable
data object OpenSourceLicensesNavKey : NavKey

fun EntryProviderScope<NavKey>.settingsEntry(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onSearchClick: () -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit = {},
    onOpenLicenses: () -> Unit = { onCategoryClick(SettingsCategory.LICENSES) },
    selectedCategory: () -> SettingsCategory? = { null },
    metadata: Map<String, Any> = emptyMap(),
    detailMetadata: Map<String, Any> = emptyMap(),
) {
    entry<SettingsNavKey>(metadata = metadata) {
        SettingsScreen(
            onBack = onBack,
            onLogout = onLogout,
            onSearchClick = onSearchClick,
            onCategoryClick = onCategoryClick,
            selectedCategory = selectedCategory(),
        )
    }
    entry<SettingsDetailNavKey>(metadata = detailMetadata) { key ->
        SettingsDetailScreen(
            category = key.category,
            onBack = onBack,
            onLogout = onLogout,
            onOpenLicenses = onOpenLicenses,
        )
    }
    entry<OpenSourceLicensesNavKey>(metadata = detailMetadata) {
        SettingsDetailScreen(
            category = SettingsCategory.LICENSES,
            onBack = onBack,
            onLogout = onLogout,
            onOpenLicenses = onOpenLicenses,
        )
    }
}
