package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.Normal
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.collections.emptyMap
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface MessageRepository {
    suspend fun insertMessage(
        conversationId: Uuid,
        senderId: Uuid,
        content: MessageContent,
        category: MessageCategory,
        renderType: MessageRenderType,
        replyToMessageId: Uuid? = null
    ): Message?

    suspend fun getHistory(
        conversationId: Uuid,
        limit: Int,
        beforeId: Uuid? = null,
        afterId: Uuid? = null
    ): List<Message>

    suspend fun searchMessages(
        conversationId: Uuid,
        keyword: String,
        limit: Int = 20
    ): List<Message>

    suspend fun getMessageById(messageId: Uuid): Message?

    suspend fun getLastMessage(conversationId: Uuid): Message?

    suspend fun getMessageRevokeContext(userId: Uuid, messageId: Uuid): Triple<Pair<Uuid, Message>, ConversationParticipant, Conversation>?

    suspend fun revokeMessage(messageId: Uuid): Boolean
    suspend fun getReadMessageUsers(messageId: Uuid): Pair<Message, List<User>>
}

class MessageDataSourceImpl : MessageRepository {
    override suspend fun insertMessage(
        conversationId: Uuid,
        senderId: Uuid,
        content: MessageContent,
        category: MessageCategory,
        renderType: MessageRenderType,
        replyToMessageId: Uuid?,
    ): Message? = dbQuery {
        val replyTo = replyToMessageId?.let {
            Messages
                .innerJoin(Users)
                .leftJoin(
                    otherTable = ConversationParticipants,
                    onColumn = { Users.id },
                    otherColumn = { ConversationParticipants.userId },
                    additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
                )
                .selectAll()
                .where { Messages.id eq it }
                .singleOrNull()
                ?.toFilteredUser()
        }

        val textToStore = when (content) {
            is System -> {
                when (content) {
                    is JoinGroupContent -> {
                        listOfNotNull(
                            content.userName,
                            content.inviterName
                        ).joinToString(" ")
                    }
                }
            }
            is Normal -> {
                when (content) {
                    is TextContent -> {
                        content.text
                    }
                    is FileContent -> {
                        content.fileName
                    }
                    is ImageContent -> null
                    is VideoContent -> null
                    is AudioContent -> null
                }
            }
        }

        Messages.insert {
            it[Messages.conversationId] = conversationId
            it[Messages.senderId] = senderId
            it[Messages.content] = content
            it[Messages.searchText] = textToStore
            it[Messages.category] = category
            it[Messages.renderType] = renderType
            it[Messages.replyToMessageId] = replyTo?.id
            it[Messages.createdAt] = Clock.System.now()
        }
            .resultedValues
            ?.singleOrNull()
            ?.also { row ->
                val newMessageId = row[Messages.id].value

                Conversations.update({
                    (Conversations.id eq conversationId) and (
                            (Conversations.lastMessageId.isNull()) or
                                    (Conversations.lastMessageId less newMessageId)
                            )
                }) {
                    it[Conversations.lastMessageId] = newMessageId
                    it[Conversations.updatedAt] = Clock.System.now()
                }
            }
            ?.toMessage(replyTo = replyTo)
    }

    override suspend fun getHistory(
        conversationId: Uuid,
        limit: Int,
        beforeId: Uuid?,
        afterId: Uuid?,
    ): List<Message> = dbQuery {
        if (beforeId != null && afterId != null && afterId >= beforeId) {
            return@dbQuery emptyList()
        }

        val query = Messages
            .innerJoin(Users)
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
            )
            .selectAll()
            .where { Messages.conversationId eq conversationId }

        beforeId?.let {
            query.andWhere { Messages.id less it }
        }

        afterId?.let {
            query.andWhere { Messages.id greater it }
        }

        val sortOrder = if (afterId != null) {
            SortOrder.ASC
        } else {
            SortOrder.DESC
        }

        val messageRows = query
            .orderBy(Messages.id to sortOrder)
            .limit(limit)
            .toList()

        buildMessagesWithReplies(messageRows, conversationId)
    }

    override suspend fun searchMessages(
        conversationId: Uuid,
        keyword: String,
        limit: Int,
    ): List<Message> = dbQuery {
        val searchPattern = "%$keyword%"

        val messageRows = Messages
            .innerJoin(Users)
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
            )
            .selectAll()
            .where {
                (Messages.conversationId eq conversationId) and (
                        (Messages.searchText like searchPattern) or
                                (Users.username like searchPattern) or
                                (Users.displayName like searchPattern)
                        )
            }
            .orderBy(Messages.id to SortOrder.DESC)
            .limit(limit)
            .toList()

        buildMessagesWithReplies(messageRows, conversationId)
    }

    override suspend fun getMessageById(messageId: Uuid): Message? = dbQuery {
        val messageRows = Messages
            .innerJoin(Users)
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq Messages.conversationId }
            )
            .selectAll()
            .where {
                Messages.id eq messageId
            }
            .singleOrNull()

        val replyToMessage = messageRows
            ?.getOrNull(Messages.replyToMessageId)
            ?.let {
                Messages
                    .innerJoin(Users)
                    .leftJoin(
                        otherTable = ConversationParticipants,
                        onColumn = { Users.id },
                        otherColumn = { ConversationParticipants.userId },
                        additionalConstraint = { ConversationParticipants.conversationId eq Messages.conversationId }
                    )
                    .selectAll()
                    .where { Messages.id eq it }
                    .singleOrNull()
                    ?.toFilteredUser()
            }

        messageRows?.toFilteredUser(replyToMessage)
    }

    override suspend fun getLastMessage(conversationId: Uuid): Message? = dbQuery {
        val messageRow = Messages
            .innerJoin(Users)
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
            )
            .selectAll()
            .where { Messages.conversationId eq conversationId }
            .orderBy(Messages.id to SortOrder.DESC)
            .limit(1)
            .singleOrNull()

        val replyToMessage = messageRow
            ?.getOrNull(Messages.replyToMessageId)
            ?.let { replyId ->
                Messages
                    .innerJoin(Users)
                    .leftJoin(
                        otherTable = ConversationParticipants,
                        onColumn = { Users.id },
                        otherColumn = { ConversationParticipants.userId },
                        additionalConstraint = { ConversationParticipants.conversationId eq Messages.conversationId }
                    )
                    .selectAll()
                    .where { Messages.id eq replyId }
                    .singleOrNull()
                    ?.toFilteredUser()
            }

        messageRow?.toFilteredUser(replyToMessage)
    }

    override suspend fun getMessageRevokeContext(
        userId: Uuid,
        messageId: Uuid,
    ): Triple<Pair<Uuid, Message>, ConversationParticipant, Conversation>? =
        dbQuery {
            Messages
                .innerJoin(Conversations)
                .innerJoin(
                    otherTable = ConversationParticipants,
                    onColumn = { Conversations.id },
                    otherColumn = { ConversationParticipants.conversationId },
                    additionalConstraint = { ConversationParticipants.userId eq userId }
                )
                .selectAll()
                .where {
                    Messages.id eq messageId
                }
                .singleOrNull()
                ?.let {
                    Triple(
                        Pair(it[Messages.senderId].value, it.toMessage()),
                        it.toConversationParticipant(),
                        it.toConversation()
                    )
                }
        }

    override suspend fun revokeMessage(messageId: Uuid): Boolean = dbQuery {
        Messages.update(where = { Messages.id eq messageId }) {
            it[Messages.revokedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun getReadMessageUsers(messageId: Uuid): Pair<Message, List<User>> = dbQuery {
        val message = Messages
            .innerJoin(Users)
            .leftJoin(ConversationParticipants)
            .selectAll()
            .where { Messages.id eq messageId }
            .singleOrNull()
            ?.toFilteredUser()
            ?: throw IllegalArgumentException("Message not found")

        val readUsers = ConversationParticipants
            .innerJoin(Users)
            .selectAll()
            .where {
                (ConversationParticipants.lastReadMessageId eq messageId) and
                        (ConversationParticipants.conversationId eq message.conversationId)
            }
            .map { it.toUser() }

        Pair(message, readUsers)
    }

    private fun buildMessagesWithReplies(
        messageRows: List<ResultRow>,
        conversationId: Uuid
    ): List<Message> {
        val replyToIds = messageRows
            .mapNotNull { it[Messages.replyToMessageId] }
            .distinct()

        val replyToMessages = if (replyToIds.isNotEmpty()) {
            Messages
                .innerJoin(Users)
                .leftJoin(
                    otherTable = ConversationParticipants,
                    onColumn = { Users.id },
                    otherColumn = { ConversationParticipants.userId },
                    additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
                )
                .selectAll()
                .where { Messages.id inList replyToIds }
                .associateBy { it[Messages.id].value }
        } else {
            emptyMap()
        }

        return messageRows.map { row ->
            val replyTo = row[Messages.replyToMessageId]?.let { replyId ->
                replyToMessages[replyId.value]?.toFilteredUser()
            }
            row.toFilteredUser(replyTo)
        }
    }
}