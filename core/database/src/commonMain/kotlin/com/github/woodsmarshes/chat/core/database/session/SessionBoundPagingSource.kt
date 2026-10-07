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
 *    [PagingSource.LoadResult.Invalid] without touching the database.
 */
internal class SessionBoundPagingSource<Key : Any, Value : Any>(
    private val gate: DatabaseSessionGate,
    private val boundDatabase: ChatDatabase?,
    private val boundGeneration: Long,
    delegateFactory: (ChatDatabase) -> PagingSource<Key, Value>,
) : PagingSource<Key, Value>() {

    private val delegate: PagingSource<Key, Value>? =
        if (boundDatabase != null && boundGeneration > 0L && gate.isGenerationActive(boundGeneration)) {
            delegateFactory(boundDatabase).also { source ->
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

    private val removeStopListener = gate.registerStopListener { invalidate() }

    init {
        registerInvalidatedCallback { removeStopListener() }
        if (delegate == null) {
            invalidate()
        }
    }

    override val jumpingSupported: Boolean
        get() = delegate?.jumpingSupported ?: false

    override fun getRefreshKey(state: PagingState<Key, Value>): Key? =
        delegate?.getRefreshKey(state)

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value> {
        val source = delegate
        if (invalid ||
            source == null ||
            boundDatabase == null ||
            boundGeneration <= 0L ||
            !gate.isGenerationActive(boundGeneration)
        ) {
            invalidate()
            return LoadResult.Invalid()
        }
        return try {
            gate.runInSession(
                expectedGeneration = boundGeneration,
                expectedDatabase = boundDatabase,
            ) { db ->
                if (invalid || db !== boundDatabase || !gate.isGenerationActive(boundGeneration)) {
                    invalidate()
                    return@runInSession LoadResult.Invalid()
                }
                try {
                    source.load(params)
                } finally {
                    if (!gate.isGenerationActive(boundGeneration)) {
                        source.invalidate()
                    }
                }
            }
        } catch (_: SessionStoppedException) {
            invalidate()
            LoadResult.Invalid()
        } catch (e: CancellationException) {
            // If the session gate stopped while the Pager's own coroutine is
            // still alive, invalidate this PagingSource and return Invalid so
            // Paging stops reading the old generation; if the Pager itself was
            // cancelled, propagate the cancellation untouched.
            if (!gate.isGenerationActive(boundGeneration)) {
                source.invalidate()
                invalidate()
            }
            currentCoroutineContext().ensureActive()
            LoadResult.Invalid()
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
