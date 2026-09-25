package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupMetadata
import com.github.woodsmarshes.chat.core.model.GroupSettings
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.connectToH2Database
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GroupProfileRepositoryTest {

    private val database = connectToH2Database()
    private val groupOwnerId = Uuid.random()
    private val matchingGroupId = Uuid.random()
    private val unrelatedGroupId = Uuid.random()
    private val deletedMatchingGroupId = Uuid.random()

    @AfterTest
    fun tearDown() {
        runBlocking {
            transaction(database) {
                GroupProfiles.deleteWhere { GroupProfiles.ownerId eq groupOwnerId }
                Conversations.deleteWhere {
                    Conversations.id inList listOf(matchingGroupId, unrelatedGroupId, deletedMatchingGroupId)
                }
                Users.deleteWhere { Users.id eq groupOwnerId }
            }
        }
    }

    private fun seed() = runBlocking {
        val now = Clock.System.now()
        transaction(database) {
            SchemaUtils.create(Users, Conversations, GroupProfiles)
            Users.insert {
                it[id] = groupOwnerId
                it[username] = "owner-$groupOwnerId"
                it[email] = "owner-$groupOwnerId@test.local"
                it[passwordHash] = "hash"
                it[salt] = ""
                it[role] = UserRole.MEMBER
                it[createdAt] = now
                it[updatedAt] = now
            }

            fun insertGroup(
                conversationId: Uuid,
                name: String,
                handle: String,
                deletedAt: Instant? = null,
            ) {
                Conversations.insert {
                    it[id] = conversationId
                    it[type] = ConversationType.GROUP
                    it[metadata] = GroupMetadata()
                    it[createdAt] = now
                    it[updatedAt] = now
                    it[Conversations.deletedAt] = deletedAt
                }
                GroupProfiles.insert {
                    it[GroupProfiles.conversationId] = conversationId
                    it[GroupProfiles.name] = name
                    it[GroupProfiles.handle] = handle
                    it[description] = null
                    it[avatarUrl] = null
                    it[GroupProfiles.ownerId] = groupOwnerId
                    it[settings] = GroupSettings()
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            insertGroup(matchingGroupId, "Alpha", "alpha")
            insertGroup(unrelatedGroupId, "Beta", "beta")
            insertGroup(deletedMatchingGroupId, "Alpha archived", "alpha-archived", deletedAt = now)
        }
    }

    @Test
    fun searchGroupReturnsOnlyLiveGroupsMatchingTheKeyword() {
        seed()

        val result = runBlocking { GroupProfileDataSourceImpl().searchGroup("alpha") }

        assertEquals(listOf(matchingGroupId), result.map { it.conversationId })
    }

    @Test
    fun searchGroupReturnsNothingForAnEmptyKeyword() {
        seed()

        val result = runBlocking { GroupProfileDataSourceImpl().searchGroup("   ") }

        assertEquals(emptyList(), result)
    }
}
