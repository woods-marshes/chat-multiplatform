package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal actual fun rememberImeAnimationInsets(): ImeAnimationInsets {
    val source = WindowInsets.imeAnimationSource
    val target = WindowInsets.imeAnimationTarget
    return remember(source, target) { ImeAnimationInsets(source, target) }
}
