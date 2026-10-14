package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncBatch
import com.github.woodsmarshes.chat.core.network.dto.events.SyncHead
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.core.network.dto.events.SyncSnapshot
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ContactRequestRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupJoinRequestRepository
import com.github.woodsmarshes.chat.repository.SyncRepository
import kotlin.uuid.Uuid

class SyncService(
    private val syncRepository: SyncRepository,
    private val participantRepository: ConversationParticipantRepository,
    private val conversationLifecycleService: ConversationLifecycleService,
    private val contactRepository: ContactRepository,
    private val contactRequestRepository: ContactRequestRepository,
    private val groupJoinRequestRepository: GroupJoinRequestRepository,
) {

    suspend fun getHeads(userId: Uuid): List<SyncHead> =
        syncRepository.getHeadsForUser(userId)

    suspend fun getChanges(
        callerUserId: Uuid,
        scope: SyncScope,
        id: Uuid,
        afterRevision: Long,
        limit: Int = 100,
    ): Result<SyncBatch, ConversationError> = coroutineBinding {
        authorizeScope(callerUserId, scope, id).bind()
        syncRepository.getChanges(
            scope = scope,
            id = id,
            afterRevision = afterRevision,
            limit = limit,
        )
    }

    suspend fun getSnapshot(
        callerUserId: Uuid,
        scope: SyncScope,
        id: Uuid,
        limit: Int = 100,
    ): Result<SyncSnapshot, ConversationError> = coroutineBinding {
        authorizeScope(callerUserId, scope, id).bind()
        syncRepository.getSnapshot(
            scope = scope,
            id = id,
            limit = limit,
            conversationBuilder = {
                if (scope == SyncScope.USER) {
                    conversationLifecycleService.getUserConversations(callerUserId).component1() ?: emptyList()
                } else {
                    emptyList()
                }
            },
            userEventsBuilder = {
                if (scope == SyncScope.USER) {
                    buildUserStateEvents(callerUserId)
                } else {
                    emptyList()
                }
            },
        )
    }

    private suspend fun authorizeScope(
        callerUserId: Uuid,
        scope: SyncScope,
        id: Uuid,
    ): Result<Unit, ConversationError> = coroutineBinding {
        when (scope) {
            SyncScope.USER -> {
                if (callerUserId != id) {
                    Err(ConversationError.PermissionDenied).bind()
                }
            }
            SyncScope.CONVERSATION -> {
                val participant = participantRepository.getConversationParticipant(
                    userId = callerUserId,
                    conversationId = id,
                )
                if (participant == null) {
                    Err(ConversationError.NotParticipant).bind()
                }
            }
        }
    }

    /**
     * Synthesizes current contact relationships, pending contact requests, and
     * pending group join requests for a `USER` snapshot so a resetting device
     * can reconstruct user-level state alongside `conversations`.
     */
    private suspend fun buildUserStateEvents(userId: Uuid): List<RealtimeEvent> {
        val events = mutableListOf<RealtimeEvent>()

        contactRepository.getContactsByUserId(userId).forEach { contact ->
            when (contact.status) {
                ContactStatus.FRIEND -> {
                    events.add(
                        ContactEventResponse.ContactAdded(
                            userId = contact.userId,
                            contactId = contact.contactId,
                            timestamp = contact.updatedAt,
                        )
                    )
                    if (contact.nickname != null || contact.alias != null) {
                        events.add(
                            ContactEventResponse.ContactUpdated(
                                userId = contact.userId,
                                contactId = contact.contactId,
                                nickname = contact.nickname,
                                alias = contact.alias,
                                timestamp = contact.updatedAt,
                            )
                        )
                    }
                }
                ContactStatus.BLOCKED -> {
                    events.add(
                        ContactEventResponse.UserBlocked(
                            userId = contact.userId,
                            blockedUserId = contact.contactId,
                            timestamp = contact.updatedAt,
                        )
                    )
                }
                ContactStatus.DELETED, ContactStatus.BLOCKED_BY -> Unit
            }
        }

        contactRequestRepository.getRequestsByReceiver(userId)
            .filter { it.status == RequestStatus.PENDING }
            .forEach { req ->
                events.add(
                    ContactEventResponse.FriendRequestSent(
                        requestId = req.id,
                        senderId = req.senderId,
                        receiverId = req.receiverId,
                        timestamp = req.updatedAt,
                    )
                )
            }

        groupJoinRequestRepository.getJoinRequestByApplicantId(userId)
            .forEach { req ->
                when (req.status) {
                    RequestStatus.PENDING -> events.add(
                        ConversationEventResponse.GroupJoinRequest(
                            requestId = req.id,
                            conversationId = req.conversationId,
                            applicantId = req.applicantId,
                            message = req.message,
                            timestamp = req.updatedAt,
                        )
                    )
                    RequestStatus.ACCEPTED, RequestStatus.REJECTED -> events.add(
                        ConversationEventResponse.GroupJoinRequestHandled(
                            requestId = req.id,
                            conversationId = req.conversationId,
                            applicantId = req.applicantId,
                            handlerId = req.handledById ?: req.applicantId,
                            approved = req.status == RequestStatus.ACCEPTED,
                            reason = req.message,
                            timestamp = req.updatedAt,
                        )
                    )
                    RequestStatus.CANCELED -> Unit
                }
            }

        return events
    }
}
