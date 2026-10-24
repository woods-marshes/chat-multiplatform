package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.database.session.ManagedDatabaseSessionGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseSessionOperationsMappingTest {

    @Test
    fun staleGenerationMapsThroughOnStoppedNotOnFailure() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        var generation = 1L
        val gate = ManagedDatabaseSessionGate(StandardTestDispatcher(testScheduler), { db }, { generation })
        try {
            gate.start()

            generation = 2L

            val stale = gate.executeResult(
                onStopped = { "stopped" },
                onFailure = { "failed" },
            ) { Ok(Unit) }

            assertEquals(Err("stopped"), stale)
        } finally {
            gate.stop()
        }
    }

    @Test
    fun realFailuresStillMapThroughOnFailure() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        val gate = ManagedDatabaseSessionGate(StandardTestDispatcher(testScheduler), { db }, { 1L })
        try {
            gate.start()

            val failed: Result<Unit, String> = gate.executeResult(
                onStopped = { "stopped" },
                onFailure = { "failed" },
            ) { throw IllegalStateException("boom") }

            assertEquals(Err("failed"), failed)
        } finally {
            gate.stop()
        }
    }
}
