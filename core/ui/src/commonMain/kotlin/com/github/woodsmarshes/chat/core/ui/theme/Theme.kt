package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.resources.MiSans_Bold
import com.github.woodsmarshes.chat.resources.MiSans_Light
import com.github.woodsmarshes.chat.resources.MiSans_Medium
import com.github.woodsmarshes.chat.resources.MiSans_Normal
import com.github.woodsmarshes.chat.resources.MiSans_Regular
import com.github.woodsmarshes.chat.resources.Res
import org.jetbrains.compose.resources.Font
//import top.yukonga.miuix.kmp.theme.ColorSchemeMode
//import top.yukonga.miuix.kmp.theme.MiuixTheme
//import top.yukonga.miuix.kmp.theme.TextStyles
//import top.yukonga.miuix.kmp.theme.ThemeController
//import top.yukonga.miuix.kmp.theme.defaultTextStyles

//fun getMiuixTextStyles(fontFamily: FontFamily): TextStyles {
//    val default = defaultTextStyles()
//    return default.copy(
//        main = default.main.copy(fontFamily = fontFamily),
//        paragraph = default.paragraph.copy(fontFamily = fontFamily),
//        body1 = default.body1.copy(fontFamily = fontFamily),
//        body2 = default.body2.copy(fontFamily = fontFamily),
//        button = default.button.copy(fontFamily = fontFamily),
//        footnote1 = default.footnote1.copy(fontFamily = fontFamily),
//        footnote2 = default.footnote2.copy(fontFamily = fontFamily),
//        headline1 = default.headline1.copy(fontFamily = fontFamily),
//        headline2 = default.headline2.copy(fontFamily = fontFamily),
//        subtitle = default.subtitle.copy(fontFamily = fontFamily),
//        title1 = default.title1.copy(fontFamily = fontFamily),
//        title2 = default.title2.copy(fontFamily = fontFamily),
//        title3 = default.title3.copy(fontFamily = fontFamily),
//        title4 = default.title4.copy(fontFamily = fontFamily),
//    )
//}

private val defaultM3Typography = Typography()
fun getM3Typography(fontFamily: FontFamily) = Typography(
    displayLarge = defaultM3Typography.displayLarge.copy(fontFamily = fontFamily),
    displayMedium = defaultM3Typography.displayMedium.copy(fontFamily = fontFamily),
    displaySmall = defaultM3Typography.displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = defaultM3Typography.headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = defaultM3Typography.headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = defaultM3Typography.headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = defaultM3Typography.titleLarge.copy(fontFamily = fontFamily),
    titleMedium = defaultM3Typography.titleMedium.copy(fontFamily = fontFamily),
    titleSmall = defaultM3Typography.titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = defaultM3Typography.bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = defaultM3Typography.bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = defaultM3Typography.bodySmall.copy(fontFamily = fontFamily),
    labelLarge = defaultM3Typography.labelLarge.copy(fontFamily = fontFamily),
    labelMedium = defaultM3Typography.labelMedium.copy(fontFamily = fontFamily),
    labelSmall = defaultM3Typography.labelSmall.copy(fontFamily = fontFamily)
)

@Composable
fun AppTheme(
    themeConfig: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val isDark = when (themeConfig.darkThemeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }

    val appFontFamily = FontFamily(
        Font(Res.font.MiSans_Light, FontWeight.Light),
        Font(Res.font.MiSans_Regular, FontWeight.Normal),
        Font(Res.font.MiSans_Normal, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.MiSans_Medium, FontWeight.Medium),
        Font(Res.font.MiSans_Bold, FontWeight.Bold)
    )

    // val miuixTextStyles = remember(appFontFamily) { getMiuixTextStyles(appFontFamily) }
    val m3Typography = remember(appFontFamily) { getM3Typography(appFontFamily) }

    val contentWithFontFallback = @Composable {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = appFontFamily),
            content = content
        )
    }


    when (themeConfig.themeBrand) {
//        ThemeBrand.MIUIX, ThemeBrand.DEFAULT,
//        ThemeBrand.ANDROID, ThemeBrand.IOS, ThemeBrand.DESKTOP -> {
//            val mode = when (themeConfig.darkThemeConfig) {
//                DarkThemeConfig.LIGHT -> ColorSchemeMode.Light
//                DarkThemeConfig.DARK -> ColorSchemeMode.Dark
//                DarkThemeConfig.FOLLOW_SYSTEM -> ColorSchemeMode.System
//            }
//            val controller = remember(themeConfig.darkThemeConfig) {
//                ThemeController(mode)
//            }
//            MiuixTheme(
//                controller = controller,
//                textStyles = miuixTextStyles
//            ) {
//                val bubbleColors = BubbleDefaults.miuixColors()
//                val bubbleShapes = BubbleDefaults.miuixShapes()
//                val colorTokens = if (isDark) ColorTokens.dark() else ColorTokens.light()
//                CompositionLocalProvider(
//                    LocalThemeConfig provides themeConfig,
//                    LocalBubbleColors provides bubbleColors,
//                    LocalBubbleShapes provides bubbleShapes,
//                    LocalColorTokens provides colorTokens,
//                    LocalShapeTokens provides ShapeDefaults.Default,
//                    content = contentWithFontFallback,
//                )
//            }
//        }
        else -> {
            val colorScheme = if (isDark) m3DarkColorScheme else m3LightColorScheme
            MaterialTheme(
                colorScheme = colorScheme,
                typography = m3Typography
            ) {
                val bubbleColors = BubbleDefaults.material3Colors(isDark)
                val bubbleShapes = BubbleDefaults.material3Shapes()
                val colorTokens = if (isDark) ColorTokens.dark() else ColorTokens.light()
                CompositionLocalProvider(
                    LocalThemeConfig provides themeConfig,
                    LocalBubbleColors provides bubbleColors,
                    LocalBubbleShapes provides bubbleShapes,
                    LocalColorTokens provides colorTokens,
                    LocalShapeTokens provides ShapeDefaults.Default,
                    content = contentWithFontFallback,
                )
            }
        }
    }
}
