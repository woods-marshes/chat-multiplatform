package com.github.woodsmarshes.chat.feature.chat.ui

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.github.woodsmarshes.chat.core.model.presentation.MessageUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ChatInitialPagingStateTest {
    @Test
    fun waitingForIdentityIsLoadingAndRealEmptyResultRemainsEmpty() = runTest {
        val presenter = object : PagingDataPresenter<MessageUiModel>(UnconfinedTestDispatcher(testScheduler)) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<MessageUiModel>) = Unit
        }
        presenter.collectFrom(pendingMessagePage())
        assertEquals(0, presenter.snapshot().size)
        assertIs<LoadState.Loading>(presenter.loadStateFlow.value?.refresh)

        presenter.collectFrom(PagingData.empty(
            sourceLoadStates = LoadStates(
                refresh = LoadState.NotLoading(false),
                prepend = LoadState.NotLoading(true),
                append = LoadState.NotLoading(true),
            ),
        ))
        assertEquals(0, presenter.snapshot().size)
        assertIs<LoadState.NotLoading>(presenter.loadStateFlow.value?.refresh)

        presenter.collectFrom(pendingMessagePage())
        assertIs<LoadState.Loading>(presenter.loadStateFlow.value?.refresh)
    }
}
