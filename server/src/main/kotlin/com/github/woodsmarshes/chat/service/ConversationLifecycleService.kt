package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.PrivateMetadata
import com.github.woodsmarshes.chat.core.model.error.ConversationError
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateConversationRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreateGroupRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.CreatePrivateRequest
import com.github.woodsmarshes.chat.core.network.dto.conversation.SimpleMessage
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.conversation.toGroupInfo
import com.github.woodsmarshes.chat.core.network.dto.conversation.toUserInfo
import com.github.woodsmarshes.chat.events.ConversationEvent
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.repository.ConversationRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.UserRepository
import kotlin.time.Clock
import kotlin.uuid.Uuid

class ConversationLifecycleService(
    private val conversationRepository: ConversationRepository,
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val groupProfileRepository: GroupProfileRepository,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val eventBus: EventBus,
) {
    suspend fun createConversation(userId: Uuid, req: CreateConversationRequest): Result<Conversation, ConversationError> = coroutineBinding {
        val conversation = when (req) {
            is CreateGroupRequest -> {
                val metadata = GroupMetadata()
                conversationRepository.insertConversation(ConversationType.GROUP, metadata).also { conv ->
                    groupProfileRepository.initGroupProfile(
                        conversationId = conv.id,
                        name = req.name,
                        handle = req.handle,
                        ownerId = userId,
                        settings = req.settings,
                        description = req.description,
                        avatarUrl = req.avatar
                    ) ?: Err(ConversationError.OperationFailed).bind()
                    conversationParticipantRepository.insertConversationParticipant(
                        ConversationParticipant(
                            conversationId = conv.id,
                            userId = userId,
                            role = ConversationRole.OWNER,
                            lastReadMessageId = null,
                            joinedAt = Clock.System.now(),
                            settings = ParticipantSettings(),
                        )
                    ) ?: Err(ConversationError.OperationFailed).bind()
                    if (req.memberIds.isNotEmpty()) {
                        val friendIds = req.memberIds.intersect(contactRepository.getFriendIds(userId).toSet())
                        val invited = conversationParticipantRepository.inviteUsersToConversation(
                            conv.id, userId, friendIds
                        )
                        if (invited.isNotEmpty()) {
                            eventBus.publishConversationEvent(
                                ConversationEvent.UserJoinedConversation(
                                    conversationId = conv.id,
                                    userId = invited.map { it.userId },
                                    inviterId = userId,
                                    timestamp = Clock.System.now()
                                )
                            )
                        }
                    }
                }
            }

            is CreatePrivateRequest -> {
                val existingConversation =
                    conversationRepository.getExistingPrivateConversation(userId, req.targetUserId)
                if (existingConversation != null) {
                    existingConversation
                } else {
                    val metadata = PrivateMetadata()
                    conversationRepository.insertConversation(ConversationType.PRIVATE, metadata).also { conv ->
                        val participants = listOf(
                            ConversationParticipant(
                                conversationId = conv.id, userId = userId,
                                role = ConversationRole.PARTICIPANT, lastReadMessageId = null,
                                joinedAt = Clock.System.now(), settings = ParticipantSettings(),
                            ),
                            ConversationParticipant(
                                conversationId = conv.id, userId = req.targetUserId,
                                role = ConversationRole.PARTICIPANT, lastReadMessageId = null,
                                joinedAt = Clock.System.now(), settings = ParticipantSettings(),
                            )
                        )
                        participants.forEach { participant ->
                            conversationParticipantRepository.insertConversationParticipant(participant)
                        }
                        eventBus.publishConversationEvent(
                            ConversationEvent.UserJoinedConversation(
                                conversationId = conv.id,
                                userId = listOf(req.targetUserId),
                                inviterId = userId,
                                timestamp = Clock.System.now()
                            )
                        )
                    }
                }
            }
        }

        eventBus.publishConversationEvent(
            ConversationEvent.ConversationCreated(
                conversationId = conversation.id,
                type = conversation.type,
                creatorId = userId,
                timestamp = Clock.System.now()
            )
        )
        conversation
    }

    suspend fun getConversationDetail(conversationId: Uuid, userId: Uuid): Result<ConversationResponse, ConversationError> = coroutineBinding {
        val (participant, conversation) = conversationParticipantRepository.getConversationParticipantWithConversation(
            userId, conversationId
        ) ?: Err(ConversationError.NotParticipant).bind()
        val conversationInfo = when (conversation.type) {
            ConversationType.GROUP -> {
                val (groupProfile, user) = groupProfileRepository.getGroupProfileWithUser(conversationId)
                    ?: Err(ConversationError.DataIntegrityError).bind()
                groupProfile.toGroupInfo(conversation.deletedAt, user.toUserInfo())
            }
            ConversationType.PRIVATE -> {
                val participants = conversationParticipantRepository.getPrivateConversationOtherParticipantsWithUser(
                    userId, conversationId
                ) ?: Err(ConversationError.TargetUserNotFound).bind()
                participants.second.toUserInfo()
            }
        }
        ConversationResponse(
            conversationId = conversation.id,
            type = conversation.type,
            lastMessage = null,
            metadata = conversation.metadata,
            participant = participant,
            conversationInfo = conversationInfo
        )
    }

    suspend fun getUserConversations(userId: Uuid): Result<List<ConversationResponse>, ConversationError>
    = coroutineBinding {
        val conversationParticipant =
            conversationParticipantRepository.getConversationParticipantByUserId(userId)
        val conversations =
            conversationRepository.getConversations(conversationParticipant.map { it.conversationId })
        val groupIdList = mutableListOf<Uuid>()
        val userIdList = mutableListOf<Uuid>()
        conversations.forEach { (conversation, _) ->
            when (conversation.type) {
                ConversationType.GROUP -> groupIdList.add(conversation.id)
                ConversationType.PRIVATE -> userIdList.add(conversation.id)
            }
        }
        val userMap = userRepository
            .getPrivateConversationOtherUser(
                userId = userId,
                conversationIds = userIdList
            )
            .associateBy { it.id }

        val groupMap = groupProfileRepository
            .getGroupProfilesWithUsers(groupIdList)
            .associateBy { (group, _) ->
                group.conversationId
            }

        conversationParticipant.mapNotNull { participant ->
            val (conversation, lastMessage) = conversations.find {
                it.first.id == participant.conversationId
            } ?: return@mapNotNull null
            val conversationInfo = when (conversation.type) {
                ConversationType.GROUP -> {
                    val (groupProfile, user) = groupMap[conversation.id] ?: return@mapNotNull null
                    groupProfile.toGroupInfo(
                        conversation.deletedAt,
                        user.toUserInfo()
                    )
                }
                ConversationType.PRIVATE -> {
                    val otherUser = userMap[conversation.id] ?: return@mapNotNull null
                    otherUser.toUserInfo()
                }
            }
            ConversationResponse(
                conversationId = conversation.id,
                type = conversation.type,
                lastMessage = lastMessage?.let { message ->
                    SimpleMessage(
                        id = message.id, sender = message.sender, category = message.category,
                        createdAt = message.createdAt, revokedAt = message.revokedAt,
                        content = message.content, senderContext = message.senderContext
                    )
                },
                metadata = conversation.metadata,
                participant = participant,
                conversationInfo = conversationInfo
            )
        }.sortedByDescending { it.lastMessage?.createdAt }
    }

    suspend fun deleteConversation(conversationId: Uuid, userId: Uuid): Result<Unit, ConversationError> = coroutineBinding {
        val conversation = conversationRepository.getConversation(conversationId)
            ?: Err(ConversationError.NotFound).bind()
        val participant = conversationParticipantRepository.getConversationParticipant(conversationId = conversationId, userId = userId)
            ?: Err(ConversationError.NotParticipant).bind()
        when (conversation.type) {
            ConversationType.GROUP -> {
                if (participant.role !in listOf(ConversationRole.OWNER, ConversationRole.ADMIN)) {
                    Err(ConversationError.PermissionDenied).bind()
                }
                val deleted = conversationRepository.softDeleteConversation(conversationId)
                if (deleted) {
                    eventBus.publishConversationEvent(
                        ConversationEvent.ConversationDeleted(
                            conversationId = conversationId, deleterId = userId,
                            timestamp = Clock.System.now()
                        )
                    )
                } else {
                    Err(ConversationError.OperationFailed).bind()
                }
            }
            ConversationType.PRIVATE -> {
                Err(ConversationError.PrivateChatDeleteNotAllowed).bind()
            }
        }
    }

    suspend fun searchGroups(keyword: String): Result<List<GroupProfile>, ConversationError> = coroutineBinding {
        groupProfileRepository.searchGroup(keyword)
    }

    suspend fun checkHandle(handle: String): Result<Boolean, ConversationError> = coroutineBinding {
        groupProfileRepository.checkHandleExists(handle)
    }
}
