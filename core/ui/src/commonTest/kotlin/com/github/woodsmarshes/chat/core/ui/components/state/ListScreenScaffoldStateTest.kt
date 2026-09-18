package com.github.woodsmarshes.chat.core.ui.components.state

import kotlin.test.Test
import kotlin.test.assertEquals

class ListScreenScaffoldStateTest {

    @Test
    fun loadingWithoutDataShowsLoading() {
        assertEquals(
            ListScreenState.LOADING,
            resolveListScreenState(isLoading = true, hasError = false, isEmpty = true),
        )
    }

    @Test
    fun errorWithoutDataShowsError() {
        assertEquals(
            ListScreenState.ERROR,
            resolveListScreenState(isLoading = false, hasError = true, isEmpty = true),
        )
    }

    @Test
    fun emptyDataWithoutLoadingOrErrorShowsEmpty() {
        assertEquals(
            ListScreenState.EMPTY,
            resolveListScreenState(isLoading = false, hasError = false, isEmpty = true),
        )
    }

    @Test
    fun dataShowsContent() {
        assertEquals(
            ListScreenState.CONTENT,
            resolveListScreenState(isLoading = false, hasError = false, isEmpty = false),
        )
    }

    @Test
    fun loadingWithExistingDataKeepsContent() {
        assertEquals(
            ListScreenState.CONTENT,
            resolveListScreenState(isLoading = true, hasError = false, isEmpty = false),
        )
    }

    @Test
    fun transientErrorWithExistingDataKeepsContent() {
        assertEquals(
            ListScreenState.CONTENT,
            resolveListScreenState(isLoading = false, hasError = true, isEmpty = false),
        )
    }

    @Test
    fun loadingWinsOverErrorWhileEmpty() {
        assertEquals(
            ListScreenState.LOADING,
            resolveListScreenState(isLoading = true, hasError = true, isEmpty = true),
        )
    }
}
