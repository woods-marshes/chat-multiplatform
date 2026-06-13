package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.conversation.GroupJoinRequestAction
import com.github.woodsmarshes.chat.core.network.dto.conversation.HandleGroupRequest
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.GroupJoinRequestRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import kotlin.time.Clock
import kotlin.uuid.Uuid

class GroupMembershipService(
    private val conversationRepository: ConversationRepository,
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val groupJoinRequestRepository: GroupJoinRequestRepository,
    private val groupProfileRepository: GroupProfileRepository,
    private val contactRepository: ContactRepository,
    private val eventBus: EventBus,
) {
    suspend fun inviteUsersToGroup(conversationId: Uuid, inviterId: Uuid, userIds: List<Uuid>): Result<Unit, ConversationError> = coroutineBinding {
        val (participant, group, conversation) = conversationParticipantRepository.getGroupParticipantContext(
            inviterId, conversationId
        ) ?: Err(ConversationError.NotParticipant).bind()
        conversation.deletedAt?.let { Err(ConversationError.Deleted).bind() }
        val inviterRole = participant.role
        if (!group.settings.allowMemberInvite && inviterRole !in setOf(ConversationRole.OWNER, ConversationRole.ADMIN)) {
            Err(ConversationError.PermissionDenied).bind()
        }
        val friendIds = userIds.intersect(contactRepository.getFriendIds(inviterId).toSet())
        val invited = conversationParticipantRepository.inviteUsersToConversation(conversationId, inviterId, friendIds)
        if (invited.isNotEmpty()) {
            eventBus.publishConversationEvent(
                ConversationEvent.UserJoinedConversation(
                    conversationId = conversationId,
                    userId = invited.map { it.userId },
                    inviterId = inviterId,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ConversationError.OperationFailed).bind()
        }
    }

    suspend fun inviteUser(conversationId: Uuid, userId: Uuid, targetUserId: Uuid): Result<Unit, ConversationError> {
        return inviteUsersToGroup(conversationId, userId, listOf(targetUserId))
    }

    suspend fun joinGroup(conversationId: Uuid, userId: Uuid, message: String?): Result<Unit, ConversationError> = coroutineBinding {
        val (conversation, groupProfile) =
            conversationRepository.getConversationWithGroupProfile(conversationId)
            ?: Err(ConversationError.NotFound).bind()
        val existingParticipant =
            conversationParticipantRepository.getConversationParticipant(
                conversationId = conversationId,
                userId = userId
            )
        if (existingParticipant != null) {
            Err(ConversationError.UserAlreadyMember).bind()
        }
        if (groupProfile.settings.joinApprovalRequired) {
            val request = groupJoinRequestRepository
                .insertGroupJoinRequest(
                    conversationId = conversationId,
                    applicantId = userId,
                    message = message
                ) ?: Err(ConversationError.RequestAlreadyPending).bind()
            eventBus.publishConversationEvent(
                ConversationEvent.GroupJoinRequest(
                    requestId = request.id, conversationId = conversationId,
                    applicantId = request.applicantId, message = message,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            conversationParticipantRepository.insertConversationParticipant(
                ConversationParticipant(
                    conversationId = conversationId,
                    userId = userId,
                    role = ConversationRole.MEMBER,
                    lastReadMessageId = null,
                    joinedAt = Clock.System.now(),
                    settings = ParticipantSettings()
                )
            ) ?: Err(ConversationError.OperationFailed).bind()
            eventBus.publishConversationEvent(
                ConversationEvent.UserJoinedConversation(
                    conversationId = conversationId, userId = listOf(userId),
                    inviterId = null, timestamp = Clock.System.now()
                )
            )
        }
    }

    suspend fun leaveGroup(conversationId: Uuid, userId: Uuid): Result<Unit, ConversationError> = coroutineBinding {
        val success = conversationParticipantRepository.deleteConversationParticipant(conversationId, userId)
        if (success) {
            eventBus.publishConversationEvent(
                ConversationEvent.UserLeftConversation(
                    conversationId = conversationId, userId = userId,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ConversationError.OperationFailed).bind()
        }
    }

    suspend fun getParticipants(conversationId: Uuid, userId: Uuid): Result<List<Pair<ConversationParticipant, User>>, ConversationError> = coroutineBinding {
        conversationParticipantRepository.getConversationParticipantWithConversation(userId, conversationId)
            ?: Err(ConversationError.NotParticipant).bind()
        conversationParticipantRepository.getConversationParticipantsWithUser(conversationId)
            .sortedBy { it.first.role }
    }

    suspend fun getGroupRequestsByConversation(
        conversationId: Uuid,
        adminId: Uuid,
        status: RequestStatus? = RequestStatus.PENDING,
    ): Result<List<GroupJoinRequest>, ConversationError> = coroutineBinding {
        val participant = conversationParticipantRepository.getConversationParticipant(conversationId = conversationId, userId = adminId)
            ?: Err(ConversationError.NotParticipant).bind()
        if (participant.role !in listOf(ConversationRole.OWNER, ConversationRole.ADMIN)) {
            Err(ConversationError.PermissionDenied).bind()
        }
        groupJoinRequestRepository.getJoinRequestsByConversation(conversationId = conversationId, status = status)
    }

    suspend fun getGroupRequests(userId: Uuid, status: RequestStatus? = null): Result<List<GroupJoinRequest>, ConversationError> = coroutineBinding {
        getIncomingGroupRequests(userId, status).bind() + getSentGroupRequests(userId, status).bind()
    }

    suspend fun getIncomingGroupRequests(userId: Uuid, status: RequestStatus? = null): Result<List<GroupJoinRequest>, ConversationError> = coroutineBinding {
        val conversations = conversationParticipantRepository.getConversationParticipantByUserId(
            userId, roles = listOf(ConversationRole.OWNER, ConversationRole.ADMIN)
        )
        groupJoinRequestRepository.getJoinRequestsByConversations(conversations.map { it.conversationId }, status)
    }

    suspend fun getSentGroupRequests(userId: Uuid, status: RequestStatus? = null): Result<List<GroupJoinRequest>, ConversationError> = coroutineBinding {
        groupJoinRequestRepository.getJoinRequestByApplicantId(userId, status)
    }

    suspend fun handleGroupRequest(adminId: Uuid, requests: List<HandleGroupRequest>): Result<Unit, ConversationError> = coroutineBinding {
        if (requests.isEmpty()) {
            Err(ConversationError.InvalidRequest).bind()
        }
        val requestIds = requests.map { it.groupJoinRequestId }
        val joinRequests = groupJoinRequestRepository.getJoinRequestByIds(requestIds)
        joinRequests.forEach { joinRequest ->
            if (joinRequest.status != RequestStatus.PENDING) {
                Err(ConversationError.RequestAlreadyProcessed).bind()
            }
        }
        val conversationIds = joinRequests.map { it.conversationId }.distinct()
        val participants = conversationParticipantRepository.getConversationParticipants(adminId, conversationIds)
            .associateBy { it.conversationId }
        participants.forEach { (_, participant) ->
            if (participant.role !in setOf(ConversationRole.ADMIN, ConversationRole.OWNER)) {
                Err(ConversationError.PermissionDenied).bind()
            }
        }
        val updates = requests.map { request ->
            Triple(
                request.groupJoinRequestId, request.reason, when (request.action) {
                    GroupJoinRequestAction.APPROVE -> RequestStatus.ACCEPTED
                    GroupJoinRequestAction.REJECT -> RequestStatus.REJECTED
                }
            )
        }
        val success = groupJoinRequestRepository.updateGroupJoinRequests(adminId, updates)
        if (!success) {
            Err(ConversationError.OperationFailed).bind()
        }
        val approvedRequests = requests.filter { it.action == GroupJoinRequestAction.APPROVE }
        approvedRequests.forEach { request ->
            val joinRequest = joinRequests.find { it.id == request.groupJoinRequestId }
                ?: Err(ConversationError.RequestNotFound).bind()
            eventBus.publishConversationEvent(
                ConversationEvent.GroupJoinRequestHandled(
                    requestId = joinRequest.id, conversationId = joinRequest.conversationId,
                    applicantId = joinRequest.applicantId, handlerId = adminId,
                    approved = joinRequest.status == RequestStatus.ACCEPTED,
                    reason = joinRequest.message, timestamp = Clock.System.now()
                )
            )
            val invited = conversationParticipantRepository.inviteUserToConversation(
                conversationId = joinRequest.conversationId, inviterId = adminId,
                userId = joinRequest.applicantId
            )
            if (invited != null) {
                eventBus.publishConversationEvent(
                    ConversationEvent.UserJoinedConversation(
                        conversationId = invited.conversationId,
                        userId = listOf(invited.userId), inviterId = null,
                        timestamp = Clock.System.now()
                    )
                )
            }
        }
    }
}
