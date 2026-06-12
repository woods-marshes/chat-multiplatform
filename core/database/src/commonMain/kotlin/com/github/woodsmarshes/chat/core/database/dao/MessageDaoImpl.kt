package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.paging3.QueryPagingSource
import com.github.woodsmarshes.chat.core.model.MessageStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetLatestMessage
import io.github.woodsmarshes.chat.db.GetLatestMessages
import io.github.woodsmarshes.chat.db.GetMessageById
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByIds
import io.github.woodsmarshes.chat.db.GetMessagesWithAllRelationsByPage
import io.github.woodsmarshes.chat.db.KeyedMessagesWithRelations
import io.github.woodsmarshes.chat.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MessageDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : MessageDao {

    private val queries
        get() = dbProvider().messagesQueries

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        queries.transaction(body = body)
    }

    override suspend fun insertMessage(message: MessageEntity) {
        queries.upsertMessage(message)
    }

    override suspend fun insertMessages(messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        queries.transaction {
            messages.forEach { insertMessage(it) }
        }
    }

    override fun getMessagesPaged(
        conversationId: Uuid,
        beforeTimestamp: Instant,
        limit: Long
    ): Flow<List<MessageEntity>> {
        return queries.getMessagesPaged(conversationId, beforeTimestamp, limit)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun pagingSource(conversationId: Uuid, pageSize: Long): PagingSource<Uuid, KeyedMessagesWithRelations> {
        return QueryPagingSource(
            transacter = queries,
            context = ioContext,
            pageBoundariesProvider = { anchorId, limit ->
                 queries.messageBoundaries(
                     limit = limit,
                     referenceId = anchorId ?: Uuid.NIL,
                     conversationId = conversationId
                 )
            },
            queryProvider = { beginInclusive, endExclusive ->
                queries.keyedMessagesWithRelations(
                    conversationId = conversationId,
                    beginInclusive = beginInclusive,
                    endExclusive = endExclusive
                )
            }
        )
//        return MessagePagingSource(
//            queries = queries,
//            conversationId = conversationId,
//            ioContext = ioContext
//        )
    }

    override fun getMessagesWithRelationsByPage(
        conversationId: Uuid,
        beforeId: Uuid?,
        limit: Long
    ): Flow<List<GetMessagesWithAllRelationsByPage>> {
        return queries.getMessagesWithAllRelationsByPage(conversationId, beforeId, limit)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getMessagesWithRelationsByIds(ids: List<Uuid>): Flow<List<GetMessagesWithAllRelationsByIds>> {
        return queries.getMessagesWithAllRelationsByIds(ids)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getLatestMessage(conversationId: Uuid): Flow<GetLatestMessage?> {
        return queries.getLatestMessage(conversationId)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getLatestMessages(conversationIds: List<Uuid>): Flow<List<GetLatestMessages>> {
        return queries.getLatestMessages(conversationIds)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getMessageById(id: Uuid): Flow<GetMessageById?> {
        return queries.getMessageById(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getRepliesToMessage(messageId: Uuid): Flow<List<MessageEntity>> {
        return queries.getRepliesToMessage(messageId)
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun updateMessageStatus(
        oldId: Uuid,
        newId: Uuid,
        createdAt: Instant,
        status: MessageStatus
    ) {
        queries.updateMessageStatus(status, createdAt, newId, oldId)
    }

    override suspend fun revokeMessage(id: Uuid, revokedAt: Instant) {
        queries.revokeMessage(revokedAt, id)
    }

    override suspend fun deleteMessage(id: Uuid) {
        queries.deleteMessage(id)
    }

    override suspend fun clearConversationHistory(conversationId: Uuid) {
        queries.clearConversationHistory(conversationId)
    }

    override fun countUnreadAfter(
        conversationId: Uuid,
        myUserId: Uuid,
        lastReadTimestamp: Instant
    ): Flow<Long> {
        return queries.countUnreadAfter(conversationId, myUserId, lastReadTimestamp)
            .asFlow()
            .mapToOneOrNull(ioContext)
            .map { it ?: 0L }
    }
}