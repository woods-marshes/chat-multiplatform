package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Coordinates user intent independently from animated window geometry. */
internal class InputPanelState {
    var selector by mutableStateOf(InputSelector.NONE)
        private set
    var awaitingKeyboard by mutableStateOf(false)
        private set
    var panelHeightPx by mutableStateOf(0)
        private set
    var stableImeHeightPx by mutableStateOf(0)
        private set

    fun openPanel(selector: InputSelector, currentImePx: Int, fallbackPx: Int) {
        require(selector != InputSelector.NONE)
        if (this.selector == InputSelector.NONE && !awaitingKeyboard) {
            panelHeightPx = stableImeHeightPx.takeIf { it > 0 }
                ?: currentImePx.takeIf { it > 0 }
                ?: fallbackPx
        }
        this.selector = selector
        awaitingKeyboard = false
    }

    fun requestKeyboard() {
        if (selector != InputSelector.NONE) {
            selector = InputSelector.NONE
            awaitingKeyboard = true
        }
    }

    fun observeIme(current: Int, source: Int, target: Int) {
        // Only completed geometry can replace the cache, including a smaller keyboard.
        if (current > 0 && current == source && source == target) {
            stableImeHeightPx = current
            if (awaitingKeyboard) awaitingKeyboard = false
        }
    }

    fun close() {
        selector = InputSelector.NONE
        awaitingKeyboard = false
        panelHeightPx = 0
    }

    fun abandonKeyboardRequest() {
        awaitingKeyboard = false
        panelHeightPx = 0
    }

    fun occupiedHeight(current: Int, source: Int, target: Int, navigation: Int): Int {
        val reserved = when {
            selector != InputSelector.NONE -> panelHeightPx
            awaitingKeyboard -> {
                // Use the system animation itself to reconcile a different keyboard height.
                val progress = if (target > source) {
                    ((current - source).toFloat() / (target - source)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                (panelHeightPx + (target - panelHeightPx) * progress).toInt()
            }
            else -> 0
        }
        return maxOf(current, reserved, navigation)
    }
}
