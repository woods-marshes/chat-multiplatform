package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface GroupProfileRepository {

    suspend fun initGroupProfile(
        conversationId: Uuid,
        name: String,
        handle: String? = null,
        ownerId: Uuid,
        settings: GroupSettings?,
        description: String? = null,
        avatarUrl: String? = null,
    ): GroupProfile?

    suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String? = null,
        handle: String? = null,
        ownerId: Uuid? = null,
        description: String? = null,
        avatarUrl: String? = null,
        settings: GroupSettings? = null,
    ): Boolean

    suspend fun getGroupProfile(conversationId: Uuid): GroupProfile?

    suspend fun getGroupProfileWithUser(conversationId: Uuid): Pair<GroupProfile, User>?

    suspend fun getGroupProfilesWithUsers(conversationIds: List<Uuid>): List<Pair<GroupProfile, User>>

    suspend fun searchGroup(keyword: String): List<GroupProfile>

    suspend fun checkHandleExists(handle: String): Boolean
}

class GroupProfileDataSourceImpl : GroupProfileRepository {
    override suspend fun initGroupProfile(
        conversationId: Uuid,
        name: String,
        handle: String?,
        ownerId: Uuid,
        settings: GroupSettings?,
        description: String?,
        avatarUrl: String?
    ): GroupProfile? = dbQuery {
        val now = Clock.System.now()
        GroupProfiles.insert {
            it[this.conversationId] = conversationId
            it[this.name] = name
            it[this.handle] = handle
            it[this.description] = description
            it[this.avatarUrl] = avatarUrl
            it[this.ownerId] = ownerId
            it[this.settings] = settings ?: GroupSettings()
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }
            .resultedValues
            ?.singleOrNull()
            ?.toGroupProfile()
    }

    override suspend fun updateGroupProfile(
        conversationId: Uuid,
        name: String?,
        handle: String?,
        ownerId: Uuid?,
        description: String?,
        avatarUrl: String?,
        settings: GroupSettings?
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        GroupProfiles.update({
            GroupProfiles.conversationId eq conversationId
        }) {
            if (name != null) it[this.name] = name
            if (handle != null) it[this.handle] = handle
            if (ownerId != null) it[this.ownerId] = ownerId
            if (description != null) it[this.description] = description
            if (avatarUrl != null) it[this.avatarUrl] = avatarUrl
            if (settings != null) it[this.settings] = settings
            it[this.updatedAt] = now
        } > 0
    }

    override suspend fun getGroupProfile(conversationId: Uuid): GroupProfile? = dbQuery {
        GroupProfiles
            .selectAll()
            .where { GroupProfiles.conversationId eq conversationId }
            .singleOrNull()
            ?.toGroupProfile()
    }

    override suspend fun getGroupProfileWithUser(conversationId: Uuid): Pair<GroupProfile, User>? = dbQuery {
        (GroupProfiles innerJoin Users)
            .selectAll()
            .where { GroupProfiles.conversationId eq conversationId }
            .map {
                it.toGroupProfile() to it.toUser()
            }
            .singleOrNull()
    }

    override suspend fun getGroupProfilesWithUsers(conversationIds: List<Uuid>): List<Pair<GroupProfile, User>> = dbQuery {
        (GroupProfiles innerJoin Users)
            .selectAll()
            .where { GroupProfiles.conversationId inList conversationIds }
            .map {
                it.toGroupProfile() to it.toUser()
            }
    }

    override suspend fun searchGroup(keyword: String): List<GroupProfile> = dbQuery {
        val queryTerm = keyword.trim().lowercase()

        (GroupProfiles innerJoin Conversations)
            .selectAll()
            .where {
                val conditions = mutableListOf<Op<Boolean>>()
                conditions.add(Conversations.deletedAt.isNull())
                conditions.add(GroupProfiles.handle.lowerCase() like "%$queryTerm%")
                conditions.add(GroupProfiles.name.lowerCase() like "%$queryTerm%")
                conditions.add(GroupProfiles.description.lowerCase() like "%$queryTerm%")
                conditions.reduce { acc, op -> acc or op }
            }
            .limit(20)
            .map { it.toGroupProfile() }
    }

    override suspend fun checkHandleExists(handle: String): Boolean = dbQuery {
        GroupProfiles
            .select(GroupProfiles.handle)
            .where { GroupProfiles.handle eq handle }
            .limit(1)
            .count() > 0
    }
}