package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Clock

/**
 * Authoritative per-scope revision counter and retention floor.
 *
 * Rows in this table are locked via `SELECT ... FOR UPDATE` in deterministic
 * `(scope, scope_id)` order whenever one or more streams are appended in a
 * transaction, guaranteeing gap-free monotonic revisions per stream without
 * relying on global BIGSERIAL ordering.
 */
object SyncStreams : Table("sync_streams") {
    val scope = enumerationByName("scope", 20, SyncScope::class)
    val scopeId = uuid("scope_id")
    val lastRevision = long("last_revision").default(0L)
    val minRetainedRevision = long("min_retained_revision").default(1L)
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }

    override val primaryKey = PrimaryKey(scope, scopeId)
}

/**
 * Append-only journal of committed state changes for a `(scope, scope_id)`
 * stream.
 *
 * For `MessageEventResponse.Received` entries, the stored `event` carries only
 * a minimal stub for the message body; the delta reader re-hydrates the full
 * message from `Messages` at read time so that revoked message content is
 * never retained or leaked from the journal.
 */
object SyncChanges : Table("sync_changes") {
    val scope = enumerationByName("scope", 20, SyncScope::class)
    val scopeId = uuid("scope_id")
    val revision = long("revision")
    val event = jsonb<RealtimeEvent>("event", ProjectJson)
    val messageId = uuid("message_id").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }

    override val primaryKey = PrimaryKey(scope, scopeId, revision)

    init {
        index("idx_sync_changes_message_id", false, messageId)
        index("idx_sync_changes_created_at", false, createdAt)
    }
}
