package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatInitialContentTest {
    private val idle = LoadState.NotLoading(false)
    private val error = LoadState.Error(IllegalStateException("offline"))

    private fun states(
        aggregate: LoadState = idle,
        source: LoadState = idle,
        mediator: LoadState? = idle,
    ) = CombinedLoadStates(
        refresh = aggregate,
        prepend = idle,
        append = idle,
        source = LoadStates(source, idle, idle),
        mediator = mediator?.let { LoadStates(it, idle, idle) },
    )

    @Test
    fun idleAggregateDoesNotHideLoadingLocalSource() {
        // Exact state captured on-device during the empty-state flash.
        assertEquals(ChatInitialContent.Loading, chatInitialContent(0, states(source = LoadState.Loading)))
    }

    @Test
    fun eitherRefreshLoadingPreventsEmptyState() {
        assertEquals(ChatInitialContent.Loading, chatInitialContent(0, states(aggregate = LoadState.Loading)))
        assertEquals(ChatInitialContent.Loading, chatInitialContent(0, states(mediator = LoadState.Loading)))
        assertEquals(ChatInitialContent.Loading, chatInitialContent(0, states(source = LoadState.Loading, mediator = null)))
    }

    @Test
    fun trueEmptyAppearsAfterSourceAndMediatorComplete() {
        assertEquals(ChatInitialContent.Empty, chatInitialContent(0, states()))
        assertEquals(ChatInitialContent.Empty, chatInitialContent(0, states(mediator = null)))
    }

    @Test
    fun errorWithoutItemsOffersRetryEvenWhenAggregateLags() {
        assertEquals(ChatInitialContent.Error, chatInitialContent(0, states(source = error)))
        assertEquals(ChatInitialContent.Error, chatInitialContent(0, states(mediator = error)))
    }

    @Test
    fun cachedMessagesRemainVisibleDuringRefreshAndErrors() {
        assertEquals(ChatInitialContent.Messages, chatInitialContent(20, states(source = LoadState.Loading)))
        assertEquals(ChatInitialContent.Messages, chatInitialContent(20, states(mediator = error)))
    }

    @Test
    fun capturedEntrySequenceNeverDisplaysEmpty() {
        val sequence = listOf(
            0 to states(aggregate = LoadState.Loading, source = LoadState.Loading, mediator = null),
            0 to states(source = LoadState.Loading),
            0 to states(aggregate = LoadState.Loading, source = LoadState.Loading, mediator = LoadState.Loading),
            20 to states(aggregate = LoadState.Loading, mediator = LoadState.Loading),
            20 to states(),
        )
        assertEquals(
            listOf(ChatInitialContent.Loading, ChatInitialContent.Loading, ChatInitialContent.Loading,
                ChatInitialContent.Messages, ChatInitialContent.Messages),
            sequence.map { (count, loadStates) -> chatInitialContent(count, loadStates) },
        )
    }
}
