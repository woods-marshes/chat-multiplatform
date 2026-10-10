package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupJoinRequest
import com.github.woodsmarshes.chat.core.data.model.toGroupOwnerUserEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfile
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipant
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toPeerParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupJoinRequestDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationType
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
import com.github.woodsmarshes.chat.core.model.ui.LastMessageInfo
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.FileApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.GroupJoinRequestAction
import com.github.woodsmarshes.chat.core.network.dto.conversation.HandleGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.github.woodsmarshes.chat.core.database.di.DatabaseHolder
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.io.Buffer
import kotlin.collections.emptyList
import kotlin.collections.map
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationRepositoryImpl(
    private val groupProfileDao: GroupProfileDao,
    private val conversationDao: ConversationDao,
    private val participantDao: ParticipantDao,
    private val messageDao: MessageDao,
    private val groupJoinRequestDao: GroupJoinRequestDao,
    private val userDao: UserDao,
    private val conversationApi: ConversationApi,
    private val userApi: UserApi,
    private val fileApi: FileApi,
    private val databaseHolder: DatabaseHolder,
    private val userSettingDataSource: UserSettingDataSource,
) : ConversationRepository {

    private suspend fun <T> sessionOperation(block: suspend () -> Result<T, ConversationError>): Result<T, ConversationError> =
        databaseHolder.sessionGate.executeResult({ ConversationError.Unknown(it.message) }, block)

    private fun <T> sessionFlow(factory: suspend () -> Flow<T>): Flow<T> =
        databaseHolder.sessionGate.observeSession(factory)

    private val log = KotlinLogging.logger {}
    val ownUser = userSettingDataSource.user

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getConversationListFlow(): Flow<List<ConversationUiModel>> {
        return sessionFlow {
            ownUser.flatMapLatest { currentUser ->
                if (currentUser == null) {
                    flowOf(emptyList())
                } else {
                    conversationDao.getConversationListView(currentUser.id).flatMapLatest { entities ->
                        if (entities.isEmpty()) {
                            return@flatMapLatest flowOf(emptyList())
                        }
                        val groupIds = mutableListOf<Uuid>()
                        val c2CIds = mutableListOf<Uuid>()
                        entities.forEach { entity ->
                            when (entity.conversation_type) {
                                ConversationType.GROUP -> {
                                    groupIds.add(entity.conversation_id)
                                }
                                ConversationType.PRIVATE -> {
                                   c2CIds.add(entity.conversation_id)
                                }
                            }
                        }
                        // One COUNT flow per conversation, keyed by the participant's
                        // last_read_message_id; message/participant writes re-emit these.
                        val unreadFlows: List<Flow<Long>> = entities.map { entity ->
                            messageDao.countUnreadAfter(
                                conversationId = entity.conversation_id,
                                myUserId = currentUser.id,
                                lastReadMessageId = entity.participant_last_read_message_id,
                            )
                        }
                        val unreadCountsFlow: Flow<List<Long>> =
                            combine(unreadFlows) { counts -> counts.toList() }
                        combine(
                            groupProfileDao.getGroupProfiles(groupIds),
                            participantDao.getParticipantsExcludingUser(c2CIds, currentUser.id),
                            participantDao.getConversationMemberAvatars(currentUser.id),
                            unreadCountsFlow,
                        ) { groupRows, c2cRows, memberAvatarRows, unreadCounts ->
                            val groups = groupRows.associateBy { it.conversation_id }
                            val c2cs = c2cRows.associateBy { it.conversation_id }
                            val memberAvatars = memberAvatarRows
                                .groupBy { it.conversation_id }
                                .mapValues { (_, rows) ->
                                    rows.map { row ->
                                        SenderUser(
                                            id = row.u_id,
                                            username = row.u_username,
                                            displayName = row.u_display_name,
                                            avatarUrl = row.u_avatar,
                                            role = row.p_role
                                        )
                                    }
                                }
                            entities.mapIndexed { index, entity ->
                                val lastMessage =
                                    if (entity.last_message_id != null) {
                                        LastMessageInfo(
                                            id = entity.last_message_id!!,
                                            content = entity.last_message_content!!,
                                            renderType = entity.last_message_render_type!!,
                                            senderName = entity.last_message_sender_participant_settings?.nickname ?: entity.last_message_sender_username,
                                            senderAvatar = entity.last_message_sender_avatar,
                                            createdAt = entity.last_message_created_at!!,
                                            isOwnMessage = currentUser.id == entity.last_message_sender_id
                                        )
                                    } else null
                                log.debug { "[getConversationListFlow] lastMessage=$lastMessage" }
                                when (entity.conversation_type) {
                                    ConversationType.GROUP -> {
                                        ConversationUiModel(
                                            id = entity.conversation_id,
                                            type = entity.conversation_type,
                                            name = groups[entity.conversation_id]?.name,
                                            avatarUrl = groups[entity.conversation_id]?.avatar_url,
                                            description = groups[entity.conversation_id]?.description,
                                            handle = groups[entity.conversation_id]?.handle,
                                            lastMessage = lastMessage,
                                            unreadCount = unreadCounts[index].toInt(),
                                            isPinned = entity.participant_settings?.pinnedAt != null,
                                            createdAt = entity.conversation_created_at,
                                            memberAvatars = memberAvatars[entity.conversation_id] ?: emptyList()
                                        )
                                    }
                                    ConversationType.PRIVATE -> {
                                        ConversationUiModel(
                                            id = entity.conversation_id,
                                            type = entity.conversation_type,
                                            name = c2cs[entity.conversation_id]?.username,
                                            avatarUrl = c2cs[entity.conversation_id]?.avatar,
                                            description = c2cs[entity.conversation_id]?.bio,
                                            handle = null,
                                            lastMessage = lastMessage,
                                            unreadCount = unreadCounts[index].toInt(),
                                            isPinned = entity.participant_settings?.pinnedAt != null,
                                            createdAt = entity.conversation_created_at
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun syncConversations(): Result<Unit, ConversationError> = sessionOperation { coroutineBinding {
        bindApi(ConversationError::Unknown) {
            userApi.getMyConversations()
        }.also { responses ->
            conversationDao.insertConversations(responses.map { it.toConversation().toEntity() })
            val (groupResponses, privateResponses) = responses.partition { it.type == ConversationType.GROUP }
            userDao.insertUsers(
                privateResponses.mapNotNull { it.toUserEntity() }
                        + groupResponses.mapNotNull { it.toGroupOwnerUserEntity() }
                        // A group's last message can come from any member (or
                        // from self in a private chat); MessageEntity.user_id
                        // has a foreign key, so every sender needs a row.
                        + responses.mapNotNull { it.lastMessage?.sender?.toUserEntity() }
            )
            groupProfileDao.insertGroupProfiles(groupResponses.mapNotNull { it.toGroupProfileEntity() })
            participantDao.insertParticipants(responses.map { it.toParticipantEntity() })
            // The sync API carries no peer participant row for private chats;
            // seed it once so peer lookups (name, avatar) resolve.
            participantDao.insertParticipantsIfAbsent(responses.mapNotNull { it.toPeerParticipantEntity() })
            // A single malformed lastMessage must not abort the whole sync:
            // each insert runs in its own transaction, so one row violating a
            // foreign key (sender, conversation, anything) skips only itself.
            responses.mapNotNull { response -> response.toMessageEntity() }
                .forEach { message ->
                    runCatching { messageDao.insertMessage(message) }
                        .onFailure { if (it is CancellationException) throw it; log.warn(it) { "Skipping unpersistable last message ${message.id}" } }
                }
        }
    } }

    override suspend fun createDirectChat(targetUserId: Uuid): Result<Conversation, ConversationError> = sessionOperation { coroutineBinding {
        val conversation = bindApi(ConversationError::Unknown) {
            conversationApi.createConversation(
                CreatePrivateRequest(targetUserId)
            )
        }

        bindApi(ConversationError::Unknown) {
            conversationApi.getDetail(conversation.id)
        }.also { response ->
            conversationDao.insertConversation(response.toConversation().toEntity())
            response.toUserEntity()?.let { userDao.insertUser(it) }
            participantDao.insertParticipant(response.toParticipantEntity())
            participantDao.insertParticipant(response.toParticipantEntity().copy(
                user_id = targetUserId
            ))
        }.toConversation()
    } }

    override suspend fun createGroup(
        name: String,
        handle: String?,
        description: String?,
        avatar: String?,
        settings: GroupSettings?,
        memberIds: List<Uuid>
    ): Result<Conversation, ConversationError> = sessionOperation { coroutineBinding {
        val conversation = bindApi(ConversationError::Unknown) {
            conversationApi.createConversation(
                CreateGroupRequest(
                    name = name,
                    handle = handle,
                    description = description,
                    avatar = avatar,
                    settings = settings,
                    memberIds = memberIds
                )
            )
        }

        bindApi(ConversationError::Unknown) {
            conversationApi.getDetail(conversation.id)
        }.also { response ->
            conversationDao.insertConversation(response.toConversation().toEntity())
            response.toGroupProfileEntity()?.let { groupProfileDao.insertGroupProfile(it) }
            participantDao.insertParticipant(response.toParticipantEntity())
        }.toConversation().also {
            if (memberIds.isNotEmpty()) {
                runCatching {
                    val participants = conversationApi.getParticipants(conversation.id)
                    userDao.insertUsers(participants.map { (_, user) -> user.toUserEntity() })
                    participantDao.insertParticipants(participants.map { (p, _) -> p.toEntity() })
                }
            }
        }
    } }

    override suspend fun checkHandleExists(handle: String): Result<Boolean, ConversationError> =
        sessionOperation {
            coroutineBinding {
                bindApi(ConversationError::Unknown) {
                    conversationApi.checkExists(handle.trim().removePrefix("@"))
                }
            }
        }

    override suspend fun uploadGroupAvatar(
        conversationId: Uuid,
        bytes: ByteArray,
    ): Result<String, ConversationError> = sessionOperation {
        coroutineBinding {
            val url = bindApi(ConversationError::Unknown) {
                val buffer = Buffer().apply { write(bytes) }
                fileApi.uploadAvatar(
                    source = buffer,
                    isGroup = true,
                    targetId = conversationId,
                    onProgress = { _, _ -> },
                )
            }
            if (url.isNotBlank()) {
                updateGroupProfile(
                    conversationId = conversationId,
                    avatarUrl = url,
                ).bind()
            }
            url
        }
    }

    override suspend fun joinGroup(id: Uuid, message: String?): Result<Unit, ConversationError> = sessionOperation { coroutineBinding {
        val success = bindApi(ConversationError::Unknown) {
            conversationApi.joinGroup(id, message)
        }
        if (!success) {
            Err(ConversationError.OperationFailed).bind<Unit>()
        }

        // Sync the conversation to get updated participant info
        syncConversations()
        Unit
    } }

    override suspend fun inviteUsers(
        conversationId: Uuid,
        userIds: List<Uuid>,
    ): Result<Unit, ConversationError> = sessionOperation {
        coroutineBinding {
            val success = bindApi(ConversationError::Unknown) {
                conversationApi.inviteUsers(conversationId, userIds)
            }
            if (!success) {
                Err(ConversationError.OperationFailed).bind<Unit>()
            }
            refreshGroupDetail(conversationId)
        }
    }

    override suspend fun leaveGroup(conversationId: Uuid): Result<Unit, ConversationError> =
        sessionOperation {
            coroutineBinding {
                val success = bindApi(ConversationError::Unknown) {
                    conversationApi.leaveGroup(conversationId)
                }
                if (!success) {
                    Err(ConversationError.OperationFailed).bind<Unit>()
                }
                val meId = ownUser.firstOrNull()?.id
                if (meId != null) {
                    participantDao.removeParticipant(conversationId, meId)
                }
                conversationDao.softDeleteConversation(conversationId, Clock.System.now())
            }
        }

    override suspend fun deleteConversation(conversationId: Uuid): Result<Unit, ConversationError> =
        sessionOperation {
            coroutineBinding {
                val success = bindApi(ConversationError::Unknown) {
                    conversationApi.deleteConversation(conversationId)
                }
                if (!success) {
                    Err(ConversationError.OperationFailed).bind<Unit>()
                }
                conversationDao.softDeleteConversation(conversationId, Clock.System.now())
            }
        }

    override suspend fun getGroupJoinRequests(
        conversationId: Uuid,
        status: RequestStatus,
    ): Result<List<GroupJoinRequest>, ConversationError> = sessionOperation {
        coroutineBinding {
            val requests = bindApi(ConversationError::Unknown) {
                conversationApi.getGroupJoinRequests(conversationId, status)
            }
            persistGroupJoinRequests(requests)
            requests
        }
    }

    override suspend fun handleGroupJoinRequest(
        conversationId: Uuid,
        requestId: Uuid,
        approve: Boolean,
        reason: String?,
    ): Result<Unit, ConversationError> = sessionOperation {
        coroutineBinding {
            val action = if (approve) GroupJoinRequestAction.APPROVE else GroupJoinRequestAction.REJECT
            val success = bindApi(ConversationError::Unknown) {
                conversationApi.handleGroupRequests(
                    conversationId = conversationId,
                    requests = listOf(
                        HandleGroupRequest(
                            groupJoinRequestId = requestId,
                            action = action,
                            reason = reason,
                        )
                    ),
                )
            }
            if (!success) {
                Err(ConversationError.OperationFailed).bind<Unit>()
            }
            if (approve) {
                refreshGroupDetail(conversationId)
            }
            // Mirror the decision locally: the Handled realtime event only
            // reaches the applicant, so the approving admin must update the
            // ledger row themselves.
            groupJoinRequestDao.updateGroupJoinRequestStatus(
                id = requestId,
                status = if (approve) RequestStatus.ACCEPTED else RequestStatus.REJECTED,
                handledBy = userSettingDataSource.user.firstOrNull()?.id,
                updatedAt = Clock.System.now(),
            )
        }
    }

    override suspend fun getIncomingGroupRequests(
        status: RequestStatus?,
    ): Result<List<GroupJoinRequest>, ConversationError> = sessionOperation {
        coroutineBinding {
            val requests = bindApi(ConversationError::Unknown) {
                // null = every status; the server already supports it
                userApi.getIncomingGroupRequests(status)
            }
            persistGroupJoinRequests(requests)
            requests
        }
    }

    override suspend fun getSentGroupRequests(
        status: RequestStatus?,
    ): Result<List<GroupJoinRequest>, ConversationError> = sessionOperation {
        coroutineBinding {
            val requests = bindApi(ConversationError::Unknown) {
                userApi.getSentGroupRequests(status)
            }
            persistGroupJoinRequests(requests)
            requests
        }
    }

    private suspend fun persistGroupJoinRequests(requests: List<GroupJoinRequest>) {
        if (requests.isEmpty()) return
        groupJoinRequestDao.transaction {
            requests.forEach { request ->
                // Row-isolated: one malformed row must not abort the batch sync.
                runCatching { groupJoinRequestDao.upsertGroupJoinRequest(request.toEntity()) }
                    .onFailure { log.warn(it) { "Skipping unpersistable group join request ${request.id}" } }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeGroupJoinRequests(
        conversationId: Uuid,
        status: RequestStatus?,
    ): Flow<List<GroupJoinRequest>> =
        groupJoinRequestDao.selectGroupJoinRequestsByConversation(conversationId, status)
            .map { rows -> rows.map { it.toGroupJoinRequest() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeIncomingGroupRequests(): Flow<List<GroupJoinRequest>> =
        ownUser.flatMapLatest { me ->
            if (me == null) {
                flowOf(emptyList())
            } else {
                groupJoinRequestDao.selectIncomingGroupRequests(me.id)
                    .map { rows -> rows.map { it.toGroupJoinRequest() } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSentGroupRequests(): Flow<List<GroupJoinRequest>> =
        ownUser.flatMapLatest { me ->
            if (me == null) {
                flowOf(emptyList())
            } else {
                groupJoinRequestDao.selectSentGroupRequests(me.id)
                    .map { rows -> rows.map { it.toGroupJoinRequest() } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getMyParticipantFlow(conversationId: Uuid): Flow<ConversationParticipant?> =
        ownUser.flatMapLatest { me ->
            if (me == null) {
                flowOf(null)
            } else {
                participantDao.getParticipant(conversationId, me.id).map { it?.toParticipant() }
            }
        }

    override suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String?,
        description: String?,
        avatarUrl: String?,
        handle: String?,
        ownerId: Uuid?,
        settings: GroupSettings?
    ): Result<Conversation, ConversationError> = sessionOperation {
        coroutineBinding {
            bindApi(ConversationError::Unknown) {
                conversationApi.updateGroupSettings(
                    conversationId,
                    UpdateConversationSettingsRequest(
                        name = name,
                        handle = handle,
                        description = description,
                        avatarUrl = avatarUrl,
                        ownerId = ownerId,
                        settings = settings,
                    )
                )
            }

            // Refresh conversation data
            bindApi(ConversationError::Unknown) {
                conversationApi.getDetail(conversationId)
            }.also { response ->
                conversationDao.insertConversation(response.toConversation().toEntity())
                response.toGroupProfileEntity()?.let { groupProfileDao.insertGroupProfile(it) }
            }.toConversation()
        }
    }

    override suspend fun getParticipants(id: Uuid): Flow<List<Pair<ConversationParticipant, User>>> {
        return sessionFlow { flow {
            try {
                val participants = conversationApi.getParticipants(id)
                emit(participants)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "getParticipants failed; emitting empty list" }
                emit(emptyList())
            }
        } }
    }

    override suspend fun updateMyParticipantSettings(
        conversationId: Uuid,
        settings: ParticipantSettings
    ): Result<Unit, ConversationError> = sessionOperation { coroutineBinding {
        val success = bindApi(ConversationError::Unknown) {
            conversationApi.updatePersonalSettings(conversationId, settings)
        }

        if (success) {
            // The settings update might affect how we display the conversation
            syncConversations()
        }

        Unit
    } }

    override suspend fun searchGroups(keyword: String): Result<List<GroupProfile>, ConversationError> = sessionOperation { coroutineBinding {
            bindApi(ConversationError::Unknown) {
                conversationApi.searchGroups(keyword.trim())
            }.also { groups ->
                // Cache hits so the group info page resolves from local storage later.
                groupProfileDao.insertGroupProfiles(groups.map { it.toEntity() })
            }
        } }

    override fun getGroupProfileFlow(conversationId: Uuid): Flow<GroupProfile?> =
        groupProfileDao.getGroupProfile(conversationId).map { it?.toGroupProfile() }

    override fun getGroupMembersFlow(conversationId: Uuid): Flow<List<SenderUser>> =
        participantDao.getParticipantsWithUserInfo(conversationId).map { rows ->
            rows.map { row ->
                SenderUser(
                    id = row.user_id,
                    username = row.username,
                    displayName = row.display_name,
                    avatarUrl = row.avatar,
                    role = row.role,
                )
            }
        }

    override suspend fun refreshGroupDetail(conversationId: Uuid): Result<Unit, ConversationError> = sessionOperation { coroutineBinding {
        bindApi(ConversationError::Unknown) {
            conversationApi.getDetail(conversationId)
        }.also { response ->
            conversationDao.insertConversation(response.toConversation().toEntity())
            response.toGroupProfileEntity()?.let { groupProfileDao.insertGroupProfile(it) }
            participantDao.insertParticipant(response.toParticipantEntity())
        }
        runCatching {
            val participants = conversationApi.getParticipants(conversationId)
            userDao.insertUsers(participants.map { (_, user) -> user.toUserEntity() })
            participantDao.insertParticipants(participants.map { (p, _) -> p.toEntity() })
        }.onFailure { e ->
            if (e is CancellationException) throw e
            log.warn(e) { "Failed to refresh group participants for $conversationId" }
        }
        Unit
    } }

    override fun getConversationHeaderFlow(conversationId: Uuid): Flow<ConversationHeader?> =
        combine(
            conversationDao.getConversationById(conversationId),
            groupProfileDao.getGroupProfile(conversationId),
            participantDao.getParticipantsWithUserInfo(conversationId),
            userSettingDataSource.user,
        ) { conversation, groupProfile, participants, me ->
            when {
                conversation == null -> null
                conversation.type == ConversationType.GROUP -> groupProfile?.let { profile ->
                    ConversationHeader(
                        conversationId = conversationId,
                        isGroup = true,
                        title = profile.name,
                        avatarUrl = profile.avatar_url,
                    )
                }
                else -> {
                    // Private chat: title and avatar come from the other
                    // participant; nickname (contact remark) wins over the
                    // profile display name, matching the list screen.
                    val peer = participants.firstOrNull { it.user_id != me?.id }
                    peer?.let {
                        ConversationHeader(
                            conversationId = conversationId,
                            isGroup = false,
                            title = it.nickname ?: it.display_name ?: it.username,
                            avatarUrl = it.avatar,
                            peerUserId = it.user_id,
                        )
                    }
                }
            }
        }
}
