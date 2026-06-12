package com.github.woodsmarshes.chat.components.widgets.message.item

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.model.TimeMessage
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px
import kotlin.time.Instant

@Composable
fun TimeMessageItem(
    modifier: Modifier = Modifier,
    message: TimeMessage,
    formatDateTime: (instant: Instant) -> String,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.px),
        contentAlignment = Alignment.Center
    ) {
        SpanText(
            text = formatDateTime(message.detail.timestamp),
            modifier = Modifier
                .backgroundColor(Colors.LightGray.copyf(alpha = 0.4f))
                .borderRadius(4.px)
                .padding(leftRight = 8.px, topBottom = 4.px)
                .fontSize(0.75.em)
                .color(Colors.DarkSlateGray)
        )
    }
}