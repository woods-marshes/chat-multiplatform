package com.github.woodsmarshes.chat.core.database.di

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.woodsmarshes.chat.db.ChatDatabase
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseVersionGuardTest {
    @Test
    fun rejectsNewerFileWithoutChangingVersionOrDataAndClosesDriver() = runBlocking {
        val file = Files.createTempFile("chat-version-", ".db")
        val url = "jdbc:sqlite:$file"
        try {
            val original = JdbcSqliteDriver(url)
            original.execute(null, "CREATE TABLE marker(value TEXT)", 0).await()
            original.execute(null, "INSERT INTO marker VALUES ('preserved')", 0).await()
            val newer = ChatDatabase.Schema.version + 1
            original.execute(null, "PRAGMA user_version = $newer", 0).await()
            original.close()
            val actual = JdbcSqliteDriver(url)
            var closed = false
            val tracking = object : SqlDriver by actual {
                override fun close() { closed = true; actual.close() }
            }
            assertFailsWith<IllegalStateException> { initializeDatabaseDriver(tracking, ChatDatabase.Schema) }
            assertTrue(closed)
            val reopened = JdbcSqliteDriver(url)
            try {
                val version = reopened.executeQuery(null, "PRAGMA user_version", { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
                }, 0).value
                assertEquals(newer, version)
                val marker = reopened.executeQuery(null, "SELECT value FROM marker", { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
                }, 0).value
                assertEquals("preserved", marker)
            } finally { reopened.close() }
        } finally { Files.deleteIfExists(file) }
    }

    @Test
    fun createsAndReopensSupportedDatabase() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            initializeDatabaseDriver(driver, ChatDatabase.Schema)
            initializeDatabaseDriver(driver, ChatDatabase.Schema)
            val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            }, 0).value
            assertEquals(ChatDatabase.Schema.version, version)
        } finally { driver.close() }
    }
}
