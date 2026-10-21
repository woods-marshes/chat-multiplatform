package com.github.woodsmarshes.chat.core.database.session

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Wraps a SQLDelight [PagingSource] so that:
 *  - Every [load] call is registered with [gate] (`DatabaseSessionGate.runInSession`),
 *    fixed to [boundDatabase] and [boundGeneration].
 *  - When the session stops (`stop()`), any in-flight [load] (including its
 *    `finally` blocks and SQLDelight listener registration) is cancelled and
 *    awaited before the database driver can be closed.
 *  - When the session stops or [invalidate] occurs, the underlying
 *    [PagingSource] is invalidated so its SQLDelight `Query.Listener` is
 *    unregistered immediately.
 *  - If a stale `Pager` from an ended session attempts to [load] before or
 *    after a new session starts, its [boundGeneration] / [boundDatabase] no
 *    longer matches the active session and [load] returns
 *    [PagingSource.LoadResult.Error] without touching the database or triggering a refresh loop.
 */
internal class SessionBoundPagingSource<Key : Any, Value : Any>(
    private val gate: DatabaseSessionGate,
    private val boundDatabase: ChatDatabase?,
    private val boundGeneration: Long,
    delegateFactory: (ChatDatabase) -> PagingSource<Key, Value>,
) : PagingSource<Key, Value>() {

    private fun isSessionActive(): Boolean =
        boundDatabase != null &&
            boundGeneration > 0L &&
            gate.isGenerationActive(boundGeneration) &&
            gate.boundDatabase === boundDatabase

    private val delegate: PagingSource<Key, Value>? =
        if (isSessionActive()) {
            delegateFactory(checkNotNull(boundDatabase)).also { source ->
                registerInvalidatedCallback {
                    source.invalidate()
                }
                source.registerInvalidatedCallback {
                    if (!invalid) {
                        invalidate()
                    }
                }
            }
        } else {
            null
        }

    init {
        // Only an existing delegate needs teardown. Inactive replacements must not invalidate.
        if (delegate != null) {
            val removeStopListener = gate.registerStopListener { invalidate() }
            registerInvalidatedCallback { removeStopListener() }
            // The session may have stopped between delegate creation and registration.
            if (!isSessionActive()) invalidate()
        }
    }

    override val jumpingSupported: Boolean
        get() = delegate?.jumpingSupported ?: false

    override fun getRefreshKey(state: PagingState<Key, Value>): Key? =
        delegate?.getRefreshKey(state)

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> {
        currentCoroutineContext().ensureActive()
        val source = delegate
        if (source == null || !isSessionActive()) return sessionEnded()
        if (invalid) return LoadResult.Invalid()
        return try {
            gate.runInSession(
                expectedGeneration = boundGeneration,
                expectedDatabase = boundDatabase,
            ) { db ->
                when {
                    db !== boundDatabase || !isSessionActive() -> sessionEnded()
                    invalid -> LoadResult.Invalid()
                    else -> {
                        val result = source.load(params)
                        if (isSessionActive()) result else sessionEnded()
                    }
                }
            }
        } catch (error: SessionStoppedException) {
            currentCoroutineContext().ensureActive()
            LoadResult.Error(error)
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (!isSessionActive()) sessionEnded() else throw error
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            LoadResult.Error(error)
        } finally {
            // Release SQLDelight listeners; the next inactive source will not trigger another refresh.
            if (!isSessionActive()) source.invalidate()
        }
    }

    private fun sessionEnded(): LoadResult<Key, Value> =
        LoadResult.Error(SessionStoppedException("The database session for this paging source has ended"))
}
