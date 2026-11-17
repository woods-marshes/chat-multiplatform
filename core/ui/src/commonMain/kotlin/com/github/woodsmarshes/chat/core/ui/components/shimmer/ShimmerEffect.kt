package com.github.woodsmarshes.chat.core.ui.components.shimmer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
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

/**
 * Shared animation clock so a whole skeleton list animates on one timeline
 * instead of spawning one [rememberInfiniteTransition] per skeleton bar.
 */
class ShimmerClock(val progress: State<Float>)

/**
 * Provided by list-level skeletons ([ListSkeleton]); individual skeleton bars
 * fall back to a private clock when no provider is present.
 */
val LocalShimmerClock = staticCompositionLocalOf<ShimmerClock?> { null }

@Composable
fun rememberShimmerClock(): ShimmerClock {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    return remember { ShimmerClock(progress) }
}

/**
 * Shimmer overlay driven by [clock] (or a fallback private clock). Reads the
 * progress in the draw phase: animation frames never recompose callers.
 *
 * Replaces the former `composed { }` implementation, which re-ran its lambda
 * on every recomposition and rebuilt the brush each time.
 */
@Composable
fun Modifier.shimmer(
    cornerRadius: Dp = 0.dp,
    colors: List<Color>? = null,
    clock: ShimmerClock? = null,
    isLoading: Boolean = true,
): Modifier {
    if (!isLoading) return this
    val resolvedClock = clock ?: rememberShimmerClock()
    val colorScheme = MaterialTheme.colorScheme
    // Remembered: the default list is rebuilt on every call, so a bare
    // `colors ?: listOf(...)` would invalidate the brush's remember key each
    // recomposition and rebuild the gradient.
    val shimmerColors = remember(colors, colorScheme) {
        colors ?: listOf(
            colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
            colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        )
    }
    val density = LocalDensity.current
    val cornerPx = with(density) { cornerRadius.toPx() }
    val gradientLengthPx = with(density) { 320.dp.toPx() }
    val brush = remember(shimmerColors, gradientLengthPx) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(gradientLengthPx, 0f),
        )
    }
    return drawWithCache {
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
            val currentProgress = resolvedClock.progress.value
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
