package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.github.woodsmarshes.chat.core.model.ContactStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.GetAllContactsWithUserInfo
import io.github.woodsmarshes.chat.db.SearchContacts
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ContactDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
) : ContactDao {
    private val queries
        get() = dbProvider().contactsQueries

    override suspend fun insertContact(contact: ContactEntity) {
        queries.upsertContact(contact)
    }

    override suspend fun insertContacts(contacts: List<ContactEntity>) {
        if (contacts.isEmpty()) return
        queries.transaction {
            contacts.forEach { insertContact(it) }
        }
    }

    override fun getAllContactsWithUserInfo(): Flow<List<GetAllContactsWithUserInfo>> {
        return queries.getAllContactsWithUserInfo()
            .asFlow()
            .mapToList(ioContext)
    }

    override fun getContactById(contactId: Uuid): Flow<ContactEntity?> {
        return queries.getContactById(contactId)
            .asFlow()
            .mapToOneOrNull(ioContext)
    }

    override fun getBlockedContacts(): Flow<List<ContactEntity>> {
        return queries.getBlockedContacts()
            .asFlow()
            .mapToList(ioContext)
    }

    override fun searchContacts(query: String): Flow<List<SearchContacts>> {
        return queries.searchContacts(query)
            .asFlow()
            .mapToList(ioContext)
    }

    override suspend fun updateAlias(
        contactId: Uuid,
        alias: String?,
        updatedAt: Instant
    ) {
        queries.updateAlias(alias, updatedAt, contactId)
    }

    override suspend fun updateStatus(
        contactId: Uuid,
        status: ContactStatus,
        updatedAt: Instant
    ) {
        queries.updateStatus(status, updatedAt, contactId)
    }

    override suspend fun deleteContact(contactId: Uuid) {
        queries.deleteContact(contactId)
    }

    override fun countFriends(): Flow<Long> {
        return queries.countFriends()
            .asFlow()
            .mapToOne(ioContext)
    }
}