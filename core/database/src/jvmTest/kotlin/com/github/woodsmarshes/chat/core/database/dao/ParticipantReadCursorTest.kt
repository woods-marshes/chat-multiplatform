package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Locks the monotonicity contract of updateLastReadMessage: read receipts
 * arrive per-device and can race, so an older (or null) receipt must never
 * move the cursor backwards — the server already gates its broadcasts the
 * same way (ReadCursorUpdate.ADVANCED only).
 */
class ParticipantReadCursorTest {
    private val conversation = Uuid.parse("018f0000-0000-7000-8000-000000000100")
    private val user = Uuid.parse("018f0000-0000-7000-8000-000000000200")
    // Same-millisecond UUIDv7 ids: the low bits still order these two.
    private val older = Uuid.parse("018f0000-0000-7000-8000-00000000000a")
    private val newer = Uuid.parse("018f0000-0000-7000-8000-00000000000b")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private suspend fun fixture(block: suspend (ParticipantDaoImpl) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        try {
            db.usersQueries.upsertUser(UserEntity(user, "user", null, null, null, null, now, now, null, UserRole.MEMBER))
            db.conversationsQueries.upsertConversation(ConversationEntity(conversation, ConversationType.PRIVATE, null, null, now, now, null))
            block(ParticipantDaoImpl({ db }, Dispatchers.Unconfined))
        } finally {
            driver.close()
        }
    }

    private suspend fun ParticipantDaoImpl.seedRow() {
        insertParticipant(ParticipantEntity(conversation, user, ConversationRole.MEMBER, null, now, null, ParticipantSettings()))
    }

    private suspend fun ParticipantDaoImpl.cursor(): Uuid? =
        getParticipant(conversation, user).first()?.last_read_message_id

    @Test
    fun nullCursorAdvancesToFirstReceipt() = runBlocking {
        fixture { dao ->
            dao.seedRow()
            dao.updateLastReadMessage(conversation, user, older)
            assertEquals(older, dao.cursor())
        }
    }

    @Test
    fun olderReceiptDoesNotRegressTheCursor() = runBlocking {
        fixture { dao ->
            dao.seedRow()
            dao.updateLastReadMessage(conversation, user, newer)
            dao.updateLastReadMessage(conversation, user, older)
            assertEquals(newer, dao.cursor(), "an out-of-order older receipt must not move the cursor back")
        }
    }

    @Test
    fun nullReceiptDoesNotClearTheCursor() = runBlocking {
        fixture { dao ->
            dao.seedRow()
            dao.updateLastReadMessage(conversation, user, newer)
            dao.updateLastReadMessage(conversation, user, null)
            assertEquals(newer, dao.cursor(), "a malformed receipt must not clear the cursor")
        }
    }

    @Test
    fun repeatedReceiptIsAnIdempotentNoOp() = runBlocking {
        fixture { dao ->
            dao.seedRow()
            dao.updateLastReadMessage(conversation, user, newer)
            dao.updateLastReadMessage(conversation, user, newer)
            assertEquals(newer, dao.cursor())
        }
    }
}
