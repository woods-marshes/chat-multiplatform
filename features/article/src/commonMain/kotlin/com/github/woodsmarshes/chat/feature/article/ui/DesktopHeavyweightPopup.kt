package com.github.woodsmarshes.chat.feature.article.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset

@Composable
expect fun DesktopHeavyweightPopup(
    alignment: Alignment = Alignment.BottomEnd,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable () -> Unit
)