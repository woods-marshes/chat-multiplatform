package com.github.woodsmarshes.chat.core.ui.utils

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun rememberScreenCornerRadius(): Dp {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return 0.dp
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val insets = view.rootWindowInsets ?: return 0.dp

    // 获取左上角的物理圆角信息
    val topLeftCorner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
    return if (topLeftCorner != null) {
        with(density) { topLeftCorner.radius.toDp() }
    } else {
        0.dp // 直角屏幕返回 0
    }
}