package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import com.github.woodsmarshes.chat.UncoloredButtonVariant
import com.github.woodsmarshes.chat.model.viewmodel.login.AuthScreenState
import com.github.woodsmarshes.chat.toSitePalette
import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.css.JustifyItems
import com.varabyte.kobweb.compose.css.TextAlign
import com.varabyte.kobweb.compose.css.Transition
import com.varabyte.kobweb.compose.css.functions.calc
import com.varabyte.kobweb.compose.css.justifyItems
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.bottom
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.display
import com.varabyte.kobweb.compose.ui.modifiers.flexGrow
import com.varabyte.kobweb.compose.ui.modifiers.fontWeight
import com.varabyte.kobweb.compose.ui.modifiers.gridColumn
import com.varabyte.kobweb.compose.ui.modifiers.gridRow
import com.varabyte.kobweb.compose.ui.modifiers.gridTemplateColumns
import com.varabyte.kobweb.compose.ui.modifiers.left
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.compose.ui.modifiers.position
import com.varabyte.kobweb.compose.ui.modifiers.setVariable
import com.varabyte.kobweb.compose.ui.modifiers.textAlign
import com.varabyte.kobweb.compose.ui.modifiers.top
import com.varabyte.kobweb.compose.ui.modifiers.transition
import com.varabyte.kobweb.compose.ui.modifiers.translateX
import com.varabyte.kobweb.compose.ui.modifiers.width
import com.varabyte.kobweb.compose.ui.modifiers.zIndex
import com.varabyte.kobweb.compose.ui.styleModifier
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.ButtonVars
import com.varabyte.kobweb.silk.components.text.SpanText
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.palette.color
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.cssRem
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gridTemplateColumns
import org.jetbrains.compose.web.css.ms
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import kotlin.js.unsafeCast

val AuthModeToggleStyle = CssStyle.base {
    val sitePalette = colorMode.toSitePalette()
    Modifier
        .position(Position.Relative) // <-- 关键：为绝对定位的滑块提供参考系
        .backgroundColor(sitePalette.subtle)
        .borderRadius(999.px)
        .padding(4.px)
}

// 切换器中每个按钮的样式
val AuthModeButtonStyle = CssStyle.base {
    Modifier
        .width(50.percent) // <-- 关键：固定宽度为 50%
        .borderRadius(999.px)
        .padding(topBottom = 0.5.cssRem, leftRight = 1.5.cssRem)
        .fontWeight(FontWeight.SemiBold)
        .textAlign(TextAlign.Center) // 确保文本居中
        .transition(Transition.of("color", 200.ms))
        .zIndex(1)
}

// 滑动背景的样式
val AuthModeSliderStyle = CssStyle.base {
    val sitePalette = colorMode.toSitePalette()
    Modifier
        .position(Position.Absolute)
        .top(4.px)
        .bottom(4.px)
        .width(50.percent) // <-- 宽度也是 50%
        .borderRadius(999.px)
        .backgroundColor(sitePalette.brand.primary)
        .transition(Transition.of("transform", 250.ms, AnimationTimingFunction.EaseInOut))
        .zIndex(0)
}

@Composable
fun AuthModeToggle(currentMode: AuthScreenState, onModeChange: (AuthScreenState) -> Unit) {
    val sitePalette = ColorMode.current.toSitePalette()

    Row(AuthModeToggleStyle.toModifier()) {
        // 滑动的背景块
        Box(
            AuthModeSliderStyle.toModifier()
                .translateX(if (currentMode == AuthScreenState.LOGIN) 0.percent else 100.percent)
        )

        // 登录按钮
        Button(
            onClick = { onModeChange(AuthScreenState.LOGIN) },
            modifier = AuthModeButtonStyle.toModifier()
                .setVariable(
                    ButtonVars.Color,
                    if (currentMode == AuthScreenState.LOGIN) {
                        // 选中时：白色
                        Colors.White
                    } else {
                        // 未选中时：使用一个半透明的、当前主题的文本颜色，确保对比度
                        ColorMode.current.toPalette().color.toRgb().copyf(alpha = 0.7f)
                    }
                ),
            variant = UncoloredButtonVariant
        ) {
            SpanText("Login")
        }

        // 注册按钮
        Button(
            onClick = { onModeChange(AuthScreenState.REGISTER) },
            modifier = AuthModeButtonStyle.toModifier()
                .setVariable(
                    ButtonVars.Color,
                    if (currentMode == AuthScreenState.REGISTER) {
                        Colors.White
                    } else {
                        ColorMode.current.toPalette().color.toRgb().copyf(alpha = 0.7f)
                    }
                ),
            variant = UncoloredButtonVariant
        ) {
            SpanText("Register")
        }
    }
}