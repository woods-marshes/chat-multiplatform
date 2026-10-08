package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.repository.ReadCursorUpdate
import com.github.woodsmarshes.chat.events.MessageEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class MessageServiceTest {

    private val groupProfileRepository = mockk<GroupProfileRepository>()
    private val userSettingRepository = mockk<UserSettingRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val contactRepository = mockk<ContactRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val service = MessageService(
        groupProfileRepository = groupProfileRepository,
        userSettingRepository = userSettingRepository,
        messageRepository = messageRepository,
        contactRepository = contactRepository,
        conversationParticipantRepository = participantRepository,
        eventBus = eventBus,
        attachments = mockk<AttachmentLifecycle>(),
    )

    @Test
    fun markAsReadUpdatesReadCursorOfTheReadingUser() = runBlocking {
        val conversationId = Uuid.random()
        val userId = Uuid.random()
        val messageId = Uuid.random()
        coEvery { participantRepository.updateReadLastMessage(any(), any(), any()) } returns ReadCursorUpdate.ADVANCED

        val result = service.markAsRead(conversationId, userId, messageId)

        assertTrue(result.isOk)
        coVerify(exactly = 1) { eventBus.publishMessageEvent(match {
            it is MessageEvent.ReadMessage && it.messageId == messageId && it.readerId == userId && it.conversationId == conversationId
        }) }
        coVerify(exactly = 1) {
            participantRepository.updateReadLastMessage(userId, conversationId, messageId)
        }
    }

    @Test
    fun unchangedReceiptSucceedsWithoutPublishingAnOlderCursor() = runBlocking {
        coEvery { participantRepository.updateReadLastMessage(any(), any(), any()) } returns ReadCursorUpdate.UNCHANGED
        assertTrue(service.markAsRead(Uuid.random(), Uuid.random(), Uuid.random()).isOk)
        coVerify(exactly = 0) { eventBus.publishMessageEvent(any()) }
    }

    @Test
    fun invalidReceiptFailsWithoutBroadcast() = runBlocking {
        coEvery { participantRepository.updateReadLastMessage(any(), any(), any()) } returns ReadCursorUpdate.INVALID
        assertTrue(service.markAsRead(Uuid.random(), Uuid.random(), Uuid.random()).isErr)
        coVerify(exactly = 0) { eventBus.publishMessageEvent(any()) }
    }
}
