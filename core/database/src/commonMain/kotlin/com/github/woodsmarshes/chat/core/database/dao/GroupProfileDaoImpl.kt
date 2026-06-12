package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.GroupSettings
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GetGroupWithLastMessage
import io.github.woodsmarshes.chat.db.GroupProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GroupProfileDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : GroupProfileDao {
    private val queries
        get() = dbProvider().groupProfilesQueries

    override suspend fun insertGroupProfile(groupProfile: GroupProfileEntity) {
        queries.upsertGroupProfile(groupProfile)
    }

    override suspend fun insertGroupProfiles(groupProfiles: List<GroupProfileEntity>) {
        if (groupProfiles.isEmpty()) return
        queries.transaction {
            groupProfiles.forEach { insertGroupProfile(it) }
        }
    }

    override fun getGroupProfile(conversationId: Uuid): Flow<GroupProfileEntity?> {
        return queries.getGroupProfile(conversationId)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getGroupProfiles(conversationIds: List<Uuid>): Flow<List<GroupProfileEntity>> {
        return queries.getGroupProfiles(conversationIds)
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getGroupByHandle(handle: String): Flow<GroupProfileEntity?> {
        return queries.getGroupByHandle(handle)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getGroupWithLastMessage(): Flow<List<GetGroupWithLastMessage>> {
        return queries.getGroupWithLastMessage()
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun updateGroupInfo(
        conversationId: Uuid,
        name: String,
        description: String?,
        avatarUrl: String?,
        updatedAt: Instant
    ) {
        queries.updateGroupInfo(name, description, avatarUrl, updatedAt, conversationId)
    }

    override suspend fun updateGroupSettings(
        conversationId: Uuid,
        settings: GroupSettings,
        updatedAt: Instant
    ) {
        queries.updateGroupSettings(settings, updatedAt, conversationId)
    }

    override suspend fun transferOwnership(
        conversationId: Uuid,
        newOwnerId: Uuid,
        updatedAt: Instant
    ) {
        queries.transferOwnership(newOwnerId, updatedAt, conversationId)
    }

    override suspend fun deleteGroupProfile(conversationId: Uuid) {
        queries.deleteGroupProfile(conversationId)
    }
}