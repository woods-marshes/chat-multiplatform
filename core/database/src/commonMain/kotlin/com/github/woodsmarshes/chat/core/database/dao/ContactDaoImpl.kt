package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import com.github.woodsmarshes.chat.core.common.session.SessionStoppedException
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import com.github.woodsmarshes.chat.core.model.ContactStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactEntity
import io.github.woodsmarshes.chat.db.ContactsQueries
import io.github.woodsmarshes.chat.db.GetAllContactsWithUserInfo
import io.github.woodsmarshes.chat.db.GetBlockedContactsWithUserInfo
import io.github.woodsmarshes.chat.db.SearchContacts
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Dynamic [ContactDao] backed by [dbProvider]. Calling [bindToCurrentDatabase]
 * captures the active [ChatDatabase] at the start of a session and returns a
 * [PinnedContactDao] fixed to that database instance, so subsequent writes and
 * transactions never re-resolve [dbProvider] into a different user's database.
 */
class ContactDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : ContactDao {
    override val boundDatabase: ChatDatabase?
        get() = null

    private suspend fun writeDatabase(): ChatDatabase {
        currentCoroutineContext().ensureActive()
        val contextDb = currentCoroutineContext()[BoundDatabaseElement]?.database
        if (contextDb != null) {
            return contextDb
        }
        return dbProvider()
    }

    private suspend fun writeQueries(): ContactsQueries =
        writeDatabase().contactsQueries

    override fun bindToCurrentDatabase(): ContactDao {
        val capturedDb = runCatching { dbProvider() }.getOrElse {
            throw SessionStoppedException(it.message ?: "Database is not initialized")
        }
        return PinnedContactDao(
            boundDatabase = capturedDb,
            ioContext = ioContext,
            sessionGate = PinnedDatabaseSessionGate(capturedDb, sessionGate),
        )
    }

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        val targetDb = writeDatabase()
        withContext(BoundDatabaseElement(targetDb)) {
            targetDb.contactsQueries.transaction(body = body)
        }
    }

    override suspend fun insertContact(contact: ContactEntity) {
        writeQueries().upsertContact(contact)
    }

    override suspend fun insertContacts(contacts: List<ContactEntity>) {
        if (contacts.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            contacts.forEach { q.upsertContact(it) }
        }
    }

    override fun getAllContactsWithUserInfo(): Flow<List<GetAllContactsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getAllContactsWithUserInfo() },
            extractor = { it.executeAsList() },
        )

    override fun getBlockedContactsWithUserInfo(): Flow<List<GetBlockedContactsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getBlockedContactsWithUserInfo() },
            extractor = { it.executeAsList() },
        )

    override fun getContactById(contactId: Uuid): Flow<ContactEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getContactById(contactId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getBlockedContacts(): Flow<List<ContactEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getBlockedContacts() },
            extractor = { it.executeAsList() },
        )

    override fun searchContacts(query: String): Flow<List<SearchContacts>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.searchContacts(query) },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateAlias(
        contactId: Uuid,
        alias: String?,
        updatedAt: Instant,
    ) {
        writeQueries().updateAlias(alias, updatedAt, contactId)
    }

    override suspend fun updateStatus(
        contactId: Uuid,
        status: ContactStatus,
        updatedAt: Instant,
    ) {
        writeQueries().updateStatus(status, updatedAt, contactId)
    }

    override suspend fun deleteContact(contactId: Uuid) {
        writeQueries().deleteContact(contactId)
    }

    override fun countFriends(): Flow<Long> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.countFriends() },
            extractor = { it.executeAsOne() },
        )
}

private class PinnedContactDao(
    override val boundDatabase: ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate,
) : ContactDao {
    private suspend fun writeQueries(): ContactsQueries {
        currentCoroutineContext().ensureActive()
        return boundDatabase.contactsQueries
    }

    override fun bindToCurrentDatabase(): ContactDao = this

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        val q = writeQueries()
        withContext(BoundDatabaseElement(boundDatabase)) {
            q.transaction(body = body)
        }
    }

    override suspend fun insertContact(contact: ContactEntity) {
        writeQueries().upsertContact(contact)
    }

    override suspend fun insertContacts(contacts: List<ContactEntity>) {
        if (contacts.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            contacts.forEach { q.upsertContact(it) }
        }
    }

    override fun getAllContactsWithUserInfo(): Flow<List<GetAllContactsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getAllContactsWithUserInfo() },
            extractor = { it.executeAsList() },
        )

    override fun getBlockedContactsWithUserInfo(): Flow<List<GetBlockedContactsWithUserInfo>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getBlockedContactsWithUserInfo() },
            extractor = { it.executeAsList() },
        )

    override fun getContactById(contactId: Uuid): Flow<ContactEntity?> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getContactById(contactId) },
            extractor = { it.executeAsOneOrNull() },
        )

    override fun getBlockedContacts(): Flow<List<ContactEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.getBlockedContacts() },
            extractor = { it.executeAsList() },
        )

    override fun searchContacts(query: String): Flow<List<SearchContacts>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.searchContacts(query) },
            extractor = { it.executeAsList() },
        )

    override suspend fun updateAlias(
        contactId: Uuid,
        alias: String?,
        updatedAt: Instant,
    ) {
        writeQueries().updateAlias(alias, updatedAt, contactId)
    }

    override suspend fun updateStatus(
        contactId: Uuid,
        status: ContactStatus,
        updatedAt: Instant,
    ) {
        writeQueries().updateStatus(status, updatedAt, contactId)
    }

    override suspend fun deleteContact(contactId: Uuid) {
        writeQueries().deleteContact(contactId)
    }

    override fun countFriends(): Flow<Long> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactsQueries.countFriends() },
            extractor = { it.executeAsOne() },
        )
}
