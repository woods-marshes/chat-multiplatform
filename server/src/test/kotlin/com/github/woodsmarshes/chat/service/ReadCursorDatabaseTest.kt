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

    private fun insert(id: Uuid, conversation: Uuid, sender: Uuid, body: String) = transaction(TestDb.database) {
        Messages.insert {
            it[Messages.id] = id
            it[conversationId] = conversation
            it[senderId] = sender
            it[content] = TextContent(body)
            it[category] = MessageCategory.NORMAL
            it[renderType] = MessageRenderType.TEXT
            it[createdAt] = Clock.System.now()
        }
    }

    @Test
    fun cursorAdvancesOnceAndRejectsInvalidTargets() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val outsider = TestDb.user("outsider")
        val conversation = TestDb.privateConversation(alice, bob)
        val other = TestDb.privateConversation(alice, outsider)
        insert(first, conversation, alice, "first")
        insert(second, conversation, alice, "second")
        insert(third, other, alice, "other")
        val advancedToFirst = participants.updateReadLastMessage(bob, conversation, first)
        assertEquals(ReadCursorUpdate.ADVANCED, advancedToFirst.outcome)
        assertEquals(1L, advancedToFirst.unreadCount)
        val advancedToSecond = participants.updateReadLastMessage(bob, conversation, second)
        assertEquals(ReadCursorUpdate.ADVANCED, advancedToSecond.outcome)
        assertEquals(0L, advancedToSecond.unreadCount)
        assertEquals(ReadCursorUpdate.UNCHANGED, participants.updateReadLastMessage(bob, conversation, first).outcome)
        assertEquals(ReadCursorUpdate.UNCHANGED, participants.updateReadLastMessage(bob, conversation, second).outcome)
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(bob, conversation, third).outcome)
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(outsider, conversation, first).outcome)
        assertEquals(ReadCursorUpdate.INVALID, participants.updateReadLastMessage(bob, conversation, Uuid.random()).outcome)
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
        insert(first, conversation, alice, "first")
        insert(second, conversation, alice, "second")
        insert(third, conversation, alice, "third")
        val otherId = Uuid.parse("01960000-0000-7000-8000-000000000004")
        insert(otherId, other, alice, "other")
        participants.updateReadLastMessage(alice, conversation, first)
        participants.updateReadLastMessage(bob, conversation, second)
        participants.updateReadLastMessage(carol, conversation, third)
        participants.updateReadLastMessage(outsider, other, otherId)
        assertEquals(setOf(bob, carol), messages.getReadMessageUsers(second)!!.second.map { it.id }.toSet())
        assertEquals(setOf(alice, bob, carol), messages.getReadMessageUsers(first)!!.second.map { it.id }.toSet())
        assertEquals(setOf(carol), messages.getReadMessageUsers(third)!!.second.map { it.id }.toSet())
    }
}
