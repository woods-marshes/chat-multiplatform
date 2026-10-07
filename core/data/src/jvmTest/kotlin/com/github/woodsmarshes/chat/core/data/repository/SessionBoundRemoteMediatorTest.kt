package com.github.woodsmarshes.chat.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.data.paging.SessionBoundRemoteMediator
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalPagingApi::class)
class SessionBoundRemoteMediatorTest {
    @Test
    fun loadInstallsDatabaseContextAndReturnsResult() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            val gate = ManagedDatabaseSessionGate(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler), { db }, { 1L })
            gate.start()
            val expected = RemoteMediator.MediatorResult.Success(true)
            val mediator = object : SessionBoundRemoteMediator<Int, String>(gate) {
                override suspend fun loadInSession(loadType: LoadType, state: PagingState<Int, String>): MediatorResult {
                    assertSame(db, currentCoroutineContext()[BoundDatabaseElement]?.database)
                    return expected
                }
            }
            assertSame(expected, mediator.load(LoadType.REFRESH, PagingState(emptyList(), null, PagingConfig(20), 0)))
            gate.stop()
        } finally { driver.close() }
    }

    @Test
    fun businessFailureIsReportedExactlyOnceButCancellationIsNotMapped() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            val gate = ManagedDatabaseSessionGate(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler), { db }, { 1L })
            gate.start()
            val failure = IllegalStateException("load failed")
            var cancel = false
            var reports = 0
            val mediator = object : SessionBoundRemoteMediator<Int, String>(gate) {
                override suspend fun loadInSession(loadType: LoadType, state: PagingState<Int, String>): MediatorResult {
                    if (cancel) throw CancellationException("session stopped")
                    throw failure
                }
                override fun onLoadFailure(failure: Exception) { reports++ }
            }
            val state = PagingState<Int, String>(emptyList(), null, PagingConfig(20), 0)
            val result = mediator.load(LoadType.REFRESH, state) as RemoteMediator.MediatorResult.Error
            assertEquals(failure::class, result.throwable::class)
            assertEquals(failure.message, result.throwable.message)
            assertEquals(1, reports)
            cancel = true
            assertFailsWith<CancellationException> { mediator.load(LoadType.REFRESH, state) }
            assertEquals(1, reports)
            gate.stop()
        } finally { driver.close() }
    }
}
