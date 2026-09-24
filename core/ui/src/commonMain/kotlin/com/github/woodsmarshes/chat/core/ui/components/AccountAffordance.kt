package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable

/**
 * Slot rendering the signed-in user's avatar inside top app bars.
 *
 * Provided by the app shell on compact layouts, where the reference design
 * keeps the account affordance in the top bar. On medium+ layouts the shell
 * pins the avatar to the navigation rail bottom instead and provides null, so
 * the affordance is not duplicated.
 */
val LocalAccountAffordance: ProvidableCompositionLocal<(@Composable () -> Unit)?> =
    compositionLocalOf { null }
