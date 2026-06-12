package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ProfileVisibility.*
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface UserRepository{
    suspend fun insertUser(
        username: String,
        email: String,
        passwordHash: String,
        salt: String,
        role: UserRole,
    ): User?

    suspend fun updateUser(
        userId: Uuid,
        displayName: String? = null,
        avatarUrl: String? = null,
        bio: String? = null
    ): User?

    suspend fun deleteUser(userId: Uuid): Boolean

    suspend fun getUserProfileForViewer(targetUserId: Uuid, viewerId: Uuid): User?

    suspend fun findAuthInfoByEmail(email: String): UserAuthInfo?

    suspend fun getUserById(userId: Uuid): User?

    suspend fun getUsersById(userIds: List<Uuid>): List<User>

    suspend fun getPrivateConversationOtherUser(userId: Uuid, conversationIds: List<Uuid>): List<User>

    suspend fun getUsers(): List<User>

    suspend fun changeUserRole(userId: Uuid, userRole: UserRole): Boolean

    suspend fun checkExists(
        email: String? = null,
        username: String? = null
    ): Boolean

    suspend fun searchUsers(keyword: String): List<User>
}

class UserDataSourceImpl : UserRepository {
    override suspend fun insertUser(
        username: String,
        email: String,
        passwordHash: String,
        salt: String,
        role: UserRole,
    ): User? = dbQuery {
        Users.insert {
            it[this.username] = username
            it[this.email] = email
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
            it[this.passwordHash] = passwordHash
            it[this.salt] = salt
            it[this.role] = role
        }.resultedValues
            ?.singleOrNull()
            ?.toUser()
    }

    override suspend fun updateUser(
        userId: Uuid,
        displayName: String?,
        avatarUrl: String?,
        bio: String?,
    ): User? = dbQuery {
        Users.updateReturning(where = { Users.id eq userId }) {
            displayName?.let { displayName -> it[Users.displayName] = displayName }
            avatarUrl?.let { avatarUrl -> it[Users.avatarUrl] = avatarUrl }
            bio?.let { bio -> it[Users.bio] = bio }
            it[updatedAt] = Clock.System.now()
        }.singleOrNull()
            ?.toUser()
    }

    override suspend fun deleteUser(userId: Uuid) = dbQuery {
        Users.deleteWhere {
            id eq userId
        } > 0
    }

    override suspend fun getUserProfileForViewer(
        targetUserId: Uuid,
        viewerId: Uuid
    ): User? = dbQuery {
        (Users innerJoin UserSettings)
            .selectAll()
            .where { Users.id eq targetUserId }
            .singleOrNull()
            ?.let { row ->
                val user = row.toUser()
                val privacy = row[UserSettings.profileVisibility]
                val shouldHideEmail = (privacy == PRIVATE) ||
                        (privacy == FRIENDS && !areFriends(targetUserId, viewerId))

                if (shouldHideEmail) user.copy(email = null) else user
            }
    }

    private suspend fun areFriends(userId1: Uuid, userId2: Uuid): Boolean = dbQuery {
        Contacts.selectAll()
            .where {
                (Contacts.userId eq userId1) and (Contacts.contactId eq userId2)
            }
            .limit(1)
            .count() > 0
    }

    override suspend fun findAuthInfoByEmail(email: String): UserAuthInfo? = dbQuery {
        Users.selectAll()
            .where { Users.email eq email }
            .map { it.toUserAuthInfo() }
            .singleOrNull()
    }

    override suspend fun getUserById(userId: Uuid) = dbQuery {
        Users.selectAll()
            .where { Users.id eq userId }
            .map { it.toUser() }
            .singleOrNull()
    }

    override suspend fun getUsersById(userIds: List<Uuid>): List<User> = dbQuery {
        Users.selectAll()
            .where { Users.id inList userIds }
            .map { it.toUser() }
    }

    override suspend fun getPrivateConversationOtherUser(
        userId: Uuid,
        conversationIds: List<Uuid>
    ): List<User> = dbQuery {
        ConversationParticipants
            .innerJoin(
                otherTable = Users,
                onColumn = { ConversationParticipants.userId },
                otherColumn = { Users.id },
                additionalConstraint = { Users.id neq userId },
            )
            .selectAll()
            .where {
                ConversationParticipants.conversationId inList conversationIds
            }
            .map { it.toUser() }
    }

    override suspend fun getUsers(): List<User> = dbQuery {
        Users.selectAll().map { it.toUser() }
    }

    override suspend fun changeUserRole(userId: Uuid, userRole: UserRole): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[updatedAt] = Clock.System.now()
            it[role] = userRole
        } > 0
    }

    override suspend fun checkExists(
        email: String?,
        username: String?,
    ): Boolean {
        if (email == null && username == null) {
            return false
        }
        return dbQuery {
            val query = Users.select(Users.id)
            email?.let { query.andWhere  { Users.email eq it } }
            username?.let { query.andWhere { Users.username eq it } }
            query
                .limit(1)
                .count() > 0
        }
    }

    override suspend fun searchUsers(keyword: String): List<User> {
        val queryTerm = keyword.trim().lowercase()

        val textConditions = listOf(
            Users.username.lowerCase() like "%$queryTerm%",
            Users.email.lowerCase() like "%$queryTerm%",
            Users.displayName.lowerCase() like "%$queryTerm%"
        )

        return dbQuery {
            (Users innerJoin UserSettings)
                .selectAll()
                .where {
                    val conditions = mutableListOf<Op<Boolean>>()

                    conditions.addAll(textConditions)

                    conditions.add(UserSettings.allowSearch eq true)

                    conditions.reduce { acc, op -> acc or op }
                }
                .limit(20)
                .map {
                    when (it[UserSettings.profileVisibility]) {
                        PUBLIC -> it.toUser()
                        else -> {
                            it.toUser().copy(
                                email = null
                            )
                        }
                    }
                }
        }
    }
}
data class UserAuthInfo(
    val userId: Uuid,
    val passwordHash: String,
    val salt: String,
    val domainUser: User
)