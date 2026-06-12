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
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
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
        val success = groupProfileRepository.updateGroupProfile(
            conversationId = conversationId,
            name = req.name, handle = req.handle, ownerId = req.ownerId,
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

    suspend fun updatePersonalSettings(conversationId: Uuid, userId: Uuid, req: ParticipantSettings): Result<Unit, ConversationError> = coroutineBinding {
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
            Err(ConversationError.OperationFailed).bind()
        }
    }
}
