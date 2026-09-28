package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.utils.inTransaction
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationSettingsService(
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val groupProfileRepository: GroupProfileRepository,
    private val eventBus: EventBus,
) {
    suspend fun updateGroupSettings(conversationId: Uuid, userId: Uuid, req: UpdateConversationSettingsRequest): Result<Unit, ConversationError> = coroutineBinding {
        val (participant, conversation) = conversationParticipantRepository.getConversationParticipantWithConversation(
            userId, conversationId
        ) ?: Err(ConversationError.NotParticipant).bind()
        if (conversation.deletedAt != null) Err(ConversationError.Deleted).bind()
        if (participant.role != ConversationRole.OWNER) {
            Err(ConversationError.PermissionDenied).bind()
        }

        // An ownerId different from the caller is an ownership transfer, which
        // is validated and applied atomically (roles + profile row) before the
        // plain profile fields; self-ownership just rewrites the same value.
        val transferRequested = req.ownerId != null && req.ownerId != userId
        if (transferRequested) {
            transferOwnership(conversationId, currentOwnerId = userId, newOwnerId = req.ownerId!!)
        }

        val success = groupProfileRepository.updateGroupProfile(
            conversationId = conversationId,
            name = req.name, handle = req.handle,
            // The new owner is already persisted by the transfer above.
            ownerId = if (transferRequested) null else req.ownerId,
            description = req.description, avatarUrl = req.avatarUrl,
            settings = req.settings
        )
        if (success) {
            eventBus.publishConversationEvent(
                ConversationEvent.GroupProfileUpdated(
                    conversationId = conversationId, updaterId = userId,
                    profile = req, timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ConversationError.OperationFailed).bind()
        }
    }

    /**
     * Swaps group ownership inside a single transaction: the outgoing owner is
     * demoted to ADMIN, the target — who must already be a participant — is
     * promoted to OWNER, and the profile row is repointed. Any failure throws
     * so the earlier role writes roll back with the transaction; committing
     * only half of this used to lock the new owner out of every owner-only
     * operation while the old owner kept their rights.
     */
    private suspend fun transferOwnership(conversationId: Uuid, currentOwnerId: Uuid, newOwnerId: Uuid) {
        if (conversationParticipantRepository.getConversationParticipant(newOwnerId, conversationId) == null) {
            throw AppException(ConversationError.NotParticipant)
        }
        inTransaction {
            val demoted = conversationParticipantRepository.updateConversationParticipantRole(
                userId = currentOwnerId, conversationId = conversationId, role = ConversationRole.ADMIN
            )
            val promoted = demoted && conversationParticipantRepository.updateConversationParticipantRole(
                userId = newOwnerId, conversationId = conversationId, role = ConversationRole.OWNER
            )
            val profileUpdated = promoted && groupProfileRepository.updateGroupProfile(
                conversationId = conversationId, ownerId = newOwnerId
            )
            if (!profileUpdated) {
                throw AppException(ConversationError.OperationFailed)
            }
        }
    }

    suspend fun updatePersonalSettings(conversationId: Uuid, userId: Uuid, req: ParticipantSettings): Result<Unit, ConversationError> = coroutineBinding {
        // Per the repository contract, false means no matching participant
        // row — the caller is not a member, not a generic failure.
        val success = conversationParticipantRepository.updateParticipantSettings(
            userId = userId, conversationId = conversationId, settings = req
        )
        if (success) {
            eventBus.publishConversationEvent(
                ConversationEvent.PersonalSettingsUpdated(
                    conversationId = conversationId, userId = userId,
                    settings = req, timestamp = Clock.System.now()
                )
            )
        } else {
            Err(ConversationError.NotParticipant).bind()
        }
    }
}
