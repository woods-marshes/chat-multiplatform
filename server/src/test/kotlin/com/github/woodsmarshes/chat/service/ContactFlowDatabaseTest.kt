package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.dto.contact.AddContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRequestSourceImpl
import com.github.woodsmarshes.chat.repository.ContactSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationDataSourceImpl
import com.github.woodsmarshes.chat.repository.ConversationParticipantDataSourceImpl
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.support.TestDb
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * The full contact lifecycle on a real database: request, approve (which
 * creates both contact rows and a private conversation atomically), block
 * interactions and the delete teardown.
 */
class ContactFlowDatabaseTest {

    private val eventBus = mockk<EventBus>(relaxUnitFun = true)

    private val contactRepository = ContactSourceImpl()
    private val contactRequestRepository = ContactRequestSourceImpl()
    private val conversationRepository = ConversationDataSourceImpl()
    private val participantRepository = ConversationParticipantDataSourceImpl()

    private val service = ContactService(
        contactRepository = contactRepository,
        contactRequestRepository = contactRequestRepository,
        conversationRepository = conversationRepository,
        conversationParticipantRepository = participantRepository,
        eventBus = eventBus,
    )

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
    }

    @Test
    fun requestApproveFlowCreatesContactsAndPrivateConversation() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")

        service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "hi"))
        val pending = contactRequestRepository.getRequestsByReceiver(bob).single()
        assertEquals(RequestStatus.PENDING, pending.status)

        val result = service.handleFriendRequest(bob, pending.id, ContactRequestAction.APPROVE, null)

        assertEquals(Unit, result.get())
        assertEquals(RequestStatus.ACCEPTED, contactRequestRepository.getRequestById(pending.id)!!.status)
        assertEquals(ContactStatus.FRIEND, contactRepository.getContact(alice, bob)!!.status)
        assertEquals(ContactStatus.FRIEND, contactRepository.getContact(bob, alice)!!.status)

        val conversation = conversationRepository.getExistingPrivateConversation(alice, bob)
        assertNotNull(conversation)
        assertEquals(2, participantRepository.getConversationParticipants(conversation.id).size)

        coVerify {
            eventBus.publishContactEvent(match { it is com.github.woodsmarshes.chat.events.ContactEvent.FriendRequestSent })
            eventBus.publishContactEvent(match { it is com.github.woodsmarshes.chat.events.ContactEvent.FriendRequestAccepted })
            eventBus.publishConversationEvent(match { it is com.github.woodsmarshes.chat.events.ConversationEvent.UserJoinedConversation })
        }
    }

    @Test
    fun duplicatePendingRequestIsRejected() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")

        service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "hi"))
        val again = service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "hi again"))

        assertEquals(ContactError.RequestAlreadySent, again.getError())
    }

    @Test
    fun existingFriendshipShortCircuitsNewRequests() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)

        val result = service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "hi"))

        assertEquals(ContactError.AlreadyFriends, result.getError())
    }

    @Test
    fun blockedTargetRejectsTheRequest() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        contactRepository.upsertContactStatus(userId = bob, contactId = alice, status = ContactStatus.BLOCKED)

        val result = service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "hi"))

        assertEquals(ContactError.BlockedByTarget, result.getError())
    }

    @Test
    fun approvingAnUnknownRequestIsReported() = runBlocking {
        val bob = TestDb.user("bob")

        assertEquals(
            ContactError.RequestNotFound,
            service.handleFriendRequest(bob, Uuid.random(), ContactRequestAction.APPROVE, null).getError(),
        )
    }

    @Test
    fun deleteContactSoftDeletesRowsAndTheirConversation() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        val conversationId = TestDb.privateConversation(alice, bob)

        val result = service.deleteContact(alice, bob)

        assertEquals(Unit, result.get())
        assertEquals(ContactStatus.DELETED, contactRepository.getContact(alice, bob)!!.status)
        assertEquals(ContactStatus.DELETED, contactRepository.getContact(bob, alice)!!.status)
        val conversation = transaction(TestDb.database) {
            Conversations.selectAll().where { Conversations.id eq conversationId }.single()
        }
        assertNotNull(conversation[Conversations.deletedAt])
        coVerify {
            eventBus.publishContactEvent(match { it is com.github.woodsmarshes.chat.events.ContactEvent.ContactDeleted })
        }
    }

    @Test
    fun deletedFriendsCanBefriendEachOtherAgain() = runBlocking {
        val alice = TestDb.user("alice")
        val bob = TestDb.user("bob")
        TestDb.contacts(alice, bob)
        service.deleteContact(alice, bob)

        val sent = service.sendFriendRequest(alice, AddContactRequest(targetId = bob, message = "round two"))
        assertEquals(Unit, sent.get())
        val pending = contactRequestRepository.getRequestsByReceiver(bob).single()

        val approved = service.handleFriendRequest(bob, pending.id, ContactRequestAction.APPROVE, null)

        assertEquals(Unit, approved.get())
        assertEquals(ContactStatus.FRIEND, contactRepository.getContact(alice, bob)!!.status)
    }
}
