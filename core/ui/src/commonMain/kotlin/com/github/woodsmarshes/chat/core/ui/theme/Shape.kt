package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 形状令牌（参考 Jetcaster 的 Shape.kt 设计）。
 */
@Immutable
data class ShapeTokens(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val extraLarge: Shape,
    val ownBubble: Shape,
    val otherBubble: Shape,
    val mediaBubble: Shape,
)

val LocalShapeTokens = staticCompositionLocalOf<ShapeTokens> {
    error("LocalShapeTokens not provided — 请确保 ChatTheme 包裹了此内容")
}

object ShapeDefaults {
    val Default = ShapeTokens(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
        ownBubble = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
        otherBubble = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
        mediaBubble = RoundedCornerShape(16.dp),
    )
}
