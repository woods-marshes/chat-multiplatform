package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetConversationMemberAvatars
import io.github.woodsmarshes.chat.db.GetParticipantsExcludingUser
import io.github.woodsmarshes.chat.db.GetParticipantsWithUserInfo
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.ParticipantsQueries
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ParticipantDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : ParticipantDao {
    override val boundDatabase: ChatDatabase?
        get() = null

    private suspend fun writeQueries(): ParticipantsQueries {
        currentCoroutineContext().ensureActive()
        val contextDb = currentCoroutineContext()[BoundDatabaseElement]?.database
        if (contextDb != null) {
            return contextDb.participantsQueries
        }
        return dbProvider().participantsQueries
    }

    override fun bindToCurrentDatabase(): ParticipantDao {
        val capturedDb = runCatching { dbProvider() }.getOrElse {
            throw SessionStoppedException(it.message ?: "Database is not initialized")
        }
        return PinnedParticipantDao(
            boundDatabase = capturedDb,
            ioContext = ioContext,
            sessionGate = PinnedDatabaseSessionGate(capturedDb, sessionGate),
        )
    }

    override suspend fun insertParticipant(participant: ParticipantEntity) {
        writeQueries().upsertParticipant(participant)
    }

    override suspend fun insertParticipantIfAbsent(participant: ParticipantEntity) {
        writeQueries().insertParticipantIfAbsent(participant)
    }

    override suspend fun insertParticipants(participants: List<ParticipantEntity>) {
        if (participants.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            participants.forEach { q.upsertParticipant(it) }
        }
    }

    override suspend fun insertParticipantsIfAbsent(participants: List<ParticipantEntity>) {
        if (participants.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            participants.forEach { q.insertParticipantIfAbsent(it) }
        }
    }

    override fun getParticipantsByConversationId(conversationId: Uuid): Flow<List<ParticipantEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsByConversationId(conversationId) },
            extractor = { it.executeAsList() },
        )

    override fun getParticipantsWithUserInfo(conversationId: Uuid): Flow<List<GetParticipantsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsWithUserInfo(conversationId) },
            extractor = { it.executeAsList() },
        )

    override fun getParticipant(
        conversationId: Uuid,
        userId: Uuid,
    ): Flow<ParticipantEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipant(conversationId, userId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getParticipantsExcludingUser(
        conversationIds: List<Uuid>,
        excludeUserId: Uuid,
    ): Flow<List<GetParticipantsExcludingUser>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsExcludingUser(conversationIds, excludeUserId) },
            extractor = { it.executeAsList() },
        )

    override fun getConversationMemberAvatars(ownUserId: Uuid): Flow<List<GetConversationMemberAvatars>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getConversationMemberAvatars(ownUserId) },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateRole(
        conversationId: Uuid,
        userId: Uuid,
        role: ConversationRole,
    ) {
        writeQueries().updateRole(role, conversationId, userId)
    }

    override suspend fun updateLastReadMessage(
        conversationId: Uuid,
        userId: Uuid,
        lastMessageId: Uuid?,
    ) {
        writeQueries().updateLastReadMessage(lastMessageId, conversationId, userId)
    }

    override suspend fun updateMuteStatus(
        conversationId: Uuid,
        userId: Uuid,
        mutedUntil: Instant?,
    ) {
        writeQueries().updateMuteStatus(mutedUntil, conversationId, userId)
    }

    override suspend fun updateSettings(
        conversationId: Uuid,
        userId: Uuid,
        settings: ParticipantSettings,
    ) {
        writeQueries().updateSettings(settings, conversationId, userId)
    }

    override suspend fun removeParticipant(conversationId: Uuid, userId: Uuid) {
        writeQueries().removeParticipant(conversationId, userId)
    }

    override suspend fun removeAllParticipantsFromConversation(conversationId: Uuid) {
        writeQueries().removeAllParticipantsFromConversation(conversationId)
    }
}

private class PinnedParticipantDao(
    override val boundDatabase: ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate,
) : ParticipantDao {
    private suspend fun writeQueries(): ParticipantsQueries {
        currentCoroutineContext().ensureActive()
        return boundDatabase.participantsQueries
    }

    override fun bindToCurrentDatabase(): ParticipantDao = this

    override suspend fun insertParticipant(participant: ParticipantEntity) {
        writeQueries().upsertParticipant(participant)
    }

    override suspend fun insertParticipantIfAbsent(participant: ParticipantEntity) {
        writeQueries().insertParticipantIfAbsent(participant)
    }

    override suspend fun insertParticipants(participants: List<ParticipantEntity>) {
        if (participants.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            participants.forEach { q.upsertParticipant(it) }
        }
    }

    override suspend fun insertParticipantsIfAbsent(participants: List<ParticipantEntity>) {
        if (participants.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            participants.forEach { q.insertParticipantIfAbsent(it) }
        }
    }

    override fun getParticipantsByConversationId(conversationId: Uuid): Flow<List<ParticipantEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsByConversationId(conversationId) },
            extractor = { it.executeAsList() },
        )

    override fun getParticipantsWithUserInfo(conversationId: Uuid): Flow<List<GetParticipantsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsWithUserInfo(conversationId) },
            extractor = { it.executeAsList() },
        )

    override fun getParticipant(
        conversationId: Uuid,
        userId: Uuid,
    ): Flow<ParticipantEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipant(conversationId, userId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getParticipantsExcludingUser(
        conversationIds: List<Uuid>,
        excludeUserId: Uuid,
    ): Flow<List<GetParticipantsExcludingUser>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getParticipantsExcludingUser(conversationIds, excludeUserId) },
            extractor = { it.executeAsList() },
        )

    override fun getConversationMemberAvatars(ownUserId: Uuid): Flow<List<GetConversationMemberAvatars>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.participantsQueries.getConversationMemberAvatars(ownUserId) },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateRole(
        conversationId: Uuid,
        userId: Uuid,
        role: ConversationRole,
    ) {
        writeQueries().updateRole(role, conversationId, userId)
    }

    override suspend fun updateLastReadMessage(
        conversationId: Uuid,
        userId: Uuid,
        lastMessageId: Uuid?,
    ) {
        writeQueries().updateLastReadMessage(lastMessageId, conversationId, userId)
    }

    override suspend fun updateMuteStatus(
        conversationId: Uuid,
        userId: Uuid,
        mutedUntil: Instant?,
    ) {
        writeQueries().updateMuteStatus(mutedUntil, conversationId, userId)
    }

    override suspend fun updateSettings(
        conversationId: Uuid,
        userId: Uuid,
        settings: ParticipantSettings,
    ) {
        writeQueries().updateSettings(settings, conversationId, userId)
    }

    override suspend fun removeParticipant(conversationId: Uuid, userId: Uuid) {
        writeQueries().removeParticipant(conversationId, userId)
    }

    override suspend fun removeAllParticipantsFromConversation(conversationId: Uuid) {
        writeQueries().removeAllParticipantsFromConversation(conversationId)
    }
}
