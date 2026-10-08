package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.MessageDataSourceImpl
import com.github.woodsmarshes.chat.repository.ReadCursorUpdate
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.support.TestDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ReadCursorDatabaseTest {
    private val participants = ConversationParticipantDataSourceImpl()
    private val messages = MessageDataSourceImpl()
    private val first = Uuid.parse("01960000-0000-7000-8000-000000000001")
    private val second = Uuid.parse("01960000-0000-7000-8000-000000000002")
    private val third = Uuid.parse("01960000-0000-7000-8000-000000000003")

    @BeforeTest
    fun reset() = TestDb.reset()

    private fun insert(id: Uuid, conversation: Uuid, sender: Uuid, seq: Long) = transaction(TestDb.database) {
        Messages.insert {
            it[Messages.id] = id
            it[conversationId] = conversation
            it[senderId] = sender
            it[content] = TextContent("message $seq")
            it[category] = MessageCategory.NORMAL
            it[renderType] = MessageRenderType.TEXT
            it[createdAt] = Clock.System.now()
            it[Messages.seq] = seq
        }
    }

    @Test
    fun cursorAdvancesOnceAndRejectsInvalidTargets() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val outsider = TestDb.user("outsider")
        val conversation = TestDb.privateConversation(alice, bob)
        val other = TestDb.privateConversation(alice, outsider)
        insert(first, conversation, alice, 1)
        insert(second, conversation, alice, 2)
        insert(third, other, alice, 1)
        assertEquals(ReadCursorUpdate.ADVANCED, participants.updateReadLastMessage(bob, conversation, second))
        assertEquals(ReadCursorUpdate.UNCHANGED, participants.updateReadLastMessage(bob, conversation, first))
        assertEquals(ReadCursorUpdate.UNCHANGED, participants.updateReadLastMessage(bob, conversation, second))
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(bob, conversation, third))
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(outsider, conversation, first))
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(bob, conversation, Uuid.random()))
        assertEquals(second, participants.getConversationParticipant(bob, conversation)?.lastReadMessageId)
    }

    @Test
    fun readersIncludeEqualAndLaterCursorsButExcludeEarlierNullAndOtherConversation() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val carol = TestDb.user("carol")
        val dave = TestDb.user("dave")
        val outsider = TestDb.user("outsider")
        val conversation = TestDb.groupConversation(alice, listOf(bob, carol, dave))
        val other = TestDb.privateConversation(alice, outsider)
        insert(first, conversation, alice, 1)
        insert(second, conversation, alice, 2)
        insert(third, conversation, alice, 3)
        val otherId = Uuid.parse("01960000-0000-7000-8000-000000000004")
        insert(otherId, other, alice, 1)
        participants.updateReadLastMessage(alice, conversation, first)
        participants.updateReadLastMessage(bob, conversation, second)
        participants.updateReadLastMessage(carol, conversation, third)
        participants.updateReadLastMessage(outsider, other, otherId)
        assertEquals(setOf(bob, carol), messages.getReadMessageUsers(second)!!.second.map { it.id }.toSet())
        assertEquals(setOf(alice, bob, carol), messages.getReadMessageUsers(first)!!.second.map { it.id }.toSet())
        assertEquals(setOf(carol), messages.getReadMessageUsers(third)!!.second.map { it.id }.toSet())
    }
}
