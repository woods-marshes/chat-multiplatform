package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.GroupJoinRequest
import com.github.woodsmarshes.chat.core.model.RequestStatus
import com.github.woodsmarshes.chat.core.network.dto.events.ConversationEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.repository.database.schema.GroupJoinRequests
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface GroupJoinRequestRepository {
    suspend fun insertGroupJoinRequest(
        conversationId: Uuid,
        applicantId: Uuid,
        message: String? = null
    ): GroupJoinRequest?

    suspend fun updateJoinRequestStatus(
        joinRequestId: Uuid,
        handledId: Uuid,
        reason: String?,
        status: RequestStatus,
    ): Boolean

    suspend fun updateGroupJoinRequests(
        handledId: Uuid,
        updates: List<Triple<Uuid, String?, RequestStatus>>,
    ): Boolean

    suspend fun getJoinRequestById(joinRequestId: Uuid): GroupJoinRequest?

    suspend fun getJoinRequestByIds(joinRequestIds: List<Uuid>): List<GroupJoinRequest>

    suspend fun getJoinRequestByApplicantId(applicantId: Uuid, status: RequestStatus? = null): List<GroupJoinRequest>

    suspend fun getJoinRequestsByHandled(handledId: Uuid, status: RequestStatus? = null): List<GroupJoinRequest>

    suspend fun getJoinRequestsByConversation(conversationId: Uuid, status: RequestStatus? = null): List<GroupJoinRequest>

    suspend fun getJoinRequestsByConversations(conversationIds: List<Uuid>, status: RequestStatus? = null): List<GroupJoinRequest>

    suspend fun deleteJoinRequest(joinRequestId: Uuid): Boolean
}

class GroupJoinRequestSourceImpl(
    private val syncRepository: SyncRepository = SyncDataSourceImpl(),
) : GroupJoinRequestRepository {
    override suspend fun insertGroupJoinRequest(
        conversationId: Uuid,
        applicantId: Uuid,
        message: String?
    ): GroupJoinRequest? = dbQuery {
        val existing = GroupJoinRequests
            .selectAll()
            .where {
                (GroupJoinRequests.conversationId eq conversationId) and
                        (GroupJoinRequests.applicantId eq applicantId) and
                        (GroupJoinRequests.status eq RequestStatus.PENDING)
            }
            .any()

        if (existing) return@dbQuery null

        val now = Clock.System.now()
        val inserted = GroupJoinRequests.insert {
            it[this.conversationId] = conversationId
            it[this.applicantId] = applicantId
            it[this.message] = message
            it[this.createdAt] = now
            it[this.updatedAt] = now
        }
            .resultedValues
            ?.singleOrNull()
            ?.toGroupJoinRequest()

        if (inserted != null) {
            val event = ConversationEventResponse.GroupJoinRequest(
                requestId = inserted.id,
                conversationId = conversationId,
                applicantId = applicantId,
                message = message,
                timestamp = now,
            )
            syncRepository.appendChanges(
                listOf(
                    SyncAppendTarget(SyncScope.CONVERSATION, conversationId, event),
                    SyncAppendTarget(SyncScope.USER, applicantId, event),
                )
            )
        }
        inserted
    }

    override suspend fun updateJoinRequestStatus(
        joinRequestId: Uuid,
        handledId: Uuid,
        reason: String?,
        status: RequestStatus,
    ): Boolean = dbQuery {
        val existing = GroupJoinRequests
            .selectAll()
            .where {
                (GroupJoinRequests.id eq joinRequestId) and
                    (GroupJoinRequests.status eq RequestStatus.PENDING)
            }
            .singleOrNull()
            ?.toGroupJoinRequest()
            ?: return@dbQuery false

        val now = Clock.System.now()
        val updated = GroupJoinRequests.update({
            (GroupJoinRequests.id eq joinRequestId) and
                    (GroupJoinRequests.status eq RequestStatus.PENDING)
        }) {
            it[this.handledById] = handledId
            it[this.message] = reason
            it[this.status] = status
            it[this.updatedAt] = now
        } > 0

        if (updated) {
            syncRepository.appendChange(
                scope = SyncScope.USER,
                id = existing.applicantId,
                event = ConversationEventResponse.GroupJoinRequestHandled(
                    requestId = joinRequestId,
                    conversationId = existing.conversationId,
                    applicantId = existing.applicantId,
                    handlerId = handledId,
                    approved = status == RequestStatus.ACCEPTED,
                    reason = reason,
                    timestamp = now,
                ),
            )
        }
        updated
    }

    override suspend fun updateGroupJoinRequests(
        handledId: Uuid,
        updates: List<Triple<Uuid, String?, RequestStatus>>,
    ): Boolean = dbQuery {
        if (updates.isEmpty()) return@dbQuery false
        var updatedCount = 0
        val currentTimestamp = Clock.System.now()
        val targets = mutableListOf<SyncAppendTarget>()

        updates.forEach { (joinRequestId, reason, status) ->
            val existing = GroupJoinRequests
                .selectAll()
                .where {
                    (GroupJoinRequests.id eq joinRequestId) and
                        (GroupJoinRequests.status eq RequestStatus.PENDING)
                }
                .singleOrNull()
                ?.toGroupJoinRequest()
            val count = GroupJoinRequests.update({
                (GroupJoinRequests.id eq joinRequestId) and
                        (GroupJoinRequests.status eq RequestStatus.PENDING)
            }) {
                it[this.handledById] = handledId
                it[this.status] = status
                it[this.message] = reason
                it[this.updatedAt] = currentTimestamp
            }
            updatedCount += count
            if (count > 0 && existing != null) {
                targets.add(
                    SyncAppendTarget(
                        scope = SyncScope.USER,
                        id = existing.applicantId,
                        event = ConversationEventResponse.GroupJoinRequestHandled(
                            requestId = joinRequestId,
                            conversationId = existing.conversationId,
                            applicantId = existing.applicantId,
                            handlerId = handledId,
                            approved = status == RequestStatus.ACCEPTED,
                            reason = reason,
                            timestamp = currentTimestamp,
                        ),
                    )
                )
            }
        }
        val allSucceeded = updatedCount == updates.size
        if (allSucceeded && targets.isNotEmpty()) {
            syncRepository.appendChanges(targets)
        }
        allSucceeded
    }

    override suspend fun getJoinRequestById(joinRequestId: Uuid): GroupJoinRequest? = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where { GroupJoinRequests.id eq joinRequestId }
            .singleOrNull()
            ?.toGroupJoinRequest()
    }

    override suspend fun getJoinRequestByIds(joinRequestIds: List<Uuid>): List<GroupJoinRequest> = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where { GroupJoinRequests.id inList joinRequestIds }
            .map {
                it.toGroupJoinRequest()
            }
    }

    override suspend fun getJoinRequestByApplicantId(
        applicantId: Uuid,
        status: RequestStatus?
    ): List<GroupJoinRequest> = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where {
                val baseCondition = GroupJoinRequests.applicantId eq applicantId
                if (status != null) {
                    baseCondition and (GroupJoinRequests.status eq status)
                } else {
                    baseCondition
                }
            }
            .orderBy(GroupJoinRequests.updatedAt to SortOrder.DESC)
            .map { it.toGroupJoinRequest() }
    }

    override suspend fun getJoinRequestsByHandled(
        handledId: Uuid,
        status: RequestStatus?
    ): List<GroupJoinRequest> = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where {
                val baseCondition = GroupJoinRequests.handledById eq handledId
                if (status != null) {
                    baseCondition and (GroupJoinRequests.status eq status)
                } else {
                    baseCondition
                }
            }
            .orderBy(GroupJoinRequests.updatedAt to SortOrder.DESC)
            .map { it.toGroupJoinRequest() }
    }

    override suspend fun getJoinRequestsByConversation(
        conversationId: Uuid,
        status: RequestStatus?
    ): List<GroupJoinRequest> = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where {
                val baseCondition = GroupJoinRequests.conversationId eq conversationId
                if (status != null) {
                    baseCondition and (GroupJoinRequests.status eq status)
                } else {
                    baseCondition
                }
            }
            .orderBy(GroupJoinRequests.updatedAt to SortOrder.DESC)
            .map { it.toGroupJoinRequest() }
    }

    override suspend fun getJoinRequestsByConversations(
        conversationIds: List<Uuid>,
        status: RequestStatus?
    ): List<GroupJoinRequest> = dbQuery {
        GroupJoinRequests
            .selectAll()
            .where {
                val baseCondition = GroupJoinRequests.conversationId inList conversationIds
                if (status != null) {
                    baseCondition and (GroupJoinRequests.status eq status)
                } else {
                    baseCondition
                }
            }
            .orderBy(GroupJoinRequests.updatedAt to SortOrder.DESC)
            .map { it.toGroupJoinRequest() }
    }

    override suspend fun deleteJoinRequest(joinRequestId: Uuid): Boolean = dbQuery {
        GroupJoinRequests.deleteWhere { GroupJoinRequests.id eq joinRequestId } > 0
    }
}