package com.github.woodsmarshes.chat.core.ui.components.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputPanelStateTest {
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
