package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.MessageStatus
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.MessageEntity
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

    @Test
    fun fileDatabaseGetSyncCursorAndGetRetryableMessagesStayOnTransactionConnection() = runBlocking {
        val file = Files.createTempFile("chat-tx-cursor-", ".db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:$file")
        val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val ioDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            // Pass a separate ioContext dispatcher to prove that one-shot reads
            // execute directly on the calling transaction thread rather than
            // hopping to ioContext via asFlow().mapToOneOrNull(ioContext).first().
            val dao = MessageDaoImpl({ db }, ioDispatcher)
            val userId = Uuid.parse("00000000-0000-0000-0000-000000000001")
            val conversationId = Uuid.parse("00000000-0000-0000-0000-000000000002")
            val msgId = Uuid.parse("00000000-0000-0000-0000-000000000010")
            val now = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000)

            withContext(writer) {
                db.usersQueries.upsertUser(
                    UserEntity(userId, "tx-user", null, null, null, null, now, now, null, UserRole.MEMBER),
                )
                db.conversationsQueries.upsertConversation(
                    ConversationEntity(conversationId, ConversationType.PRIVATE, null, null, now, now, null, unread_count = 0),
                )
            }

            assertFailsWith<IllegalStateException> {
                withContext(writer) {
                    dao.transaction {
                        dao.setSyncCursor(conversationId, msgId, 42L)
                        val cursor = dao.getSyncCursor(conversationId)
                        assertEquals(msgId.toString(), cursor?.after_id)
                        assertEquals(42L, cursor?.after_seq)

                        dao.insertMessage(
                            MessageEntity(
                                id = msgId,
                                conversation_id = conversationId,
                                user_id = userId,
                                category = MessageCategory.NORMAL,
                                render_type = MessageRenderType.TEXT,
                                content = TextContent("pending"),
                                reply_to_message_id = null,
                                created_at = now,
                                revoked_at = null,
                                local_send_status = MessageStatus.SENDING,
                                in_timeline = true,
                            ),
                        )
                        val retryable = dao.getRetryableMessages(
                            kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 1_000),
                        )
                        assertEquals(listOf(msgId), retryable.map { it.id })

                        dao.demoteTimelineBefore(conversationId, msgId)
                        assertEquals(1L, dao.getWindowGeneration(conversationId))

                        error("Rollback uncommitted cursor and message")
                    }
                }
            }

            withContext(writer) {
                assertNull(dao.getSyncCursor(conversationId))
                assertEquals(
                    emptyList(),
                    dao.getRetryableMessages(kotlin.time.Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 1_000)),
                )
                assertEquals(0L, dao.getWindowGeneration(conversationId))
            }
        } finally {
            writer.close()
            ioDispatcher.close()
            driver.close()
            Files.deleteIfExists(file)
        }
    }
}
