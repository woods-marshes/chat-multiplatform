package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset

@Composable
actual fun DesktopHeavyweightPopup(
    alignment: Alignment,
    offset: IntOffset,
    content: @Composable (() -> Unit),
) {
    content()
}