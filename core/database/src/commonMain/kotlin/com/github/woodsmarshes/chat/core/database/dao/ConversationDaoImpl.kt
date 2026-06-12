package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.GetConversationListView
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ConversationDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : ConversationDao {
    private val queries
        get() = dbProvider().conversationsQueries

    override suspend fun insertConversation(conversation: ConversationEntity) {
        queries.upsertConversation(conversation)
    }

    override suspend fun insertConversations(conversations: List<ConversationEntity>) {
        if (conversations.isEmpty()) return
        queries.transaction {
            conversations.forEach { insertConversation(it) }
        }
    }

    override fun getAllActiveConversations(): Flow<List<ConversationEntity>> {
        return queries.getAllActiveConversations()
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getConversationById(id: Uuid): Flow<ConversationEntity?> {
        return queries.getConversationById(id)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getConversationListView(currentUserId: Uuid): Flow<List<GetConversationListView>> {
        return queries.getConversationListView(currentUserId)
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun updateLastMessage(
        id: Uuid,
        lastMessageId: Uuid?,
        updatedAt: Instant
    ) {
        queries.updateLastMessage(
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
        queries.updateMetadata(
            metadata = metadata,
            updatedAt = updatedAt,
            id = id
        )
    }

    override suspend fun softDeleteConversation(id: Uuid, deletedAt: Instant) {
        queries.softDeleteConversation(
            deletedAt = deletedAt,
            id = id
        )
    }

    override suspend fun hardDeleteConversation(id: Uuid) {
        queries.hardDeleteConversation(id)
    }
}