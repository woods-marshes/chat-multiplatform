package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.css.TextOverflow
import com.varabyte.kobweb.compose.css.WhiteSpace
import com.varabyte.kobweb.compose.foundation.layout.Arrangement
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.BoxScope
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderBottom
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.textOverflow
import com.varabyte.kobweb.compose.ui.modifiers.whiteSpace
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.palette.border
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.em
import org.jetbrains.compose.web.css.px

val TopBarStyle = CssStyle.base {
    val sitePalette = colorMode.toSitePalette()
    Modifier
        .fillMaxWidth()
        .height(3.5.cssRem)
        .backgroundColor(sitePalette.subtle)
        .borderBottom(1.px, LineStyle.Solid, colorMode.toPalette().border)
        .padding(leftRight = 1.cssRem)
}

val TopBarTitleStyle = CssStyle.base {
    Modifier
        .fontSize(1.1.em)
        .fontWeight(FontWeight.SemiBold)
        .whiteSpace(WhiteSpace.NoWrap)
        .textOverflow(TextOverflow.Ellipsis)
}

/**
 * 一个现代化的顶部应用栏组件。
 *
 * @param title 中间的标题内容。
 * @param navigationIcon 左侧的导航图标，通常是返回或菜单按钮。如果为 null，则不显示。
 * @param actionIcon 右侧的操作图标，通常是菜单或设置按钮。如果为 null，则不显示。
 * @param onNavigationClick 导航图标的点击事件。
 * @param onActionClick 操作图标的点击事件。
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: @Composable BoxScope.() -> Unit,
    navigationIcon: @Composable (() -> Unit)? = null,
    actionIcon: @Composable (() -> Unit)? = null,
    onNavigationClick: () -> Unit = {},
    onActionClick: () -> Unit = {},
) {
    Row(
        modifier = TopBarStyle.toModifier().then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.5.cssRem)
    ) {
        if (navigationIcon != null) {
            IconButton(onClick = onNavigationClick, content = navigationIcon)
        }

        Box(
            Modifier
                .weight(1f)
                .padding(leftRight = 0.5.cssRem)
                .overflow(Overflow.Hidden),
            contentAlignment = Alignment.Center
        ) {
            Box(TopBarTitleStyle.toModifier()) {
                title()
            }
        }

        if (actionIcon != null) {
            IconButton(onClick = onActionClick, content = actionIcon)
        }
    }
}