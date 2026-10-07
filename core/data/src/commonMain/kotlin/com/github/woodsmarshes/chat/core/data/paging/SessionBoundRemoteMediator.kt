package com.github.woodsmarshes.chat.core.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** One load boundary for identity pinning, cancellation and paging failures. */
@OptIn(ExperimentalPagingApi::class)
abstract class SessionBoundRemoteMediator<Key : Any, Value : Any>(
    private val gate: DatabaseSessionGate,
) : RemoteMediator<Key, Value>() {
    private val generation = gate.currentGeneration
    private val database = gate.boundDatabase

    final override suspend fun load(loadType: LoadType, state: PagingState<Key, Value>): MediatorResult =
        try {
            gate.runInSession(generation, database) { db ->
                withContext(BoundDatabaseElement(db)) { loadInSession(loadType, state) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onLoadFailure(failure)
            MediatorResult.Error(failure)
        }

    protected abstract suspend fun loadInSession(loadType: LoadType, state: PagingState<Key, Value>): MediatorResult

    protected open fun onLoadFailure(failure: Exception) = Unit
}
