package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

/** Maps domain errors and database context onto the shared resource lifecycle. */
internal class RepositorySessionRunner<S : Any>(
    dispatcher: CoroutineDispatcher,
    private val ownerName: String,
    private val captureState: suspend () -> S,
    private val isSameState: (existing: S, candidate: S) -> Boolean,
    private val databaseOf: (S) -> ChatDatabase?,
) {
    private val lifecycle = com.github.woodsmarshes.chat.core.common.session.ResourceSession(
        dispatcher = dispatcher,
        create = { ActiveSession(captureState()) },
        validateExisting = { active ->
            check(isSameState(active.state, captureState())) {
                "$ownerName is already bound to different resources; stop it before rebinding"
            }
        },
    )

    internal inner class ActiveSession(val state: S) {
        suspend fun ensureCurrent() {
            currentCoroutineContext().ensureActive()
            if (!lifecycle.isCurrent(this)) throw SessionStoppedException("$ownerName session ended")
        }
    }

    suspend fun startSession() = lifecycle.start()
    suspend fun stopSession() = lifecycle.stop()

    suspend fun <T, E> execute(
        onStoppedError: () -> E,
        onUnexpectedError: (Throwable) -> E,
        block: suspend (session: ActiveSession) -> Result<T, E>,
    ): Result<T, E> {
        return try {
            lifecycle.execute { activeSession ->
                val dbElement = databaseOf(activeSession.state)?.let(::BoundDatabaseElement)
                    ?: EmptyCoroutineContext
                withContext(dbElement) {
                    val result = block(activeSession)
                    currentCoroutineContext().ensureActive()
                    result
                }
            }
        } catch (_: SessionStoppedException) {
            Err(onStoppedError())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Err(onUnexpectedError(e))
        }
    }
}
