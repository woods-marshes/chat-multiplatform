package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.network.api.Endpoints.toFullUrl
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.animation
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.objectFit
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.rotate
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.graphics.ImageLoading
import com.varabyte.kobweb.silk.components.icons.fa.FaSpinner
import com.varabyte.kobweb.silk.components.icons.fa.FaUser
import com.varabyte.kobweb.silk.components.icons.fa.FaUsers
import com.varabyte.kobweb.silk.components.icons.fa.IconSize
import com.varabyte.kobweb.silk.style.animation.Keyframes
import com.varabyte.kobweb.silk.style.animation.toAnimation
import kotlinx.browser.window
import org.jetbrains.compose.web.css.AnimationTimingFunction
import org.jetbrains.compose.web.css.deg
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.s
import org.jetbrains.compose.web.css.times

@Composable
fun ConversationAvatar(
    modifier: Modifier = Modifier,
    avatarUrl: String?,
    isGroup: Boolean,
    size: CSSLengthOrPercentageNumericValue = 48.px,
) {
    var isLoading by remember(avatarUrl) { mutableStateOf(true) }
    var hasError by remember(avatarUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .borderRadius(50.percent) // 圆形头像
            .backgroundColor(Colors.LightGray), // 默认背景色
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null && !hasError) {
            Image(
                src = avatarUrl.toFullUrl(),
                modifier = Modifier
                    .fillMaxSize()
                    .objectFit(ObjectFit.Cover)
                    .thenIf(isLoading || hasError) { Modifier.opacity(0) },
                loading = ImageLoading.Lazy,
                description = "Conversation Avatar",
                ref = ref { imageElement ->

                    imageElement.onload = null
                    imageElement.onerror = null

                    imageElement.onload = {
                        window.requestAnimationFrame {
                            isLoading = false
                            hasError = false
                        }
                    }
                    imageElement.onerror = { _, _, _, _, _ ->
                        window.requestAnimationFrame {
                            isLoading = false
                            hasError = true
                        }
                    }
                }
            )
        }

        if (isLoading || hasError || avatarUrl == null) {
            if (isLoading && !hasError) {
                SpinnerIcon()
            } else {
                val iconModifier = Modifier.fontSize(size * 0.6).color(Colors.White)
                if (isGroup) {
                    FaUsers(iconModifier)
                } else {
                    FaUser(iconModifier)
                }
            }
        }
    }
}

@Composable
fun SpinnerIcon(modifier: Modifier = Modifier, size: IconSize = IconSize.LG) {
    FaSpinner(
        modifier = Modifier
            .animation(SpinAnimation.toAnimation(
                duration = 1.s,
                timingFunction = AnimationTimingFunction.Linear,
                iterationCount = AnimationIterationCount.Infinite
            ))
            .then(modifier),
        size = size
    )
}