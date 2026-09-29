package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.get
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.events.ContactEvent
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ContactRequestRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.utils.connectToH2Database
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Covers the two inTransaction units of work (APPROVE and deleteContact) —
 * the multi-write atomic paths where a partial commit would corrupt the
 * contact/conversation state.
 */
class ContactServiceTest {

    // Real H2 connection so the service's inTransaction blocks have a
    // transaction context; every repository call itself is mocked.
    private val database = connectToH2Database()

    private val contactRepository = mockk<ContactRepository>()
    private val contactRequestRepository = mockk<ContactRequestRepository>()
    private val conversationRepository = mockk<ConversationRepository>()
    private val participantRepository = mockk<ConversationParticipantRepository>()
    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val service = ContactService(
        contactRepository = contactRepository,
        contactRequestRepository = contactRequestRepository,
        conversationRepository = conversationRepository,
        conversationParticipantRepository = participantRepository,
        eventBus = eventBus,
    )

    private val userId = Uuid.random()
    private val senderId = Uuid.random()
    private val requestId = Uuid.random()
    private val conversationId = Uuid.random()
    private val now = Clock.System.now()

    private fun request(receiverId: Uuid = userId) = ContactRequest(
        id = requestId,
        senderId = senderId,
        receiverId = receiverId,
        message = "be my friend",
        status = RequestStatus.PENDING,
        createdAt = now,
        updatedAt = now,
    )

    private fun conversation() = Conversation(
        id = conversationId,
        type = ConversationType.PRIVATE,
        metadata = PrivateMetadata(),
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        lastMessageId = null,
    )

    @Test
    fun approveCreatesBothContactsAndANewConversation() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()
        coEvery { contactRequestRepository.updateRequestStatus(requestId, RequestStatus.ACCEPTED, null) } returns true
        coEvery {
            contactRepository.upsertContact(eq(userId), eq(senderId), any(), any(), eq(ContactStatus.FRIEND))
        } returns true
        coEvery {
            contactRepository.upsertContact(eq(senderId), eq(userId), any(), any(), eq(ContactStatus.FRIEND))
        } returns true
        coEvery { conversationRepository.getExistingPrivateConversation(userId, senderId) } returns null
        coEvery { conversationRepository.insertConversation(ConversationType.PRIVATE, PrivateMetadata()) } returns conversation()
        coEvery { participantRepository.insertConversationParticipant(any()) } answers { firstArg() }

        val result = service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null)

        assertEquals(Unit, result.get())
        coVerify(exactly = 2) { participantRepository.insertConversationParticipant(any()) }
        coVerify {
            eventBus.publishConversationEvent(match { it is ConversationEvent.UserJoinedConversation })
            eventBus.publishContactEvent(match { it is ContactEvent.FriendRequestAccepted })
        }
    }

    @Test
    fun approveReusesTheExistingPrivateConversation() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()
        coEvery { contactRequestRepository.updateRequestStatus(requestId, RequestStatus.ACCEPTED, null) } returns true
        coEvery { contactRepository.upsertContact(any(), any(), any(), any(), any()) } returns true
        coEvery { conversationRepository.getExistingPrivateConversation(userId, senderId) } returns conversation()

        service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null)

        coVerify(exactly = 0) { conversationRepository.insertConversation(any(), any()) }
        coVerify(exactly = 0) { participantRepository.insertConversationParticipant(any()) }
    }

    @Test
    fun approveByAnyoneButTheReceiverIsDenied() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request(receiverId = senderId)

        val result = service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null)

        assertEquals(ContactError.PermissionDenied, result.getError())
    }

    @Test
    fun approveFailsWhenTheRequestCannotBeMarkedAccepted() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()
        coEvery { contactRequestRepository.updateRequestStatus(requestId, RequestStatus.ACCEPTED, null) } returns false

        assertEquals(ContactError.OperationFailed, service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null).getError())
    }

    @Test
    fun approveFailsWhenAContactRowCannotBeWritten() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()
        coEvery { contactRequestRepository.updateRequestStatus(requestId, RequestStatus.ACCEPTED, null) } returns true
        coEvery {
            contactRepository.upsertContact(eq(userId), eq(senderId), any(), any(), eq(ContactStatus.FRIEND))
        } returns true
        coEvery {
            contactRepository.upsertContact(eq(senderId), eq(userId), any(), any(), eq(ContactStatus.FRIEND))
        } returns false

        assertEquals(ContactError.OperationFailed, service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null).getError())
        coVerify(exactly = 0) { conversationRepository.insertConversation(any(), any()) }
    }

    @Test
    fun rejectPublishesARejectedEventWithTheRemark() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()
        coEvery { contactRequestRepository.updateRequestStatus(requestId, RequestStatus.REJECTED, "not now") } returns true

        service.handleFriendRequest(userId, requestId, ContactRequestAction.REJECT, "not now")

        coVerify {
            eventBus.publishContactEvent(match {
                it is ContactEvent.FriendRequestRejected && it.reason == "not now"
            })
        }
    }

    @Test
    fun cancelIsOnlyForTheSender() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns request()

        val result = service.handleFriendRequest(userId, requestId, ContactRequestAction.CANCEL, null)

        assertEquals(ContactError.PermissionDenied, result.getError())
    }

    @Test
    fun deleteContactTearsDownBothRowsAndTheConversation() = runBlocking {
        coEvery {
            contactRepository.updateContact(eq(userId), eq(senderId), any(), any(), eq(ContactStatus.DELETED))
        } returns true
        coEvery {
            contactRepository.updateContact(eq(senderId), eq(userId), any(), any(), eq(ContactStatus.DELETED))
        } returns true
        coEvery { conversationRepository.getExistingPrivateConversation(userId, senderId) } returns conversation()
        coEvery { conversationRepository.softDeleteConversation(conversationId) } returns true

        val result = service.deleteContact(userId, senderId)

        assertEquals(Unit, result.get())
        coVerify {
            eventBus.publishContactEvent(match { it is ContactEvent.ContactDeleted })
        }
    }

    @Test
    fun deleteContactFailsWhenAContactRowIsMissing() = runBlocking {
        coEvery {
            contactRepository.updateContact(eq(userId), eq(senderId), any(), any(), eq(ContactStatus.DELETED))
        } returns true
        coEvery {
            contactRepository.updateContact(eq(senderId), eq(userId), any(), any(), eq(ContactStatus.DELETED))
        } returns false

        assertEquals(ContactError.OperationFailed, service.deleteContact(userId, senderId).getError())
        coVerify(exactly = 0) { conversationRepository.softDeleteConversation(any()) }
    }

    @Test
    fun deleteContactFailsWhenTheConversationCannotBeSoftDeleted() = runBlocking {
        coEvery { contactRepository.updateContact(any(), any(), any(), any(), any()) } returns true
        coEvery { conversationRepository.getExistingPrivateConversation(userId, senderId) } returns conversation()
        coEvery { conversationRepository.softDeleteConversation(conversationId) } returns false

        assertEquals(ContactError.OperationFailed, service.deleteContact(userId, senderId).getError())
        coVerify(exactly = 0) { eventBus.publishContactEvent(any()) }
    }

    @Test
    fun unknownRequestIsReported() = runBlocking {
        coEvery { contactRequestRepository.getRequestById(requestId) } returns null

        val result = service.handleFriendRequest(userId, requestId, ContactRequestAction.APPROVE, null)

        assertEquals(ContactError.RequestNotFound, result.getError())
    }
}
