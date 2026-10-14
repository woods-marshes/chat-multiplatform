package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.ContactRequest
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.network.dto.events.ContactEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.repository.database.schema.ContactRequests
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface ContactRequestRepository {
    suspend fun insertContactRequest(
        senderId: Uuid,
        receiverId: Uuid,
        message: String? = null
    ): ContactRequest?

    suspend fun updateRequestStatus(
        requestId: Uuid,
        status: RequestStatus,
        remark: String?,
        expectedStatus: RequestStatus = RequestStatus.PENDING,
    ): Boolean

    suspend fun getRequestById(requestId: Uuid): ContactRequest?

    suspend fun getRequestsByReceiver(receiverId: Uuid): List<ContactRequest>

    suspend fun getRequestsBySender(senderId: Uuid): List<ContactRequest>

    suspend fun getRequestsBySenderAndReceiver(senderId: Uuid, receiverId: Uuid): List<ContactRequest>

    suspend fun deleteRequest(requestId: Uuid): Boolean
}


class ContactRequestSourceImpl(
    private val syncRepository: SyncRepository = SyncDataSourceImpl(),
) : ContactRequestRepository {
    override suspend fun insertContactRequest(
        senderId: Uuid,
        receiverId: Uuid,
        message: String?
    ): ContactRequest? = dbQuery {
        val now = Clock.System.now()
        val inserted = ContactRequests.insert {
            it[this.senderId] = senderId
            it[this.receiverId] = receiverId
            it[this.message] = message
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }
            .resultedValues
            ?.singleOrNull()
            ?.toContactRequest()

        if (inserted != null) {
            val event = ContactEventResponse.FriendRequestSent(
                requestId = inserted.id,
                senderId = inserted.senderId,
                receiverId = inserted.receiverId,
                timestamp = now,
            )
            syncRepository.appendChanges(
                listOf(
                    SyncAppendTarget(SyncScope.USER, senderId, event),
                    SyncAppendTarget(SyncScope.USER, receiverId, event),
                )
            )
        }
        inserted
    }

    override suspend fun updateRequestStatus(
        requestId: Uuid,
        status: RequestStatus,
        remark: String?,
        expectedStatus: RequestStatus,
    ): Boolean = dbQuery {
        val existing = ContactRequests
            .selectAll()
            .where {
                (ContactRequests.id eq requestId) and
                    (ContactRequests.status eq expectedStatus)
            }
            .singleOrNull()
            ?.toContactRequest()
            ?: return@dbQuery false

        val now = Clock.System.now()
        val updated = ContactRequests.update({
            (ContactRequests.id eq requestId) and
                (ContactRequests.status eq expectedStatus)
        }) {
            it[this.status] = status
            it[this.updatedAt] = now
            it[this.message] = remark
        } > 0

        if (updated) {
            val event = when (status) {
                RequestStatus.ACCEPTED -> ContactEventResponse.FriendRequestAccepted(
                    requestId = requestId,
                    senderId = existing.senderId,
                    receiverId = existing.receiverId,
                    timestamp = now,
                )
                RequestStatus.REJECTED -> ContactEventResponse.FriendRequestRejected(
                    requestId = requestId,
                    senderId = existing.senderId,
                    receiverId = existing.receiverId,
                    reason = remark,
                    timestamp = now,
                )
                else -> null
            }
            if (event != null) {
                syncRepository.appendChanges(
                    listOf(
                        SyncAppendTarget(SyncScope.USER, existing.senderId, event),
                        SyncAppendTarget(SyncScope.USER, existing.receiverId, event),
                    )
                )
            }
        }
        updated
    }

    override suspend fun getRequestById(requestId: Uuid): ContactRequest? = dbQuery {
        ContactRequests
            .selectAll()
            .where { ContactRequests.id eq requestId }
            .singleOrNull()
            ?.toContactRequest()
    }

    override suspend fun getRequestsByReceiver(receiverId: Uuid): List<ContactRequest> = dbQuery {
        ContactRequests
            .selectAll()
            .where { ContactRequests.receiverId eq receiverId }
            .map { it.toContactRequest() }
    }

    override suspend fun getRequestsBySender(senderId: Uuid): List<ContactRequest> = dbQuery {
        ContactRequests
            .selectAll()
            .where { ContactRequests.senderId eq senderId }
            .map { it.toContactRequest() }
    }

    override suspend fun getRequestsBySenderAndReceiver(senderId: Uuid, receiverId: Uuid): List<ContactRequest> = dbQuery {
        ContactRequests
            .selectAll()
            .where {
                (ContactRequests.senderId eq senderId) and
                        (ContactRequests.receiverId eq receiverId)
            }
            .map { it.toContactRequest() }
    }

    override suspend fun deleteRequest(requestId: Uuid): Boolean = dbQuery {
        ContactRequests.deleteWhere { ContactRequests.id eq requestId } > 0
    }
}