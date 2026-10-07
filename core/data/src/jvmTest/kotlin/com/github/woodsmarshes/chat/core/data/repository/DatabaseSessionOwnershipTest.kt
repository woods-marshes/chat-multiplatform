package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.dao.ArticleDaoImpl
import com.github.woodsmarshes.chat.core.database.dao.UserDaoImpl
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import androidx.paging.PagingSource
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, androidx.paging.ExperimentalPagingApi::class)
class DatabaseSessionOwnershipTest {
    @Test
    fun articleMediatorIsCancelledBeforeLateResponseCanWriteNewDatabase() = runTest {
        val driverA = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val driverB = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val dbA = createDatabase { schema -> schema.create(driverA).await(); driverA }
        val dbB = createDatabase { schema -> schema.create(driverB).await(); driverB }
        var active = dbA
        var generation = 1L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { active }, { generation })
        val holder = io.mockk.mockk<com.github.woodsmarshes.chat.core.database.di.DatabaseHolder>()
        io.mockk.every { holder.sessionGate } returns gate
        val api = io.mockk.mockk<com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi>()
        val entered = CompletableDeferred<Unit>()
        val cleanup = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        io.mockk.coEvery { api.listMyArticles(any(), any()) } coAnswers {
            entered.complete(Unit)
            try { awaitCancellation() } finally {
                withContext(NonCancellable) { cleanup.complete(Unit); release.await() }
            }
        }
        val dispatchers = io.mockk.mockk<com.github.woodsmarshes.chat.core.common.AppDispatchers>()
        io.mockk.every { dispatchers.io } returns dispatcher
        val mediator = com.github.woodsmarshes.chat.core.data.paging.ArticleRemoteMediator(
            true, null, api, ArticleDaoImpl({ active }, dispatcher, gate),
            UserDaoImpl({ active }, dispatcher, gate), dispatchers, holder,
        )
        try {
            gate.start()
            val load = launch {
                mediator.load(androidx.paging.LoadType.REFRESH, androidx.paging.PagingState(emptyList(), null, androidx.paging.PagingConfig(20), 0))
            }
            entered.await()
            var stopped = false
            val stop = launch { gate.stop(); stopped = true }
            cleanup.await()
            runCurrent()
            assertFalse(stopped)
            release.complete(Unit)
            stop.join()
            load.join()
            active = dbB
            generation++
            gate.start()
            val result = mediator.load(androidx.paging.LoadType.REFRESH, androidx.paging.PagingState(emptyList(), null, androidx.paging.PagingConfig(20), 0))
            assertTrue(result is androidx.paging.RemoteMediator.MediatorResult.Error)
            io.mockk.coVerify(exactly = 1) { api.listMyArticles(any(), any()) }
        } finally {
            release.complete(Unit)
            gate.stop()
            driverA.close()
            driverB.close()
        }
    }

    @Test
    fun stopEndsObservationAndOldPagingSourceCannotJoinNewGeneration() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        var generation = 1L
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = ManagedDatabaseSessionGate(dispatcher, { db }, { generation })
        try {
            gate.start()
            val dao = UserDaoImpl({ db }, dispatcher, gate)
            val staleFlow = dao.getUserById(Uuid.NIL)
            var values = 0
            val collector = launch { staleFlow.collect { values++ } }
            runCurrent()
            assertTrue(values == 1)
            val source = ArticleDaoImpl({ db }, dispatcher, gate).pagingSource(20, null, null)
            source.load(PagingSource.LoadParams.Refresh(null, 20, false))
            gate.stop()
            collector.join()
            assertTrue(source.invalid, "Idle paging sources must remove listeners before the database closes")
            generation++
            gate.start()
            staleFlow.collect { error("Old observation entered a new generation") }
            assertTrue(source.load(PagingSource.LoadParams.Refresh(null, 20, false)) is PagingSource.LoadResult.Invalid)
            gate.stop()
        } finally {
            gate.stop()
            driver.close()
        }
    }

    @Test
    fun stopWaitsForSuspendingOperationCleanupBeforeClose() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val gate = ManagedDatabaseSessionGate(StandardTestDispatcher(testScheduler), { db }, { 1L })
        val entered = CompletableDeferred<Unit>()
        val cleanup = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stopped = false
        try {
            gate.start()
            val operation = launch {
                gate.runInSession(1L, db) {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) { cleanup.complete(Unit); release.await() }
                    }
                }
            }
            entered.await()
            val stop = launch { gate.stop(); stopped = true }
            cleanup.await()
            runCurrent()
            assertFalse(stopped)
            release.complete(Unit)
            stop.join()
            operation.join()
            assertTrue(stopped)
        } finally {
            release.complete(Unit)
            gate.stop()
            driver.close()
        }
    }
}
