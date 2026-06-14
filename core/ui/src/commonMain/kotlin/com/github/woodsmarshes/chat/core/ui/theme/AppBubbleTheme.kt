package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
//import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * CompositionLocals for bubble-specific theme tokens.
 *
 * These are derived from [ColorTokens] and [ShapeTokens] in core:designsystem,
 * but exposed as separate CompositionLocals for convenience — bubble components
 * only need bubble-specific colors/shapes, not the full token set.
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
 * - [material3Colors] / [material3Shapes] derive from the canonical
 *   [ColorTokens] in core:designsystem.
 */
object BubbleDefaults {

//    @Composable
//    fun miuixColors(): BubbleColorTokens = with(MiuixTheme.colorScheme) {
//        BubbleColorTokens(
//            ownBackground = primary,
//            ownContent = onPrimary,
//            otherBackground = secondaryContainer,
//            otherContent = onSecondaryContainer,
//            timestampColor = disabledOnSecondaryVariant,
//            senderNameColor = onSecondaryContainer,
//            iconTint = onSecondaryContainer,
//            inputBarBackground = surface.copy(alpha = 0.95f),
//            inputFieldBackground = secondaryContainer,
//            inputFieldContent = onSecondaryContainer,
//            inputFieldPlaceholder = disabledOnSecondaryVariant,
//            inputIconTint = onSecondaryContainer,
//            inputSendIconTint = primary,
//            panelBackground = surface,
//            errorColor = error,
//            surfaceColor = surface,
//            onSurfaceColor = onSurface,
//        )
//    }

    fun material3Colors(isDark: Boolean): BubbleColorTokens =
        if (isDark) ColorTokens.dark().bubble else ColorTokens.light().bubble

    fun miuixShapes(): ShapeTokens = ShapeDefaults.Default

    fun material3Shapes(): ShapeTokens = ShapeDefaults.Default
}
