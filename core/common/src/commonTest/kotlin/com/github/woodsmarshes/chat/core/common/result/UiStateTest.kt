package com.github.woodsmarshes.chat.core.common.result

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiStateTest {

    private data class TestError(val reason: String)

    // ── Result.toUiState ────────────────────────────────────────────────

    @Test
    fun `Ok maps to Success carrying the value`() {
        val result: Result<Int, String> = Ok(42)

        val state = result.toUiState()

        assertIs<UiState.Success<Int>>(state)
        assertEquals(42, state.data)
    }

    @Test
    fun `Err maps to Error using the default toString mapper`() {
        val result: Result<Int, String> = Err("boom")

        val state = result.toUiState()

        assertIs<UiState.Error>(state)
        assertEquals("boom", state.message)
    }

    @Test
    fun `Err maps through the provided error mapper`() {
        val result: Result<Int, TestError> = Err(TestError("bad-credentials"))

        val state = result.toUiState { "code: ${it.reason}" }

        assertIs<UiState.Error>(state)
        assertEquals("code: bad-credentials", state.message)
    }

    @Test
    fun `null value Success is preserved`() {
        val result: Result<Int?, String> = Ok(null)

        val state = result.toUiState()

        assertIs<UiState.Success<*>>(state)
        assertEquals(null, state.data)
    }

    // ── Flow<Result>.asUiState ──────────────────────────────────────────

    @Test
    fun `asUiState emits Loading first then the mapped states`() = runTest {
        val flow = flow<Result<Int, String>> {
            emit(Ok(1))
            emit(Ok(2))
        }

        val states = flow.asUiState().toList()

        assertEquals(UiState.Loading, states.first())
        assertEquals(2, states.filterIsInstance<UiState.Success<Int>>().size)
        assertEquals(1, (states[1] as UiState.Success).data)
        assertEquals(2, (states[2] as UiState.Success).data)
    }

    @Test
    fun `asUiState maps failures through the default mapper`() = runTest {
        val flow = flow<Result<Unit, String>> {
            emit(Err("nope"))
        }

        val states = flow.asUiState().toList()

        assertEquals(UiState.Loading, states[0])
        assertIs<UiState.Error>(states[1])
        assertEquals("nope", (states[1] as UiState.Error).message)
    }

    @Test
    fun `asUiState converts upstream exceptions into an Error state`() = runTest {
        val flow = flow<Result<Int, String>> {
            throw IllegalStateException("disk on fire")
        }

        val states = flow.asUiState().toList()

        assertEquals(2, states.size)
        assertEquals(UiState.Loading, states[0])
        assertIs<UiState.Error>(states[1])
        assertEquals("disk on fire", (states[1] as UiState.Error).message)
    }

    @Test
    fun `asUiState falls back to Unknown Error when the exception has no message`() = runTest {
        val flow = flow<Result<Int, String>> {
            throw RuntimeException()
        }

        val states = flow.asUiState().toList()

        assertIs<UiState.Error>(states[1])
        assertTrue((states[1] as UiState.Error).message.contains("Unknown"))
    }
}
