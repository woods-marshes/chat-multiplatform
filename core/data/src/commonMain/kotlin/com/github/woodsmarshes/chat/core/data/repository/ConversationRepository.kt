package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.ui.ConversationHeader
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
        settings: GroupSettings? = null,
        memberIds: List<Uuid> = emptyList()
    ): Result<Conversation, ConversationError>

    suspend fun checkHandleExists(handle: String): Result<Boolean, ConversationError> =
        Err(ConversationError.OperationFailed)

    suspend fun uploadGroupAvatar(
        conversationId: Uuid,
        bytes: ByteArray,
    ): Result<String, ConversationError> = Err(ConversationError.OperationFailed)

    suspend fun joinGroup(id: Uuid, message: String? = null): Result<Unit, ConversationError>

    suspend fun inviteUsers(
        conversationId: Uuid,
        userIds: List<Uuid>,
    ): Result<Unit, ConversationError> = Err(ConversationError.OperationFailed)

    suspend fun leaveGroup(conversationId: Uuid): Result<Unit, ConversationError> =
        Err(ConversationError.OperationFailed)

    suspend fun deleteConversation(conversationId: Uuid): Result<Unit, ConversationError> =
        Err(ConversationError.OperationFailed)

    suspend fun getGroupJoinRequests(
        conversationId: Uuid,
        status: RequestStatus = RequestStatus.PENDING,
    ): Result<List<GroupJoinRequest>, ConversationError> = Err(ConversationError.OperationFailed)

    suspend fun handleGroupJoinRequest(
        conversationId: Uuid,
        requestId: Uuid,
        approve: Boolean,
        reason: String? = null,
    ): Result<Unit, ConversationError> = Err(ConversationError.OperationFailed)

    suspend fun getIncomingGroupRequests(
        status: RequestStatus? = RequestStatus.PENDING,
    ): Result<List<GroupJoinRequest>, ConversationError> = Err(ConversationError.OperationFailed)

    suspend fun getSentGroupRequests(
        status: RequestStatus? = null,
    ): Result<List<GroupJoinRequest>, ConversationError> = Err(ConversationError.OperationFailed)

    /** Live local ledger of join requests for one conversation; [status] = null for every status. */
    fun observeGroupJoinRequests(
        conversationId: Uuid,
        status: RequestStatus? = RequestStatus.PENDING,
    ): Flow<List<GroupJoinRequest>> = emptyFlow()

    /** Join requests for groups the current user owns or administers, every status. */
    fun observeIncomingGroupRequests(): Flow<List<GroupJoinRequest>> = emptyFlow()

    /** Join requests sent by the current user, every status. */
    fun observeSentGroupRequests(): Flow<List<GroupJoinRequest>> = emptyFlow()

    fun getMyParticipantFlow(conversationId: Uuid): Flow<ConversationParticipant?> = emptyFlow()

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
