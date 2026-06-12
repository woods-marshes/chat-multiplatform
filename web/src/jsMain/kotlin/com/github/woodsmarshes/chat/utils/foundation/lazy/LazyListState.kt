package com.github.woodsmarshes.chat.utils.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
class LazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0
) {
    // 当前滚动位置 (像素)
    private val _scrollOffset = MutableStateFlow(initialFirstVisibleItemScrollOffset.toDouble())
    val scrollOffset = _scrollOffset.asStateFlow()

    // 可见项的索引范围
    var visibleItemsRange by mutableStateOf<IntRange>(IntRange.EMPTY)
        private set

    // 列表项的平均高度，用于估算总高度
    var averageItemHeight by mutableStateOf(72.0) // 给一个初始估算值

    internal fun onScroll(newScrollOffset: Double) {
        _scrollOffset.value = newScrollOffset
    }

    internal fun updateVisibleItemsRange(range: IntRange) {
        if (range != visibleItemsRange) {
            visibleItemsRange = range
        }
    }
}

@Composable
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
): LazyListState {
    return remember {
        LazyListState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)
    }
}