package com.github.woodsmarshes.chat.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactNavigationLayoutTest {
    @Test
    fun movesOnlyTheBarAcrossTheFullWindow() {
        assertEquals(2200, compactBarTop(2460, 260, 1f))
        assertEquals(2330, compactBarTop(2460, 260, 0.5f))
        assertEquals(2460, compactBarTop(2460, 260, 0f))
    }

    @Test
    fun clampsProgressAndAcceptsResizedWindows() {
        assertEquals(800, compactBarTop(1000, 200, 2f))
        assertEquals(1000, compactBarTop(1000, 200, -1f))
        assertEquals(700, compactBarTop(800, 100, 1f))
    }
}
