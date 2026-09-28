package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.repository.database.schema.backfillMessageSeq
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class MessageSeqTest {

    private val database: Database = connectToH2Database()
    private val repository = MessageDataSourceImpl()

    private val senderId = Uuid.random()
    private val conversationId = Uuid.random()
    private val now = kotlin.time.Clock.System.now()

    @BeforeTest
    fun seed() {
        runBlocking {
            transaction(database) {
                SchemaUtils.create(Users, Conversations, Messages)
                Users.insert {
                    it[id] = senderId
                    it[username] = "sender-$senderId"
                    it[email] = "sender-$senderId@test.local"
                    it[passwordHash] = "hash"
                    it[salt] = ""
                    it[role] = UserRole.MEMBER
                    it[createdAt] = now
                    it[updatedAt] = now
                }
                Conversations.insert {
                    it[id] = conversationId
                    it[type] = ConversationType.PRIVATE
                    it[metadata] = PrivateMetadata()
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) {
                Messages.deleteWhere { Messages.conversationId eq conversationId }
                Conversations.deleteWhere { Conversations.id eq conversationId }
                Users.deleteWhere { Users.id eq senderId }
            }
        }
    }

    @Test
    fun insertsAllocateGapFreePerConversationSeq() {
        val first = runBlocking {
            repository.insertMessage(
                conversationId = conversationId,
                senderId = senderId,
                content = TextContent(text = "one"),
                category = MessageCategory.NORMAL,
                renderType = MessageRenderType.TEXT,
            )
        }?.first
        val second = runBlocking {
            repository.insertMessage(
                conversationId = conversationId,
                senderId = senderId,
                content = TextContent(text = "two"),
                category = MessageCategory.NORMAL,
                renderType = MessageRenderType.TEXT,
            )
        }?.first

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(1L, first.seq)
        assertEquals(2L, second.seq)
    }

    @Test
    fun backfillSequencesLegacyRowsAndContinuesAllocationFromThere() {
        // Simulate legacy rows written before the seq column: direct inserts
        // with no seq and an allocator still at 0.
        val legacyConversationId = conversationId
        val legacySenderId = senderId
        transaction(database) {
            Messages.insert {
                it[id] = Uuid.random()
                it[conversationId] = legacyConversationId
                it[senderId] = legacySenderId
                it[content] = TextContent(text = "legacy-1")
                it[searchText] = "legacy-1"
                it[category] = MessageCategory.NORMAL
                it[renderType] = MessageRenderType.TEXT
                it[createdAt] = now
            }
            Messages.insert {
                it[id] = Uuid.random()
                it[conversationId] = legacyConversationId
                it[senderId] = legacySenderId
                it[content] = TextContent(text = "legacy-2")
                it[searchText] = "legacy-2"
                it[category] = MessageCategory.NORMAL
                it[renderType] = MessageRenderType.TEXT
                it[createdAt] = now
            }
        }

        backfillMessageSeq(database)

        val seqs = transaction(database) {
            Messages.selectAll()
                .where { Messages.conversationId eq conversationId }
                .map { it[Messages.seq] }
        }
        assertEquals(listOf(1L, 2L), seqs.filterNotNull().sorted())
        val nextSeq = transaction(database) {
            Conversations.selectAll()
                .where { Conversations.id eq conversationId }
                .single()[Conversations.nextSeq]
        }
        assertEquals(2L, nextSeq)

        // Allocation resumes after the high-water mark, keeping the sequence
        // gap-free across the upgrade.
        val afterUpgrade = runBlocking {
            repository.insertMessage(
                conversationId = conversationId,
                senderId = senderId,
                content = TextContent(text = "post-upgrade"),
                category = MessageCategory.NORMAL,
                renderType = MessageRenderType.TEXT,
            )
        }?.first
        assertNotNull(afterUpgrade)
        assertEquals(3L, afterUpgrade.seq)
    }
}
