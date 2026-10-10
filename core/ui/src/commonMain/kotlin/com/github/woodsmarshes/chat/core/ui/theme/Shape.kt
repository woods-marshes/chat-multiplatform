package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shape tokens for Material 3 and Miuix styles.
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

    val Miuix = ShapeTokens(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
        ownBubble = RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp),
        otherBubble = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
        mediaBubble = RoundedCornerShape(18.dp),
    )
}

