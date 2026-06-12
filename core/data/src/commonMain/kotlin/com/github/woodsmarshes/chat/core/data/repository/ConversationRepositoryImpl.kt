package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.combine
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.data.model.toConversation
import com.github.woodsmarshes.chat.core.data.model.toEntity
import com.github.woodsmarshes.chat.core.data.model.toGroupProfileEntity
import com.github.woodsmarshes.chat.core.data.model.toMessageEntity
import com.github.woodsmarshes.chat.core.data.model.toParticipantEntity
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
import com.github.woodsmarshes.chat.core.model.ui.LastMessageInfo
import com.github.woodsmarshes.chat.core.network.api.rest.ConversationApi
import com.github.woodsmarshes.chat.core.network.api.rest.UserApi
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.UpdateConversationSettingsRequest
import com.github.woodsmarshes.chat.core.network.ktor.HttpEventBus
import com.github.woodsmarshes.chat.core.network.ktor.bindApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
                    combine(
                        groupProfileDao.getGroupProfiles(groupIds),
                        participantDao.getParticipantsExcludingUser(c2CIds, currentUser.id)
                    ) { groups, c2Cs->
                        val groups = groups.associateBy { it.conversation_id }
                        val c2Cs = c2Cs.associateBy { it.conversation_id }
                        entities.map { entity ->
                            val lastMessage = if (
                                entity.last_message_id != null
                            ) {
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
                            log.info { "[ConversationRepositoryImpl] lastMessage is ${lastMessage.toString()}" }
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
                                        unreadCount = 0,
                                        isPinned = entity.participant_settings?.pinnedAt != null
                                    )
                                }
                                ConversationType.PRIVATE -> {
                                    ConversationUiModel(
                                        id = entity.conversation_id,
                                        type = entity.conversation_type,
                                        name = c2Cs[entity.conversation_id]?.username,
                                        avatarUrl = c2Cs[entity.conversation_id]?.avatar,
                                        description = c2Cs[entity.conversation_id]?.bio,
                                        handle = null,
                                        lastMessage = lastMessage,
                                        unreadCount = 0,
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
            groupProfileDao.insertGroupProfiles(groupResponses.mapNotNull { it.toGroupProfileEntity() })
            userDao.insertUsers(privateResponses.mapNotNull { it.toUserEntity() })
            participantDao.insertParticipants(responses.map { it.toParticipantEntity() })
            messageDao.insertMessages(responses.mapNotNull { it.toMessageEntity() })
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
            } catch (e: Exception) {
                // Emit empty list or handle error appropriately
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
