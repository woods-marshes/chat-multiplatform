package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationParticipant
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface ConversationParticipantRepository {
    suspend fun insertConversationParticipant(conversationParticipant: ConversationParticipant): ConversationParticipant?

    suspend fun updateParticipantSettings(userId: Uuid, conversationId: Uuid, settings: ParticipantSettings): Boolean

    suspend fun updateReadLastMessage(userId: Uuid, conversationId: Uuid, messageId: Uuid): Boolean

    suspend fun updateConversationParticipantRole(userId: Uuid, conversationId: Uuid, role: ConversationRole): Boolean

    suspend fun deleteConversationParticipant(userId: Uuid, conversationId: Uuid): Boolean

    suspend fun getConversationParticipant(userId: Uuid, conversationId: Uuid): ConversationParticipant?

    suspend fun getUserConversationParticipants(userId: Uuid): List<ConversationParticipant>

    suspend fun getConversationParticipants(userId: Uuid, conversationIds: List<Uuid>): List<ConversationParticipant>

    suspend fun getConversationParticipantWithConversation(
        userId: Uuid,
        conversationId: Uuid
    ): Pair<ConversationParticipant, Conversation>?

    suspend fun getParticipantContext(
        userId: Uuid,
        conversationId: Uuid
    ): Triple<ConversationParticipant, User, Conversation>?

    suspend fun getGroupParticipantContext(
        userId: Uuid,
        conversationId: Uuid
    ): Triple<ConversationParticipant, GroupProfile, Conversation>?

    suspend fun getConversationParticipants(conversationId: Uuid): List<ConversationParticipant>

    suspend fun getConversationParticipantsWithUser(conversationId: Uuid): List<Pair<ConversationParticipant, User>>

    suspend fun getPrivateConversationOtherParticipantsWithUser(
        userId: Uuid,
        conversationId: Uuid
    ): Pair<ConversationParticipant, User>?

    suspend fun getConversationParticipants(): List<ConversationParticipant>

    suspend fun getConversationParticipantByUserId(
        userId: Uuid,
        roles: List<ConversationRole> = emptyList()
    ): List<ConversationParticipant>

    suspend fun inviteUsersToConversation(
        conversationId: Uuid,
        inviterId: Uuid,
        userIds: Set<Uuid>
    ): List<ConversationParticipant>

    suspend fun inviteUserToConversation(
        conversationId: Uuid,
        inviterId: Uuid,
        userId: Uuid
    ): ConversationParticipant?

    suspend fun getUserConversationRole(userId: Uuid, conversationId: Uuid): ConversationRole?
}

class ConversationParticipantDataSourceImpl : ConversationParticipantRepository {

    override suspend fun insertConversationParticipant(conversationParticipant: ConversationParticipant): ConversationParticipant? = dbQuery {
        ConversationParticipants.insert {
            it[this.conversationId] = conversationParticipant.conversationId
            it[this.userId] = conversationParticipant.userId
            it[this.role] = conversationParticipant.role
            it[this.settings] = conversationParticipant.settings
        }
            .resultedValues
            ?.singleOrNull()
            ?.toConversationParticipant()
    }

    override suspend fun updateParticipantSettings(
        userId: Uuid,
        conversationId: Uuid,
        settings: ParticipantSettings
    ): Boolean = dbQuery {
        ConversationParticipants.update(
            where = {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
        ) {
            it[this.settings] = settings
        } > 0
    }

    override suspend fun updateReadLastMessage(
        userId: Uuid,
        conversationId: Uuid,
        messageId: Uuid
    ): Boolean = dbQuery {
        ConversationParticipants.update(
            where = {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
        ) {
            it[this.lastReadMessageId] = messageId
        } > 0
    }

    override suspend fun updateConversationParticipantRole(
        userId: Uuid,
        conversationId: Uuid,
        role: ConversationRole
    ): Boolean = dbQuery {
        ConversationParticipants.update(
            where = {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
        ) {
            it[this.role] = role
        } > 0
    }

    override suspend fun deleteConversationParticipant(userId: Uuid, conversationId: Uuid): Boolean = dbQuery {
        ConversationParticipants.deleteWhere {
            (ConversationParticipants.userId eq userId) and
                    (ConversationParticipants.conversationId eq conversationId)
        } > 0
    }

    override suspend fun getConversationParticipant(
        userId: Uuid,
        conversationId: Uuid
    ): ConversationParticipant? = dbQuery {
        ConversationParticipants
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
            .singleOrNull()
            ?.toConversationParticipant()
    }

    override suspend fun getUserConversationParticipants(userId: Uuid): List<ConversationParticipant> = dbQuery {
        ConversationParticipants
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId)
            }
            .map {
                it.toConversationParticipant()
            }
    }

    override suspend fun getConversationParticipants(
        userId: Uuid,
        conversationIds: List<Uuid>
    ): List<ConversationParticipant> = dbQuery {
        ConversationParticipants
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId inList conversationIds)
            }
            .map {
                it.toConversationParticipant()
            }
    }

    override suspend fun getConversationParticipantWithConversation(
        userId: Uuid,
        conversationId: Uuid
    ): Pair<ConversationParticipant, Conversation>? = dbQuery {
        (ConversationParticipants innerJoin Conversations)
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
            .singleOrNull()
            ?.let {
                it.toConversationParticipant() to it.toConversation()
            }
    }

    override suspend fun getParticipantContext(
        userId: Uuid,
        conversationId: Uuid
    ): Triple<ConversationParticipant, User, Conversation>? = dbQuery {
        (ConversationParticipants innerJoin Users innerJoin Conversations)
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
            .singleOrNull()
            ?.let {
                Triple(
                    it.toConversationParticipant(),
                    it.toUser(),
                    it.toConversation()
                )
            }
    }

    override suspend fun getGroupParticipantContext(
        userId: Uuid,
        conversationId: Uuid
    ): Triple<ConversationParticipant, GroupProfile, Conversation>? = dbQuery {
        (ConversationParticipants innerJoin Conversations innerJoin GroupProfiles)
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId) and
                        (Conversations.type eq ConversationType.GROUP)
            }
            .singleOrNull()
            ?.let {
                Triple(
                    it.toConversationParticipant(),
                    it.toGroupProfile(),
                    it.toConversation()
                )
            }
    }

    override suspend fun getConversationParticipants(conversationId: Uuid): List<ConversationParticipant> = dbQuery {
        ConversationParticipants
            .selectAll()
            .where {
                ConversationParticipants.conversationId eq conversationId
            }
            .map { it.toConversationParticipant() }
    }

    override suspend fun getConversationParticipantsWithUser(conversationId: Uuid): List<Pair<ConversationParticipant, User>> = dbQuery{
        (ConversationParticipants innerJoin Users)
            .selectAll()
            .where {
                ConversationParticipants.conversationId eq conversationId
            }
            .map {
                it.toConversationParticipant() to it.toUser()
            }
    }

    override suspend fun getPrivateConversationOtherParticipantsWithUser(
        userId: Uuid,
        conversationId: Uuid
    ): Pair<ConversationParticipant, User>? = dbQuery {
        (ConversationParticipants innerJoin Users innerJoin UserSettings)
            .selectAll()
            .where {
                (ConversationParticipants.conversationId eq conversationId) and
                        (ConversationParticipants.userId neq userId)
            }
            .singleOrNull()
            ?.let {
                val user = it.toUser()
                val shouldHideEmail = it[UserSettings.profileVisibility] == ProfileVisibility.PRIVATE
                it.toConversationParticipant() to if (shouldHideEmail) user.copy(email = null) else user
            }
    }

    override suspend fun getConversationParticipants(): List<ConversationParticipant> = dbQuery {
        ConversationParticipants
            .selectAll()
            .map { it.toConversationParticipant() }
    }

    override suspend fun getConversationParticipantByUserId(userId: Uuid, roles: List<ConversationRole>): List<ConversationParticipant> = dbQuery {
        val query = ConversationParticipants.selectAll()
            .where { ConversationParticipants.userId eq userId }

        if (roles.isNotEmpty()) {
            query.andWhere { ConversationParticipants.role inList roles }
        }

        query.map { it.toConversationParticipant() }
    }

    override suspend fun inviteUsersToConversation(
        conversationId: Uuid,
        inviterId: Uuid,
        userIds: Set<Uuid>
    ): List<ConversationParticipant> = dbQuery {
        val existingUserIds = ConversationParticipants
            .select(ConversationParticipants.userId)
            .where {
                (ConversationParticipants.conversationId eq conversationId) and
                        (ConversationParticipants.userId inList userIds)
            }
            .map { it[ConversationParticipants.userId].value }
            .toSet()

        val newUserIds = userIds - existingUserIds
        if (newUserIds.isEmpty()) return@dbQuery emptyList()
        // 批量插入参与者
        val participants = newUserIds.map { userId ->
            ConversationParticipant(
                conversationId = conversationId,
                userId = userId,
                role = ConversationRole.MEMBER,
                lastReadMessageId = null,
                joinedAt = Clock.System.now(),
                settings = ParticipantSettings(),
            )
        }

        ConversationParticipants.batchInsert(participants) { participant ->
            this[ConversationParticipants.conversationId] = participant.conversationId
            this[ConversationParticipants.userId] = participant.userId
            this[ConversationParticipants.role] = participant.role
            this[ConversationParticipants.lastReadMessageId] = participant.lastReadMessageId
            this[ConversationParticipants.joinedAt] = participant.joinedAt
            this[ConversationParticipants.settings] = participant.settings
        }

        participants
    }

    override suspend fun inviteUserToConversation(
        conversationId: Uuid,
        inviterId: Uuid,
        userId: Uuid
    ): ConversationParticipant? = dbQuery {
        val notInGroup = ConversationParticipants
            .select(ConversationParticipants.userId)
            .where {
                (ConversationParticipants.conversationId eq conversationId) and
                        (ConversationParticipants.userId eq userId)
            }
            .empty()

        if (notInGroup) {
            val participant = ConversationParticipant(
                conversationId = conversationId,
                userId = userId,
                role = ConversationRole.MEMBER,
                lastReadMessageId = null,
                joinedAt = Clock.System.now(),
                settings = ParticipantSettings()
            )

            ConversationParticipants.insert {
                it[ConversationParticipants.conversationId] = participant.conversationId
                it[ConversationParticipants.userId] = participant.userId
                it[ConversationParticipants.role] = participant.role
                it[ConversationParticipants.lastReadMessageId] = participant.lastReadMessageId
                it[ConversationParticipants.joinedAt] = participant.joinedAt
                it[ConversationParticipants.settings] = participant.settings
            }.resultedValues?.singleOrNull()?.let {
                participant
            }
        } else null
    }

    override suspend fun getUserConversationRole(
        userId: Uuid,
        conversationId: Uuid
    ): ConversationRole? = dbQuery {
        ConversationParticipants
            .select(ConversationParticipants.role)
            .where {
                (ConversationParticipants.userId eq userId) and
                        (ConversationParticipants.conversationId eq conversationId)
            }
            .map { it[ConversationParticipants.role] }
            .singleOrNull()
    }
}