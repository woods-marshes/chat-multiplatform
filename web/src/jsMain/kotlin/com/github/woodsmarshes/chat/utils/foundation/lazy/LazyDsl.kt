package com.github.woodsmarshes.chat.utils.foundation.lazy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.varabyte.kobweb.compose.css.Overflow
import com.varabyte.kobweb.compose.dom.ElementRefScope
import com.varabyte.kobweb.compose.dom.refScope
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.height
import com.varabyte.kobweb.compose.ui.modifiers.left
import com.varabyte.kobweb.compose.ui.modifiers.overflow
import com.varabyte.kobweb.compose.ui.modifiers.position
import com.varabyte.kobweb.compose.ui.modifiers.top
import org.jetbrains.compose.web.css.CSSLengthValue
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.px
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/** Receiver scope which is used by [LazyColumn] and [LazyRow]. */
@LazyScopeMarker
interface LazyListScop{
    /**
     * Adds a single item.
     *
     * @param key a stable and unique key representing the item. Using the same key for multiple
     *   items in the list is not allowed. Type of the key should be saveable via Bundle on Android.
     *   If null is passed the position in the list will represent the key. When you specify the key
     *   the scroll position will be maintained based on the key, which means if you add/remove
     *   items before the current visible item the item with the given key will be kept as the first
     *   visible one. This can be overridden by calling 'requestScrollToItem' on the
     *   'LazyListState'.
     * @param contentType the type of the content of this item. The item compositions of the same
     *   type could be reused more efficiently. Note that null is a valid type and items of such
     *   type will be considered compatible.
     * @param content the content of the item
     */
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        error("The method is not implemented")
    }

    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        item(key, null, content)
    }

    /**
     * Adds a [count] of items.
     *
     * @param count the items count
     * @param key a factory of stable and unique keys representing the item. Using the same key for
     *   multiple items in the list is not allowed. Type of the key should be saveable via Bundle on
     *   Android. If null is passed the position in the list will represent the key. When you
     *   specify the key the scroll position will be maintained based on the key, which means if you
     *   add/remove items before the current visible item the item with the given key will be kept
     *   as the first visible one. This can be overridden by calling 'requestScrollToItem' on the
     *   'LazyListState'.
     * @param contentType a factory of the content types for the item. The item compositions of the
     *   same type could be reused more efficiently. Note that null is a valid type and items of
     *   such type will be considered compatible.
     * @param itemContent the content displayed by a single item
     */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        error("The method is not implemented")
    }

    @Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        items(count, key, { null }, itemContent)
    }

    /**
     * Adds a sticky header item, which will remain pinned even when scrolling after it. The header
     * will remain pinned until the next header will take its place.
     *
     * @sample androidx.compose.foundation.samples.StickyHeaderListSample
     * @param key a stable and unique key representing the item. Using the same key for multiple
     *   items in the list is not allowed. Type of the key should be saveable via Bundle on Android.
     *   If null is passed the position in the list will represent the key. When you specify the key
     *   the scroll position will be maintained based on the key, which means if you add/remove
     *   items before the current visible item the item with the given key will be kept as the first
     *   visible one. This can be overridden by calling 'requestScrollToItem' on the
     *   'LazyListState'.
     * @param contentType the type of the content of this item. The item compositions of the same
     *   type could be reused more efficiently. Note that null is a valid type and items of such
     *   type will be considered compatible.
     * @param content the content of the header
     */
    @Deprecated(
        "Please use the overload with indexing capabilities.",
        level = DeprecationLevel.HIDDEN,
        replaceWith = ReplaceWith("stickyHeader(key, contentType, { _ -> content() })"),
    )
    fun stickyHeader(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    ) = stickyHeader(key, contentType) { _ -> content() }

    /**
     * Adds a sticky header item, which will remain pinned even when scrolling after it. The header
     * will remain pinned until the next header will take its place.
     *
     * @sample androidx.compose.foundation.samples.StickyHeaderListSample
     * @sample androidx.compose.foundation.samples.StickyHeaderHeaderIndexSample
     * @param key a stable and unique key representing the item. Using the same key for multiple
     *   items in the list is not allowed. Type of the key should be saveable via Bundle on Android.
     *   If null is passed the position in the list will represent the key. When you specify the key
     *   the scroll position will be maintained based on the key, which means if you add/remove
     *   items before the current visible item the item with the given key will be kept as the first
     *   visible one. This can be overridden by calling 'requestScrollToItem' on the
     *   'LazyListState'.
     * @param contentType the type of the content of this item. The item compositions of the same
     *   type could be reused more efficiently. Note that null is a valid type and items of such
     *   type will be considered compatible.
     * @param content the content of the header, the header index is provided, this is the item
     *   position within the total set of items in this lazy list (the global index).
     */
    fun stickyHeader(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        item(key, contentType) { content.invoke(this, 0) }
    }
}

/**
 * Adds a list of items.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the list is not allowed. Type of the key should be saveable via Bundle on
 *   Android. If null is passed the position in the list will represent the key. When you specify
 *   the key the scroll position will be maintained based on the key, which means if you add/remove
 *   items before the current visible item the item with the given key will be kept as the first
 *   visible one. This can be overridden by calling 'requestScrollToItem' on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScop.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(items[index]) } else null,
        contentType = { index: Int -> contentType(items[index]) },
    ) {
        itemContent(items[it])
    }

@Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
inline fun <T> LazyListScop.items(
    items: List<T>,
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) = items(items, key, itemContent = itemContent)

/**
 * Adds a list of items where the content of an item is aware of its index.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the list is not allowed. Type of the key should be saveable via Bundle on
 *   Android. If null is passed the position in the list will represent the key. When you specify
 *   the key the scroll position will be maintained based on the key, which means if you add/remove
 *   items before the current visible item the item with the given key will be kept as the first
 *   visible one. This can be overridden by calling 'requestScrollToItem' on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScop.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index -> contentType(index, items[index]) },
    ) {
        itemContent(it, items[it])
    }

@Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
inline fun <T> LazyListScop.itemsIndexed(
    items: List<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) = itemsIndexed(items, key, itemContent = itemContent)

/**
 * Adds an array of items.
 *
 * @param items the data array
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the list is not allowed. Type of the key should be saveable via Bundle on
 *   Android. If null is passed the position in the list will represent the key. When you specify
 *   the key the scroll position will be maintained based on the key, which means if you add/remove
 *   items before the current visible item the item with the given key will be kept as the first
 *   visible one. This can be overridden by calling 'requestScrollToItem' on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScop.items(
    items: Array<T>,
    noinline key: ((item: T) -> Any)? = null,
    noinline contentType: (item: T) -> Any? = { null },
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(items[index]) } else null,
        contentType = { index: Int -> contentType(items[index]) },
    ) {
        itemContent(items[it])
    }

@Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
inline fun <T> LazyListScop.items(
    items: Array<T>,
    noinline key: ((item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(item: T) -> Unit,
) = items(items, key, itemContent = itemContent)

/**
 * Adds an array of items where the content of an item is aware of its index.
 *
 * @param items the data array
 * @param key a factory of stable and unique keys representing the item. Using the same key for
 *   multiple items in the list is not allowed. Type of the key should be saveable via Bundle on
 *   Android. If null is passed the position in the list will represent the key. When you specify
 *   the key the scroll position will be maintained based on the key, which means if you add/remove
 *   items before the current visible item the item with the given key will be kept as the first
 *   visible one. This can be overridden by calling 'requestScrollToItem' on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of the same
 *   type could be reused more efficiently. Note that null is a valid type and items of such type
 *   will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScop.itemsIndexed(
    items: Array<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) =
    items(
        count = items.size,
        key = if (key != null) { index: Int -> key(index, items[index]) } else null,
        contentType = { index -> contentType(index, items[index]) },
    ) {
        itemContent(it, items[it])
    }

@Deprecated("Use the non deprecated overload", level = DeprecationLevel.HIDDEN)
inline fun <T> LazyListScop.itemsIndexed(
    items: Array<T>,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
) = itemsIndexed(items, key, itemContent = itemContent)

@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    reverseLayout: Boolean = false, // 支持反向布局
    spacedBy: CSSLengthValue = 0.px,
    ref: ElementRefScope<HTMLElement>? = null,
    content: LazyListScop.() -> Unit
) {
    val scope = LazyListScopeImpl().apply(content)
    val items = scope.items

    var containerElement by remember { mutableStateOf<HTMLDivElement?>(null) }
    val averageItemHeight = state.averageItemHeight

    val spaceBetweenItems = remember(spacedBy) {
            spacedBy.value.toDouble()
    }
    val itemSizeWithSpacing = averageItemHeight + spaceBetweenItems

    // 预估的总高度
    val totalHeight = if (items.isNotEmpty()) {
        items.size * itemSizeWithSpacing - spaceBetweenItems
    } else {
        0.0
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .position(Position.Relative)
            .overflow { y(Overflow.Auto) },
        ref = refScope {
            add(ref) // 允许外部传入 ref
            disposableRef { element ->
                containerElement = element as HTMLDivElement
                val scrollListener: (Event) -> Unit = {
                    // 对于反向布局，scrollTop 仍然是从顶部开始的。我们需要调整计算逻辑。
                    val currentScroll = if (reverseLayout) {
                        // "滚动了多少" = "总可滚动高度" - "当前滚动条位置"
                        element.scrollHeight - element.scrollTop - element.clientHeight
                    } else {
                        element.scrollTop
                    }
                    state.onScroll(currentScroll)
                }
                element.addEventListener("scroll", scrollListener)
                onDispose {
                    element.removeEventListener("scroll", scrollListener)
                }
            }
        }
    ) {
        // 1. “撑高”元素，用于生成正确的滚动条
        Box(
            Modifier
                .position(Position.Absolute)
                .top(0.px)
                .left(0.px)
                .height(totalHeight.px)
                .fillMaxWidth()
        )

        // 2. 可见项的容器
        Box(
            Modifier
                .fillMaxSize()
                .position(Position.Absolute)
                .top(0.px)
                .left(0.px)
        ) {
            val containerHeight = containerElement?.clientHeight ?: 0
            if (containerHeight > 0 && items.isNotEmpty()) {
                val scrollOffset by state.scrollOffset.collectAsState()
                val scrollTop = containerElement?.scrollTop ?: 0.0

                val firstVisibleItemIndex = (scrollTop / itemSizeWithSpacing).toInt()

                val visibleItemCount: Int = (containerHeight / itemSizeWithSpacing).toInt() + 3

                val startIndex = (firstVisibleItemIndex - 1).coerceAtLeast(0)
                val endIndex = (firstVisibleItemIndex + visibleItemCount).coerceAtMost(items.lastIndex)
                if (startIndex <= endIndex) {
                    state.updateVisibleItemsRange(startIndex..endIndex)
                    // 渲染可见项
                    for (i in startIndex..endIndex) {
                        val item = items[i]
                        val itemOffset = i * itemSizeWithSpacing

                        val itemModifier = if (reverseLayout) {
                            Modifier
                                .position(Position.Absolute)
                                .top((totalHeight - itemOffset - averageItemHeight).px) // 从底部开始计算位置
                                .fillMaxWidth()
                        } else {
                            Modifier
                                .position(Position.Absolute)
                                .top(itemOffset.px)
                                .fillMaxWidth()
                        }

                        key(item.key) {
                            Box(
                                itemModifier
                            ) {
                                val itemScope = object : LazyItemScope {
                                    override val index: Int = i
                                    override val key: Any = item.key
                                }
                                item.content(itemScope)
                            }
                        }
                    }
                }
            }
        }
    }

    // 在反向布局的初始渲染时滚动到底部
    LaunchedEffect(containerElement, items.size, reverseLayout) {
        if (reverseLayout && containerElement != null && items.isNotEmpty()) {
            // 首次加载或条目数变化时，滚动到底部
            containerElement?.scrollTo(0.0, containerElement!!.scrollHeight.toDouble())
        }
    }
}
