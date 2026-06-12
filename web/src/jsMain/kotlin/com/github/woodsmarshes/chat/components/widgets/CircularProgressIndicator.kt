package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.animation
import com.varabyte.kobweb.compose.ui.modifiers.border
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.rotate
import com.varabyte.kobweb.silk.style.CssStyle
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.base
import com.varabyte.kobweb.silk.style.toModifier
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.deg
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.s

val SpinAnimation = Keyframes {
    from { Modifier.rotate(0.deg) }
    to { Modifier.rotate(360.deg) }
}

val CircularProgressIndicatorStyle = CssStyle.base {
    Modifier
        .border(width = 3.px, style = LineStyle.Solid, color = Colors.LightGray)
        .borderRadius(50.percent)
        .animation(
            SpinAnimation.toAnimation(
                duration = 1.s,
                timingFunction = AnimationTimingFunction.Linear,
                iterationCount = AnimationIterationCount.Infinite
            )
        )
}

@Composable
fun CircularProgressIndicator(modifier: Modifier = Modifier) {
    Box(CircularProgressIndicatorStyle.toModifier().then(modifier))
}