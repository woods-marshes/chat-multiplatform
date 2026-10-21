package com.github.woodsmarshes.chat.core.database.session

import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionBoundPagingSourceTest {
    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(null, 10, false)

    private class Delegate(
        private val action: suspend () -> PagingSource.LoadResult<Int, Int> = {
            PagingSource.LoadResult.Page(listOf(1), null, null)
        },
    ) : PagingSource<Int, Int>() {
        var loads = 0
        override fun getRefreshKey(state: PagingState<Int, Int>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Int> {
            loads++
            return action()
        }
    }

    private suspend fun TestScope.withDatabase(
        block: suspend (ChatDatabase, ManagedDatabaseSessionGate) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val gate = ManagedDatabaseSessionGate(StandardTestDispatcher(testScheduler), { db }, { 1L })
        try { block(db, gate) } finally { gate.stop(); driver.close() }
    }

    @Test
    fun inactiveSourceNeverInvalidatesOrCreatesDatabaseDelegate() = runTest {
        withDatabase { db, gate ->
            val source = SessionBoundPagingSource<Int, Int>(gate, db, 1L) { error("Inactive factory") }
            assertFalse(source.invalid)
            repeat(3) {
                val result = assertIs<PagingSource.LoadResult.Error<Int, Int>>(source.load(refresh()))
                assertIs<SessionStoppedException>(result.throwable)
                assertFalse(source.invalid)
            }
        }
    }

    @Test
    fun collectedPagerStopsRecreatingSourcesAfterSessionEnds() = runTest {
        withDatabase { db, gate ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            val delegates = mutableListOf<Delegate>()
            val sources = mutableListOf<SessionBoundPagingSource<Int, Int>>()
            val presenter = object : PagingDataPresenter<Int>(dispatcher) {
                override suspend fun presentPagingDataEvent(event: PagingDataEvent<Int>) = Unit
            }
            gate.start()
            val pager = Pager(PagingConfig(pageSize = 10, enablePlaceholders = false)) {
                // Fail fast instead of starving the test scheduler on the old implementation.
                check(sources.size < 8) { "Stopped session caused a PagingSource recreation loop" }
                SessionBoundPagingSource(gate, db, 1L) {
                    Delegate().also { delegates.add(it) }
                }.also { sources.add(it) }
            }
            val collector = backgroundScope.launch { pager.flow.collectLatest(presenter::collectFrom) }
            try {
                runCurrent()
                assertEquals(listOf(1), presenter.snapshot().items)
                assertEquals(1, sources.size)
                delegates.last().invalidate()
                runCurrent()
                assertEquals(2, sources.size)
                assertEquals(listOf(1), presenter.snapshot().items)
                gate.stop()
                runCurrent()
                assertTrue(collector.isActive)
                assertEquals(3, sources.size, "Only one inactive replacement is allowed")
                assertEquals(2, delegates.size)
                assertTrue(delegates.all { it.invalid })
                assertFalse(sources.last().invalid)
                assertIs<LoadState.Error>(presenter.loadStateFlow.value?.refresh)
                var probeRan = false
                launch { probeRan = true }
                runCurrent()
                assertTrue(probeRan)
                presenter.retry()
                runCurrent()
                assertEquals(3, sources.size)
                assertEquals(2, delegates.sumOf { it.loads })
                assertIs<LoadState.Error>(presenter.loadStateFlow.value?.refresh)
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun stoppedLoadWaitsForCleanupAndReportsTerminalError() = runTest {
        withDatabase { db, gate ->
            val entered = CompletableDeferred<Unit>()
            val cleaning = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val delegate = Delegate {
                entered.complete(Unit)
                try { awaitCancellation() } finally {
                    withContext(NonCancellable) { cleaning.complete(Unit); release.await() }
                }
            }
            gate.start()
            val source = SessionBoundPagingSource(gate, db, 1L) { delegate }
            val load = async { source.load(refresh()) }
            try {
                entered.await()
                val stop = async { gate.stop() }
                cleaning.await()
                runCurrent()
                assertFalse(stop.isCompleted)
                release.complete(Unit)
                stop.await()
                assertIs<PagingSource.LoadResult.Error<Int, Int>>(load.await())
                assertTrue(delegate.invalid)
            } finally { release.complete(Unit); load.cancelAndJoin() }
        }
    }

    @Test
    fun callerCancellationPropagatesAndKeepsActiveSessionUsable() = runTest {
        withDatabase { db, gate ->
            val entered = CompletableDeferred<Unit>()
            val cleaned = CompletableDeferred<Unit>()
            val delegate = Delegate {
                entered.complete(Unit)
                try { awaitCancellation() } finally { cleaned.complete(Unit) }
            }
            gate.start()
            val source = SessionBoundPagingSource(gate, db, 1L) { delegate }
            val load = async { source.load(refresh()) }
            entered.await()
            load.cancelAndJoin()
            cleaned.await()
            assertTrue(load.isCancelled)
            assertTrue(gate.isGenerationActive(1L))
            assertFalse(source.invalid)
            assertFalse(delegate.invalid)
        }
    }

    @Test
    fun delegateCancellationIsNotConvertedIntoAutomaticRefresh() = runTest {
        withDatabase { db, gate ->
            gate.start()
            val source = SessionBoundPagingSource(gate, db, 1L) {
                Delegate { throw CancellationException("Delegate cancelled") }
            }
            var cancelled = false
            try { source.load(refresh()) } catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertFalse(source.invalid)
        }
    }

    @Test
    fun oldGenerationCannotReviveWhenTheSameDatabaseIsReused() = runTest {
        withDatabase { db, _ ->
            var generation = 1L
            val gate = ManagedDatabaseSessionGate(StandardTestDispatcher(testScheduler), { db }, { generation })
            gate.start()
            val delegate = Delegate()
            val source = SessionBoundPagingSource(gate, db, 1L) { delegate }
            try {
                assertIs<PagingSource.LoadResult.Page<Int, Int>>(source.load(refresh()))
                gate.stop()
                generation++
                gate.start()
                assertIs<PagingSource.LoadResult.Error<Int, Int>>(source.load(refresh()))
                val replacement = SessionBoundPagingSource<Int, Int>(gate, db, 1L) {
                    error("Old Pager must not join a new generation")
                }
                assertFalse(replacement.invalid)
                assertIs<PagingSource.LoadResult.Error<Int, Int>>(replacement.load(refresh()))
                assertEquals(1, delegate.loads)
            } finally { gate.stop() }
        }
    }

    @Test
    fun synchronousStopDuringRegistrationReleasesListenerAndDelegate() = runTest {
        withDatabase { db, _ ->
            var active = true
            var registrations = 0
            var removals = 0
            val gate = object : DatabaseSessionGate {
                override val boundDatabase = db
                override val currentGeneration = 1L
                override fun isGenerationActive(generation: Long) = active && generation == 1L
                override fun registerStopListener(listener: () -> Unit): () -> Unit {
                    registrations++
                    active = false
                    listener()
                    return { removals++ }
                }
                override suspend fun <T> runInSession(
                    expectedGeneration: Long?, expectedDatabase: ChatDatabase?,
                    block: suspend (ChatDatabase) -> T,
                ): T = error("Stopped gate must not run loads")
            }
            val delegate = Delegate()
            val source = SessionBoundPagingSource(gate, db, 1L) { delegate }
            assertTrue(source.invalid)
            assertTrue(delegate.invalid)
            assertEquals(1, removals)
            val replacement = SessionBoundPagingSource<Int, Int>(gate, db, 1L) { error("Inactive factory") }
            assertFalse(replacement.invalid)
            assertIs<PagingSource.LoadResult.Error<Int, Int>>(replacement.load(refresh()))
            assertEquals(1, registrations)
        }
    }

}
