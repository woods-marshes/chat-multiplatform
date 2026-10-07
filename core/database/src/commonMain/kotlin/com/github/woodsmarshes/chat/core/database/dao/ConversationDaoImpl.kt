package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GetConversationListView
import kotlinx.coroutines.flow.Flow
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : ConversationDao {
    private suspend fun writeQueries() = (currentCoroutineContext()[BoundDatabaseElement]?.database ?: dbProvider()).also { currentCoroutineContext().ensureActive() }.conversationsQueries

    private val queries
        get() = dbProvider().conversationsQueries

    override suspend fun insertConversation(conversation: ConversationEntity) {
        writeQueries().upsertConversation(conversation)
    }

    override suspend fun insertConversations(conversations: List<ConversationEntity>) {
        if (conversations.isEmpty()) return
        writeQueries().transaction {
            conversations.forEach { insertConversation(it) }
        }
    }

    override fun getAllActiveConversations(): Flow<List<ConversationEntity>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.conversationsQueries.getAllActiveConversations() }, { it.executeAsList() })
    }

    override fun getConversationById(id: Uuid): Flow<ConversationEntity?> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.conversationsQueries.getConversationById(id) }, { it.executeAsOneOrNull() })
    }

    override fun getConversationListView(currentUserId: Uuid): Flow<List<GetConversationListView>> {
        return sessionBoundQueryFlow(sessionGate, ioContext, { db -> db.conversationsQueries.getConversationListView(currentUserId) }, { it.executeAsList() })
    }

    override suspend fun updateLastMessage(
        id: Uuid,
        lastMessageId: Uuid?,
        updatedAt: Instant
    ) {
        writeQueries().updateLastMessage(
            lastMessageId = lastMessageId,
            updatedAt = updatedAt,
            id = id
        )
    }

    override suspend fun updateMetadata(
        id: Uuid,
        metadata: ConversationMetadata?,
        updatedAt: Instant
    ) {
        writeQueries().updateMetadata(
            metadata = metadata,
            updatedAt = updatedAt,
            id = id
        )
    }

    override suspend fun softDeleteConversation(id: Uuid, deletedAt: Instant) {
        writeQueries().softDeleteConversation(
            deletedAt = deletedAt,
            id = id
        )
    }

    override suspend fun hardDeleteConversation(id: Uuid) {
        writeQueries().hardDeleteConversation(id)
    }
}