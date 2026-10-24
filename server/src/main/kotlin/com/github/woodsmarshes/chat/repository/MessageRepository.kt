package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.AudioContent
import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.FileContent
import com.github.woodsmarshes.chat.core.model.ImageContent
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.Normal
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.VideoContent
import com.github.woodsmarshes.chat.core.model.error.MessageError
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.exceptions.AppException
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.PrivateFiles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.PRIVATE_FILE_URL_PREFIX
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import java.sql.SQLException
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface MessageRepository {
    /**
     * Persists a message and returns `(message, isNew)`.
     *
     * When [requestId] is supplied (client outbox resend), retries with an
     * identical payload return the already-persisted row with `isNew = false`
     * and leave conversation state and the sync journal untouched. Reusing a
     * [requestId] with a different payload throws `AppException(MessageError.InvalidContent)`.
     */
    suspend fun insertMessage(
        conversationId: Uuid,
        senderId: Uuid,
        content: MessageContent,
        category: MessageCategory,
        renderType: MessageRenderType,
        replyToMessageId: Uuid? = null,
        requestId: Uuid? = null,
    ): Pair<Message, Boolean>?

    /**
     * Looks up a previously persisted message by its client-generated
     * [requestId], scoped to the conversation and sender that created it.
     */
    suspend fun findMessageByRequestId(
        requestId: Uuid,
        conversationId: Uuid,
        senderId: Uuid,
    ): Message?

    suspend fun getHistory(
        conversationId: Uuid,
        limit: Int,
        beforeId: Uuid? = null,
    ): List<Message>

    suspend fun searchMessages(
        conversationId: Uuid,
        keyword: String,
        limit: Int = 20,
    ): List<Message>

    suspend fun getMessageById(messageId: Uuid): Message?

    suspend fun getMessageRevokeContext(
        userId: Uuid,
        messageId: Uuid,
    ): Triple<Pair<Uuid, Message>, ConversationParticipant, Conversation>?

    suspend fun revokeMessage(messageId: Uuid): Boolean

    /**
     * How many live non-revoked file messages still reference the private
     * attachment stored under [fileName]. Matches the physical storage URL in
     * `Messages.content` — not the user-supplied display name in `searchText`
     * — and fails closed (returning a positive count) if any candidate row
     * cannot be decoded.
     */
    suspend fun countLiveFileReferences(fileName: String): Long

    suspend fun getReadMessageUsers(messageId: Uuid): Pair<Message, List<User>>?

    suspend fun getUnreadCounts(userId: Uuid): Map<Uuid, Long>

    suspend fun getUnreadCount(conversationId: Uuid, userId: Uuid): Long
}

class MessageDataSourceImpl(
    private val syncRepository: SyncRepository = SyncDataSourceImpl(),
) : MessageRepository {

    private companion object {
        /** 23503 = PostgreSQL FK violation, 23506 = H2 referential integrity. */
        val FK_VIOLATION_SQL_STATES = setOf("23503", "23506")
        /** 23505 = PostgreSQL / H2 unique constraint violation. */
        val UNIQUE_VIOLATION_SQL_STATES = setOf("23505")
    }

    override suspend fun insertMessage(
        conversationId: Uuid,
        senderId: Uuid,
        content: MessageContent,
        category: MessageCategory,
        renderType: MessageRenderType,
        replyToMessageId: Uuid?,
        requestId: Uuid?,
    ): Pair<Message, Boolean>? =
        try {
            insertMessageInTransaction(
                conversationId = conversationId,
                senderId = senderId,
                content = content,
                category = category,
                renderType = renderType,
                replyToMessageId = replyToMessageId,
                requestId = requestId,
            )
        } catch (e: AppException) {
            throw e
        } catch (e: SQLException) {
            if (e.sqlState in UNIQUE_VIOLATION_SQL_STATES && requestId != null) {
                // Concurrent duplicate send with the same (senderId, conversationId, requestId)
                // won the insert race between our pre-check and INSERT. Look up
                // the winner's row and validate payload equivalence.
                val winner = findMessageByRequestIdWithReply(requestId, conversationId, senderId)
                    ?: throw AppException(MessageError.OperationFailed)
                validateIdempotentPayload(winner, content, category, replyToMessageId)
                return Pair(winner, false)
            }
            if (e.sqlState in FK_VIOLATION_SQL_STATES) throw AppException(MessageError.ConversationNotFound)
            throw AppException(MessageError.OperationFailed)
        }

    private suspend fun insertMessageInTransaction(
        conversationId: Uuid,
        senderId: Uuid,
        content: MessageContent,
        category: MessageCategory,
        renderType: MessageRenderType,
        replyToMessageId: Uuid?,
        requestId: Uuid?,
    ): Pair<Message, Boolean>? = dbQuery {
        // Lock the conversation row first so concurrent sends in the same
        // conversation serialize on the conversation row before touching
        // Messages or SyncStreams. This also prevents unique-index races from
        // aborting the enclosing transaction under PostgreSQL when two
        // identical requestIds arrive concurrently.
        val convExists = Conversations
            .selectAll()
            .where { Conversations.id eq conversationId }
            .forUpdate()
            .singleOrNull() != null
        if (!convExists) {
            throw AppException(MessageError.ConversationNotFound)
        }

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
                .where { (Messages.id eq it) and (Messages.conversationId eq conversationId) }
                .singleOrNull()
                ?.toFilteredUser()
        }

        if (requestId != null) {
            val existing = Messages
                .selectAll()
                .where {
                    (Messages.clientRequestId eq requestId) and
                        (Messages.conversationId eq conversationId) and
                        (Messages.senderId eq senderId)
                }
                .singleOrNull()
            if (existing != null) {
                val existingMsg = existing.toMessage(replyTo = replyTo)
                validateIdempotentPayload(existingMsg, content, category, replyTo?.id)
                return@dbQuery Pair(existingMsg, false)
            }
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
                    is TextContent -> content.text
                    is FileContent -> content.fileName
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
            it[Messages.clientRequestId] = requestId
        }
            .resultedValues
            ?.singleOrNull()
            ?.let { row ->
                val newMessageId = row[Messages.id].value
                val message = row.toMessage(replyTo = replyTo)

                // Record which conversation each private attachment reached,
                // atomically with the message: the download authorization for
                // /v1/files/content/… is derived from these rows.
                content.privateFileNames().forEach { fileName ->
                    PrivateFiles.upsert {
                        it[this.fileName] = fileName
                        it[this.conversationId] = conversationId
                    }
                }

                Conversations.update({
                    (Conversations.id eq conversationId) and (
                        Conversations.lastMessageId.isNull() or
                            (Conversations.lastMessageId less newMessageId)
                        )
                }) {
                    it[Conversations.lastMessageId] = newMessageId
                    it[Conversations.updatedAt] = Clock.System.now()
                }

                // Append the message creation to the conversation sync stream
                // in the exact same transaction.
                syncRepository.appendChange(
                    scope = SyncScope.CONVERSATION,
                    id = conversationId,
                    event = MessageEventResponse.Received(
                        message = message,
                        conversationId = conversationId,
                        senderId = senderId,
                        requestId = (requestId ?: newMessageId).toString(),
                    ),
                )

                Pair(message, true)
            }
    }

    private fun validateIdempotentPayload(
        existing: Message,
        requestedContent: MessageContent,
        requestedCategory: MessageCategory,
        requestedReplyToId: Uuid?,
    ) {
        val existingReplyId = existing.replyTo?.id
        // If the message was already revoked, its content was wiped to TextContent(""),
        // so only verify category and replyTo when revokedAt is non-null.
        val contentMatches = if (existing.revokedAt != null) {
            true
        } else {
            existing.content == requestedContent ||
                (existing.content is MediaContent &&
                    requestedContent is MediaContent &&
                    (existing.content as MediaContent).url == requestedContent.url &&
                    (existing.content as MediaContent).fileName == requestedContent.fileName)
        }
        val replyMatches = existingReplyId == requestedReplyToId ||
            (existing.replyTo == null && requestedReplyToId != null && existingReplyId == null)
        if (!contentMatches || existing.category != requestedCategory || !replyMatches) {
            throw AppException(MessageError.InvalidContent)
        }
    }

    private suspend fun findMessageByRequestIdWithReply(
        requestId: Uuid,
        conversationId: Uuid,
        senderId: Uuid,
    ): Message? = dbQuery {
        val row = Messages
            .selectAll()
            .where {
                (Messages.clientRequestId eq requestId) and
                    (Messages.conversationId eq conversationId) and
                    (Messages.senderId eq senderId)
            }
            .singleOrNull() ?: return@dbQuery null
        val replyTo = row[Messages.replyToMessageId]?.value?.let { replyId ->
            Messages
                .innerJoin(Users)
                .leftJoin(
                    otherTable = ConversationParticipants,
                    onColumn = { Users.id },
                    otherColumn = { ConversationParticipants.userId },
                    additionalConstraint = { ConversationParticipants.conversationId eq conversationId }
                )
                .selectAll()
                .where { (Messages.id eq replyId) and (Messages.conversationId eq conversationId) }
                .singleOrNull()
                ?.toFilteredUser()
        }
        row.toMessage(replyTo = replyTo)
    }

    override suspend fun findMessageByRequestId(
        requestId: Uuid,
        conversationId: Uuid,
        senderId: Uuid,
    ): Message? = findMessageByRequestIdWithReply(requestId, conversationId, senderId)

    private fun String.escapeLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    /** File names of private attachments carried by this content, if any. */
    private fun MessageContent.privateFileNames(): List<String> = when (this) {
        is Normal -> when (this) {
            is FileContent -> listOfNotNull(
                url.takeIf { it.startsWith(PRIVATE_FILE_URL_PREFIX) }?.substringAfterLast('/')
            )
            else -> emptyList()
        }
        is System -> emptyList()
    }

    override suspend fun getHistory(
        conversationId: Uuid,
        limit: Int,
        beforeId: Uuid?,
    ): List<Message> = dbQuery {
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

        val messageRows = query
            .orderBy(Messages.id to SortOrder.DESC)
            .limit(limit)
            .toList()

        buildMessagesWithReplies(messageRows, conversationId)
    }

    override suspend fun searchMessages(
        conversationId: Uuid,
        keyword: String,
        limit: Int,
    ): List<Message> = dbQuery {
        val searchTerm = keyword.trim().lowercase()
        if (searchTerm.isEmpty()) {
            return@dbQuery emptyList()
        }
        val searchPattern = "%${searchTerm.escapeLike()}%"

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
                (Messages.conversationId eq conversationId) and
                    (Messages.revokedAt.isNull()) and (
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
                    val replyId = it[Messages.replyToMessageId]?.value
                    val replyTo = replyId?.let { rid ->
                        Messages.selectAll().where { Messages.id eq rid }.singleOrNull()?.toMessage()
                    }
                    Triple(
                        Pair(it[Messages.senderId].value, it.toMessage(replyTo = replyTo)),
                        it.toConversationParticipant(),
                        it.toConversation()
                    )
                }
        }

    override suspend fun revokeMessage(messageId: Uuid): Boolean = dbQuery {
        val existingRow = Messages
            .selectAll()
            .where { (Messages.id eq messageId) and Messages.revokedAt.isNull() }
            .forUpdate()
            .singleOrNull() ?: return@dbQuery false

        val conversationId = existingRow[Messages.conversationId].value
        val senderId = existingRow[Messages.senderId].value
        val now = Clock.System.now()

        // Blanking the payload is what makes a revoke irreversible from the
        // read paths: history, "last message", sync delta hydration, and search
        // all read these columns. A second revoke reports failure instead of
        // rewriting the timestamp.
        val updated = Messages.update(
            where = { (Messages.id eq messageId) and Messages.revokedAt.isNull() }
        ) {
            it[Messages.revokedAt] = now
            it[Messages.content] = TextContent("")
            it[Messages.searchText] = ""
        } > 0

        if (updated) {
            syncRepository.appendChange(
                scope = SyncScope.CONVERSATION,
                id = conversationId,
                event = MessageEventResponse.Withdrawn(
                    messageId = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    timestamp = now,
                ),
            )
        }
        updated
    }

    override suspend fun countLiveFileReferences(fileName: String): Long = dbQuery {
        val expectedUrl = "$PRIVATE_FILE_URL_PREFIX$fileName"
        var references = 0L
        Messages
            .select(Messages.content)
            .where {
                (Messages.renderType eq MessageRenderType.FILE) and
                    Messages.revokedAt.isNull()
            }
            .forEach { row ->
                val matchesOrUnreadable = try {
                    when (val decoded = row[Messages.content]) {
                        is FileContent -> decoded.url == expectedUrl
                        else -> true
                    }
                } catch (_: Exception) {
                    // Fail closed if historical JSON cannot be decoded into the
                    // current MessageContent schema: never delete a physical
                    // attachment whose reference state is uncertain.
                    true
                }
                if (matchesOrUnreadable) {
                    references += 1L
                }
            }
        references
    }

    override suspend fun getReadMessageUsers(messageId: Uuid): Pair<Message, List<User>>? = dbQuery<Pair<Message, List<User>>?> {
        val message = Messages
            .innerJoin(Users)
            .selectAll()
            .where { Messages.id eq messageId }
            .singleOrNull()
            ?.toFilteredUser()
            ?: return@dbQuery null

        val readUsers = ConversationParticipants
            .innerJoin(Users)
            .selectAll()
            .where {
                ((ConversationParticipants.lastReadMessageId eq messageId) or
                    (ConversationParticipants.lastReadMessageId greater messageId)) and
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
                .where {
                    (Messages.id inList replyToIds) and
                        (Messages.conversationId eq conversationId)
                }
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

    override suspend fun getUnreadCounts(userId: Uuid): Map<Uuid, Long> = dbQuery {
        Messages
            .join(
                ConversationParticipants,
                JoinType.INNER,
                additionalConstraint = {
                    (ConversationParticipants.conversationId eq Messages.conversationId) and
                        (ConversationParticipants.userId eq userId)
                },
            )
            .select(Messages.conversationId, Messages.id.count())
            .where {
                (Messages.senderId neq userId) and
                    Messages.revokedAt.isNull() and
                    (ConversationParticipants.lastReadMessageId.isNull() or
                        (Messages.id greater ConversationParticipants.lastReadMessageId))
            }
            .groupBy(Messages.conversationId)
            .associate { it[Messages.conversationId].value to it[Messages.id.count()] }
    }

    override suspend fun getUnreadCount(conversationId: Uuid, userId: Uuid): Long = dbQuery {
        Messages
            .join(
                ConversationParticipants,
                JoinType.INNER,
                additionalConstraint = {
                    (ConversationParticipants.conversationId eq Messages.conversationId) and
                        (ConversationParticipants.userId eq userId)
                },
            )
            .select(Messages.id.count())
            .where {
                (Messages.conversationId eq conversationId) and
                    (Messages.senderId neq userId) and
                    Messages.revokedAt.isNull() and
                    (ConversationParticipants.lastReadMessageId.isNull() or
                        (Messages.id greater ConversationParticipants.lastReadMessageId))
            }
            .firstOrNull()
            ?.get(Messages.id.count()) ?: 0L
    }
}
