package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 完整设计颜色令牌（参考 Jetcaster 的 Color.kt + NiA 的 Color.kt 设计）。
 *
 * - 覆盖 Material3 全部 30 个颜色角色
 * - 额外聊天气泡专用角色（own/other bubble colors）
 * - 所有令牌标记 @Immutable 避免无效重组
 * - 使用 staticCompositionLocalOf 确保令牌变化时只重组直接读取者
 */

// ============ Material 3 浅色令牌 ============
object LightTokens {
    val Primary = Color(0xFF6750A4)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFEADDFF)
    val OnPrimaryContainer = Color(0xFF21005D)
    val Secondary = Color(0xFF625B71)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFE8DEF8)
    val OnSecondaryContainer = Color(0xFF1D192B)
    val Tertiary = Color(0xFF7D5260)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFFFD8E4)
    val OnTertiaryContainer = Color(0xFF31111D)
    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFF9DEDC)
    val OnErrorContainer = Color(0xFF410E0B)
    val Background = Color(0xFFFFFBFE)
    val OnBackground = Color(0xFF1C1B1F)
    val Surface = Color(0xFFFFFBFE)
    val OnSurface = Color(0xFF1C1B1F)
    val SurfaceVariant = Color(0xFFE7E0EC)
    val OnSurfaceVariant = Color(0xFF49454F)
    val Outline = Color(0xFF79747E)
    val OutlineVariant = Color(0xFFCAC4D0)
    val InverseSurface = Color(0xFF313033)
    val InverseOnSurface = Color(0xFFF4EFF4)
    val InversePrimary = Color(0xFFD0BCFF)
    val SurfaceDim = Color(0xFFD8D8D8)
    val SurfaceBright = Color(0xFFFFFBFE)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFF7F2FA)
    val SurfaceContainer = Color(0xFFF3EDF7)
    val SurfaceContainerHigh = Color(0xFFECE6F0)
    val SurfaceContainerHighest = Color(0xFFE6E0E9)
}

// ============ Material 3 深色令牌 ============
object DarkTokens {
    val Primary = Color(0xFFD0BCFF)
    val OnPrimary = Color(0xFF381E72)
    val PrimaryContainer = Color(0xFF4F378B)
    val OnPrimaryContainer = Color(0xFFEADDFF)
    val Secondary = Color(0xFFCCC2DC)
    val OnSecondary = Color(0xFF332D41)
    val SecondaryContainer = Color(0xFF4A4458)
    val OnSecondaryContainer = Color(0xFFE8DEF8)
    val Tertiary = Color(0xFFEFB8C8)
    val OnTertiary = Color(0xFF492532)
    val TertiaryContainer = Color(0xFF633B48)
    val OnTertiaryContainer = Color(0xFFFFD8E4)
    val Error = Color(0xFFF2B8B5)
    val OnError = Color(0xFF601410)
    val ErrorContainer = Color(0xFF8C1D18)
    val OnErrorContainer = Color(0xFFF9DEDC)
    val Background = Color(0xFF1C1B1F)
    val OnBackground = Color(0xFFE6E1E5)
    val Surface = Color(0xFF1C1B1F)
    val OnSurface = Color(0xFFE6E1E5)
    val SurfaceVariant = Color(0xFF49454F)
    val OnSurfaceVariant = Color(0xFFCAC4D0)
    val Outline = Color(0xFF938F99)
    val OutlineVariant = Color(0xFF49454F)
    val InverseSurface = Color(0xFFE6E1E5)
    val InverseOnSurface = Color(0xFF313033)
    val InversePrimary = Color(0xFF6750A4)
    val SurfaceDim = Color(0xFF141316)
    val SurfaceBright = Color(0xFF3B383E)
    val SurfaceContainerLowest = Color(0xFF0F0D13)
    val SurfaceContainerLow = Color(0xFF1D1B20)
    val SurfaceContainer = Color(0xFF211F26)
    val SurfaceContainerHigh = Color(0xFF2B2930)
    val SurfaceContainerHighest = Color(0xFF36343B)
}

// ============ 聊天气泡专用令牌 ============
@Immutable
data class BubbleColorTokens(
    val ownBackground: Color,
    val ownContent: Color,
    val otherBackground: Color,
    val otherContent: Color,
    val timestampColor: Color,
    val senderNameColor: Color,
    val iconTint: Color,
    val inputBarBackground: Color,
    val inputFieldBackground: Color,
    val inputFieldContent: Color,
    val inputFieldPlaceholder: Color,
    val inputIconTint: Color,
    val inputSendIconTint: Color,
    val panelBackground: Color,
    val errorColor: Color,
    val surfaceColor: Color,
    val onSurfaceColor: Color,
)

/**
 * 全局颜色令牌 CompositionLocal。
 *
 * 使用 staticCompositionLocalOf 而非 compositionLocalOf：
 * - 设计令牌在运行时极少变化（仅主题切换时）
 * - static 版本确保非直接读取者不重组
 * - 参考 Jetcaster 和 NiA 的最佳实践
 */
val LocalColorTokens = staticCompositionLocalOf<ColorTokens> {
    error("LocalColorTokens not provided — 请确保 ChatTheme 包裹了此内容")
}

@Immutable
data class ColorTokens(
    // M3 标准角色
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    // 聊天气泡
    val bubble: BubbleColorTokens,
) {
    companion object {
        fun light() = ColorTokens(
            primary = LightTokens.Primary,
            onPrimary = LightTokens.OnPrimary,
            primaryContainer = LightTokens.PrimaryContainer,
            onPrimaryContainer = LightTokens.OnPrimaryContainer,
            secondary = LightTokens.Secondary,
            onSecondary = LightTokens.OnSecondary,
            secondaryContainer = LightTokens.SecondaryContainer,
            onSecondaryContainer = LightTokens.OnSecondaryContainer,
            tertiary = LightTokens.Tertiary,
            onTertiary = LightTokens.OnTertiary,
            tertiaryContainer = LightTokens.TertiaryContainer,
            onTertiaryContainer = LightTokens.OnTertiaryContainer,
            error = LightTokens.Error,
            onError = LightTokens.OnError,
            errorContainer = LightTokens.ErrorContainer,
            onErrorContainer = LightTokens.OnErrorContainer,
            background = LightTokens.Background,
            onBackground = LightTokens.OnBackground,
            surface = LightTokens.Surface,
            onSurface = LightTokens.OnSurface,
            surfaceVariant = LightTokens.SurfaceVariant,
            onSurfaceVariant = LightTokens.OnSurfaceVariant,
            outline = LightTokens.Outline,
            outlineVariant = LightTokens.OutlineVariant,
            inverseSurface = LightTokens.InverseSurface,
            inverseOnSurface = LightTokens.InverseOnSurface,
            inversePrimary = LightTokens.InversePrimary,
            surfaceDim = LightTokens.SurfaceDim,
            surfaceBright = LightTokens.SurfaceBright,
            surfaceContainerLowest = LightTokens.SurfaceContainerLowest,
            surfaceContainerLow = LightTokens.SurfaceContainerLow,
            surfaceContainer = LightTokens.SurfaceContainer,
            surfaceContainerHigh = LightTokens.SurfaceContainerHigh,
            surfaceContainerHighest = LightTokens.SurfaceContainerHighest,
            bubble = BubbleColorTokens(
                ownBackground = LightTokens.Primary,
                ownContent = LightTokens.OnPrimary,
                otherBackground = LightTokens.SecondaryContainer,
                otherContent = LightTokens.OnSecondaryContainer,
                timestampColor = LightTokens.Outline,
                senderNameColor = LightTokens.OnSecondaryContainer,
                iconTint = LightTokens.OnSurfaceVariant,
                inputBarBackground = LightTokens.Surface.copy(alpha = 0.95f),
                inputFieldBackground = LightTokens.SecondaryContainer,
                inputFieldContent = LightTokens.OnSecondaryContainer,
                inputFieldPlaceholder = LightTokens.Outline,
                inputIconTint = LightTokens.OnSurfaceVariant,
                inputSendIconTint = LightTokens.Primary,
                panelBackground = LightTokens.Surface,
                errorColor = LightTokens.Error,
                surfaceColor = LightTokens.Surface,
                onSurfaceColor = LightTokens.OnSurface,
            ),
        )

        fun dark() = ColorTokens(
            primary = DarkTokens.Primary,
            onPrimary = DarkTokens.OnPrimary,
            primaryContainer = DarkTokens.PrimaryContainer,
            onPrimaryContainer = DarkTokens.OnPrimaryContainer,
            secondary = DarkTokens.Secondary,
            onSecondary = DarkTokens.OnSecondary,
            secondaryContainer = DarkTokens.SecondaryContainer,
            onSecondaryContainer = DarkTokens.OnSecondaryContainer,
            tertiary = DarkTokens.Tertiary,
            onTertiary = DarkTokens.OnTertiary,
            tertiaryContainer = DarkTokens.TertiaryContainer,
            onTertiaryContainer = DarkTokens.OnTertiaryContainer,
            error = DarkTokens.Error,
            onError = DarkTokens.OnError,
            errorContainer = DarkTokens.ErrorContainer,
            onErrorContainer = DarkTokens.OnErrorContainer,
            background = DarkTokens.Background,
            onBackground = DarkTokens.OnBackground,
            surface = DarkTokens.Surface,
            onSurface = DarkTokens.OnSurface,
            surfaceVariant = DarkTokens.SurfaceVariant,
            onSurfaceVariant = DarkTokens.OnSurfaceVariant,
            outline = DarkTokens.Outline,
            outlineVariant = DarkTokens.OutlineVariant,
            inverseSurface = DarkTokens.InverseSurface,
            inverseOnSurface = DarkTokens.InverseOnSurface,
            inversePrimary = DarkTokens.InversePrimary,
            surfaceDim = DarkTokens.SurfaceDim,
            surfaceBright = DarkTokens.SurfaceBright,
            surfaceContainerLowest = DarkTokens.SurfaceContainerLowest,
            surfaceContainerLow = DarkTokens.SurfaceContainerLow,
            surfaceContainer = DarkTokens.SurfaceContainer,
            surfaceContainerHigh = DarkTokens.SurfaceContainerHigh,
            surfaceContainerHighest = DarkTokens.SurfaceContainerHighest,
            bubble = BubbleColorTokens(
                ownBackground = DarkTokens.Primary,
                ownContent = DarkTokens.OnPrimary,
                otherBackground = DarkTokens.SecondaryContainer,
                otherContent = DarkTokens.OnSecondaryContainer,
                timestampColor = DarkTokens.Outline,
                senderNameColor = DarkTokens.OnSecondaryContainer,
                iconTint = DarkTokens.OnSurfaceVariant,
                inputBarBackground = DarkTokens.Surface.copy(alpha = 0.95f),
                inputFieldBackground = DarkTokens.SecondaryContainer,
                inputFieldContent = DarkTokens.OnSecondaryContainer,
                inputFieldPlaceholder = DarkTokens.Outline,
                inputIconTint = DarkTokens.OnSurfaceVariant,
                inputSendIconTint = DarkTokens.Primary,
                panelBackground = DarkTokens.Surface,
                errorColor = DarkTokens.Error,
                surfaceColor = DarkTokens.Surface,
                onSurfaceColor = DarkTokens.OnSurface,
            ),
        )
    }
}
