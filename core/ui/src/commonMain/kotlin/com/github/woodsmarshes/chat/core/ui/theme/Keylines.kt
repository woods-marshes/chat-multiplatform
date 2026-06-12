package com.github.woodsmarshes.chat.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距令牌（参考 Jetcaster 的 Keylines.kt）。
 *
 * 提供一致的间距梯度，避免在各页面硬编码 dp 值。
 */
object Keylines {
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val XLarge: Dp = 32.dp
    val XXLarge: Dp = 48.dp

    /** 标准水平边距 */
    val Horizontal: Dp = 16.dp

    /** 标准垂直边距 */
    val Vertical: Dp = 12.dp
}
