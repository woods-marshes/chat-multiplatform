package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import com.github.woodsmarshes.chat.core.database.di.BoundDatabaseElement
import com.github.woodsmarshes.chat.core.database.session.DatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.DirectDatabaseSessionGate
import com.github.woodsmarshes.chat.core.database.session.sessionBoundQueryFlow
import com.github.woodsmarshes.chat.core.model.RequestStatus
import io.github.woodsmarshes.chat.db.ChatDatabase
import io.github.woodsmarshes.chat.db.GroupJoinRequestEntity
import io.github.woodsmarshes.chat.db.GroupJoinRequestsQueries
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Local ledger of group join requests. Intentionally foreign-key-free: the
 * applicant or the target conversation may not be cached yet, and a failed
 * cache write must never mask a successful server mutation.
 */
interface GroupJoinRequestDao {
    suspend fun transaction(body: suspend SuspendingTransactionWithoutReturn.() -> Unit)

    /** Authoritative full-row write (server sync). */
    suspend fun upsertGroupJoinRequest(request: GroupJoinRequestEntity)

    suspend fun upsertGroupJoinRequests(requests: List<GroupJoinRequestEntity>)

    /**
     * Event-seeded write: fills a missing row but never downgrades the status
     * of an already-handled one, and keeps any message already known.
     */
    suspend fun seedGroupJoinRequest(request: GroupJoinRequestEntity)

    suspend fun updateGroupJoinRequestStatus(
        id: Uuid,
        status: RequestStatus,
        handledBy: Uuid?,
        updatedAt: Instant,
    )

    /** Requests of one conversation; pass [status] = null for every status. */
    fun selectGroupJoinRequestsByConversation(
        conversationId: Uuid,
        status: RequestStatus? = null,
    ): Flow<List<GroupJoinRequestEntity>>

    /** Requests for conversations where [meId] is OWNER or ADMIN, every status. */
    fun selectIncomingGroupRequests(meId: Uuid): Flow<List<GroupJoinRequestEntity>>

    /** Requests sent by [meId], every status; tab filtering happens in memory. */
    fun selectSentGroupRequests(meId: Uuid): Flow<List<GroupJoinRequestEntity>>

    suspend fun deleteGroupJoinRequestsForConversation(conversationId: Uuid)
}

class GroupJoinRequestDaoImpl(
    private val dbProvider: () -> ChatDatabase,
    private val ioContext: CoroutineContext,
    private val sessionGate: DatabaseSessionGate = DirectDatabaseSessionGate(dbProvider),
) : GroupJoinRequestDao {
    private suspend fun writeDatabase(): ChatDatabase {
        currentCoroutineContext().ensureActive()
        val contextDb = currentCoroutineContext()[BoundDatabaseElement]?.database
        if (contextDb != null) {
            return contextDb
        }
        return dbProvider()
    }

    private suspend fun writeQueries(): GroupJoinRequestsQueries =
        writeDatabase().groupJoinRequestsQueries

    override suspend fun transaction(
        body: suspend SuspendingTransactionWithoutReturn.() -> Unit,
    ) {
        val targetDb = writeDatabase()
        withContext(BoundDatabaseElement(targetDb)) {
            targetDb.groupJoinRequestsQueries.transaction(body = body)
        }
    }

    override suspend fun upsertGroupJoinRequest(request: GroupJoinRequestEntity) {
        writeQueries().upsertGroupJoinRequest(request)
    }

    override suspend fun upsertGroupJoinRequests(requests: List<GroupJoinRequestEntity>) {
        if (requests.isEmpty()) return
        val q = writeQueries()
        q.transaction {
            requests.forEach { request ->
                q.upsertGroupJoinRequest(request)
            }
        }
    }

    override suspend fun seedGroupJoinRequest(request: GroupJoinRequestEntity) {
        writeQueries().seedGroupJoinRequest(request)
    }

    override suspend fun updateGroupJoinRequestStatus(
        id: Uuid,
        status: RequestStatus,
        handledBy: Uuid?,
        updatedAt: Instant,
    ) {
        writeQueries().updateGroupJoinRequestStatus(status, handledBy, updatedAt, id)
    }

    override fun selectGroupJoinRequestsByConversation(
        conversationId: Uuid,
        status: RequestStatus?,
    ): Flow<List<GroupJoinRequestEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db ->
                db.groupJoinRequestsQueries.selectGroupJoinRequestsByConversation(conversationId, status)
            },
            extractor = { it.executeAsList() },
        )

    override fun selectIncomingGroupRequests(meId: Uuid): Flow<List<GroupJoinRequestEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db ->
                db.groupJoinRequestsQueries.selectIncomingGroupRequests(meId)
            },
            extractor = { it.executeAsList() },
        )

    override fun selectSentGroupRequests(meId: Uuid): Flow<List<GroupJoinRequestEntity>> =
        sessionBoundQueryFlow(
            gate = sessionGate,
            ioContext = ioContext,
            queryFactory = { db ->
                db.groupJoinRequestsQueries.selectSentGroupRequests(meId)
            },
            extractor = { it.executeAsList() },
        )

    override suspend fun deleteGroupJoinRequestsForConversation(conversationId: Uuid) {
        writeQueries().deleteGroupJoinRequestsForConversation(conversationId)
    }
}
