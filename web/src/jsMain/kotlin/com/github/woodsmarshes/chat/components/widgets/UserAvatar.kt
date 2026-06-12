package com.github.woodsmarshes.chat.components.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.woodsmarshes.chat.network.api.Endpoints.toFullUrl
import com.varabyte.kobweb.compose.css.CSSLengthOrPercentageNumericValue
import com.varabyte.kobweb.compose.css.ObjectFit
import com.varabyte.kobweb.compose.dom.ref
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.backgroundColor
import com.varabyte.kobweb.compose.ui.modifiers.borderRadius
import com.varabyte.kobweb.compose.ui.modifiers.color
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fontSize
import com.varabyte.kobweb.compose.ui.modifiers.objectFit
import com.varabyte.kobweb.compose.ui.modifiers.onClick
import com.varabyte.kobweb.compose.ui.modifiers.onContextMenu
import com.varabyte.kobweb.compose.ui.modifiers.opacity
import com.varabyte.kobweb.compose.ui.modifiers.size
import com.varabyte.kobweb.compose.ui.thenIf
import com.varabyte.kobweb.silk.components.graphics.Image
import com.varabyte.kobweb.silk.components.graphics.ImageLoading
import com.varabyte.kobweb.silk.components.icons.fa.FaUser
import kotlinx.browser.window
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.times
import org.w3c.dom.HTMLImageElement

@Composable
fun UserAvatar(
    modifier: Modifier = Modifier,
    avatarUrl: String?,
    description: String = "User Avatar",
    size: CSSLengthOrPercentageNumericValue = 40.px,
    onClick: (url: String?) -> Unit = {},
    onContextMenu: (url: String?) -> Unit = {}
) {
    var isLoading by remember(avatarUrl) { mutableStateOf(true) }
    var hasError by remember(avatarUrl) { mutableStateOf(false) }

    val fullUrl = remember(avatarUrl) { avatarUrl?.toFullUrl() }

    Box(
        modifier = modifier
            .size(size)
            .borderRadius(50.percent) // 等同于 clip(CircleShape)
            .backgroundColor(Colors.LightGray) // 占位符的背景色
            // 应用点击事件
            .onClick {
                onClick(fullUrl)
            }
            .onContextMenu {
                it.preventDefault()
                onContextMenu(fullUrl)
            },
        contentAlignment = Alignment.Center
    ) {
        if (fullUrl != null && !hasError) {
            Image(
                src = fullUrl,
                description = description,
                modifier = Modifier
                    .fillMaxSize()
                    .objectFit(ObjectFit.Cover)
                    .thenIf(isLoading) { Modifier.opacity(0) },
                loading = ImageLoading.Lazy, // 使用浏览器懒加载以优化性能
                ref = ref { imageElement: HTMLImageElement ->
                    // 清理旧的监听器以防元素重用
                    imageElement.onload = null
                    imageElement.onerror = null

                    // 如果图片已经加载完成 (可能来自缓存)，立即更新状态
                    if (imageElement.complete) {
                        isLoading = false
                        return@ref
                    }

                    // 添加新的事件监听器
                    imageElement.onload = {
                        window.requestAnimationFrame { isLoading = false }
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

        // --- 占位符逻辑 ---
        if (isLoading || hasError || fullUrl == null) {
            if (isLoading && !hasError && fullUrl != null) {
                SpinnerIcon()
            } else {
                // 加载失败或没有URL时，显示一个默认的 F/A 用户图标
                FaUser(Modifier.fontSize(size * 0.5).color(Colors.White))
            }
        }
    }
}