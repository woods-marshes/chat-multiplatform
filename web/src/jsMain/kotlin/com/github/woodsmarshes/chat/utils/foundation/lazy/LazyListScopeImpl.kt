package com.github.woodsmarshes.chat.utils.foundation.lazy

import androidx.compose.runtime.Composable

internal class LazyListItem(
    val key: Any,
    val contentType: Any?,
    val content: @Composable LazyItemScope.() -> Unit
)
internal class LazyListScopeImpl : LazyListScop {
    val items = mutableListOf<LazyListItem>()
    private var nextDefaultKey = 0

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        items.add(LazyListItem(key ?: nextDefaultKey++, contentType, content))
    }

    override fun items(
        count: Int,
        key: ((index: Int) -> Any)?,
        contentType: (index: Int) -> Any?,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) {
        for (i in 0 until count) {
            val finalKey = key?.invoke(i) ?: (nextDefaultKey + i)
            val finalContentType = contentType(i)
            item(finalKey, finalContentType) {
                val scope = object : LazyItemScope {
                    override val index: Int = i
                    override val key: Any = finalKey
                }
                itemContent(scope, i)
            }
        }
        nextDefaultKey += count
    }
}