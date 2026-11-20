package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

/**
 * Persistent record of every mutating admin action (ban, role change,
 * session revocation, password reset, conversation/message moderation).
 * Rows are written inside the same transaction as the mutation they audit,
 * so a rolled-back action leaves no audit entry. [adminId] and [targetId]
 * are plain UUID columns without foreign keys: the audit trail must
 * survive whatever happens to the referenced users later.
 */
object AdminAuditLogs : Table("admin_audit_logs") {
    val id = uuid("id")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val adminId = uuid("admin_id")
    val action = varchar("action", 64)
    val targetId = uuid("target_id").nullable()
    val detail = text("detail").nullable()

    override val primaryKey = PrimaryKey(id)
}
