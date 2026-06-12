package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.woodsmarshes.chat.model.ReplyPreview
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.silk.components.icons.fa.FaReply
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px

@Composable
fun ReplyPreview(
    replyToMessage: ReplyPreview?,
    modifier: Modifier = Modifier,
    contentColor: Color
) {
    if (replyToMessage == null) return

    val previewText = remember(replyToMessage.formatReplyPreviewMessage) {
        val text = replyToMessage.formatReplyPreviewMessage
        if (text.length > 50) "${text.take(50)}..." else text
    }

    Row(
        modifier = modifier.padding(leftRight = 8.px, topBottom = 4.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.px)
    ) {
        FaReply(Modifier.fontSize(14.px).color(contentColor))
        Column {
            SpanText(
                text = replyToMessage.sender?.showName ?: "Unknown User",
                modifier = Modifier
                    .fontWeight(FontWeight.Bold)
                    .color(contentColor.darkened(0.8f))
                    .fontSize(0.9.em)
            )
            SpanText(
                text = previewText,
                modifier = Modifier
                    .color(contentColor)
                    .fontSize(0.9.em)
            )
        }
    }
}