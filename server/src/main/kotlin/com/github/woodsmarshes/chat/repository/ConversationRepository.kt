package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.GroupProfiles
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface ConversationRepository {
    suspend fun insertConversation(
        type: ConversationType,
        metadata: ConversationMetadata? = null,
    ): Conversation

    suspend fun updateConversation(conversationId: Uuid, metadata: ConversationMetadata): Boolean

    suspend fun deleteConversation(conversationId: Uuid): Boolean

    suspend fun getConversation(conversationId: Uuid): Conversation?

    suspend fun getConversationWithGroupProfile(conversationId: Uuid): Pair<Conversation, GroupProfile>?

    suspend fun getConversations(): List<Conversation>

    suspend fun getConversations(conversationList: List<Uuid>): List<Pair<Conversation, Message?>>

    suspend fun getExistingPrivateConversation(userId1: Uuid, userId2: Uuid): Conversation?

    suspend fun softDeleteConversation(conversationId: Uuid): Boolean

    suspend fun getConversationIncludeDeleted(conversationId: Uuid): Conversation?
}

class ConversationDataSourceImpl : ConversationRepository {
    override suspend fun insertConversation(
        type: ConversationType,
        metadata: ConversationMetadata?,
    ): Conversation = dbQuery {
        Conversations.insert {
            it[Conversations.type] = type
            it[Conversations.metadata] = metadata
            it[createdAt] = Clock.System.now()
        }.resultedValues
            ?.singleOrNull()
            ?.toConversation()
            ?: throw IllegalStateException("Insert successful but returned no data")
    }

    override suspend fun updateConversation(conversationId: Uuid, metadata: ConversationMetadata): Boolean = dbQuery {
        Conversations.update({ Conversations.id eq conversationId }) {
            it[Conversations.metadata] = metadata
            it[updatedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun deleteConversation(conversationId: Uuid): Boolean = dbQuery {
        Conversations.deleteWhere { Conversations.id eq conversationId } > 0
    }

    override suspend fun getConversation(conversationId: Uuid): Conversation? = dbQuery {
        Conversations.selectAll()
            .where { (Conversations.id eq conversationId) and (Conversations.deletedAt.isNull()) }
            .map { it.toConversation() }
            .singleOrNull()
    }

    override suspend fun getConversationWithGroupProfile(conversationId: Uuid): Pair<Conversation, GroupProfile>? = dbQuery {
        (Conversations innerJoin GroupProfiles)
            .selectAll()
            .where { (Conversations.id eq conversationId) and (Conversations.deletedAt.isNull()) }
            .map { it.toConversation() to it.toGroupProfile() }
            .singleOrNull()
    }

    override suspend fun getConversations(): List<Conversation> = dbQuery {
        Conversations
            .selectAll()
            .where { Conversations.deletedAt.isNull() }
            .map { it.toConversation() }
    }

    override suspend fun getConversations(conversationList: List<Uuid>): List<Pair<Conversation, Message?>> = dbQuery {
        Conversations
            .leftJoin(
                otherTable = Messages,
                onColumn = { Conversations.lastMessageId },
                otherColumn = { Messages.id },
            )
            .leftJoin(
                otherTable = Users,
                onColumn = { Messages.senderId },
                otherColumn = { Users.id }
            )
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq Conversations.id }
            )
            .selectAll()
            .where {
                (Conversations.id inList conversationList) and
                        (Conversations.deletedAt.isNull())
            }
            .orderBy(Messages.id to SortOrder.DESC)
            .map { row ->
                val conversation = row.toConversation()
                val lastMessage = row.getOrNull(Messages.id)?.let {
                    row.toFilteredUser()
                }
                conversation to lastMessage
            }
    }

    override suspend fun getExistingPrivateConversation(
        userId1: Uuid,
        userId2: Uuid
    ): Conversation? = dbQuery {
        (Conversations innerJoin ConversationParticipants)
            .select(Conversations.columns)
            .where {
                (Conversations.type eq ConversationType.PRIVATE) and
                        (ConversationParticipants.userId inList listOf(userId1, userId2)) and
                        (Conversations.deletedAt.isNull())
            }
            .groupBy(Conversations.id)
            .having {
                ConversationParticipants.userId.count() eq 2L
            }
            .map { it.toConversation() }
            .firstOrNull()
    }

    override suspend fun softDeleteConversation(conversationId: Uuid): Boolean = dbQuery {
        Conversations.update({ Conversations.id eq conversationId }) {
            it[deletedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun getConversationIncludeDeleted(conversationId: Uuid): Conversation? = dbQuery {
        Conversations.selectAll()
            .where { Conversations.id eq conversationId }
            .map { it.toConversation() }
            .singleOrNull()
    }
}
