package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.utils.PRIVATE_FILE_URL_PREFIX
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class MessageServiceSendTest {

    private val groupProfileRepository = mockk<GroupProfileRepository>()
    private val userSettingRepository = mockk<UserSettingRepository>(relaxUnitFun = true)
    private val messageRepository = mockk<MessageRepository>()
    private val contactRepository = mockk<ContactRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)
    private val attachments = mockk<AttachmentLifecycle>()

    private val service = MessageService(
        groupProfileRepository = groupProfileRepository,
        userSettingRepository = userSettingRepository,
        messageRepository = messageRepository,
        contactRepository = contactRepository,
        conversationParticipantRepository = participantRepository,
        eventBus = eventBus,
        attachments = attachments,
    )

    private val userId = Uuid.random()
    private val conversationId = Uuid.random()
    private val requestId = Uuid.random()
    private val now = Clock.System.now()

    private fun givenMembership() {
        val participant = ConversationParticipant(
            conversationId = conversationId,
            userId = userId,
            role = ConversationRole.MEMBER,
            lastReadMessageId = null,
            joinedAt = now,
            settings = ParticipantSettings(),
        )
        val user = User(
            id = userId,
            username = "sender",
            email = null,
            displayName = null,
            avatarUrl = null,
            bio = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        val conversation = Conversation(
            id = conversationId,
            type = ConversationType.GROUP,
            metadata = GroupMetadata(),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            lastMessageId = null,
        )
        coEvery {
            participantRepository.getParticipantContext(userId, conversationId)
        } returns Triple(participant, user, conversation)
        coEvery { groupProfileRepository.getGroupProfile(conversationId) } returns null
    }

    private fun message(id: Uuid = Uuid.random()) = Message(
        id = id,
        conversationId = conversationId,
        category = MessageCategory.NORMAL,
        createdAt = now,
        content = TextContent("hello"),
    )

    @Test
    fun withdrawingAFileMessageDelegatesAttachmentGcToTheLifecycle() = runBlocking {
        val fileName = "report-${Uuid.random()}.pdf"
        val fileMessage = message().copy(
            content = FileContent(
                url = "$PRIVATE_FILE_URL_PREFIX$fileName",
                fileName = fileName,
                mimeType = "application/pdf",
                size = 3,
            )
        )
        coEvery { messageRepository.getMessageRevokeContext(userId, fileMessage.id) } returns Triple(
            Pair(userId, fileMessage),
            participantForRevocation(),
            conversationForRevocation(),
        )
        coEvery { messageRepository.revokeMessage(fileMessage.id) } returns true
        coJustRun { attachments.gcAfterWithdraw(any()) }

        service.withdrawMessage(userId, fileMessage.id)

        // Whether the file is actually deleted is decided inside the
        // lifecycle (covered by AttachmentLifecycleTest).
        coVerify(exactly = 1) { attachments.gcAfterWithdraw(fileMessage) }
    }

    private fun participantForRevocation() = ConversationParticipant(
        conversationId = conversationId,
        userId = userId,
        role = ConversationRole.MEMBER,
        lastReadMessageId = null,
        joinedAt = now,
        settings = ParticipantSettings(),
    )

    private fun conversationForRevocation() = Conversation(
        id = conversationId,
        type = ConversationType.GROUP,
        metadata = GroupMetadata(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        lastMessageId = null,
    )

    @Test
    fun outboxResendReturnsPersistedMessageWithoutConsumingUploadOrBroadcasting() = runBlocking {
        givenMembership()
        val persisted = message(requestId)
        coEvery {
            messageRepository.findMessageByRequestId(requestId, conversationId, userId)
        } returns persisted
        val media = ImageContent(
            url = "/uploads/image/abc.png",
            fileName = "abc.png",
            width = 1,
            height = 1,
            size = 1,
        )

        val result = service.sendMessage(
            userId = userId,
            conversationId = conversationId,
            content = media,
            requestId = requestId.toString(),
        )

        assertTrue(result.isOk)
        assertEquals(persisted.id, result.get()!!.id)
        coVerify(exactly = 0) { attachments.resolveTrustedMedia(any()) }
        coVerify(exactly = 0) { messageRepository.insertMessage(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { eventBus.publishMessageEvent(any()) }
    }

    @Test
    fun concurrentDuplicateDoesNotBroadcastASecondTime() = runBlocking {
        givenMembership()
        val persisted = message(requestId)
        coEvery {
            messageRepository.findMessageByRequestId(requestId, conversationId, userId)
        } returns null
        coEvery {
            messageRepository.insertMessage(any(), any(), any(), any(), any(), any(), any())
        } returns (persisted to false)

        val result = service.sendMessage(
            userId = userId,
            conversationId = conversationId,
            content = TextContent("hello"),
            requestId = requestId.toString(),
        )

        assertTrue(result.isOk)
        coVerify(exactly = 0) { eventBus.publishMessageEvent(any()) }
    }

    @Test
    fun firstSendBroadcastsExactlyOnce() = runBlocking {
        givenMembership()
        val persisted = message(requestId)
        coEvery {
            messageRepository.findMessageByRequestId(requestId, conversationId, userId)
        } returns null
        coEvery {
            messageRepository.insertMessage(any(), any(), any(), any(), any(), any(), any())
        } returns (persisted to true)

        val result = service.sendMessage(
            userId = userId,
            conversationId = conversationId,
            content = TextContent("hello"),
            requestId = requestId.toString(),
        )

        assertTrue(result.isOk)
        coVerify(exactly = 1) { eventBus.publishMessageEvent(any()) }
    }

    @Test
    fun malformedRequestIdIsRejectedInsteadOfSilentlyLosingIdempotency() = runBlocking {
        givenMembership()

        val result = service.sendMessage(
            userId = userId,
            conversationId = conversationId,
            content = TextContent("hello"),
            requestId = "not-a-uuid",
        )

        assertEquals(MessageError.InvalidContent, result.getError())
        coVerify(exactly = 0) { messageRepository.insertMessage(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun clientSuppliedSystemMessageIsRejected() = runBlocking {
        givenMembership()
        val forged = JoinGroupContent(userId = userId, userName = "admin")

        val result = service.sendMessage(
            userId = userId,
            conversationId = conversationId,
            content = forged,
            requestId = requestId.toString(),
        )

        assertEquals(MessageError.InvalidContent, result.getError())
        coVerify(exactly = 0) { messageRepository.insertMessage(any(), any(), any(), any(), any(), any(), any()) }
    }
}
