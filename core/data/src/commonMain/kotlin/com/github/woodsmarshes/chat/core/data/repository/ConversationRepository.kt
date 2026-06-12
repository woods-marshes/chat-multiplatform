package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ConversationRepository {
    suspend fun getConversationListFlow(): Flow<List<ConversationUiModel>>

    suspend fun syncConversations(): Result<Unit, ConversationError>

    suspend fun createDirectChat(targetUserId: Uuid): Result<Conversation, ConversationError>

    suspend fun createGroup(
        name: String,
        handle: String? = null,
        description: String? = null,
        avatar: String? = null,
        memberIds: List<Uuid> = emptyList()
    ): Result<Conversation, ConversationError>

    suspend fun joinGroup(id: Uuid, message: String? = null): Result<Unit, ConversationError>

    suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String? = null,
        description: String? = null,
        avatarUrl: String? = null,
        handle: String? = null,
        ownerId: Uuid? = null,
        settings: GroupSettings? = null
    ): Result<Conversation, ConversationError>

    suspend fun getParticipants(id: Uuid): Flow<List<Pair<ConversationParticipant, User>>>

    suspend fun updateMyParticipantSettings(
        conversationId: Uuid,
        settings: ParticipantSettings
    ): Result<Unit, ConversationError>
}