package com.github.woodsmarshes.chat.feature.chat.ui.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputPanelStateTest {
    @Test
    fun repeatedKeyboardHandoffsIgnoreTransientOversizedTargets() {
        val state = InputPanelState()
        state.observeIme(1048, 1048, 1048)
        repeat(6) {
            state.openPanel(InputSelector.EMOJI, 1048, 742)
            state.observeIme(0, 0, 0)
            state.requestKeyboard()
            for (current in listOf(0, 170, 718, 1044, 1107, 1134)) {
                assertEquals(1048, state.occupiedHeight(current, 0, 1135, 66))
            }
            state.observeIme(1048, 1048, 1048)
            assertEquals(1048, state.occupiedHeight(1048, 1048, 1048, 66))
            assertFalse(state.awaitingKeyboard)
        }
    }

    @Test
    fun realKeyboardSizeChangeTakesOverWhenSettled() {
        for (newHeight in listOf(800, 1200)) {
            val state = InputPanelState()
            state.observeIme(1048, 1048, 1048)
            state.openPanel(InputSelector.IMAGE, 0, 742)
            state.requestKeyboard()
            assertEquals(1048, state.occupiedHeight(newHeight / 2, 0, newHeight, 66))
            state.observeIme(newHeight, newHeight, newHeight)
            assertEquals(newHeight, state.stableImeHeightPx)
            assertEquals(newHeight, state.occupiedHeight(newHeight, newHeight, newHeight, 66))
        }
    }

    @Test
    fun fullKeyboardPanelCycleKeepsOneReservation() {
        val state = InputPanelState()
        state.observeIme(360, 360, 360)
        state.openPanel(InputSelector.EMOJI, 360, 270)
        for (ime in listOf(360, 270, 180, 90, 0)) {
            assertEquals(360, state.occupiedHeight(ime, 360, 0, 24))
        }
        state.openPanel(InputSelector.IMAGE, 0, 270)
        assertEquals(360, state.occupiedHeight(0, 0, 0, 24))
        state.requestKeyboard()
        for (ime in listOf(0, 90, 180, 270, 360)) {
            assertEquals(360, state.occupiedHeight(ime, 0, 360, 24))
        }
        state.observeIme(360, 360, 360)
        state.close()
        assertEquals(24, state.occupiedHeight(0, 0, 0, 24))
    }

    @Test
    fun animationFramesDoNotOverwriteStableHeight() {
        val state = InputPanelState()
        state.observeIme(100, 0, 300)
        assertEquals(0, state.stableImeHeightPx)
        state.observeIme(300, 300, 300)
        state.observeIme(200, 300, 0)
        assertEquals(300, state.stableImeHeightPx)
        state.observeIme(240, 240, 240)
        assertEquals(240, state.stableImeHeightPx)
    }

    @Test
    fun panelSurvivesLateImeEvents() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 150, 270)
        state.observeIme(300, 300, 300)
        assertEquals(InputSelector.EMOJI, state.selector)
    }

    @Test
    fun switchingPanelsPreservesReservedHeight() {
        val state = InputPanelState()
        state.observeIme(300, 300, 300)
        state.openPanel(InputSelector.EMOJI, 300, 270)
        state.openPanel(InputSelector.IMAGE, 0, 270)
        assertEquals(300, state.panelHeightPx)
        assertEquals(300, state.occupiedHeight(180, 300, 0, 24))
    }

    @Test
    fun keyboardHandoffDoesNotCollapseBeforeImeArrives() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 0, 300)
        state.requestKeyboard()
        assertTrue(state.awaitingKeyboard)
        assertEquals(300, state.occupiedHeight(0, 0, 0, 24))
        assertEquals(300, state.occupiedHeight(150, 0, 300, 24))
        state.observeIme(300, 300, 300)
        assertFalse(state.awaitingKeyboard)
    }

    @Test
    fun smallerKeyboardReconcilesDuringSystemAnimation() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 0, 300)
        state.requestKeyboard()
        assertEquals(300, state.occupiedHeight(0, 0, 200, 24))
        assertEquals(250, state.occupiedHeight(100, 0, 200, 24))
        assertEquals(200, state.occupiedHeight(200, 0, 200, 24))
        state.observeIme(200, 200, 200)
        assertEquals(200, state.occupiedHeight(200, 200, 200, 24))
    }

    @Test
    fun latestPanelRequestWinsOverPendingKeyboard() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 0, 300)
        state.requestKeyboard()
        state.openPanel(InputSelector.IMAGE, 100, 270)
        state.observeIme(300, 300, 300)
        assertFalse(state.awaitingKeyboard)
        assertEquals(InputSelector.IMAGE, state.selector)
        assertEquals(300, state.panelHeightPx)
    }

    @Test
    fun failedKeyboardRequestReleasesReservation() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 0, 300)
        state.requestKeyboard()
        state.abandonKeyboardRequest()
        assertEquals(24, state.occupiedHeight(0, 0, 0, 24))
        assertFalse(state.awaitingKeyboard)
    }

    @Test
    fun keyboardRequestWithoutImeInsetsCollapsesImmediately() {
        val state = InputPanelState()
        state.openPanel(InputSelector.EMOJI, 0, 300)
        state.requestKeyboard(reportsImeInsets = false)
        assertEquals(InputSelector.NONE, state.selector)
        assertFalse(state.awaitingKeyboard)
        assertEquals(0, state.panelHeightPx)
        assertEquals(24, state.occupiedHeight(0, 0, 0, 24))
        assertEquals(0, state.occupiedHeight(0, 0, 0, 0))
    }

    @Test
    fun keyboardRequestWithoutOpenPanelIsNoOp() {
        val state = InputPanelState()
        state.requestKeyboard()
        assertFalse(state.awaitingKeyboard)
        state.requestKeyboard(reportsImeInsets = false)
        assertFalse(state.awaitingKeyboard)
        assertEquals(InputSelector.NONE, state.selector)
    }

    @Test
    fun invalidatingCacheKeepsOpenPanelHeight() {
        val state = InputPanelState()
        state.observeIme(300, 300, 300)
        state.openPanel(InputSelector.EMOJI, 300, 270)
        state.invalidateImeCache()
        assertEquals(0, state.stableImeHeightPx)
        assertEquals(300, state.panelHeightPx)
        state.close()
        state.openPanel(InputSelector.EMOJI, 0, 270)
        assertEquals(270, state.panelHeightPx)
    }

    @Test
    fun closeRetainsCacheButRemovesPanel() {
        val state = InputPanelState()
        state.observeIme(300, 300, 300)
        state.openPanel(InputSelector.EMOJI, 300, 270)
        state.close()
        assertEquals(InputSelector.NONE, state.selector)
        assertEquals(300, state.stableImeHeightPx)
        assertEquals(24, state.occupiedHeight(0, 0, 0, 24))
    }
}
