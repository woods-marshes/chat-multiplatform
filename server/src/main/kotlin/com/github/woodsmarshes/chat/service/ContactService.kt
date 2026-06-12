package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ContactError
import com.github.woodsmarshes.chat.core.network.dto.contact.AddContactRequest
import com.github.woodsmarshes.chat.core.network.dto.contact.ContactRequestAction
import com.github.woodsmarshes.chat.events.ContactEvent
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ContactRequestRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ContactService(
    private val contactRepository: ContactRepository,
    private val contactRequestRepository: ContactRequestRepository,
    private val conversationRepository: ConversationRepository,
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val eventBus: EventBus,
) {

    suspend fun getContacts(userId: Uuid): Result<List<Pair<Contact, User>>, ContactError> = coroutineBinding {
        contactRepository.getContactsWithUser(userId)
    }

    suspend fun sendFriendRequest(userId: Uuid, req: AddContactRequest): Result<Unit, ContactError> = coroutineBinding {
        val existingContact1 = contactRepository.getContact(userId, req.targetId)
        val existingContact2 = contactRepository.getContact(req.targetId, userId)
        if (existingContact1?.status == ContactStatus.BLOCKED) {
            Err(ContactError.UserBlocked).bind()
        }
        if (existingContact2?.status == ContactStatus.BLOCKED) {
            Err(ContactError.BlockedByTarget).bind()
        }
        if (existingContact1?.status == ContactStatus.FRIEND || existingContact2?.status == ContactStatus.FRIEND) {
            Err(ContactError.AlreadyFriends).bind()
        }
        val existingRequests = contactRequestRepository.getRequestsBySenderAndReceiver(userId, req.targetId)
        val pendingRequest = existingRequests.find { it.status == RequestStatus.PENDING }
        if (pendingRequest != null) {
            Err(ContactError.RequestAlreadySent).bind()
        }
        val contactRequest = contactRequestRepository.insertContactRequest(
            senderId = userId,
            receiverId = req.targetId,
            message = req.message
        ) ?: Err(ContactError.OperationFailed).bind()

        eventBus.publishContactEvent(
            ContactEvent.FriendRequestSent(
                requestId = contactRequest.id,
                senderId = contactRequest.senderId,
                receiverId = contactRequest.receiverId,
                timestamp = Clock.System.now()
            )
        )
    }

    suspend fun getSentRequests(userId: Uuid): Result<List<ContactRequest>, ContactError> = coroutineBinding {
        contactRequestRepository.getRequestsBySender(userId)
    }
    suspend fun getReceivedRequests(userId: Uuid): Result<List<ContactRequest>, ContactError> = coroutineBinding {
        contactRequestRepository.getRequestsByReceiver(userId)
    }
    suspend fun getAllRequests(userId: Uuid): Result<List<ContactRequest>, ContactError> = coroutineBinding {
        val sentRequests = contactRequestRepository.getRequestsBySender(userId)
        val receivedRequests = contactRequestRepository.getRequestsByReceiver(userId)
        (sentRequests + receivedRequests).sortedByDescending { it.createdAt }
    }

    suspend fun handleFriendRequest(
        userId: Uuid,
        contactRequestId: Uuid,
        action: ContactRequestAction,
        remark: String?
    ): Result<Unit, ContactError> = coroutineBinding {
        val contactRequest = contactRequestRepository.getRequestById(contactRequestId)
            ?: Err(ContactError.RequestNotFound).bind()
        when (action) {
            ContactRequestAction.APPROVE -> {
                if (contactRequest.receiverId != userId) {
                    Err(ContactError.PermissionDenied).bind()
                }

                val updated = contactRequestRepository.updateRequestStatus(contactRequestId, RequestStatus.ACCEPTED, remark)
                if (!updated) Err(ContactError.OperationFailed).bind()

                contactRepository.upsertContact(
                    userId = userId,
                    contactId = contactRequest.senderId,
                    status = ContactStatus.FRIEND,
                )
                contactRepository.upsertContact(
                    userId = contactRequest.senderId,
                    contactId = userId,
                    status = ContactStatus.FRIEND,
                )
                val existingConversation =
                    conversationRepository.getExistingPrivateConversation(userId, contactRequest.senderId)
                val conversationId = existingConversation?.id
                    ?: conversationRepository.insertConversation(
                        ConversationType.PRIVATE,
                        PrivateMetadata()
                    )
                        .also { conversation ->
                            val participants = listOf(
                                ConversationParticipant(
                                    conversationId = conversation.id,
                                    userId = userId,
                                    role = ConversationRole.PARTICIPANT,
                                    lastReadMessageId = null,
                                    joinedAt = Clock.System.now(),
                                    settings = ParticipantSettings(),
                                ), ConversationParticipant(
                                    conversationId = conversation.id,
                                    userId = contactRequest.senderId,
                                    role = ConversationRole.PARTICIPANT,
                                    lastReadMessageId = null,
                                    joinedAt = Clock.System.now(),
                                    settings = ParticipantSettings(),
                                )
                            )
                            participants.forEach { participant ->
                                conversationParticipantRepository.insertConversationParticipant(participant)
                            }
                        }.id

                eventBus.publishConversationEvent(
                    ConversationEvent.UserJoinedConversation(
                        conversationId = conversationId,
                        userId = listOf(userId, contactRequest.senderId),
                        inviterId = userId,
                        timestamp = Clock.System.now()
                    )
                )

                eventBus.publishContactEvent(
                    ContactEvent.FriendRequestAccepted(
                        requestId = contactRequestId,
                        senderId = contactRequest.senderId,
                        receiverId = userId,
                        timestamp = Clock.System.now()
                    )
                )
            }
            ContactRequestAction.REJECT -> {
                if (contactRequest.receiverId != userId) {
                    Err(ContactError.PermissionDenied).bind()
                }
                val updated = contactRequestRepository.updateRequestStatus(contactRequestId, RequestStatus.REJECTED, remark)
                if (updated) {
                    eventBus.publishContactEvent(
                        ContactEvent.FriendRequestRejected(
                            requestId = contactRequestId,
                            senderId = contactRequest.senderId,
                            receiverId = userId,
                            reason = remark,
                            timestamp = Clock.System.now()
                        )
                    )
                } else {
                    Err(ContactError.OperationFailed).bind()
                }
            }
            ContactRequestAction.CANCEL -> {
                if (contactRequest.senderId != userId) {
                    Err(ContactError.PermissionDenied).bind()
                }
                val updated = contactRequestRepository.updateRequestStatus(contactRequestId, RequestStatus.CANCELED, remark)
                if (!updated) Err(ContactError.OperationFailed).bind()
            }
        }
    }

    suspend fun deleteContact(userId: Uuid, id: Uuid): Result<Unit, ContactError> = coroutineBinding {
        val result1 = contactRepository.updateContact(userId = userId, contactId = id, status = ContactStatus.DELETED)
        val result2 = contactRepository.updateContact(userId = id, contactId = userId, status = ContactStatus.DELETED)
        if (!result1 || !result2) {
            Err(ContactError.OperationFailed).bind()
        }
        conversationRepository.getExistingPrivateConversation(userId, id)?.let {conversation ->
            val deleted = conversationRepository.softDeleteConversation(conversation.id)
            if (deleted) {
                eventBus.publishContactEvent(
                    ContactEvent.ContactDeleted(
                        userId = userId,
                        contactId = id,
                        timestamp = Clock.System.now()
                    )
                )
            } else {
                Err(ContactError.OperationFailed).bind()
            }
        }
    }

    suspend fun blockUser(userId: Uuid, id: Uuid): Result<Unit, ContactError> = coroutineBinding {
        val success = contactRepository.upsertContactStatus(userId = userId, contactId = id, status = ContactStatus.BLOCKED)
        if (success) {
            eventBus.publishContactEvent(
                ContactEvent.UserBlocked(
                    userId = userId,
                    blockedUserId = id,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ContactError.OperationFailed).bind()
        }
    }

    suspend fun unblockUser(userId: Uuid, id: Uuid): Result<Unit, ContactError> = coroutineBinding {
        val success = contactRepository.upsertContactStatus(userId = userId, contactId = id, status = ContactStatus.FRIEND)
        if (success) {
            eventBus.publishContactEvent(
                ContactEvent.UserUnblocked(
                    userId = userId,
                    unblockedUserId = id,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ContactError.OperationFailed).bind()
        }
    }

    suspend fun updateContactInfo(userId: Uuid, id: Uuid, nickname: String?, alias: String?): Result<Unit, ContactError> = coroutineBinding {
        val success = contactRepository.updateContact(userId, id, nickname, alias)
        if (success) {
            eventBus.publishContactEvent(
                ContactEvent.ContactUpdated(
                    userId = userId,
                    contactId = id,
                    nickname = nickname,
                    alias = alias,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ContactError.OperationFailed).bind()
        }
    }

}
