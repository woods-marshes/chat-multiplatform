package com.github.woodsmarshes.chat.core.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.QueryResult
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import io.github.woodsmarshes.chat.db.ParticipantEntity
import com.github.woodsmarshes.chat.core.database.dao.MessageDaoImpl
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MessagePersistenceTest {
    private val user = Uuid.parse("00000000-0000-0000-0000-000000000001")
    private val conversation = Uuid.parse("00000000-0000-0000-0000-000000000002")
    private val local = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val server = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val reply = Uuid.parse("00000000-0000-0000-0000-000000000030")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private suspend fun fixture(block: suspend (MessageDaoImpl, io.github.woodsmarshes.chat.db.ChatDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            val foreignKeys = driver.executeQuery(null, "PRAGMA foreign_keys", { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
            }, 0).value
            assertEquals(1L, foreignKeys, "The merge tests must enforce foreign keys")
            db.usersQueries.upsertUser(UserEntity(user, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
            db.conversationsQueries.upsertConversation(ConversationEntity(conversation, ConversationType.PRIVATE, null, null, now, now, null))
            block(MessageDaoImpl({ db }, Dispatchers.Unconfined), db)
        } finally {
            driver.close()
        }
    }

    private fun row(id: Uuid, status: MessageStatus = MessageStatus.SENDING, replyTo: Uuid? = null) =
        MessageEntity(id, conversation, user, MessageCategory.NORMAL, MessageRenderType.TEXT, TextContent("hello"), replyTo, now, null, status)

    @Test
    fun fourAckStatesConvergeAndPreserveReplies() = runBlocking {
        for (hasLocal in listOf(false, true)) for (hasServer in listOf(false, true)) {
            fixture { dao, db ->
                if (hasLocal) dao.insertMessage(row(local))
                if (hasServer) dao.insertMessage(row(server, MessageStatus.SENT))
                if (hasLocal) {
                    dao.insertMessage(row(reply, replyTo = local))
                    db.conversationsQueries.updateLastMessage(local, now, conversation)
                    db.participantsQueries.upsertParticipant(ParticipantEntity(
                        conversation, user, ConversationRole.MEMBER, local, now, null, ParticipantSettings(),
                    ))
                }
                repeat(2) { dao.mergeServerMessage(row(server, MessageStatus.SENT), local) }
                assertNull(dao.getMessageById(local).first())
                assertEquals(MessageStatus.SENT, dao.getMessageById(server).first()?.local_send_status)
                if (hasLocal) {
                    assertEquals(server, dao.getMessageById(reply).first()?.reply_to_message_id)
                    assertEquals(server, db.participantsQueries.getParticipant(conversation, user).executeAsOneOrNull()?.last_read_message_id)
                    assertEquals(server, db.conversationsQueries.getConversationById(conversation).executeAsOneOrNull()?.last_message_id)
                }
                assertEquals(if (hasLocal) 2 else 1, dao.getMessagesPaged(conversation, Instant.DISTANT_FUTURE, 100).first().size)
            }
        }
    }

    @Test
    fun manualRetryHasFreshBudgetWithoutChangingTimeline() = runBlocking {
        fixture { dao, _ ->
            dao.insertMessage(row(local, MessageStatus.FAILED))
            val attempt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 172_800_000)
            dao.transaction {
                dao.updateMessageStatus(local, local, now, MessageStatus.SENDING)
                dao.startAttempt(local, attempt)
            }
            dao.failStaleMessages(Instant.fromEpochMilliseconds(attempt.toEpochMilliseconds() - 86_400_000))
            assertEquals(MessageStatus.SENDING, dao.getMessageById(local).first()?.local_send_status)
            assertEquals(now, dao.getMessageById(local).first()?.created_at)
            assertTrue(dao.getRetryableMessages(attempt).isEmpty())
            val later = Instant.fromEpochMilliseconds(attempt.toEpochMilliseconds() + 20_000)
            assertEquals(1, dao.getRetryableMessages(later).size)
            dao.recordAttempt(local, now, later)
            assertTrue(dao.getRetryableMessages(later).isEmpty())
        }
    }

    @Test
    fun migrationPreservesMessagesAndCanReopenLegacyVersionZero() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val db = createDatabase { schema -> schema.create(driver).await(); driver }
            db.usersQueries.upsertUser(UserEntity(user, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
            db.conversationsQueries.upsertConversation(ConversationEntity(conversation, ConversationType.PRIVATE, null, null, now, now, null))
            val dao = MessageDaoImpl({ db }, Dispatchers.Unconfined)
            dao.insertMessage(row(local, MessageStatus.FAILED))
            driver.execute(null, "DROP TABLE MessageAttempt", 0).await()
            driver.execute(null, "DROP TABLE MessageSyncCursor", 0).await()
            val schema = io.github.woodsmarshes.chat.db.ChatDatabase.Schema
            schema.migrate(driver, 0, schema.version).await()
            schema.migrate(driver, 0, schema.version).await()
            assertEquals(MessageStatus.FAILED, dao.getMessageById(local).first()?.local_send_status)
            assertTrue(dao.claimFailedMessage(local, now))
            assertEquals(false, dao.claimFailedMessage(local, now))
            dao.setSyncCursor(conversation, server)
            assertEquals(server, dao.getSyncCursor(conversation))
        } finally {
            driver.close()
        }
    }
    @Test
    fun syncCursorRollsBackWithFailedPage() = runBlocking {
        fixture { dao, _ ->
            try {
                dao.transaction {
                    dao.insertMessage(row(server))
                    dao.setSyncCursor(conversation, server)
                    error("Injected page failure")
                }
            } catch (_: IllegalStateException) {
                // Expected rollback.
            }
            assertNull(dao.getSyncCursor(conversation))
            assertNull(dao.getMessageById(server).first())
        }
    }
}
