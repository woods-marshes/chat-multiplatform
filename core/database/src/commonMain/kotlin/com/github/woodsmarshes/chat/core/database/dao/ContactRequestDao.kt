package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.PinnedDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import com.github.woodsmarshes.chat.core.model.RequestStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.ContactRequestEntity
import io.github.woodsmarshes.chat.db.ContactRequestsQueries
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Local ledger of friend requests. Intentionally foreign-key-free: the sender
 * or receiver of a request may not have a UserEntity row cached yet, and a
 * failed cache write must never mask a successful server mutation.
 */
interface ContactRequestDao {
    val boundDatabase: ChatDatabase?

    /**
     * Captures the [ChatDatabase] active at the moment this method is called
     * and returns a handle pinned to that exact database instance, so writes
     * triggered outside session-bound contexts (e.g. the realtime event
     * collector) can never land in a different account's database after a
     * suspension point.
     */
    fun bindToCurrentDatabase(): ContactRequestDao

    suspend fun transaction(body: suspend SuspendingTransactionWithoutReturn.() -> Unit)

    /** Authoritative full-row write (server sync). */
    suspend fun upsertContactRequest(request: ContactRequestEntity)

    suspend fun upsertContactRequests(requests: List<ContactRequestEntity>)

    /**
     * Event-seeded write: fills a missing row but never downgrades the status
     * of an already-handled one, and keeps any message already known.
     */
    suspend fun seedContactRequest(request: ContactRequestEntity)

    suspend fun updateContactRequestStatus(id: Uuid, status: RequestStatus, updatedAt: Instant)

    fun selectAllContactRequests(): Flow<List<ContactRequestEntity>>

    suspend fun deleteContactRequest(id: Uuid)
}

class ContactRequestDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : ContactRequestDao {
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

    private suspend fun writeQueries(): ContactRequestsQueries =
        writeDatabase().contactRequestsQueries

    override fun bindToCurrentDatabase(): ContactRequestDao {
        val capturedDb = runCatching { dbProvider() }.getOrElse {
            throw com.github.woodsmarshes.chat.core.common.session.SessionStoppedException(
                it.message ?: "Database is not initialized"
            )
        }
        return PinnedContactRequestDao(
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
            targetDb.contactRequestsQueries.transaction(body = body)
        }
    }

    override suspend fun upsertContactRequest(request: ContactRequestEntity) {
        writeQueries().upsertContactRequest(request)
    }

    override suspend fun upsertContactRequests(requests: List<ContactRequestEntity>) {
        if (requests.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            requests.forEach { request ->
                q.upsertContactRequest(request)
            }
        }
    }

    override suspend fun seedContactRequest(request: ContactRequestEntity) {
        writeQueries().seedContactRequest(request)
    }

    override suspend fun updateContactRequestStatus(id: Uuid, status: RequestStatus, updatedAt: Instant) {
        writeQueries().updateContactRequestStatus(status, updatedAt, id)
    }

    override fun selectAllContactRequests(): Flow<List<ContactRequestEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactRequestsQueries.selectAllContactRequests() },
            extractor = { it.executeAsList() },
        )

    override suspend fun deleteContactRequest(id: Uuid) {
        writeQueries().deleteContactRequest(id)
    }
}

private class PinnedContactRequestDao(
    override val boundDatabase: ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate,
) : ContactRequestDao {
    private suspend fun writeQueries(): ContactRequestsQueries {
        currentCoroutineContext().ensureActive()
        return boundDatabase.contactRequestsQueries
    }

    override fun bindToCurrentDatabase(): ContactRequestDao = this

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        val q = writeQueries()
        withContext(BoundDatabaseElement(boundDatabase)) {
            q.transaction(body = body)
        }
    }

    override suspend fun upsertContactRequest(request: ContactRequestEntity) {
        writeQueries().upsertContactRequest(request)
    }

    override suspend fun upsertContactRequests(requests: List<ContactRequestEntity>) {
        if (requests.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            requests.forEach { request ->
                q.upsertContactRequest(request)
            }
        }
    }

    override suspend fun seedContactRequest(request: ContactRequestEntity) {
        writeQueries().seedContactRequest(request)
    }

    override suspend fun updateContactRequestStatus(id: Uuid, status: RequestStatus, updatedAt: Instant) {
        writeQueries().updateContactRequestStatus(status, updatedAt, id)
    }

    override fun selectAllContactRequests(): Flow<List<ContactRequestEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db -> db.contactRequestsQueries.selectAllContactRequests() },
            extractor = { it.executeAsList() },
        )

    override suspend fun deleteContactRequest(id: Uuid) {
        writeQueries().deleteContactRequest(id)
    }
}
