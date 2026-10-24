package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
// `alias` as a member property (a contact's alias column) would shadow the
// extension used to alias the TABLE for the self-join below.
import org.jetbrains.exposed.v1.core.alias as tableAlias
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface ContactRepository {
    suspend fun insertContact(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus = ContactStatus.FRIEND,
        nickname: String? = null,
        alias: String? = null
    ): Contact?

    /**
     * true = a matching row was updated; false = the contact does not exist
     * for this user — never a validation failure.
     */
    suspend fun updateContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String? = null,
        alias: String? = null,
        status: ContactStatus? = null
    ): Boolean

    suspend fun upsertContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String? = null,
        alias: String? = null,
        status: ContactStatus? = null
    ): Boolean

    suspend fun upsertContactStatus(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus
    ): Boolean

    /**
     * Status transition guarded by the current status: updates only when the
     * row exists AND is in [expected] state. Unlike [upsertContactStatus] it
     * can never create a row, so callers can rely on it to never fabricate a
     * relationship.
     */
    suspend fun updateContactStatusIf(
        userId: Uuid,
        contactId: Uuid,
        expected: ContactStatus,
        newStatus: ContactStatus
    ): Boolean

    suspend fun deleteContact(userId: Uuid, contactId: Uuid): Boolean

    suspend fun getContact(userId: Uuid, contactId: Uuid): Contact?

    /**
     * Same as [getContact] but takes a row lock (SELECT ... FOR UPDATE) that
     * is held until the enclosing transaction ends. Must be called inside an
     * open transaction; used to serialize read-then-write decisions (such as
     * unblock) against concurrent writers of the same row.
     */
    suspend fun getContactForUpdate(userId: Uuid, contactId: Uuid): Contact?

    suspend fun getContactPairByConversation(userId: Uuid, conversationId: Uuid): Pair<Contact, Contact>?

    suspend fun getContactsWithUser(userId: Uuid): List<Pair<Contact, User>>

    suspend fun getContactsByUserId(userId: Uuid): List<Contact>

    suspend fun areFriends(userId1: Uuid, userId2: Uuid): Boolean

    suspend fun getFriendIds(userId: Uuid): List<Uuid>
}



class ContactSourceImpl(
    private val syncRepository: SyncRepository = SyncDataSourceImpl(),
) : ContactRepository {
    override suspend fun insertContact(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus,
        nickname: String?,
        alias: String?
    ): Contact? = dbQuery {
        val now = Clock.System.now()
        val inserted = Contacts.insert {
            it[this.userId] = userId
            it[this.contactId] = contactId
            it[this.status] = status
            it[this.nickname] = nickname
            it[this.alias] = alias
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }
            .resultedValues
            ?.singleOrNull()
            ?.toContact()

        if (inserted != null) {
            recordContactStatusChange(userId, contactId, status, nickname, alias, now)
        }
        inserted
    }

    override suspend fun updateContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String?,
        alias: String?,
        status: ContactStatus?,
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = Contacts.update(
            where = { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) }
        ) {
            if (nickname != null) it[this.nickname] = nickname
            if (alias != null) it[this.alias] = alias
            if (status != null) it[this.status] = status
            it[this.updatedAt] = now
        } > 0
        if (updated) {
            if (status != null) {
                recordContactStatusChange(userId, contactId, status, nickname, alias, now)
            } else {
                syncRepository.appendChange(
                    scope = SyncScope.USER,
                    id = userId,
                    event = ContactEventResponse.ContactUpdated(
                        userId = userId,
                        contactId = contactId,
                        nickname = nickname,
                        alias = alias,
                        timestamp = now,
                    ),
                )
            }
        }
        updated
    }

    override suspend fun upsertContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String?,
        alias: String?,
        status: ContactStatus?
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        // No explicit keys: the (user_id, contact_id) primary key is the
        // conflict target. A where clause would be equivalent on PostgreSQL
        // but is unsupported by H2's MERGE-based upsert.
        val upserted = Contacts.upsert(
            // Updating an existing row must not reset when the friendship began.
            onUpdateExclude = listOf(Contacts.createdAt),
        ) {
            it[this.userId] = userId
            it[this.contactId] = contactId
            if (nickname != null) it[this.nickname] = nickname
            if (alias != null) it[this.alias] = alias
            if (status != null) it[this.status] = status
            it[this.updatedAt] = now
            it[this.createdAt] = now
        }.resultedValues?.isNotEmpty() == true
        if (upserted) {
            if (status != null) {
                recordContactStatusChange(userId, contactId, status, nickname, alias, now)
            } else {
                syncRepository.appendChange(
                    scope = SyncScope.USER,
                    id = userId,
                    event = ContactEventResponse.ContactUpdated(
                        userId = userId,
                        contactId = contactId,
                        nickname = nickname,
                        alias = alias,
                        timestamp = now,
                    ),
                )
            }
        }
        upserted
    }

    override suspend fun upsertContactStatus(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        val upserted = Contacts.upsert(
            onUpdateExclude = listOf(Contacts.createdAt),
        ) {
            it[this.userId] = userId
            it[this.contactId] = contactId
            it[this.status] = status
            it[this.updatedAt] = now
            it[this.createdAt] = now
        }.resultedValues?.isNotEmpty() == true
        if (upserted) {
            recordContactStatusChange(userId, contactId, status, null, null, now)
        }
        upserted
    }

    override suspend fun updateContactStatusIf(
        userId: Uuid,
        contactId: Uuid,
        expected: ContactStatus,
        newStatus: ContactStatus
    ): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = Contacts.update(
            where = {
                (Contacts.userId eq userId) and (Contacts.contactId eq contactId) and
                        (Contacts.status eq expected)
            }
        ) {
            it[this.status] = newStatus
            it[this.updatedAt] = now
        } > 0
        if (updated) {
            val event = if (expected == ContactStatus.BLOCKED) {
                ContactEventResponse.UserUnblocked(
                    userId = userId,
                    unblockedUserId = contactId,
                    timestamp = now,
                )
            } else {
                statusToEvent(userId, contactId, newStatus, null, null, now)
            }
            syncRepository.appendChanges(
                listOf(
                    SyncAppendTarget(SyncScope.USER, userId, event),
                    SyncAppendTarget(SyncScope.USER, contactId, event),
                )
            )
        }
        updated
    }

    override suspend fun deleteContact(userId: Uuid, contactId: Uuid): Boolean = dbQuery {
        val deleted = Contacts.deleteWhere {
            (Contacts.userId eq userId) and (Contacts.contactId eq contactId)
        } > 0
        if (deleted) {
            val event = ContactEventResponse.ContactDeleted(
                userId = userId,
                contactId = contactId,
                timestamp = Clock.System.now(),
            )
            syncRepository.appendChanges(
                listOf(
                    SyncAppendTarget(SyncScope.USER, userId, event),
                    SyncAppendTarget(SyncScope.USER, contactId, event),
                )
            )
        }
        deleted
    }

    private suspend fun recordContactStatusChange(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus,
        nickname: String?,
        alias: String?,
        timestamp: kotlin.time.Instant,
    ) {
        val event = statusToEvent(userId, contactId, status, nickname, alias, timestamp)
        syncRepository.appendChanges(
            listOf(
                SyncAppendTarget(SyncScope.USER, userId, event),
                SyncAppendTarget(SyncScope.USER, contactId, event),
            )
        )
    }

    private fun statusToEvent(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus,
        nickname: String?,
        alias: String?,
        timestamp: kotlin.time.Instant,
    ): ContactEventResponse = when (status) {
        ContactStatus.FRIEND -> ContactEventResponse.ContactAdded(
            userId = userId,
            contactId = contactId,
            timestamp = timestamp,
        )
        ContactStatus.BLOCKED -> ContactEventResponse.UserBlocked(
            userId = userId,
            blockedUserId = contactId,
            timestamp = timestamp,
        )
        ContactStatus.DELETED -> ContactEventResponse.ContactDeleted(
            userId = userId,
            contactId = contactId,
            timestamp = timestamp,
        )
        ContactStatus.BLOCKED_BY -> ContactEventResponse.ContactUpdated(
            userId = userId,
            contactId = contactId,
            nickname = nickname,
            alias = alias,
            timestamp = timestamp,
        )
    }

    override suspend fun getContact(userId: Uuid, contactId: Uuid): Contact? = dbQuery {
        Contacts
            .selectAll()
            .where {
                (Contacts.userId eq userId) and (Contacts.contactId eq contactId)
            }
            .singleOrNull()
            ?.toContact()
    }

    override suspend fun getContactForUpdate(userId: Uuid, contactId: Uuid): Contact? = dbQuery {
        Contacts
            .selectAll()
            .where {
                (Contacts.userId eq userId) and (Contacts.contactId eq contactId)
            }
            .forUpdate()
            .singleOrNull()
            ?.toContact()
    }

    override suspend fun getContactPairByConversation(
        userId: Uuid,
        conversationId: Uuid
    ): Pair<Contact, Contact>? = dbQuery {
        val contactId = ConversationParticipants
            .select(ConversationParticipants.userId)
            .where {
                (ConversationParticipants.conversationId eq conversationId) and
                        (ConversationParticipants.userId neq userId)
            }
            .singleOrNull()
            ?.let { it[ConversationParticipants.userId].value } ?: return@dbQuery null
        val userIds = listOf(userId, contactId)
        val contactList = Contacts
            .selectAll()
            .where {
                (Contacts.userId inList userIds) and (Contacts.contactId inList userIds)
            }
            .map {
                it.toContact()
            }
        val userContact = contactList.find { it.userId == userId }
        val otherContact = contactList.find { it.userId == contactId }
        if (userContact != null && otherContact != null) {
            userContact to otherContact
        } else {
            null
        }
    }

    override suspend fun getContactsWithUser(userId: Uuid): List<Pair<Contact, User>>  = dbQuery {
        // The email decision must follow the same direction as
        // getUserProfileForViewer: the CONTACT (profile owner) authorizes the
        // viewer via its own contact row toward the viewer. The viewer's own
        // row cannot decide this — blocking only rewrites the blocker's row,
        // so "viewer → contact = FRIEND" would leak the email of someone who
        // blocked the viewer. The mirrored row is joined in the same query
        // (LEFT: it may not exist for strangers), so there is no N+1.
        val mirrored = Contacts.tableAlias("mirrored")
        Contacts
            .innerJoin(
                otherTable = Users,
                onColumn = { Contacts.contactId },
                otherColumn = { Users.id }
            )
            .innerJoin(
                otherTable = UserSettings,
                onColumn = { Users.id },
                otherColumn = { UserSettings.userId }
            )
            .join(
                otherTable = mirrored,
                joinType = JoinType.LEFT,
                onColumn = Contacts.contactId,
                otherColumn = mirrored[Contacts.userId],
                additionalConstraint = { mirrored[Contacts.contactId] eq Contacts.userId }
            )
            .selectAll()
            .where { Contacts.userId eq userId }
            .map {
                val contact = it.toContact()
                val user = it.toUser()
                when (it[UserSettings.profileVisibility]) {
                    ProfileVisibility.PRIVATE -> contact to user.copy(email = null)
                    ProfileVisibility.PUBLIC -> contact to user
                    // FRIENDS: only a FRIEND row from the contact toward the
                    // viewer grants the email — a viewer the contact blocked
                    // (mirror BLOCKED) or never friended (mirror absent)
                    // gets none, even if the viewer's own row says FRIEND.
                    ProfileVisibility.FRIENDS -> {
                        val mirrorStatus = it.getOrNull(mirrored[Contacts.status])
                        if (mirrorStatus == ContactStatus.FRIEND) contact to user
                        else contact to user.copy(email = null)
                    }
                }
            }
    }

    override suspend fun getContactsByUserId(userId: Uuid): List<Contact> = dbQuery {
        Contacts
            .selectAll()
            .where { Contacts.userId eq userId }
            .map { it.toContact() }
    }

    override suspend fun areFriends(userId1: Uuid, userId2: Uuid): Boolean = dbQuery {
        Contacts
            .selectAll()
            .where {
                ((Contacts.userId eq userId1) and (Contacts.contactId eq userId2)) and
                        (Contacts.status eq ContactStatus.FRIEND)
            }
            .count() > 0
    }

    override suspend fun getFriendIds(userId: Uuid): List<Uuid> = dbQuery {
        Contacts
            .selectAll()
            .where {
                (Contacts.userId eq userId) and
                        (Contacts.status eq ContactStatus.FRIEND)
            }
            .map { it[Contacts.contactId].value }
    }
}