package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/** Domain mapping only; lifecycle ownership remains in the database session. */
internal suspend fun <T, E> DatabaseSessionGate.executeResult(
    onFailure: (Exception) -> E,
    block: suspend () -> Result<T, E>,
): Result<T, E> {
    val generation = currentGeneration
    val database = boundDatabase
    return try {
        runInSession(generation, database) { db ->
            withContext(BoundDatabaseElement(db)) { block() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Err(onFailure(failure))
    }
}

/** Pins observation at creation; recollection cannot adopt a later login. */
internal fun <T> DatabaseSessionGate.observeSession(factory: suspend () -> Flow<T>): Flow<T> {
    val generation = currentGeneration
    val database = boundDatabase
    return channelFlow {
        try {
            runInSession(generation, database) { db ->
                withContext(BoundDatabaseElement(db)) { factory().collect { send(it) } }
            }
        } catch (_: SessionStoppedException) {
            // A stopped session ends observation; it does not manufacture empty business data.
        } catch (_: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }
}
