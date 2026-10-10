package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun ChatAppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    if (isMiuixTheme()) {
        val miuixCorner = if (cornerRadius == 12.dp) 16.dp else cornerRadius
        if (onClick != null) {
            MiuixCard(
                modifier = modifier.fillMaxWidth(),
                cornerRadius = miuixCorner,
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = onClick,
            ) {
                content()
            }
        } else {
            MiuixCard(
                modifier = modifier.fillMaxWidth(),
                cornerRadius = miuixCorner,
            ) {
                content()
            }
        }
    } else {
        val shape = RoundedCornerShape(cornerRadius)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick)
                    else Modifier
                ),
        ) {
            content()
        }
    }
}

