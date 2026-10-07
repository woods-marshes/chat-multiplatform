package com.github.woodsmarshes.chat.core.database.dao

import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import com.github.woodsmarshes.chat.core.model.GroupSettings
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetGroupWithLastMessage
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import io.github.woodsmarshes.chat.db.GroupProfilesQueries
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GroupProfileDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : GroupProfileDao {
    override val boundDatabase: ChatDatabase?
        get() = null

    private suspend fun writeQueries(): GroupProfilesQueries {
        currentCoroutineContext().ensureActive()
        val contextDb = currentCoroutineContext()[BoundDatabaseElement]?.database
        if (contextDb != null) {
            return contextDb.groupProfilesQueries
        }
        return dbProvider().groupProfilesQueries
    }

    override fun bindToCurrentDatabase(): GroupProfileDao {
        val capturedDb = runCatching { dbProvider() }.getOrElse {
            throw SessionStoppedException(it.message ?: "Database is not initialized")
        }
        return PinnedGroupProfileDao(
            boundDatabase = capturedDb,
            ioContext = ioContext,
            sessionGate = PinnedDatabaseSessionGate(capturedDb, sessionGate),
        )
    }

    override suspend fun insertGroupProfile(groupProfile: GroupProfileEntity) {
        writeQueries().upsertGroupProfile(groupProfile)
    }

    override suspend fun insertGroupProfiles(groupProfiles: List<GroupProfileEntity>) {
        if (groupProfiles.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            groupProfiles.forEach { q.upsertGroupProfile(it) }
        }
    }

    override fun getGroupProfile(conversationId: Uuid): Flow<GroupProfileEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupProfile(conversationId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getGroupProfiles(conversationIds: List<Uuid>): Flow<List<GroupProfileEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupProfiles(conversationIds) },
            extractor = { it.executeAsList() },
        )

    override fun getGroupByHandle(handle: String): Flow<GroupProfileEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupByHandle(handle) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getGroupWithLastMessage(): Flow<List<GetGroupWithLastMessage>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupWithLastMessage() },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateGroupInfo(
        conversationId: Uuid,
        name: String,
        description: String?,
        avatarUrl: String?,
        updatedAt: Instant,
    ) {
        writeQueries().updateGroupInfo(name, description, avatarUrl, updatedAt, conversationId)
    }

    override suspend fun updateGroupSettings(
        conversationId: Uuid,
        settings: GroupSettings,
        updatedAt: Instant,
    ) {
        writeQueries().updateGroupSettings(settings, updatedAt, conversationId)
    }

    override suspend fun transferOwnership(
        conversationId: Uuid,
        newOwnerId: Uuid,
        updatedAt: Instant,
    ) {
        writeQueries().transferOwnership(newOwnerId, updatedAt, conversationId)
    }

    override suspend fun deleteGroupProfile(conversationId: Uuid) {
        writeQueries().deleteGroupProfile(conversationId)
    }
}

private class PinnedGroupProfileDao(
    override val boundDatabase: ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate,
) : GroupProfileDao {
    private suspend fun writeQueries(): GroupProfilesQueries {
        currentCoroutineContext().ensureActive()
        return boundDatabase.groupProfilesQueries
    }

    override fun bindToCurrentDatabase(): GroupProfileDao = this

    override suspend fun insertGroupProfile(groupProfile: GroupProfileEntity) {
        writeQueries().upsertGroupProfile(groupProfile)
    }

    override suspend fun insertGroupProfiles(groupProfiles: List<GroupProfileEntity>) {
        if (groupProfiles.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            groupProfiles.forEach { q.upsertGroupProfile(it) }
        }
    }

    override fun getGroupProfile(conversationId: Uuid): Flow<GroupProfileEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupProfile(conversationId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getGroupProfiles(conversationIds: List<Uuid>): Flow<List<GroupProfileEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupProfiles(conversationIds) },
            extractor = { it.executeAsList() },
        )

    override fun getGroupByHandle(handle: String): Flow<GroupProfileEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupByHandle(handle) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getGroupWithLastMessage(): Flow<List<GetGroupWithLastMessage>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.groupProfilesQueries.getGroupWithLastMessage() },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateGroupInfo(
        conversationId: Uuid,
        name: String,
        description: String?,
        avatarUrl: String?,
        updatedAt: Instant,
    ) {
        writeQueries().updateGroupInfo(name, description, avatarUrl, updatedAt, conversationId)
    }

    override suspend fun updateGroupSettings(
        conversationId: Uuid,
        settings: GroupSettings,
        updatedAt: Instant,
    ) {
        writeQueries().updateGroupSettings(settings, updatedAt, conversationId)
    }

    override suspend fun transferOwnership(
        conversationId: Uuid,
        newOwnerId: Uuid,
        updatedAt: Instant,
    ) {
        writeQueries().transferOwnership(newOwnerId, updatedAt, conversationId)
    }

    override suspend fun deleteGroupProfile(conversationId: Uuid) {
        writeQueries().deleteGroupProfile(conversationId)
    }
}
