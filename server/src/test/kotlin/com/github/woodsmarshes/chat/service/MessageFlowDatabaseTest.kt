package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationDataSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.GroupProfileDataSourceImpl
import com.github.woodsmarshes.chat.repository.MessageDataSourceImpl
import com.github.woodsmarshes.chat.repository.PrivateFileSourceImpl
import com.github.woodsmarshes.chat.repository.UserSettingDataSourceImpl
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.support.TestDb
import com.github.woodsmarshes.chat.utils.TemporaryUploadStoreImpl
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Business flows against a real database: send, ordering (seq), history,
 * incremental sync, read cursors, blocked contacts and the private
 * attachment mapping + GC lifecycle.
 */
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class MessageFlowDatabaseTest {

    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val messageRepository = MessageDataSourceImpl()
    private val contactRepository = ContactSourceImpl()
    private val participantRepository = ConversationParticipantDataSourceImpl()
    private val conversationRepository = ConversationDataSourceImpl()
    private val privateFileRepository = PrivateFileSourceImpl()

    private val uploadStore = TemporaryUploadStoreImpl()
    private val fileService = FileService(
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
        participantRepository = participantRepository,
    )
    private val attachments = AttachmentLifecycle(
        messageRepository = messageRepository,
        fileService = fileService,
        uploadStore = uploadStore,
        privateFileRepository = privateFileRepository,
    )

    private val service = MessageService(
        groupProfileRepository = GroupProfileDataSourceImpl(),
        userSettingRepository = UserSettingDataSourceImpl(),
        messageRepository = messageRepository,
        contactRepository = contactRepository,
        conversationParticipantRepository = participantRepository,
        eventBus = eventBus,
        attachments = attachments,
    )

    private val storedFiles = mutableListOf<File>()

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
        fileService.ensureUploadDirectories()
    }

    @AfterTest
    fun cleanFiles() {
        storedFiles.forEach { it.delete() }
    }

    private suspend fun sendMessage(senderId: Uuid, conversationId: Uuid, content: MessageContent): Message =
        // Production clients mint v7 request ids (time-ordered), which the
        // server relies on for the last-message cursor; mirror that here.
        service.sendMessage(
            userId = senderId,
            conversationId = conversationId,
            content = content,
            requestId = Uuid.generateV7().toString(),
        ).get()!!

    private suspend fun sendText(senderId: Uuid, conversationId: Uuid, text: String): Message =
        sendMessage(senderId, conversationId, TextContent(text))

    @Test
    fun privateChatFlowPersistsSeqOrderLastMessageAndReadCursor() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)

        val m1 = sendText(alice, conversationId, "one")
        val m2 = sendText(bob, conversationId, "two")
        val m3 = sendText(alice, conversationId, "three")

        // Gap-free per-conversation seq across both senders.
        assertEquals(listOf(1L, 2L, 3L), listOf(m1, m2, m3).map { it.seq })

        // The conversation's last-message cursor points at the newest row.
        val conversation = conversationRepository.getExistingPrivateConversation(alice, bob)!!
        assertEquals(m3.id, conversation.lastMessageId)

        // History returns the full thread; sync after the first message
        // yields exactly the two newer ones.
        val history = service.getHistory(alice, conversationId, limit = 10, beforeId = null).get()!!
        assertEquals(setOf(m1.id, m2.id, m3.id), history.map { it.id }.toSet())
        val synced = service.syncMessages(bob, conversationId, afterId = m1.id).get()!!
        assertEquals(setOf(m2.id, m3.id), synced.map { it.id }.toSet())

        // Read cursor lands on the acknowledged message.
        service.markAsRead(conversationId, bob, m2.id)
        val bobRow = participantRepository.getConversationParticipant(bob, conversationId)!!
        assertEquals(m2.id, bobRow.lastReadMessageId)

        coVerify(exactly = 4) { eventBus.publishMessageEvent(any()) }
    }

    @Test
    fun blockedContactsCannotExchangeMessages() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)
        contactRepository.updateContact(userId = bob, contactId = alice, status = ContactStatus.BLOCKED)

        val result = service.sendMessage(
            userId = alice,
            conversationId = conversationId,
            content = TextContent("hello"),
            requestId = Uuid.generateV7().toString(),
        )

        assertEquals(MessageError.UserBlocked, result.getError())
    }

    @Test
    fun nonParticipantCannotReadOrSend() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val mallory = TestDb.user("mallory")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)
        sendText(alice, conversationId, "secret")

        assertEquals(
            MessageError.NotParticipant,
            service.getHistory(mallory, conversationId, limit = 10, beforeId = null).getError(),
        )
        assertEquals(
            MessageError.NotParticipant,
            service.sendMessage(
                userId = mallory,
                conversationId = conversationId,
                content = TextContent("intrude"),
                requestId = Uuid.generateV7().toString(),
            ).getError(),
        )
    }

    @Test
    fun deletedContactsFallUnderTheStrangerPolicy() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)
        contactRepository.deleteContact(alice, bob)

        // After deletion the rows read DELETED, which routes the message into
        // the stranger-chat policy; alice disallows strangers entirely.
        val settingsRepository = UserSettingDataSourceImpl()
        settingsRepository.initSettings(alice)
        settingsRepository.updateSettings(
            userId = alice,
            allowSearch = null,
            allowStrangerChat = false,
            showOnlineStatus = null,
            profileVisibility = null,
            friendRequestPolicy = null,
            preferences = null,
        )

        val result = service.sendMessage(
            userId = alice,
            conversationId = conversationId,
            content = TextContent("still there?"),
            requestId = Uuid.generateV7().toString(),
        )

        assertEquals(MessageError.StrangerChatDenied, result.getError())
    }

    @Test
    fun duplicateRequestIdIsANoOpReturningThePersistedMessage() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)
        val requestId = Uuid.generateV7().toString()

        val first = service.sendMessage(
            alice, conversationId, TextContent("only once"), requestId = requestId,
        ).get()!!
        val replay = service.sendMessage(
            alice, conversationId, TextContent("only once"), requestId = requestId,
        ).get()!!

        // Same message body, same echoed request id, but the SERVER id is
        // what identifies it — the replay returns the persisted row.
        assertEquals(first.id, replay.id)
        assertEquals(requestId, replay.clientRequestId?.toString())
        val rows = transaction(TestDb.database) {
            Messages.selectAll().where { Messages.conversationId eq conversationId }.toList()
        }
        assertEquals(1, rows.size)
    }

    @Test
    fun serverOwnsMessageIdentityRegardlessOfRequestIdValue() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)

        // Even a random v4 request id is accepted (it is just an outbox
        // correlation key now) and the stored id is a server-minted v7 —
        // a hostile id can no longer poison id-ordered cursors.
        val v4RequestId = Uuid.random().toString()
        val sent = service.sendMessage(
            alice, conversationId, TextContent("any id works"), requestId = v4RequestId,
        ).get()!!

        assertTrue(sent.id != Uuid.parse(v4RequestId))
        assertEquals(Uuid.parse(v4RequestId), sent.clientRequestId)
        val stored = transaction(TestDb.database) {
            Messages.selectAll().where { Messages.conversationId eq conversationId }.single()
        }
        assertEquals(sent.id, stored[Messages.id].value)
        assertEquals(Uuid.parse(v4RequestId), stored[Messages.clientRequestId])
    }

    @Test
    fun fileAttachmentMappingAuthorizesMembersAndGcRemovesEverythingOnWithdraw() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        val carol = TestDb.user("carol")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)

        val fileName = "doc-${Uuid.random()}.pdf"
        val url = "/v1/files/content/$fileName"
        val physical = File("private-uploads/file", fileName)
        physical.writeBytes(byteArrayOf(1, 2, 3))
        storedFiles.add(physical)

        // Simulate a completed upload: bytes on disk + pending registration
        // in the same store the service consults.
        uploadStore.register(
            FileContent(url = url, fileName = "doc.pdf", mimeType = "application/pdf", size = 3),
            listOf(physical.absolutePath),
        )

        // Trusted through the pending store; the download mapping is
        // recorded in the same transaction as the message.
        val sent = sendMessage(
            alice,
            conversationId,
            FileContent(url = url, fileName = "doc.pdf", mimeType = "application/pdf", size = 3),
        )

        assertEquals(listOf(conversationId), privateFileRepository.getConversationsForFile(fileName))

        // Members of a conversation the file reached may download it; an
        // outsider holding the exact URL may not.
        assertNotNull(fileService.resolveAuthorizedPrivateFile(fileName, bob))
        assertNull(fileService.resolveAuthorizedPrivateFile(fileName, carol))

        // Withdrawing the only referencing message collects the attachment.
        service.withdrawMessage(alice, sent.id)

        assertFalse(physical.exists(), "unreferenced attachment must be deleted")
        assertEquals(emptyList(), privateFileRepository.getConversationsForFile(fileName))
    }
}
