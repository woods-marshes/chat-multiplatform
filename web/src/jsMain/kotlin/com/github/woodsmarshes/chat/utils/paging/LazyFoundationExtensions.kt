package com.github.woodsmarshes.chat.utils.paging

fun <T : Any> LazyPagingItems<T>.itemKey(
    key: ((item: T) -> Any)? = null
): (index: Int) -> Any {
    return { index ->
        if (key == null) {
            getPagingPlaceholderKey(index)
        } else {
            val item = peek(index)
            if (item == null) getPagingPlaceholderKey(index) else key(item)
        }
    }
}

fun <T : Any> LazyPagingItems<T>.itemContentType(
    contentType: ((item: T) -> Any?)? = null
): (index: Int) -> Any? {
    return { index ->
        if (contentType == null) {
            null
        } else {
            val item = peek(index)
            if (item == null) PagingPlaceholderContentType else contentType(item)
        }
    }
}

object PagingPlaceholderContentType