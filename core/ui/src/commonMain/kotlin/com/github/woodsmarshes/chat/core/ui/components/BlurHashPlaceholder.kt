package com.github.woodsmarshes.chat.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.woodsmarshes.chat.core.common.utils.BlurHashDecoder

private const val DEFAULT_DECODE_WIDTH = 32
private const val DEFAULT_DECODE_HEIGHT = 20

/**
 * Renders a decoded BlurHash as a low-resolution mosaic placeholder.
 * Draws one rect per decoded cell; the grid is intentionally coarse because
 * BlurHash output is already a soft approximation of the source image.
 *
 * Returns without drawing when [blurHash] is null or malformed.
 */
@Composable
fun BlurHashPlaceholder(
    blurHash: String?,
    modifier: Modifier = Modifier,
    decodeWidth: Int = DEFAULT_DECODE_WIDTH,
    decodeHeight: Int = DEFAULT_DECODE_HEIGHT,
) {
    val pixels =
        remember(blurHash, decodeWidth, decodeHeight) {
            blurHash?.let {
                runCatching { BlurHashDecoder.decode(it, decodeWidth, decodeHeight) }.getOrNull()
            }
        } ?: return

    Canvas(modifier = modifier) {
        drawBlurHashCells(pixels, decodeWidth, decodeHeight)
    }
}

private fun DrawScope.drawBlurHashCells(
    pixels: IntArray,
    decodeWidth: Int,
    decodeHeight: Int,
) {
    val cellWidth = size.width / decodeWidth
    val cellHeight = size.height / decodeHeight
    // Overlap cells by a fraction of a pixel to hide seams between rects.
    val drawWidth = cellWidth + 1f
    val drawHeight = cellHeight + 1f
    for (y in 0 until decodeHeight) {
        for (x in 0 until decodeWidth) {
            val argb = pixels[y * decodeWidth + x]
            drawRect(
                color =
                    Color(
                        red = ((argb shr 16) and 0xFF) / 255f,
                        green = ((argb shr 8) and 0xFF) / 255f,
                        blue = (argb and 0xFF) / 255f,
                    ),
                topLeft = Offset(x * cellWidth, y * cellHeight),
                size = Size(drawWidth, drawHeight),
            )
        }
    }
}
