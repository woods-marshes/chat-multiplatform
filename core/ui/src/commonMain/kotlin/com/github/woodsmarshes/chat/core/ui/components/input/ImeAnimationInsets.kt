package com.github.woodsmarshes.chat.core.ui.components.input

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * IME geometry used to coordinate the input panel with the soft keyboard.
 *
 * @property source IME insets at the start of the current keyboard animation.
 * @property target IME insets at the end of the current keyboard animation.
 * @property reportsImeInsets whether the platform reports soft keyboard height through
 * [WindowInsets.ime]. When false, no keyboard geometry will ever arrive, so a keyboard
 * request must collapse the panel immediately instead of reserving space for it.
 */
internal class ImeAnimationInsets(
    val source: WindowInsets,
    val target: WindowInsets,
    val reportsImeInsets: Boolean,
)

@Composable
internal expect fun rememberImeAnimationInsets(): ImeAnimationInsets
