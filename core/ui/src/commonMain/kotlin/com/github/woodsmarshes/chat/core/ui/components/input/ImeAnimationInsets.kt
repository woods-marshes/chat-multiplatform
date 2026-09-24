package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

internal class ImeAnimationInsets(val source: WindowInsets, val target: WindowInsets)

@Composable
internal expect fun rememberImeAnimationInsets(): ImeAnimationInsets
