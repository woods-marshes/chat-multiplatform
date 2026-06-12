package com.github.woodsmarshes.chat.components.widgets.message

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.model.GroupUserRelationType
import com.github.woodsmarshes.chat.model.UserProfile
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.TextOverflow
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.flexShrink
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.margin
import com.varabyte.kobweb.compose.ui.modifiers.minWidth
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textOverflow
import com.varabyte.kobweb.compose.ui.modifiers.whiteSpace
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import com.varabyte.kobweb.silk.theme.shapes.Shape
import org.jetbrains.compose.web.css.CSSLengthValue
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px

@Composable
fun SenderInfoDisplay(
    sender: UserProfile?,
    modifier: Modifier = Modifier,
    fontSize: CSSLengthValue = 0.85.em,
    textColor: Color? = null
) {
    val finalTextColor = textColor ?: ColorMode.current.toPalette().color.toRgb().copyf(alpha = 0.7f)

    Row(
        modifier = modifier.minWidth(0.px),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.px)
    ) {
        if (sender != null) {
            SpanText(
                text = sender.showName,
                modifier = Modifier
                    .fontSize(fontSize)
                    .color(finalTextColor)
                    .whiteSpace(WhiteSpace.NoWrap)
                    .overflow(Overflow.Hidden)
                    .textOverflow(TextOverflow.Ellipsis)
                    .flexShrink(1)
            )

            // 身份胶囊
            sender.groupUserRelationType.takeIf { type ->
                type == GroupUserRelationType.ADMIN ||
                        type == GroupUserRelationType.BUREAUCRAT
            }?.let { relationType ->
                RoleBadge(
                    relationType = relationType,
                    modifier = Modifier.flexShrink(0)
                )
            }
        }
    }
}

/**
 * 一个私有的 Composable，用于渲染身份胶囊。
 */
@Composable
private fun RoleBadge(
    relationType: GroupUserRelationType,
    modifier: Modifier = Modifier,
) {
    val sitePalette = ColorMode.current.toSitePalette()
    val (text, backgroundColor, textColor) = when (relationType) {
        GroupUserRelationType.ADMIN -> Triple("ADMIN", sitePalette.brand.primary.toRgb().copyf(alpha = 0.2f), sitePalette.brand.primary)
        GroupUserRelationType.BUREAUCRAT -> Triple("OWNER", sitePalette.brand.accent.toRgb().copyf(alpha = 0.2f), sitePalette.brand.accent.darkened(0.2f))
        else -> Triple(null, Colors.Transparent, Colors.Transparent)
    }

    if (text != null) {
        Box(
            modifier = modifier
                .backgroundColor(backgroundColor)
                .borderRadius(4.px)
                .padding(leftRight = 6.px, topBottom = 1.px)
        ) {
            SpanText(
                text = text,
                modifier = Modifier
                    .color(textColor)
                    .fontSize(0.7.em)
                    .fontWeight(FontWeight.Bold)
            )
        }
    }
}