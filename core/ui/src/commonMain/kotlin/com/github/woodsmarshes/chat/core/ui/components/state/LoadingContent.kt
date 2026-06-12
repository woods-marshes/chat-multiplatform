package com.github.woodsmarshes.chat.core.ui.components.state
import com.github.woodsmarshes.chat.core.ui.components.shimmer.ListSkeleton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.woodsmarshes.chat.core.ui.theme.LocalBubbleColors
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    message: String = "加载中...",
    useShimmer: Boolean = false,
    shimmerCount: Int = 8,
) {
    if (useShimmer) {
        ListSkeleton(count = shimmerCount)
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                InfiniteProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    color = LocalBubbleColors.current.timestampColor,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun LoadingItem(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        InfiniteProgressIndicator()
    }
}
