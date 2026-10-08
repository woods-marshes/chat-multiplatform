package com.github.woodsmarshes.chat.core.database.dao

import androidx.paging.PagingSource
import com.github.woodsmarshes.chat.core.database.session.SessionBoundPagingSource
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
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MessageDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : MessageDao {

    private suspend fun writeQueries() = (currentCoroutineContext()[BoundDatabaseElement]?.database ?: dbProvider()).also { currentCoroutineContext().ensureActive() }.messagesQueries

    fun pinForPaging(): MessageDaoImpl {
        val db = dbProvider()
        return MessageDaoImpl({ db }, ioContext, com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate(db, sessionGate))
    }

    private val queries
        get() = dbProvider().messagesQueries

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        writeQueries().transaction(body = body)
    }

    override suspend fun findMessageById(id: Uuid): GetMessageById? =
        writeQueries().getMessageById(id).executeAsOneOrNull()

    override suspend fun insertMessage(message: MessageEntity) {
        writeQueries().upsertMessage(message)
    }

    override suspend fun insertMessages(messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        writeQueries().transaction {
            messages.forEach { insertMessage(it) }
        }
    }

    override fun getMessagesPaged(
        conversationId: Uuid,
        beforeTimestamp: Instant,
        limit: Long
    ): Flow<List<MessageEntity>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getMessagesPaged(conversationId, beforeTimestamp, limit) }, { it.executeAsList() })
    }

    override fun pagingSource(conversationId: Uuid, pageSize: Long): PagingSource<Uuid, KeyedMessagesWithRelations> {
        return SessionBoundPagingSource(sessionGate, sessionGate.boundDatabase, sessionGate.currentGeneration) { db ->
        val queries = db.messagesQueries
        QueryPagingSource(
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
    }

    override fun getMessagesWithRelationsByPage(
        conversationId: Uuid,
        beforeId: Uuid?,
        limit: Long
    ): Flow<List<GetMessagesWithAllRelationsByPage>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getMessagesWithAllRelationsByPage(conversationId, beforeId, limit) }, { it.executeAsList() })
    }

    override fun getMessagesWithRelationsByIds(ids: List<Uuid>): Flow<List<GetMessagesWithAllRelationsByIds>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getMessagesWithAllRelationsByIds(ids) }, { it.executeAsList() })
    }

    override fun getLatestMessage(conversationId: Uuid): Flow<GetLatestMessage?> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getLatestMessage(conversationId) }, { it.executeAsOneOrNull() })
    }

    override fun getLatestMessages(conversationIds: List<Uuid>): Flow<List<GetLatestMessages>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getLatestMessages(conversationIds) }, { it.executeAsList() })
    }

    override fun getMessageById(id: Uuid): Flow<GetMessageById?> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getMessageById(id) }, { it.executeAsOneOrNull() })
    }

    override fun getRepliesToMessage(messageId: Uuid): Flow<List<MessageEntity>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.getRepliesToMessage(messageId) }, { it.executeAsList() })
    }

    override suspend fun updateMessageStatus(
        oldId: Uuid,
        newId: Uuid,
        createdAt: Instant,
        status: MessageStatus
    ) {
        writeQueries().updateMessageStatus(status, createdAt, newId, oldId)
    }

    override suspend fun revokeMessage(id: Uuid, revokedAt: Instant) {
        writeQueries().revokeMessage(revokedAt, id)
    }

    override suspend fun getRetryableMessages(retryAfter: Instant): List<MessageEntity> {
        return writeQueries().getRetryableMessages(retryAfter.toEpochMilliseconds())
            .asFlow()
            .mapToList(ioContext)
            .first()
    }

    override suspend fun failStaleMessages(giveUpBefore: Instant) {
        // Generated as a suspend statement (UPDATE), executes directly.
        writeQueries().failStaleMessages(giveUpBefore.toEpochMilliseconds())
    }

    private fun Uuid.storageKey(): String = toString().replace("-", "")

    override suspend fun startAttempt(id: Uuid, now: Instant) {
        writeQueries().startAttempt(id.storageKey(), now.toEpochMilliseconds())
    }

    override suspend fun claimFailedMessage(id: Uuid, now: Instant): Boolean {
        val q = writeQueries()
        var claimed = false
        q.transaction {
            val row = q.getMessageById(id).executeAsOneOrNull()
            if (row?.local_send_status == MessageStatus.FAILED) {
                q.updateMessageStatus(MessageStatus.SENDING, row.created_at, id, id)
                q.startAttempt(id.storageKey(), now.toEpochMilliseconds())
                claimed = true
            }
        }
        return claimed
    }

    override suspend fun recordAttempt(id: Uuid, createdAt: Instant, now: Instant) {
        writeQueries().recordAttempt(id.storageKey(), createdAt.toEpochMilliseconds(), now.toEpochMilliseconds())
    }

    override suspend fun mergeServerMessage(message: MessageEntity, localId: Uuid?) {
        val q = writeQueries()
        q.transaction {
            q.upsertMessage(message.copy(local_send_status = MessageStatus.SENT))
            if (localId != null && localId != message.id) {
                q.replaceReplyReferences(message.id, localId)
                q.replaceConversationReferences(message.id, localId)
                q.replaceReadReferences(message.id, localId)
                q.deleteMessage(localId)
                q.clearAttempt(localId.storageKey())
            }
            q.clearAttempt(message.id.storageKey())
        }
    }

    override suspend fun getSyncCursor(conversationId: Uuid): Uuid? =
        writeQueries().getSyncCursor(conversationId.toString()).asFlow().mapToOneOrNull(ioContext)
            .first()?.let(Uuid::parse)

    override suspend fun setSyncCursor(conversationId: Uuid, afterId: Uuid) {
        writeQueries().setSyncCursor(conversationId.toString(), afterId.toString())
    }

    override suspend fun deleteMessage(id: Uuid) {
        writeQueries().deleteMessage(id)
    }

    override suspend fun clearConversationHistory(conversationId: Uuid) {
        writeQueries().clearConversationHistory(conversationId)
    }

    override fun countUnreadAfter(
        conversationId: Uuid,
        myUserId: Uuid,
        lastReadMessageId: Uuid?
    ): Flow<Long> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.messagesQueries.countUnreadAfter(conversationId, myUserId, lastReadMessageId) }, { it.executeAsOneOrNull() })
            .map { it ?: 0L }
    }
}