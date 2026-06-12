package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Contact
import com.github.woodsmarshes.chat.core.model.ContactStatus
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.repository.database.schema.Contacts
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.UserSettings
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
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

    suspend fun deleteContact(userId: Uuid, contactId: Uuid): Boolean

    suspend fun getContact(userId: Uuid, contactId: Uuid): Contact?

    suspend fun getContactPairByConversation(userId: Uuid, conversationId: Uuid): Pair<Contact, Contact>?

    suspend fun getContactsWithUser(userId: Uuid): List<Pair<Contact, User>>

    suspend fun getContactsByUserId(userId: Uuid): List<Contact>

    suspend fun getContactStatus(userId: Uuid, contactId: Uuid): ContactStatus?

    suspend fun areFriends(userId1: Uuid, userId2: Uuid): Boolean

    suspend fun getNonFriendIds(userId: Uuid, targetIds: List<Uuid>): List<Uuid>

    suspend fun getFriendIds(userId: Uuid): List<Uuid>
}



class ContactSourceImpl : ContactRepository {
    override suspend fun insertContact(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus,
        nickname: String?,
        alias: String?
    ): Contact? = dbQuery {
        val now = Clock.System.now()
        Contacts.insert {
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
    }

    override suspend fun updateContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String?,
        alias: String?,
        status: ContactStatus?,
    ): Boolean = dbQuery {
        Contacts.update(
            where = { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) }
        ) {
            if (nickname != null) it[this.nickname] = nickname
            if (alias != null) it[this.alias] = alias
            if (status != null) it[this.status] = status
            it[this.updatedAt] = Clock.System.now()
        } > 0
    }

    override suspend fun upsertContact(
        userId: Uuid,
        contactId: Uuid,
        nickname: String?,
        alias: String?,
        status: ContactStatus?
    ): Boolean = dbQuery {
        Contacts.upsert(
            where = { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) }
        ) {
            it[this.userId] = userId
            it[this.contactId] = contactId
            if (nickname != null) it[this.nickname] = nickname
            if (alias != null) it[this.alias] = alias
            if (status != null) it[this.status] = status
            it[this.updatedAt] = Clock.System.now()
            it[this.createdAt] = Clock.System.now()
        }.resultedValues?.isNotEmpty() == true
    }

    override suspend fun upsertContactStatus(
        userId: Uuid,
        contactId: Uuid,
        status: ContactStatus
    ): Boolean = dbQuery {
        Contacts.upsert(
            where = { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) }
        ) {
            it[this.userId] = userId
            it[this.contactId] = contactId
            it[this.status] = status
            it[this.updatedAt] = Clock.System.now()
            it[this.createdAt] = Clock.System.now()
        }.resultedValues?.isNotEmpty() == true
    }

    override suspend fun deleteContact(userId: Uuid, contactId: Uuid): Boolean = dbQuery {
        Contacts.deleteWhere {
            (Contacts.userId eq userId) and (Contacts.contactId eq contactId)
        } > 0
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
            .selectAll()
            .where { Contacts.userId eq userId }
            .map {
                when (it[UserSettings.profileVisibility]) {
                    ProfileVisibility.PRIVATE -> {
                        it.toContact() to it.toUser().copy(email = null)
                    }
                    else -> {
                        it.toContact() to it.toUser()
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

    override suspend fun getContactStatus(userId: Uuid, contactId: Uuid): ContactStatus? = dbQuery {
        Contacts
            .selectAll()
            .where {
                (Contacts.userId eq userId) and (Contacts.contactId eq contactId)
            }
            .singleOrNull()
            ?.let { it[Contacts.status] }
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

    override suspend fun getNonFriendIds(
        userId: Uuid,
        targetIds: List<Uuid>
    ): List<Uuid> = dbQuery {
        val friendIds = Contacts
            .selectAll()
            .where {
                (Contacts.userId eq userId) and
                (Contacts.status eq ContactStatus.FRIEND)
            }
            .map { it[Contacts.contactId].value }

        targetIds - friendIds.toSet()
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