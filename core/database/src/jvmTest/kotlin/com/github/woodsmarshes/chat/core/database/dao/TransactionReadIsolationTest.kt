package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

class TransactionReadIsolationTest {
    @Test
    fun fileDatabaseDirectReadsSeeOwnWritesAndRollbackDoesNotNotify() = runBlocking {
        val file = Files.createTempFile("chat-transaction-", ".db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:$file")
        val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val reader = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            val messages = MessageDaoImpl({ db }, writer)
            val users = UserDaoImpl({ db }, reader)
            val id = Uuid.random()
            val now = kotlin.time.Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            val user = UserEntity(id, "transaction-user", null, null, null, null, now, now, null, UserRole.MEMBER)
            val notifications = AtomicInteger()
            val query = db.usersQueries.getUserById(id)
            val listener = Query.Listener { notifications.incrementAndGet() }
            query.addListener(listener)
            try {
                assertFailsWith<IllegalStateException> {
                    withContext(writer) { messages.transaction {
                        users.insertUser(user)
                        assertEquals(user, query.executeAsOneOrNull())
                        assertEquals(0, notifications.get())
                        error("Injected rollback")
                    } }
                }
                assertNull(withContext(reader) { query.executeAsOneOrNull() })
                assertEquals(0, notifications.get())
                withContext(writer) { messages.transaction {
                    users.insertUser(user)
                    assertEquals(user, query.executeAsOneOrNull())
                    assertEquals(0, notifications.get())
                }
                }
                assertEquals(user, withContext(reader) { query.executeAsOneOrNull() })
                assertEquals(1, notifications.get())
            } finally { query.removeListener(listener) }
        } finally {
            writer.close()
            reader.close()
            driver.close()
            Files.deleteIfExists(file)
        }
    }
}
