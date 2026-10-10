package com.github.woodsmarshes.chat.core.ui.components.shimmer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(
    isLoading: Boolean = true,
    cornerRadius: Dp = 0.dp,
    colors: List<Color>? = null,
): Modifier = composed {
    if (!isLoading) return@composed this

    val colorScheme = MaterialTheme.colorScheme
    val shimmerColors = colors ?: listOf(
        colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
        colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    )
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val gradientLengthPx = with(density) { 320.dp.toPx() }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    val brush = remember(shimmerColors, gradientLengthPx) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(gradientLengthPx, 0f),
        )
    }

    drawWithCache {
        val totalDistance = size.width + gradientLengthPx
        val outline = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(Offset.Zero, size),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            )
        }
        onDrawWithContent {
            val currentProgress = progress
            val startX = -gradientLengthPx + totalDistance * currentProgress
            drawContent()
            clipPath(outline) {
                drawRect(
                    brush = brush,
                    topLeft = Offset(startX, 0f),
                    size = Size(gradientLengthPx, size.height),
                    alpha = 0.75f,
                )
            }
        }
    }
}
