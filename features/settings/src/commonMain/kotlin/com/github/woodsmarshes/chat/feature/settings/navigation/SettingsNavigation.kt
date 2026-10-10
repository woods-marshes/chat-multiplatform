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

/**
 * Standalone profile-edit route pushed onto a `detailPane` `ProfileNavKey`
 * (from contacts, search, or the account avatar). Unlike
 * `SettingsDetailNavKey(SettingsCategory.PROFILE)`, this route stays on the
 * caller's sub-stack and always shows a back button to pop back to the
 * underlying profile screen.
 */
@Serializable
data object EditProfileNavKey : NavKey

/**
 * Contextual profile-edit route pushed onto a `ChatProfileNavKey` in the
 * rightmost `extraPane` of a three-pane layout.
 */
@Serializable
data object ChatEditProfileNavKey : NavKey

fun EntryProviderScope<NavKey>.settingsEntry(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onCategoryClick: (SettingsCategory) -> Unit = {},
    onOpenLicenses: () -> Unit = { onCategoryClick(SettingsCategory.LICENSES) },
    selectedCategory: () -> SettingsCategory? = { null },
    metadata: Map<String, Any> = emptyMap(),
    detailMetadata: Map<String, Any> = emptyMap(),
    extraMetadata: Map<String, Any> = detailMetadata,
) {
    entry<SettingsNavKey>(metadata = metadata) {
        SettingsScreen(
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
            isSubPage = true,
        )
    }
    entry<EditProfileNavKey>(metadata = detailMetadata) {
        SettingsDetailScreen(
            category = SettingsCategory.PROFILE,
            onBack = onBack,
            onLogout = onLogout,
            isSubPage = true,
        )
    }
    entry<ChatEditProfileNavKey>(metadata = extraMetadata) {
        SettingsDetailScreen(
            category = SettingsCategory.PROFILE,
            onBack = onBack,
            onLogout = onLogout,
            isSubPage = true,
        )
    }
}
