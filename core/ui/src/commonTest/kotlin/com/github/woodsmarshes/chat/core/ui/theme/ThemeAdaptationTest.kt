package com.github.woodsmarshes.chat.core.ui.theme

import com.github.woodsmarshes.chat.core.model.DarkThemeConfig
import com.github.woodsmarshes.chat.core.model.ThemeBrand
import com.github.woodsmarshes.chat.core.ui.components.avatar.resolveMediaUrl
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThemeAdaptationTest {

    @Test
    fun themeConfigIdentifiesMiuixAndMaterial3Modes() {
        assertTrue(ThemeConfig(themeBrand = ThemeBrand.MIUIX).isMiuix)
        assertTrue(ThemeConfig(themeBrand = ThemeBrand.DEFAULT).isMiuix)
        assertFalse(ThemeConfig(themeBrand = ThemeBrand.MATERIAL3).isMiuix)
    }

    @Test
    fun miuixBridgedM3ColorSchemeInheritsMiuixPaletteWithoutM3PurpleLeak() {
        val miuixLight = miuixLightColorScheme()
        val bridgedLight = miuixBridgedM3ColorScheme(miuixLight, isDark = false)

        assertEquals(miuixLight.primary, bridgedLight.primary)
        assertEquals(miuixLight.surface, bridgedLight.surface)
        assertEquals(miuixLight.error, bridgedLight.error)
        assertEquals(miuixLight.primary, bridgedLight.inversePrimary)
        assertEquals(miuixLight.onSurface, bridgedLight.inverseSurface)
        assertNotEquals(m3LightColorScheme.primary, bridgedLight.primary)
        assertNotEquals(m3LightColorScheme.inversePrimary, bridgedLight.inversePrimary)

        val miuixDark = miuixDarkColorScheme()
        val bridgedDark = miuixBridgedM3ColorScheme(miuixDark, isDark = true)

        assertEquals(miuixDark.primary, bridgedDark.primary)
        assertEquals(miuixDark.surface, bridgedDark.surface)
        assertEquals(miuixDark.error, bridgedDark.error)
        assertEquals(miuixDark.primary, bridgedDark.inversePrimary)
        assertEquals(miuixDark.onSurface, bridgedDark.inverseSurface)
        assertNotEquals(m3DarkColorScheme.primary, bridgedDark.primary)
        assertNotEquals(m3DarkColorScheme.inversePrimary, bridgedDark.inversePrimary)
    }

    @Test
    fun m3BridgedMiuixColorsInheritsMaterial3PaletteWithoutMiuixBlueLeak() {
        val bridgedLight = m3BridgedMiuixColors(m3LightColorScheme, isDark = false)
        assertEquals(m3LightColorScheme.primary, bridgedLight.primary)
        assertEquals(m3LightColorScheme.surface, bridgedLight.surface)
        assertEquals(m3LightColorScheme.error, bridgedLight.error)

        val bridgedDark = m3BridgedMiuixColors(m3DarkColorScheme, isDark = true)
        assertEquals(m3DarkColorScheme.primary, bridgedDark.primary)
        assertEquals(m3DarkColorScheme.surface, bridgedDark.surface)
        assertEquals(m3DarkColorScheme.error, bridgedDark.error)
    }

    @Test
    fun bubbleShapesSwitchWithStyle() {
        assertEquals(ShapeDefaults.Miuix, BubbleDefaults.miuixShapes())
        assertEquals(ShapeDefaults.Default, BubbleDefaults.material3Shapes())
        assertNotEquals(BubbleDefaults.miuixShapes().ownBubble, BubbleDefaults.material3Shapes().ownBubble)
    }

    @Test
    fun resolveMediaUrlHandlesRelativeAndAbsoluteUrls() {
        val base = "http://127.0.0.1:9051/"
        assertNull(resolveMediaUrl(null, base))
        assertNull(resolveMediaUrl("   ", base))
        assertEquals("http://127.0.0.1:9051/uploads/img.png", resolveMediaUrl("/uploads/img.png", base))
        assertEquals("http://127.0.0.1:9051/uploads/img.png", resolveMediaUrl("uploads/img.png", base))
        assertEquals("https://cdn.example.com/a.jpg", resolveMediaUrl("https://cdn.example.com/a.jpg", base))
        assertEquals("https://cdn.example.com/a.jpg", resolveMediaUrl("//cdn.example.com/a.jpg", base))
    }
}
