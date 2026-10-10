package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import top.yukonga.miuix.kmp.theme.Colors as MiuixColors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * Material 3 ColorScheme 工厂（底层数据来自 ColorTokens）。
 *
 * 当 ThemeBrand == MATERIAL3 时，Theme.kt 使用此处生成的 ColorScheme。
 */
internal val m3LightColorScheme = with(ColorTokens.light()) {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}

internal val m3DarkColorScheme = with(ColorTokens.dark()) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}

/**
 * Bridges Miuix's active [MiuixColors] into a Material 3 [ColorScheme] so any
 * Material 3 component that must coexist in Miuix mode (e.g. AdaptiveSearchBar,
 * LibrariesContainer, DropdownMenu) uses Miuix colors without leaking M3 purple.
 */
internal fun miuixBridgedM3ColorScheme(miuix: MiuixColors, isDark: Boolean): ColorScheme {
    val base = if (isDark) m3DarkColorScheme else m3LightColorScheme
    return base.copy(
        primary = miuix.primary,
        onPrimary = miuix.onPrimary,
        primaryContainer = miuix.tertiaryContainer,
        onPrimaryContainer = miuix.onTertiaryContainer,
        secondary = miuix.primaryVariant,
        onSecondary = miuix.onPrimary,
        secondaryContainer = miuix.tertiaryContainer,
        onSecondaryContainer = miuix.onTertiaryContainer,
        tertiary = miuix.primary,
        onTertiary = miuix.onPrimary,
        tertiaryContainer = miuix.tertiaryContainer,
        onTertiaryContainer = miuix.onTertiaryContainer,
        error = miuix.error,
        onError = miuix.onError,
        errorContainer = miuix.errorContainer,
        onErrorContainer = miuix.onErrorContainer,
        background = miuix.surface,
        onBackground = miuix.onSurface,
        surface = miuix.surface,
        onSurface = miuix.onSurface,
        surfaceVariant = miuix.surfaceContainerHigh,
        onSurfaceVariant = miuix.onSurfaceVariantSummary,
        outline = miuix.outline,
        outlineVariant = miuix.dividerLine,
        inverseSurface = miuix.onSurface,
        inverseOnSurface = miuix.surface,
        inversePrimary = miuix.primary,
        surfaceDim = if (isDark) miuix.surface else miuix.surfaceContainerHigh,
        surfaceBright = if (isDark) miuix.surfaceContainerHighest else miuix.surface,
        surfaceContainerLowest = if (isDark) miuix.surface else miuix.background,
        surfaceContainerLow = miuix.surfaceContainer,
        surfaceContainer = miuix.surfaceContainer,
        surfaceContainerHigh = miuix.surfaceContainerHigh,
        surfaceContainerHighest = miuix.surfaceContainerHighest,
    )
}

/**
 * Bridges Material 3's active [ColorScheme] into Miuix [MiuixColors] so any
 * Miuix primitive rendered under Material 3 mode inherits M3 colors.
 */
internal fun m3BridgedMiuixColors(m3: ColorScheme, isDark: Boolean): MiuixColors {
    val base = if (isDark) miuixDarkColorScheme() else miuixLightColorScheme()
    return base.copy(
        primary = m3.primary,
        onPrimary = m3.onPrimary,
        primaryVariant = m3.primary,
        onPrimaryVariant = m3.onPrimaryContainer,
        primaryContainer = m3.primaryContainer,
        onPrimaryContainer = m3.onPrimaryContainer,
        secondary = m3.secondaryContainer,
        onSecondary = m3.onSecondaryContainer,
        secondaryVariant = m3.secondaryContainer,
        onSecondaryVariant = m3.onSecondaryContainer,
        secondaryContainer = m3.surfaceContainerHigh,
        onSecondaryContainer = m3.onSurfaceVariant,
        tertiaryContainer = m3.secondaryContainer,
        onTertiaryContainer = m3.onSecondaryContainer,
        error = m3.error,
        onError = m3.onError,
        errorContainer = m3.errorContainer,
        onErrorContainer = m3.onErrorContainer,
        background = m3.background,
        onBackground = m3.onBackground,
        onBackgroundVariant = m3.onSurfaceVariant,
        surface = m3.surface,
        onSurface = m3.onSurface,
        surfaceVariant = m3.surfaceVariant,
        onSurfaceSecondary = m3.onSurfaceVariant,
        onSurfaceVariantSummary = m3.onSurfaceVariant,
        surfaceContainer = m3.surfaceContainerLow,
        onSurfaceContainer = m3.onSurface,
        onSurfaceContainerVariant = m3.onSurfaceVariant,
        surfaceContainerHigh = m3.surfaceContainerHigh,
        onSurfaceContainerHigh = m3.onSurfaceVariant,
        surfaceContainerHighest = m3.surfaceContainerHighest,
        onSurfaceContainerHighest = m3.onSurface,
        outline = m3.outline,
        dividerLine = m3.outlineVariant,
    )
}

