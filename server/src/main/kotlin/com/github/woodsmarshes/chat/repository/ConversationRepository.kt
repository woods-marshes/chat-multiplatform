package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.SimpleUser
import com.github.woodsmarshes.chat.core.model.ConversationMetadata
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.GroupProfile
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.contract.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.contract.dto.events.SyncScope
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
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.Op
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

    suspend fun softDeleteConversation(conversationId: Uuid, deleterId: Uuid): Boolean

    suspend fun getConversationIncludeDeleted(conversationId: Uuid): Conversation?

    /** Admin: paged conversation list (all rows, incl. soft-deleted), newest activity first. */
    suspend fun listConversationsPaged(type: ConversationType?, page: Int, limit: Int): PagedConversations

    /** Admin: participant and message counts for one conversation. */
    suspend fun getConversationAdminStats(conversationId: Uuid): ConversationAdminStats

    /** Admin: participant and message counts for many conversations in two queries. */
    suspend fun getConversationAdminStatsBatch(conversationIds: List<Uuid>): Map<Uuid, ConversationAdminStats>

    /** Admin: participants of one conversation with their user profiles. */
    suspend fun listConversationParticipants(conversationId: Uuid): List<ConversationParticipantInfo>

    /** Admin: platform-wide conversation counters. */
    suspend fun countConversations(): ConversationCounts

    /** Admin: group display name for one conversation, null for PRIVATE or missing profile. */
    suspend fun getGroupName(conversationId: Uuid): String?
}

class ConversationDataSourceImpl(
    private val syncRepository: SyncRepository = SyncDataSourceImpl(),
) : ConversationRepository {
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

    override suspend fun softDeleteConversation(conversationId: Uuid, deleterId: Uuid): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = Conversations.update({ Conversations.id eq conversationId }) {
            it[deletedAt] = now
        } > 0
        if (updated) {
            val memberIds = ConversationParticipants
                .select(ConversationParticipants.userId)
                .where { ConversationParticipants.conversationId eq conversationId }
                .map { it[ConversationParticipants.userId].value }
            val event = ConversationEventResponse.ConversationDeleted(
                conversationId = conversationId,
                deleterId = deleterId,
                timestamp = now,
            )
            val targets = buildList {
                add(SyncAppendTarget(SyncScope.CONVERSATION, conversationId, event))
                memberIds.forEach { uid ->
                    add(SyncAppendTarget(SyncScope.USER, uid, event))
                }
            }
            syncRepository.appendChanges(targets)
        }
        updated
    }

    override suspend fun getConversationIncludeDeleted(conversationId: Uuid): Conversation? = dbQuery {
        Conversations.selectAll()
            .where { Conversations.id eq conversationId }
            .map { it.toConversation() }
            .singleOrNull()
    }

    override suspend fun listConversationsPaged(type: ConversationType?, page: Int, limit: Int): PagedConversations = dbQuery {
        var condition: Op<Boolean> = Op.TRUE
        type?.let { condition = condition and (Conversations.type eq it) }
        val total = Conversations.selectAll().where { condition }.count()
        val items = (Conversations leftJoin GroupProfiles)
            .selectAll()
            .where { condition }
            .orderBy(Conversations.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(((page - 1).toLong()) * limit)
            .map { row ->
                val conversation = row.toConversation()
                val groupName = if (conversation.type == ConversationType.GROUP) {
                    row.getOrNull(GroupProfiles.name)
                } else null
                AdminConversationRow(conversation = conversation, groupName = groupName)
            }
        PagedConversations(items = items, total = total)
    }

    override suspend fun getConversationAdminStats(conversationId: Uuid): ConversationAdminStats = dbQuery {
        val participantCount = ConversationParticipants.selectAll()
            .where { ConversationParticipants.conversationId eq conversationId }
            .count()
        val messageCount = Messages.selectAll()
            .where { Messages.conversationId eq conversationId }
            .count()
        ConversationAdminStats(participantCount = participantCount, messageCount = messageCount)
    }
    override suspend fun getConversationAdminStatsBatch(conversationIds: List<Uuid>): Map<Uuid, ConversationAdminStats> = dbQuery {
        if (conversationIds.isEmpty()) return@dbQuery emptyMap()
        val participantCounts = ConversationParticipants
            .select(ConversationParticipants.conversationId, ConversationParticipants.userId.count())
            .where { ConversationParticipants.conversationId inList conversationIds }
            .groupBy(ConversationParticipants.conversationId)
            .associate { it[ConversationParticipants.conversationId].value to it[ConversationParticipants.userId.count()] }
        val messageCounts = Messages
            .select(Messages.conversationId, Messages.id.count())
            .where { Messages.conversationId inList conversationIds }
            .groupBy(Messages.conversationId)
            .associate { it[Messages.conversationId].value to it[Messages.id.count()] }
        conversationIds.associateWith { id ->
            ConversationAdminStats(
                participantCount = participantCounts[id] ?: 0,
                messageCount = messageCounts[id] ?: 0,
            )
        }
    }

    override suspend fun listConversationParticipants(conversationId: Uuid): List<ConversationParticipantInfo> = dbQuery {
        (ConversationParticipants innerJoin Users)
            .selectAll()
            .where { ConversationParticipants.conversationId eq conversationId }
            .orderBy(ConversationParticipants.joinedAt, SortOrder.ASC)
            .map { row ->
                ConversationParticipantInfo(
                    user = row.toSimpleUser(),
                    role = row[ConversationParticipants.role],
                    joinedAt = row[ConversationParticipants.joinedAt],
                )
            }
    }

    override suspend fun countConversations(): ConversationCounts = dbQuery {
        val total = Conversations.selectAll().count()
        val groups = Conversations.selectAll()
            .where { Conversations.type eq ConversationType.GROUP }
            .count()
        ConversationCounts(total = total, groups = groups, privates = total - groups)
    }

    override suspend fun getGroupName(conversationId: Uuid): String? = dbQuery {
        GroupProfiles.selectAll()
            .where { GroupProfiles.conversationId eq conversationId }
            .singleOrNull()
            ?.get(GroupProfiles.name)
    }
}

data class PagedConversations(
    val items: List<AdminConversationRow>,
    val total: Long,
)

data class AdminConversationRow(
    val conversation: Conversation,
    val groupName: String?,
)

data class ConversationAdminStats(
    val participantCount: Long,
    val messageCount: Long,
)

data class ConversationParticipantInfo(
    val user: SimpleUser,
    val role: ConversationRole,
    val joinedAt: kotlin.time.Instant,
)

data class ConversationCounts(
    val total: Long,
    val groups: Long,
    val privates: Long,
)
