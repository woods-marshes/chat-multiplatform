package com.github.woodsmarshes.chat.core.common.result

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface UiState<out T> {
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
    data object Loading : UiState<Nothing>
}

fun <V, E> Result<V, E>.toUiState(
    errorMapper: (E) -> String = { it.toString() }
): UiState<V> {
    return mapBoth(
        success = { UiState.Success(it) },
        failure = { UiState.Error(errorMapper(it)) }
    )
}

fun <V, E> Flow<Result<V, E>>.asUiState(): Flow<UiState<V>> = this
    .map { it.toUiState() }
    .onStart { emit(UiState.Loading) }
    .catch { emit(UiState.Error(it.message ?: "Unknown Error")) }
