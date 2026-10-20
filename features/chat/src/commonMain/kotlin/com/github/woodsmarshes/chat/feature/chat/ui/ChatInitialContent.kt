package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

internal enum class ChatInitialContent { Messages, Loading, Error, Empty }

internal fun chatInitialContent(itemCount: Int, states: CombinedLoadStates): ChatInitialContent {
    if (itemCount > 0) return ChatInitialContent.Messages
    // The mediator's aggregate refresh may be idle while the local source is still loading.
    val refreshStates = listOfNotNull(states.refresh, states.source.refresh, states.mediator?.refresh)
    if (refreshStates.any { it is LoadState.Error }) return ChatInitialContent.Error
    if (refreshStates.any { it is LoadState.Loading }) return ChatInitialContent.Loading
    return ChatInitialContent.Empty
}
