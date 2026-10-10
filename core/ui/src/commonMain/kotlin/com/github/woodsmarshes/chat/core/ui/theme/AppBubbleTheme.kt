package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * CompositionLocals for bubble-specific theme tokens.
 *
 * Both locals default to [BubbleDefaultTokens] so consuming code never
 * null-checks; a proper implementation MUST be provided via
 * [com.github.woodsmarshes.chat.core.ui.theme.AppTheme].
 */
val LocalBubbleColors = staticCompositionLocalOf<BubbleColorTokens> {
    BubbleDefaultTokens.colors
}

val LocalBubbleShapes = staticCompositionLocalOf<ShapeTokens> {
    BubbleDefaultTokens.shapes
}

/**
 * Fallback default tokens used when no theme provider is in the tree.
 *
 * Uses the M3 light palette so previews and tests render something visible.
 */
@Immutable
object BubbleDefaultTokens {
    val colors: BubbleColorTokens = ColorTokens.light().bubble
    val shapes: ShapeTokens = ShapeDefaults.Default
}

/**
 * Factory methods for building theme-specific bubble tokens.
 *
 * - [miuixColors] / [miuixShapes] derive from MiuixTheme.
 * - [material3Colors] / [material3Shapes] derive from the canonical [ColorTokens].
 */
object BubbleDefaults {

    @Composable
    fun miuixColors(): BubbleColorTokens = with(MiuixTheme.colorScheme) {
        BubbleColorTokens(
            ownBackground = primary,
            ownContent = onPrimary,
            otherBackground = surfaceContainerHigh,
            otherContent = onSurface,
            timestampColor = onSurfaceVariantSummary,
            senderNameColor = onSurfaceSecondary,
            iconTint = onSurfaceSecondary,
            inputBarBackground = surface.copy(alpha = 0.96f),
            inputFieldBackground = surfaceContainer,
            inputFieldContent = onSurfaceContainer,
            inputFieldPlaceholder = onSurfaceVariantSummary,
            inputIconTint = onSurfaceSecondary,
            inputSendIconTint = primary,
            panelBackground = surface,
            errorColor = error,
            surfaceColor = surface,
            onSurfaceColor = onSurface,
        )
    }

    fun material3Colors(isDark: Boolean): BubbleColorTokens =
        if (isDark) ColorTokens.dark().bubble else ColorTokens.light().bubble

    fun miuixShapes(): ShapeTokens = ShapeDefaults.Miuix

    fun material3Shapes(): ShapeTokens = ShapeDefaults.Default
}

