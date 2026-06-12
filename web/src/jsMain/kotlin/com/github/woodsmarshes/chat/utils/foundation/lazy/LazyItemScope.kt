package com.github.woodsmarshes.chat.utils.foundation.lazy

import androidx.compose.runtime.Stable

@Stable
@LazyScopeMarker
interface LazyItemScope {
    val index: Int
    val key: Any
}