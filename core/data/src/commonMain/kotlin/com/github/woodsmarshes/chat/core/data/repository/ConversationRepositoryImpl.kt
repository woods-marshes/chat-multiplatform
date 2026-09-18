package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupOwnerUserEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toPeerParticipantEntity
import com.github.woodsmarshes.chat.core.data.model.toUserEntity
import com.github.woodsmarshes.chat.core.database.dao.ConversationDao
import com.github.woodsmarshes.chat.core.database.dao.GroupProfileDao
import com.github.woodsmarshes.chat.core.database.dao.MessageDao
import com.github.woodsmarshes.chat.core.database.dao.ParticipantDao
import com.github.woodsmarshes.chat.core.database.dao.UserDao
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.model.ui.ConversationUiModel
import com.github.woodsmarshes.chat.core.model.ui.SenderUser
import com.github.woodsmarshes.chat.core.model.ui.LastMessageInfo
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.collections.emptyList
import kotlin.collections.map
import kotlin.uuid.Uuid

class ConversationRepositoryImpl(
    private val groupProfileDao: GroupProfileDao,
    private val conversationDao: ConversationDao,
    private val participantDao: ParticipantDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val conversationApi: ConversationApi,
    private val userApi: UserApi,
    private val userSettingDataSource: UserSettingDataSource,
) : ConversationRepository {

    private val log = KotlinLogging.logger {}
    val ownUser = userSettingDataSource.user

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getConversationListFlow(): Flow<List<ConversationUiModel>> {
        return ownUser.flatMapLatest { currentUser ->
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
                                        isPinned = entity.participant_settings?.pinnedAt != null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun syncConversations(): Result<Unit, ConversationError> = coroutineBinding {
        bindApi(ConversationError::Unknown) {
            userApi.getMyConversations()
        }.also { responses ->
            conversationDao.insertConversations(responses.map { it.toConversation().toEntity() })
            val (groupResponses, privateResponses) = responses.partition { it.type == ConversationType.GROUP }
            userDao.insertUsers(
                privateResponses.mapNotNull { it.toUserEntity() }
                        + groupResponses.mapNotNull { it.toGroupOwnerUserEntity() }
            )
            groupProfileDao.insertGroupProfiles(groupResponses.mapNotNull { it.toGroupProfileEntity() })
            participantDao.insertParticipants(responses.map { it.toParticipantEntity() })
            // The sync API carries no peer participant row for private chats;
            // seed it once so peer lookups (name, avatar) resolve.
            participantDao.insertParticipantsIfAbsent(responses.mapNotNull { it.toPeerParticipantEntity() })
            // A single malformed lastMessage must not abort the whole sync.
            messageDao.insertMessages(
                responses.mapNotNull { response ->
                    runCatching { response.toMessageEntity() }
                        .onFailure { log.warn(it) { "Skipping unpersistable last message" } }
                        .getOrNull()
                }
            )
        }
    }

    override suspend fun createDirectChat(targetUserId: Uuid): Result<Conversation, ConversationError> = coroutineBinding {
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
    }

    override suspend fun createGroup(
        name: String,
        handle: String?,
        description: String?,
        avatar: String?,
        memberIds: List<Uuid>
    ): Result<Conversation, ConversationError> = coroutineBinding {
        val conversation = bindApi(ConversationError::Unknown) {
            conversationApi.createConversation(
                CreateGroupRequest(
                    name = name,
                    handle = handle,
                    description = description,
                    avatar = avatar,
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
        }.toConversation()
    }

    override suspend fun joinGroup(id: Uuid, message: String?): Result<Unit, ConversationError> = coroutineBinding {
        val success = bindApi(ConversationError::Unknown) {
            conversationApi.joinGroup(id, message)
        }
        
        if (success) {
            // Sync the conversation to get updated participant info
            syncConversations()
        }
        
        Unit
    }

    override suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String?,
        description: String?,
        avatarUrl: String?,
        handle: String?,
        ownerId: Uuid?,
        settings: GroupSettings?
    ): Result<Conversation, ConversationError> = coroutineBinding {
        val success = bindApi(ConversationError::Unknown) {
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

    override suspend fun getParticipants(id: Uuid): Flow<List<Pair<ConversationParticipant, User>>> {
        return flow {
            try {
                val participants = conversationApi.getParticipants(id)
                emit(participants)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "getParticipants failed; emitting empty list" }
                emit(emptyList())
            }
        }
    }

    override suspend fun updateMyParticipantSettings(
        conversationId: Uuid,
        settings: ParticipantSettings
    ): Result<Unit, ConversationError> = coroutineBinding {
        val success = bindApi(ConversationError::Unknown) {
            conversationApi.updatePersonalSettings(conversationId, settings)
        }
        
        if (success) {
            // The settings update might affect how we display the conversation
            syncConversations()
        }
        
        Unit
    }
}
