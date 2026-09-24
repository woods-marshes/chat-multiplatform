package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberImeAnimationInsets(): ImeAnimationInsets {
    val ime = WindowInsets.ime
    return remember(ime) { ImeAnimationInsets(ime, ime) }
}
