package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors

@Composable
fun ChatAppCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val bubbleColors = LocalBubbleColors.current
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bubbleColors.inputFieldBackground)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
    ) {
        content()
    }
}
