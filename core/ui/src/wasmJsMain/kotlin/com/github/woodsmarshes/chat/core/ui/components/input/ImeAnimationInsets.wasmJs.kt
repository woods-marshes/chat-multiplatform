package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Browsers do not expose soft keyboard height through Compose insets. */
@Composable
internal actual fun rememberImeAnimationInsets(): ImeAnimationInsets {
    val ime = WindowInsets.ime
    return remember(ime) {
        ImeAnimationInsets(source = ime, target = ime, reportsImeInsets = false)
    }
}
