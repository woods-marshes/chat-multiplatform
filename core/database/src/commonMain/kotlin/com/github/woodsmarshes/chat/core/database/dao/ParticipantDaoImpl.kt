package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetParticipantsExcludingUser
import io.github.woodsmarshes.chat.db.GetParticipantsWithUserInfo
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ParticipantDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : ParticipantDao {
    private val queries
        get() = dbProvider().participantsQueries

    override suspend fun insertParticipant(participant: ParticipantEntity) {
        queries.upsertParticipant(participant)
    }

    override suspend fun insertParticipants(participants: List<ParticipantEntity>) {
        if (participants.isEmpty()) return
        queries.transaction {
            participants.forEach { insertParticipant(it) }
        }
    }

    override fun getParticipantsByConversationId(conversationId: Uuid): Flow<List<ParticipantEntity>> {
        return queries.getParticipantsByConversationId(conversationId)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getParticipantsWithUserInfo(conversationId: Uuid): Flow<List<GetParticipantsWithUserInfo>> {
        return queries.getParticipantsWithUserInfo(conversationId)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getParticipant(
        conversationId: Uuid,
        userId: Uuid
    ): Flow<ParticipantEntity?> {
        return queries.getParticipant(conversationId, userId)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getParticipantsExcludingUser(
        conversationIds: List<Uuid>,
        excludeUserId: Uuid
    ): Flow<List<GetParticipantsExcludingUser>> {
        return queries.getParticipantsExcludingUser(conversationIds, excludeUserId)
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun updateRole(
        conversationId: Uuid,
        userId: Uuid,
        role: ConversationRole
    ) {
        queries.updateRole(role, conversationId, userId)
    }

    override suspend fun updateLastReadMessage(
        conversationId: Uuid,
        userId: Uuid,
        lastMessageId: Uuid?
    ) {
        queries.updateLastReadMessage(lastMessageId, conversationId, userId)
    }

    override suspend fun updateMuteStatus(
        conversationId: Uuid,
        userId: Uuid,
        mutedUntil: Instant?
    ) {
        queries.updateMuteStatus(mutedUntil, conversationId, userId)
    }

    override suspend fun updateSettings(
        conversationId: Uuid,
        userId: Uuid,
        settings: ParticipantSettings
    ) {
        queries.updateSettings(settings, conversationId, userId)
    }

    override suspend fun removeParticipant(conversationId: Uuid, userId: Uuid) {
        queries.removeParticipant(conversationId, userId)
    }

    override suspend fun removeAllParticipantsFromConversation(conversationId: Uuid) {
        queries.removeAllParticipantsFromConversation(conversationId)
    }
}