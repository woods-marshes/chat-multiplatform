package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.ui.ConversationHeader
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
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

    suspend fun searchGroups(keyword: String): Result<List<GroupProfile>, ConversationError>

    /** Offline-first stream of a cached group profile; null while nothing is stored. */
    fun getGroupProfileFlow(conversationId: Uuid): Flow<GroupProfile?>

    /** Room-backed member list for a group conversation. */
    fun getGroupMembersFlow(conversationId: Uuid): Flow<List<SenderUser>>

    /**
     * Fetches the latest conversation detail from the network and writes the
     * group profile (plus conversation row) into local storage.
     */
    suspend fun refreshGroupDetail(conversationId: Uuid): Result<Unit, ConversationError>

    /**
     * Offline-first chat-header stream (title + avatar + navigation target).
     * Groups resolve against the cached group profile; private chats against
     * the other participant's user row. Null while the conversation itself
     * is not in local storage.
     */
    fun getConversationHeaderFlow(conversationId: Uuid): Flow<ConversationHeader?>
}