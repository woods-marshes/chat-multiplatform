package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultGlowColors = listOf(
    Color(0xFF4285F4), // Google blue
    Color(0xFFEA4335), // Google red
    Color(0xFFFBBC05), // Google yellow
    Color(0xFF34A853), // Google green
    Color(0xFF4285F4), // wrap-around
)

@Composable
fun ScreenEdgeGlow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    colors: List<Color> = DefaultGlowColors,
) {
    if (!enabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "NeonTransition")

    // 1. Color flow: the static sweep gradient is rotated a full turn per
    //    cycle (equivalent to the old per-frame color-shifted list, without
    //    reallocating a list and a Brush every frame).
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shimmer"
    )

    // 2. Breathing pulse.
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Box(modifier = modifier.fillMaxSize()) {

        // =================【第一层：超柔和的大范围泛光 (Atmospheric Glow)】=================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(24.dp) // 大半径模糊，让色彩像雾气一样温柔散开
                .drawWithCache {
                    // 使用超粗画笔 (16.dp)，经模糊后形成非常宽、极具氛围感的霓虹晕染
                    val strokePx = 16.dp.toPx()
                    val inset = strokePx / 2f
                    val brush = Brush.sweepGradient(
                        colors = colors,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                    onDrawBehind {
                        rotate(degrees = shimmerProgress * 360f) {
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(inset, inset),
                                size = Size(
                                    width = size.width - strokePx,
                                    height = size.height - strokePx,
                                ),
                                style = Stroke(width = strokePx),
                                alpha = pulseAlpha * 0.7f, // 氛围光亮度稍作收敛，避免过曝
                                cornerRadius = CornerRadius(maxOf(0f, cornerRadius.toPx() - inset)),
                            )
                        }
                    }
                }
        )

        // =================【第二层：极亮的核心灯管 (Bright Neon Core)】=================
        // 霓虹灯的灵魂：极高饱和度和亮度的超窄核心，紧贴屏幕物理边缘
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(1.5.dp) // 极微弱模糊：既消除了线条锯齿，又锁死了 100% 的色彩能量
                .drawWithCache {
                    // 使用超窄画笔 (3.5.dp)，紧贴最外边缘
                    val strokePx = 3.5.dp.toPx()
                    val inset = strokePx / 2f
                    val brush = Brush.sweepGradient(
                        colors = colors,
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                    onDrawBehind {
                        rotate(degrees = shimmerProgress * 360f) {
                            drawRoundRect(
                                brush = brush,
                                topLeft = Offset(inset, inset),
                                size = Size(
                                    width = size.width - strokePx,
                                    height = size.height - strokePx,
                                ),
                                style = Stroke(width = strokePx),
                                alpha = pulseAlpha * 1.0f, // 保持最大亮度
                                cornerRadius = CornerRadius(maxOf(0f, cornerRadius.toPx() - inset)),
                            )
                        }
                    }
                }
        )
    }
}
