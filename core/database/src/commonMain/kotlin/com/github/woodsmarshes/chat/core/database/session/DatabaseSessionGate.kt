package com.github.woodsmarshes.chat.core.database.session

import app.cash.sqldelight.Query
import com.github.woodsmarshes.chat.core.common.session.ResourceSession
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Coordinates the session-bound lifetime of all database query observation
 * [Flow]s and [PagingSource] loads for a single [ChatDatabase] session.
 *
 * When `stop()` is called (before `DatabaseHolder.closeDatabaseIfCurrent`), it
 * cancels every active query observation subscription (removing SQLDelight
 * `Query.Listener`s and waiting for any in-flight `executeAsList` /
 * `executeAsOne` / `executeAsOneOrNull` to finish) and every in-flight
 * `PagingSource.load` call, and returns only after all of them have exited.
 */
interface DatabaseSessionGate {
    val boundDatabase: ChatDatabase?

    fun registerStopListener(listener: () -> Unit): () -> Unit = {}

    val currentGeneration: Long

    fun isGenerationActive(generation: Long): Boolean

    suspend fun <T> runInSession(
        expectedGeneration: Long? = null,
        expectedDatabase: ChatDatabase? = null,
        block: suspend (ChatDatabase) -> T,
    ): T
}

/**
 * Unbounded gate used when a DAO is constructed standalone in unit tests
 * without a `SessionManager`-managed [DatabaseHolder].
 */
internal class DirectDatabaseSessionGate(
    private val dbProvider: () -> ChatDatabase,
) : DatabaseSessionGate {
    override val boundDatabase: ChatDatabase?
        get() = runCatching { dbProvider() }.getOrNull()

    override val currentGeneration: Long
        get() = 1L

    override fun isGenerationActive(generation: Long): Boolean =
        runCatching { dbProvider() }.isSuccess

    override suspend fun <T> runInSession(
        expectedGeneration: Long?,
        expectedDatabase: ChatDatabase?,
        block: suspend (ChatDatabase) -> T,
    ): T {
        currentCoroutineContext().ensureActive()
        val db = expectedDatabase ?: runCatching { dbProvider() }.getOrElse {
            throw SessionStoppedException(it.message ?: "Database is not initialized")
        }
        return block(db)
    }
}

/**
 * Pinned gate bound to a specific [database] and session [generation] (if
 * backed by a [ManagedDatabaseSessionGate]).
 */
internal class PinnedDatabaseSessionGate(
    override val boundDatabase: ChatDatabase,
    private val delegate: DatabaseSessionGate,
    private val pinnedGeneration: Long = delegate.currentGeneration,
) : DatabaseSessionGate {
    override fun registerStopListener(listener: () -> Unit): () -> Unit = delegate.registerStopListener(listener)

    override val currentGeneration: Long
        get() = pinnedGeneration

    override fun isGenerationActive(generation: Long): Boolean =
        generation == pinnedGeneration &&
            delegate.isGenerationActive(pinnedGeneration) &&
            (delegate.boundDatabase == null || delegate.boundDatabase === boundDatabase)

    override suspend fun <T> runInSession(
        expectedGeneration: Long?,
        expectedDatabase: ChatDatabase?,
        block: suspend (ChatDatabase) -> T,
    ): T {
        val targetGen = expectedGeneration ?: pinnedGeneration
        if (targetGen != pinnedGeneration) {
            throw SessionStoppedException("Database session generation $targetGen does not match pinned generation $pinnedGeneration")
        }
        val targetDb = expectedDatabase ?: boundDatabase
        if (targetDb !== boundDatabase) {
            throw SessionStoppedException("Database instance does not match pinned database")
        }
        return delegate.runInSession(
            expectedGeneration = pinnedGeneration,
            expectedDatabase = boundDatabase,
            block = block,
        )
    }
}

/**
 * Session gate backed by [SessionExecutor], owned by `DatabaseHolder` (or
 * tests) so `stopSession()` deterministically cancels and awaits every active
 * query Flow subscription and Paging load before closing the SQLite driver.
 */
class ManagedDatabaseSessionGate(
    dispatcher: CoroutineDispatcher,
    private val activeDbProvider: () -> ChatDatabase?,
    private val activeGenerationProvider: () -> Long,
) : DatabaseSessionGate {
    private class Binding(val database: ChatDatabase, val generation: Long)
    private val lifecycle = ResourceSession(
        dispatcher = dispatcher,
        create = {
            Binding(activeDbProvider() ?: throw SessionStoppedException("Database is not initialized"), activeGenerationProvider())
        },
        validateExisting = { binding ->
            check(binding.database === activeDbProvider() && binding.generation == activeGenerationProvider()) {
                "Stop the current database session before rebinding"
            }
        },
        dispose = {
            val pending = listeners.value
            listeners.value = emptySet()
            var failure: Throwable? = null
            pending.forEach { listener ->
                try { listener() } catch (error: Throwable) {
                    if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        },
    )
    private val listeners = kotlinx.coroutines.flow.MutableStateFlow<Set<() -> Unit>>(emptySet())
    override fun registerStopListener(listener: () -> Unit): () -> Unit {
        while (true) {
            val old = listeners.value
            if (listeners.compareAndSet(old, old + listener)) break
        }
        if (lifecycle.current == null) listener()
        return {
            while (true) {
                val old = listeners.value
                if (listeners.compareAndSet(old, old - listener)) break
            }
        }
    }

    override val boundDatabase: ChatDatabase?
        get() = activeDbProvider()

    override val currentGeneration: Long
        get() = activeGenerationProvider()

    override fun isGenerationActive(generation: Long): Boolean =
        generation > 0L &&
            lifecycle.current != null &&
            activeGenerationProvider() == generation &&
            activeDbProvider() != null

    suspend fun start() = lifecycle.start()

    suspend fun stop() = lifecycle.stop()

    override suspend fun <T> runInSession(
        expectedGeneration: Long?,
        expectedDatabase: ChatDatabase?,
        block: suspend (ChatDatabase) -> T,
    ): T = lifecycle.execute { binding ->
        val currentGen = binding.generation
        if (expectedGeneration != null && expectedGeneration != currentGen) {
            throw SessionStoppedException(
                "Operation bound to database generation $expectedGeneration, active is $currentGen",
            )
        }
        val currentDb = binding.database
        if (activeDbProvider() !== currentDb || activeGenerationProvider() != currentGen) {
            throw SessionStoppedException("Database binding changed during the session")
        }
        if (expectedDatabase != null && expectedDatabase !== currentDb) {
            throw SessionStoppedException("Operation bound to a stale ChatDatabase instance")
        }
        block(currentDb)
    }
}

/**
 * Observes a SQLDelight [Query] inside [gate]'s session scope.
 *
 *  - If no database session is active when collected, completes immediately
 *    without touching the database driver.
 *  - While the session is active, registers a [Query.Listener] on the fixed
 *    [ChatDatabase] instance of that session and executes [extractor] on
 *    [ioContext] inside [DatabaseSessionGate.runInSession].
 *  - When `stop()` is called on the session gate, the underlying
 *    `runInSession` block is cancelled, its `finally` block unregisters the
 *    [Query.Listener], any in-flight [extractor] call on [ioContext] is
 *    cancelled and awaited by `SessionExecutor.stop()`, and the returned
 *    [Flow] completes cleanly without throwing [SessionStoppedException] into
 *    live UI collectors that have not been disposed yet.
 *  - If the collector itself cancels, the listener is likewise removed
 *    immediately and the cancellation propagates to the collector.
 */
internal fun <Row : Any, Result> sessionBoundQueryFlow(
    gate: DatabaseSessionGate,
    ioContext: CoroutineContext,
    queryFactory: (ChatDatabase) -> Query<Row>,
    extractor: (Query<Row>) -> Result,
): Flow<Result> {
    if (gate is DirectDatabaseSessionGate) return flow {
        val query = queryFactory(checkNotNull(gate.boundDatabase))
        val trigger = Channel<Unit>(Channel.CONFLATED)
        val listener = Query.Listener { trigger.trySend(Unit) }
        query.addListener(listener)
        try {
            trigger.trySend(Unit)
            for (signal in trigger) emit(withContext(ioContext.minusKey(Job)) { extractor(query) })
        } finally {
            query.removeListener(listener)
            trigger.close()
        }
    }
    val pinnedDb = gate.boundDatabase
    val pinnedGen = gate.currentGeneration
    return channelFlow {
        try {
            gate.runInSession(
                expectedGeneration = pinnedGen,
                expectedDatabase = pinnedDb,
            ) { db ->
                val query = queryFactory(db)
                val trigger = Channel<Unit>(Channel.CONFLATED)
                val listener = Query.Listener {
                    trigger.trySend(Unit)
                }
                query.addListener(listener)
                try {
                    trigger.trySend(Unit)
                    for (unit in trigger) {
                        currentCoroutineContext().ensureActive()
                        val value = withContext(ioContext.minusKey(Job)) {
                            currentCoroutineContext().ensureActive()
                            extractor(query)
                        }
                        currentCoroutineContext().ensureActive()
                        send(value)
                    }
                } finally {
                    query.removeListener(listener)
                    trigger.close()
                }
            }
        } catch (_: SessionStoppedException) {
            // Session is not active or was stopped before collection started:
            // complete the observation stream cleanly without touching the driver.
        } catch (e: CancellationException) {
            // Distinguish session teardown (channelFlow's own scope is still active)
            // from caller cancellation (channelFlow's scope was cancelled by downstream).
            currentCoroutineContext().ensureActive()
        }
    }

}
