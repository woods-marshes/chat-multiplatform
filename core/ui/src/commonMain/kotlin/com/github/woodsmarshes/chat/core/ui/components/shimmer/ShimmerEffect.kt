package com.github.woodsmarshes.chat.core.ui.components.shimmer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val defaultShimmerColors = listOf(
    Color(0xFFE0E0E0),
    Color(0xFFF5F5F5),
    Color(0xFFE0E0E0),
)

fun Modifier.shimmer(
    isLoading: Boolean = true,
    cornerRadius: Dp = 0.dp,
    colors: List<Color>? = null,
): Modifier = composed {
    if (!isLoading) return@composed this

    val shimmerColors = colors ?: defaultShimmerColors
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )

    val brush = remember(shimmerColors) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(300f, 0f),
        )
    }

    drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                brush = brush,
                topLeft = Offset(translateAnim, 0f),
                size = Size(size.width.coerceAtLeast(400f), size.height),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                alpha = 0.6f,
            )
        }
    }
}
