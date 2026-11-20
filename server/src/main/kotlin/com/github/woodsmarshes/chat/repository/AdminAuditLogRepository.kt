package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.repository.database.schema.AdminAuditLogs
import com.github.woodsmarshes.chat.utils.dbQuery
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** One persisted admin action. */
data class AdminAuditLogRecord(
    val id: Uuid,
    val createdAt: Instant,
    val adminId: Uuid,
    val action: String,
    val targetId: Uuid?,
    val detail: String?,
)

interface AdminAuditLogRepository {
    /**
     * Appends one audit row. Call inside the same [com.github.woodsmarshes.chat.utils.inTransaction]
     * as the mutation being audited so the record commits or rolls back with it.
     */
    suspend fun record(adminId: Uuid, action: String, targetId: Uuid? = null, detail: String? = null)

    /** Newest first. */
    suspend fun listRecent(limit: Int): List<AdminAuditLogRecord>

    /** Retention sweep: deletes rows older than [cutoff]; returns rows deleted. */
    suspend fun deleteOlderThan(cutoff: Instant): Int
}

class AdminAuditLogDataSourceImpl : AdminAuditLogRepository {

    override suspend fun record(adminId: Uuid, action: String, targetId: Uuid?, detail: String?) {
        dbQuery {
            AdminAuditLogs.insert {
                it[id] = Uuid.random()
                it[AdminAuditLogs.adminId] = adminId
                it[AdminAuditLogs.action] = action
                it[AdminAuditLogs.targetId] = targetId
                it[AdminAuditLogs.detail] = detail
            }
        }
    }

    override suspend fun listRecent(limit: Int): List<AdminAuditLogRecord> = dbQuery {
        AdminAuditLogs.selectAll()
            .orderBy(AdminAuditLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AdminAuditLogRecord(
                    id = row[AdminAuditLogs.id],
                    createdAt = row[AdminAuditLogs.createdAt],
                    adminId = row[AdminAuditLogs.adminId],
                    action = row[AdminAuditLogs.action],
                    targetId = row[AdminAuditLogs.targetId],
                    detail = row[AdminAuditLogs.detail],
                )
            }
    }

    override suspend fun deleteOlderThan(cutoff: Instant): Int = dbQuery {
        AdminAuditLogs.deleteWhere { AdminAuditLogs.createdAt less cutoff }
    }
}
