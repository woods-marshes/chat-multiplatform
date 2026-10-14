package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.michaelbull.result.get
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.repository.ContactRequestSourceImpl
import com.github.woodsmarshes.chat.repository.ContactSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.GroupJoinRequestSourceImpl
import com.github.woodsmarshes.chat.repository.database.schema.ALL_SCHEMA_TABLES
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.SyncChanges
import com.github.woodsmarshes.chat.repository.database.schema.SyncStreams
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.service.ConversationLifecycleService
import com.github.woodsmarshes.chat.service.SyncService
import com.github.woodsmarshes.chat.support.TestDb
import com.github.woodsmarshes.chat.utils.inTransaction
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Integration coverage for durable per-scope sync streams and their coupling
 * to message persistence. All database operations use the real schema and repositories.
 */
class SyncJournalDatabaseTest {

    private val database = TestDb.database
    private val syncRepository = SyncDataSourceImpl()
    private val messageRepository = MessageDataSourceImpl(syncRepository)
    private val senderId = Uuid.random()
    private val outsiderId = Uuid.random()
    private val conversationId = Uuid.random()

    @BeforeTest
    fun seed() {
        TestDb.reset()
        transaction(database) {
            exec("SET MODE MYSQL")
            SchemaUtils.create(*ALL_SCHEMA_TABLES.toTypedArray())
            Users.insert {
                it[id] = senderId
                it[username] = "sender-$senderId"
                it[email] = "sender-$senderId@test.local"
                it[passwordHash] = "hash"
                it[salt] = ""
                it[role] = UserRole.MEMBER
                it[createdAt] = kotlin.time.Clock.System.now()
                it[updatedAt] = kotlin.time.Clock.System.now()
            }
            Users.insert {
                it[id] = outsiderId
                it[username] = "outsider-$outsiderId"
                it[email] = "outsider-$outsiderId@test.local"
                it[passwordHash] = "hash"
                it[salt] = ""
                it[role] = UserRole.MEMBER
                it[createdAt] = kotlin.time.Clock.System.now()
                it[updatedAt] = kotlin.time.Clock.System.now()
            }
            Conversations.insert {
                it[id] = conversationId
                it[type] = ConversationType.PRIVATE
                it[metadata] = PrivateMetadata()
                it[createdAt] = kotlin.time.Clock.System.now()
                it[updatedAt] = kotlin.time.Clock.System.now()
            }
            ConversationParticipants.insert {
                it[conversationId] = this@SyncJournalDatabaseTest.conversationId
                it[userId] = senderId
                it[role] = ConversationRole.PARTICIPANT
                it[lastReadMessageId] = null
                it[joinedAt] = kotlin.time.Clock.System.now()
                it[settings] = ParticipantSettings()
            }
        }
    }

    @AfterTest
    fun tearDown() = Unit

    @Test
    fun messageCreationPersistsRecoverableChangeAndDuplicateRequestIsNoOp() = runBlocking {
        val requestId = Uuid.random()
        val first = messageRepository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent("durable body"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            requestId = requestId,
        )
        assertNotNull(first)
        assertTrue(first.second)

        val replay = messageRepository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent("durable body"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            requestId = requestId,
        )
        assertNotNull(replay)
        assertFalse(replay.second)
        assertEquals(first.first.id, replay.first.id)

        val batch = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 0L, 10)
        assertEquals(1L, batch.nextRevision)
        assertEquals(1, batch.changes.size)
        val received = assertIs<MessageEventResponse.Received>(batch.changes.single().event)
        assertEquals(first.first.id, received.message.id)
        assertEquals("durable body", (received.message.content as TextContent).text)
        assertEquals(requestId, received.message.clientRequestId)
        assertEquals(1L, transaction(database) {
            SyncStreams.selectAll().where { SyncStreams.scope eq SyncScope.CONVERSATION }
                .single()[SyncStreams.lastRevision]
        })
    }

    @Test
    fun messageAndJournalBothRollbackWithOuterTransaction() = runBlocking {
        val requestId = Uuid.random()
        try {
            inTransaction {
                messageRepository.insertMessage(
                    conversationId = conversationId,
                    senderId = senderId,
                    content = TextContent("must roll back"),
                    category = MessageCategory.NORMAL,
                    renderType = MessageRenderType.TEXT,
                    requestId = requestId,
                )
                error("force rollback")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("force rollback", expected.message)
        }

        assertTrue(transaction(database) {
            Messages.selectAll().where { Messages.conversationId eq conversationId }.empty()
        })
        assertTrue(transaction(database) {
            SyncChanges.selectAll().where {
                (SyncChanges.scope eq SyncScope.CONVERSATION) and (SyncChanges.scopeId eq conversationId)
            }.empty()
        })
        val existingStream = transaction(database) {
            SyncStreams.selectAll().where {
                (SyncStreams.scope eq SyncScope.CONVERSATION) and (SyncStreams.scopeId eq conversationId)
            }.singleOrNull()
        }
        assertNull(existingStream)
    }

    @Test
    fun changesArePagedByRevisionInAscendingOrder() = runBlocking {
        repeat(5) { index ->
            syncRepository.appendChange(
                SyncScope.CONVERSATION,
                conversationId,
                testEvent("event-$index"),
            )
        }

        val firstPage = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 0L, 2)
        assertEquals(listOf(1L, 2L), firstPage.changes.map { it.revision })
        assertEquals(2L, firstPage.nextRevision)
        assertTrue(firstPage.hasMore)
        assertFalse(firstPage.resetRequired)

        val secondPage = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, firstPage.nextRevision, 2)
        assertEquals(listOf(3L, 4L), secondPage.changes.map { it.revision })
        assertEquals(4L, secondPage.nextRevision)
        assertTrue(secondPage.hasMore)

        val lastPage = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, secondPage.nextRevision, 2)
        assertEquals(listOf(5L), lastPage.changes.map { it.revision })
        assertFalse(lastPage.hasMore)
    }

    @Test
    fun cursorsOutsideHeadOrBelowRetentionFloorRequireReset() = runBlocking {
        repeat(4) { index ->
            syncRepository.appendChange(SyncScope.CONVERSATION, conversationId, testEvent("floor-$index"))
        }
        assertEquals(2, syncRepository.advanceFloor(SyncScope.CONVERSATION, conversationId, 3L))

        val belowFloor = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 1L, 10)
        assertTrue(belowFloor.resetRequired)
        assertTrue(belowFloor.changes.isEmpty())
        val ahead = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 5L, 10)
        assertTrue(ahead.resetRequired)
        val negative = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, -1L, 10)
        assertTrue(negative.resetRequired)

        val valid = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 2L, 10)
        assertFalse(valid.resetRequired)
        assertEquals(listOf(3L, 4L), valid.changes.map { it.revision })
    }

    @Test
    fun withdrawalErasesJournalPayloadAndDeltaDoesNotRevealOriginalBody() = runBlocking {
        val inserted = messageRepository.insertMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = TextContent("top secret body"),
            category = MessageCategory.NORMAL,
            renderType = MessageRenderType.TEXT,
            requestId = Uuid.random(),
        )!!
        assertTrue(messageRepository.revokeMessage(inserted.first.id))

        val storedEvent = transaction(database) {
            SyncChanges.selectAll().orderBy(SyncChanges.revision to SortOrder.ASC).map { it[SyncChanges.event] }
        }
        val storedReceived = assertIs<MessageEventResponse.Received>(storedEvent.first())
        assertEquals("", (storedReceived.message.content as TextContent).text)
        assertNull(storedReceived.message.sender)
        assertIs<MessageEventResponse.Withdrawn>(storedEvent.last())

        val batch = syncRepository.getChanges(SyncScope.CONVERSATION, conversationId, 0L, 10)
        assertEquals(2, batch.changes.size)
        val received = assertIs<MessageEventResponse.Received>(batch.changes.first().event)
        assertEquals("", (received.message.content as TextContent).text)
        assertNotNull(received.message.revokedAt)
        assertTrue(batch.changes.none { it.event.toString().contains("top secret body") })
    }

    @Test
    fun userScopeAndConversationSnapshotsAreBoundedAndPairedWithRevision() = runBlocking {
        val userService = SyncService(
            syncRepository = syncRepository,
            participantRepository = ConversationParticipantDataSourceImpl(),
            conversationLifecycleService = mockConversationLifecycleService(),
            contactRepository = ContactSourceImpl(),
            contactRequestRepository = ContactRequestSourceImpl(),
            groupJoinRequestRepository = GroupJoinRequestSourceImpl(),
        )

            val contactEvent = ContactEventResponse.ContactAdded(
                userId = senderId,
                contactId = outsiderId,
                timestamp = kotlin.time.Clock.System.now(),
            )
            syncRepository.appendChange(SyncScope.USER, senderId, contactEvent)
            syncRepository.appendChange(SyncScope.USER, outsiderId, contactEvent)
            val contactHead = syncRepository.getHeadsForUser(senderId)
                .single { it.scope == SyncScope.USER && it.id == senderId }
            assertEquals(1L, contactHead.revision)

            val denied = userService.getChanges(outsiderId, SyncScope.USER, senderId, 0L)
            assertTrue(denied.component2() is com.github.woodsmarshes.chat.core.model.error.ConversationError.PermissionDenied)
            val notParticipant = userService.getChanges(outsiderId, SyncScope.CONVERSATION, conversationId, 0L)
            assertTrue(notParticipant.component2() is com.github.woodsmarshes.chat.core.model.error.ConversationError.NotParticipant)

            val unauthorizedSnapshot = userService.getSnapshot(outsiderId, SyncScope.USER, senderId)
            assertTrue(unauthorizedSnapshot.component2() is com.github.woodsmarshes.chat.core.model.error.ConversationError.PermissionDenied)
            val snapshot = userService.getSnapshot(senderId, SyncScope.CONVERSATION, conversationId, limit = 10).getOrThrow()
            assertEquals(SyncScope.CONVERSATION, snapshot.scope)
            assertEquals(conversationId, snapshot.id)
            assertEquals(0L, snapshot.revision)
            assertTrue(snapshot.messages.isEmpty())
            assertTrue(snapshot.conversations.isEmpty())
    }

    private fun testEvent(text: String): RealtimeEvent = ContactEventResponse.ContactAdded(
        userId = senderId,
        contactId = outsiderId,
        timestamp = kotlin.time.Clock.System.now(),
    )

    private fun mockConversationLifecycleService(): ConversationLifecycleService =
        io.mockk.mockk(relaxed = true)
}
