package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette

data class MessageColors(
    val bubbleColor: Color,
    val textColor: Color,
    val replyBackgroundColor: Color,
    val replyContentColor: Color
)

@Composable
fun messageColors(isOwn: Boolean): MessageColors {
    val sitePalette = ColorMode.current.toSitePalette()
    val palette = ColorMode.current.toPalette()

    return if (isOwn) {
        MessageColors(
            bubbleColor = sitePalette.brand.primary,
            textColor = palette.color.inverted(),
            replyBackgroundColor = sitePalette.brand.primary.darkened(0.2f).toRgb().copyf(alpha = 0.5f),
            replyContentColor = palette.color.inverted().toRgb().copyf(alpha = 0.9f)
        )
    } else {
        MessageColors(
            bubbleColor = sitePalette.subtle,
            textColor = palette.color,
            replyBackgroundColor = palette.border,
            replyContentColor = palette.color.toRgb().copyf(alpha = 0.8f)
        )
    }
}