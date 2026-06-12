package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.Err
import com.github.woodsmarshes.chat.repository.MessageRepository
import com.github.woodsmarshes.chat.repository.ConversationParticipantRepository
import com.github.woodsmarshes.chat.core.model.*
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.events.EventBus
import com.github.woodsmarshes.chat.events.MessageEvent
import com.github.woodsmarshes.chat.repository.ContactRepository
import com.github.woodsmarshes.chat.repository.GroupProfileRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.utils.TemporaryUploadStore
import kotlin.time.Clock
import kotlin.uuid.Uuid
import java.sql.SQLException

class MessageService(
    private val groupProfileRepository: GroupProfileRepository,
    private val userSettingRepository: UserSettingRepository,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val conversationParticipantRepository: ConversationParticipantRepository,
    private val eventBus: EventBus,
    private val uploadStore: TemporaryUploadStore,
) {
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(
        userId: Uuid,
        conversationId: Uuid,
        content: MessageContent,
        replyToMessageId: Uuid? = null,
        requestId: String,
    ): Result<Message, MessageError> = coroutineBinding {
        // 检查用户是否在会话中
        val ( participant, user, conversation ) = conversationParticipantRepository.getParticipantContext(
            userId = userId,
            conversationId = conversationId,
        )  ?: Err(MessageError.NotParticipant).bind()

        if (conversation.deletedAt != null) {
            Err(MessageError.ConversationDeleted).bind()
        }

        when (conversation.type) {
            ConversationType.GROUP -> {
                participant.mutedUntil?.let {
                    if (it > Clock.System.now()) Err(MessageError.ConversationMuted).bind()
                }
                groupProfileRepository.getGroupProfile(conversationId)?.settings?.muteAll?.let {
                    if (it) Err(MessageError.ConversationMuted).bind()
                }
            }
            ConversationType.PRIVATE -> {
                val contacts = contactRepository.getContactPairByConversation(
                    userId = userId,
                    conversationId = conversationId,
                )

                // 检查是否被拉黑
                if (contacts?.let { (c1, c2) ->
                        c1.status == ContactStatus.BLOCKED || c2.status == ContactStatus.BLOCKED
                    } == true) {
                    Err(MessageError.UserBlocked).bind()
                }
                // 如果不是好友，检查是否允许陌生人聊天
                if (contacts == null || contacts.let { (c1, c2) ->
                        c1.status == ContactStatus.DELETED || c2.status == ContactStatus.DELETED
                    }) {
                    userSettingRepository.getSettings(userId)?.privacy?.allowStrangerChat?.let {
                        if (!it) Err(MessageError.StrangerChatDenied).bind()
                    }
                }
            }
        }
        val trustedContent = if (content is MediaContent) {
            uploadStore.retrieveAndConfirm(content.url)
                ?: Err(MessageError.MediaExpired).bind() // 如果找不到，说明 URL 无效或文件已过期
        } else {
            content
        }
        try {
            val message = messageRepository.insertMessage(
                conversationId = conversationId,
                senderId = userId,
                content = trustedContent,
                category = when (trustedContent) {
                    is System -> MessageCategory.SYSTEM
                    is Normal -> MessageCategory.NORMAL
                },
                renderType = determineRenderType(trustedContent),
                replyToMessageId = replyToMessageId
            )?.copy(
                sender = SimpleUser(
                    id = user.id,
                    username = user.username,
                    displayName = participant.settings.nickname ?: user.displayName,
                    avatarUrl = user.avatarUrl,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt,
                    deletedAt = user.deletedAt,
                    role = user.role
                ),
                senderContext = MessageSenderContext(
                    conversationRole = participant.role,
                    participantSettings = participant.settings,
                    joinedAt = participant.joinedAt,
                    lastReadMessageId = participant.lastReadMessageId,
                    mutedUntil = participant.mutedUntil
                )
            ) ?: Err(MessageError.OperationFailed).bind()

            eventBus.publishMessageEvent(
                MessageEvent.SendMessage(
                    message = message,
                    conversationId = conversationId,
                    senderId = userId,
                    timestamp = Clock.System.now(),
                    requestId = requestId,
                )
            )

            message
        } catch (e: SQLException) {
            // SQL state 23503 (PostgreSQL) / 23506 (H2) = foreign key violation
            if (e.sqlState in setOf("23503", "23506")) {
                Err(MessageError.ConversationNotFound).bind()
            }
            Err(MessageError.OperationFailed).bind()
        }
    }

    /**
     * 撤回消息
     */
    suspend fun withdrawMessage(userId: Uuid, messageId: Uuid): Result<Message, MessageError> = coroutineBinding {
        val (messageContext, participant, conversation) = messageRepository.getMessageRevokeContext(userId, messageId)
            ?: Err(MessageError.MessageNotFound).bind()

        val (senderId, message) = messageContext

        val canRevoke = senderId == userId ||
                participant.role in setOf(ConversationRole.ADMIN, ConversationRole.OWNER)

        if (!canRevoke) {
            Err(MessageError.PermissionDenied).bind()
        }

        val success = messageRepository.revokeMessage(messageId)
        if (!success) {
            Err(MessageError.RevokeFailed).bind()
        }

        // 发布撤回事件
        eventBus.publishMessageEvent(
            MessageEvent.WithdrawMessage(
                messageId = messageId,
                conversationId = conversation.id,
                senderId = userId,
                timestamp = Clock.System.now()
            )
        )

        message.copy(
            content = TextContent("Message withdrawn"),
            category = MessageCategory.SYSTEM
        )
    }

    /**
     * 根据ID获取消息
     */
    suspend fun getMessageById(messageId: Uuid, userId: Uuid): Result<Message, MessageError> = coroutineBinding {
        val message = messageRepository.getMessageById(messageId)
            ?: Err(MessageError.MessageNotFound).bind()

        checkIfUserInConversation(conversationId = message.conversationId, userId = userId).bind()

        message
    }

    /**
     * 获取会话的最后一条消息
     */
    suspend fun getLastMessage(conversationId: Uuid, userId: Uuid): Result<Message?, MessageError> = coroutineBinding {
        checkIfUserInConversation(conversationId = conversationId, userId = userId).bind()
        messageRepository.getLastMessage(conversationId)
    }

    /**
     * 获取消息的已读用户列表
     */
    suspend fun getReadMessageUsers(userId: Uuid, messageId: Uuid): Result<List<User>, MessageError> = coroutineBinding {
        // 1. 获取消息和已读用户
        val (message, readUsers) = messageRepository.getReadMessageUsers(messageId)

        // 2. 检查用户是否在会话中
        checkIfUserInConversation(conversationId = message.conversationId, userId = userId).bind()

        // 3. 返回已读用户列表
        readUsers
    }

    /**
     * 检查特定用户是否已读该消息
     */
    suspend fun isMessageReadByUser(userId: Uuid, messageId: Uuid): Result<Boolean, MessageError> = coroutineBinding {
        val (message, readUsers) = messageRepository.getReadMessageUsers(messageId)

        checkIfUserInConversation(conversationId = message.conversationId, userId = userId).bind()

        readUsers.any { it.id == userId }
    }

    suspend fun getHistory(
        userId: Uuid,
        conversationId: Uuid,
        limit: Int,
        beforeId: Uuid?,
    ): Result<List<Message>, MessageError> = coroutineBinding {
        checkIfUserInConversation(conversationId = conversationId, userId = userId).bind()

        messageRepository.getHistory(
            conversationId = conversationId,
            limit = limit,
            beforeId = beforeId,
        )
    }

    /**
     * 搜索消息（独立接口）
     */
    suspend fun searchMessages(
        userId: Uuid,
        conversationId: Uuid,
        keyword: String,
        limit: Int = 20
    ): Result<List<Message>, MessageError> = coroutineBinding {
        checkIfUserInConversation(conversationId = conversationId, userId = userId).bind()

        messageRepository.searchMessages(conversationId, keyword, limit)
    }

    /**
     * 增量同步消息
     */
    suspend fun syncMessages(
        userId: Uuid,
        conversationId: Uuid,
        afterId: Uuid,
        limit: Int = 50
    ): Result<List<Message>, MessageError> = coroutineBinding {
        checkIfUserInConversation(conversationId = conversationId, userId = userId).bind()

        messageRepository.getHistory(
            conversationId = conversationId,
            limit = limit,
            afterId = afterId
        )
    }

    // 更新已读的消息
    suspend fun markAsRead(conversationId: Uuid, userId: Uuid, messageId: Uuid): Result<Unit, MessageError> = coroutineBinding {
        val success = conversationParticipantRepository.updateReadLastMessage(conversationId, userId, messageId)
        if (success) {
            eventBus.publishMessageEvent(
                MessageEvent.ReadMessage(
                    messageId = messageId,
                    conversationId = conversationId,
                    readerId = userId,
                    timestamp = Clock.System.now()
                )
            )
        } else {
            Err(MessageError.MessageNotFound).bind()
        }
    }

    fun onUserTyping(senderId: Uuid, conversationId: Uuid, isTyping: Boolean) {
        eventBus.publishMessageEvent(
            MessageEvent.UserTyping(
                conversationId = conversationId,
                userId = senderId,
                isTyping = isTyping,
                timestamp = Clock.System.now()
            )
        )
    }

    private suspend fun checkIfUserInConversation(conversationId: Uuid, userId: Uuid): Result<Unit, MessageError> = coroutineBinding {
        val participant = conversationParticipantRepository.getConversationParticipant(userId = userId, conversationId = conversationId)
        if (participant == null) {
            Err(MessageError.NotParticipant).bind()
        }
    }

    private fun determineRenderType(content: MessageContent): MessageRenderType = when (content) {
        is TextContent -> MessageRenderType.TEXT
        is ImageContent -> MessageRenderType.IMAGE
        is VideoContent -> MessageRenderType.VIDEO
        is AudioContent -> MessageRenderType.AUDIO
        is FileContent -> MessageRenderType.FILE
        else -> MessageRenderType.OTHER
    }
}