package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.woodsmarshes.chat.core.ui.theme.isMiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay

@Composable
fun ScreenEdgeGlow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    color: Color = if (isMiuixTheme()) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
) {
    var visible by remember(enabled) { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (enabled) {
            delay(180)
            visible = true
        }
    }
    if (!enabled || !visible) return

    val background = if (isMiuixTheme()) MiuixTheme.colorScheme.background else MaterialTheme.colorScheme.background
    val isDarkBackground = background.luminance() < 0.5f
    val haloAlpha = if (isDarkBackground) 0.4f else 0.65f
    val coreAlpha = if (isDarkBackground) 0.75f else 1f

    val infiniteTransition = rememberInfiniteTransition(label = "EdgeGlowTransition")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        // InfiniteTransition settles at this dim value when the system disables animations.
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(modifier = modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp)
                .drawWithCache {
                    val strokePx = 16.dp.toPx()
                    val inset = strokePx / 2f
                    onDrawBehind {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(inset, inset),
                            size = Size(
                                width = size.width - strokePx,
                                height = size.height - strokePx,
                            ),
                            style = Stroke(width = strokePx),
                            alpha = pulseAlpha * haloAlpha,
                            cornerRadius = CornerRadius(maxOf(0f, cornerRadius.toPx() - inset)),
                        )
                    }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val strokePx = 3.5.dp.toPx()
                    val inset = strokePx / 2f
                    onDrawBehind {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(inset, inset),
                            size = Size(
                                width = size.width - strokePx,
                                height = size.height - strokePx,
                            ),
                            style = Stroke(width = strokePx),
                            alpha = pulseAlpha * coreAlpha,
                            cornerRadius = CornerRadius(maxOf(0f, cornerRadius.toPx() - inset)),
                        )
                    }
                }
        )
    }
}
