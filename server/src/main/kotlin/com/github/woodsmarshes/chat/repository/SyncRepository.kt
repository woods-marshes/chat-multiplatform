package com.github.woodsmarshes.chat.repository

import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import com.github.woodsmarshes.chat.core.network.dto.events.MessageEventResponse
import com.github.woodsmarshes.chat.core.network.dto.events.RealtimeEvent
import com.github.woodsmarshes.chat.core.network.dto.events.SyncBatch
import com.github.woodsmarshes.chat.core.network.dto.events.SyncChange
import com.github.woodsmarshes.chat.core.network.dto.events.SyncHead
import com.github.woodsmarshes.chat.core.network.dto.events.SyncScope
import com.github.woodsmarshes.chat.core.network.dto.events.SyncSnapshot
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import com.github.woodsmarshes.chat.repository.database.schema.ConversationParticipants
import com.github.woodsmarshes.chat.repository.database.schema.Conversations
import com.github.woodsmarshes.chat.repository.database.schema.Messages
import com.github.woodsmarshes.chat.repository.database.schema.SyncChanges
import com.github.woodsmarshes.chat.repository.database.schema.SyncStreams
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.utils.dbQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.SQLException
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Target stream and event payload to record inside an active transaction.
 */
data class SyncAppendTarget(
    val scope: SyncScope,
    val id: Uuid,
    val event: RealtimeEvent,
)

interface SyncRepository {
    /**
     * Appends one event to the `(scope, id)` stream inside the enclosing (or
     * newly opened) transaction and returns the assigned revision.
     */
    suspend fun appendChange(scope: SyncScope, id: Uuid, event: RealtimeEvent): SyncHead

    /**
     * Appends multiple events across one or more streams inside a single
     * transaction, acquiring `SyncStreams` row locks in deterministic
     * `(scope.ordinal, id.toString())` order to prevent deadlocks.
     */
    suspend fun appendChanges(targets: List<SyncAppendTarget>): List<SyncHead>

    /**
     * Returns the current heads for the user's own `USER` stream and every
     * non-deleted conversation the user currently participates in.
     */
    suspend fun getHeadsForUser(userId: Uuid): List<SyncHead>

    /**
     * Reads up to [limit] changes strictly after [afterRevision] for `(scope, id)`.
     *
     * - If `afterRevision < 0`, `afterRevision > lastRevision`, or
     *   `afterRevision + 1 < minRetainedRevision` (when `lastRevision > 0`),
     *   returns `resetRequired = true` with an empty change list.
     * - Hydrates `MessageEventResponse.Received` payloads from the current
     *   `Messages` row so that revoked message bodies are never returned.
     */
    suspend fun getChanges(
        scope: SyncScope,
        id: Uuid,
        afterRevision: Long,
        limit: Int,
    ): SyncBatch

    /**
     * Captures a consistent snapshot of `(scope, id)` under the stream lock
     * (or current stream state) so the returned [SyncSnapshot.revision] and
     * state cannot drift apart under concurrent writes.
     */
    suspend fun getSnapshot(
        scope: SyncScope,
        id: Uuid,
        limit: Int,
        conversationBuilder: suspend () -> List<ConversationResponse> = { emptyList() },
        userEventsBuilder: suspend () -> List<RealtimeEvent> = { emptyList() },
    ): SyncSnapshot

    /**
     * Advances the retention floor for `(scope, id)` to [newMinRetainedRevision]
     * and deletes journal rows below that floor.
     */
    suspend fun advanceFloor(
        scope: SyncScope,
        id: Uuid,
        newMinRetainedRevision: Long,
    ): Int
}

class SyncDataSourceImpl : SyncRepository {

    override suspend fun appendChange(
        scope: SyncScope,
        id: Uuid,
        event: RealtimeEvent,
    ): SyncHead = appendChanges(listOf(SyncAppendTarget(scope, id, event))).single()

    override suspend fun appendChanges(targets: List<SyncAppendTarget>): List<SyncHead> = dbQuery {
        if (targets.isEmpty()) return@dbQuery emptyList()

        targets.forEach { target ->
            require(target.event !is SyncStateResponse) {
                "SyncStateResponse must never be persisted inside SyncChanges"
            }
        }

        // Canonical lock ordering across all touched streams prevents deadlocks
        // when concurrent multi-stream transactions (e.g. group creation,
        // membership changes, contact approvals) touch overlapping streams.
        val distinctStreams = targets
            .map { it.scope to it.id }
            .distinct()
            .sortedWith(compareBy<Pair<SyncScope, Uuid>> { it.first.ordinal }.thenBy { it.second.toString() })

        val revisionByStream = HashMap<Pair<SyncScope, Uuid>, Long>(distinctStreams.size)
        for ((scope, scopeId) in distinctStreams) {
            val row = lockOrCreateStreamRow(scope, scopeId)
            revisionByStream[scope to scopeId] = row
        }

        val now = Clock.System.now()
        val assignedHeads = ArrayList<SyncHead>(targets.size)

        for (target in targets) {
            val key = target.scope to target.id
            val nextRev = (revisionByStream[key] ?: 0L) + 1L
            revisionByStream[key] = nextRev

            val (sanitizedEvent, refMessageId) = sanitizeEventForStorage(target.event)

            SyncChanges.insert {
                it[this.scope] = target.scope
                it[this.scopeId] = target.id
                it[this.revision] = nextRev
                it[this.event] = sanitizedEvent
                it[this.messageId] = refMessageId
                it[this.createdAt] = now
            }

            assignedHeads.add(SyncHead(scope = target.scope, id = target.id, revision = nextRev))
        }

        for ((key, finalRev) in revisionByStream) {
            val (scope, scopeId) = key
            SyncStreams.update(
                where = { (SyncStreams.scope eq scope) and (SyncStreams.scopeId eq scopeId) }
            ) {
                it[this.lastRevision] = finalRev
                it[this.updatedAt] = now
            }
        }

        assignedHeads
    }

    /**
     * Locks the `SyncStreams` row for `(scope, scopeId)` with `FOR UPDATE`,
     * creating it at revision 0 if it does not exist yet and handling
     * concurrent first-time creation races via unique-constraint retry.
     */
    private fun lockOrCreateStreamRow(scope: SyncScope, scopeId: Uuid): Long {
        val existing = SyncStreams
            .selectAll()
            .where { (SyncStreams.scope eq scope) and (SyncStreams.scopeId eq scopeId) }
            .forUpdate()
            .singleOrNull()
        if (existing != null) {
            return existing[SyncStreams.lastRevision]
        }

        // INSERT ... ON CONFLICT DO NOTHING does not abort a PostgreSQL
        // transaction when two writers initialize the same stream.
        SyncStreams.insertIgnore {
            it[this.scope] = scope
            it[this.scopeId] = scopeId
            it[this.lastRevision] = 0L
            it[this.minRetainedRevision] = 1L
            it[this.updatedAt] = Clock.System.now()
        }
        return SyncStreams
            .selectAll()
            .where { (SyncStreams.scope eq scope) and (SyncStreams.scopeId eq scopeId) }
            .forUpdate()
            .single()[SyncStreams.lastRevision]
    }

    /**
     * Strips message body content before persisting a `Received` journal entry
     * so that a subsequent withdrawal never leaves the original plaintext or
     * attachment URL inside `sync_changes`. The live `Messages` row is hydrated
     * when `getChanges` serves the entry.
     */
    private fun sanitizeEventForStorage(event: RealtimeEvent): Pair<RealtimeEvent, Uuid?> {
        return when (event) {
            is MessageEventResponse.Received -> {
                val msg = event.message
                val stubMessage = Message(
                    id = msg.id,
                    conversationId = msg.conversationId,
                    sender = null,
                    category = MessageCategory.NORMAL,
                    createdAt = msg.createdAt,
                    revokedAt = msg.revokedAt,
                    replyTo = null,
                    content = TextContent(""),
                    senderContext = null,
                    clientRequestId = msg.clientRequestId,
                )
                event.copy(message = stubMessage) to msg.id
            }
            else -> event to null
        }
    }

    override suspend fun getHeadsForUser(userId: Uuid): List<SyncHead> = dbQuery {
        val conversationIds = (ConversationParticipants innerJoin Conversations)
            .selectAll()
            .where {
                (ConversationParticipants.userId eq userId) and Conversations.deletedAt.isNull()
            }
            .map { it[Conversations.id].value }

        val userRev = SyncStreams
            .selectAll()
            .where { (SyncStreams.scope eq SyncScope.USER) and (SyncStreams.scopeId eq userId) }
            .singleOrNull()
            ?.get(SyncStreams.lastRevision) ?: 0L

        val convRevs = if (conversationIds.isNotEmpty()) {
            SyncStreams
                .selectAll()
                .where {
                    (SyncStreams.scope eq SyncScope.CONVERSATION) and
                        (SyncStreams.scopeId inList conversationIds)
                }
                .associate { it[SyncStreams.scopeId] to it[SyncStreams.lastRevision] }
        } else {
            emptyMap()
        }

        buildList {
            add(SyncHead(scope = SyncScope.USER, id = userId, revision = userRev))
            conversationIds.forEach { convId ->
                add(
                    SyncHead(
                        scope = SyncScope.CONVERSATION,
                        id = convId,
                        revision = convRevs[convId] ?: 0L,
                    )
                )
            }
        }
    }

    override suspend fun getChanges(
        scope: SyncScope,
        id: Uuid,
        afterRevision: Long,
        limit: Int,
    ): SyncBatch = dbQuery {
        val coercedLimit = limit.coerceIn(1, 500)
        val streamRow = SyncStreams
            .selectAll()
            .where { (SyncStreams.scope eq scope) and (SyncStreams.scopeId eq id) }
            .singleOrNull()

        val lastRevision = streamRow?.get(SyncStreams.lastRevision) ?: 0L
        val minRetained = streamRow?.get(SyncStreams.minRetainedRevision) ?: 1L

        // Out-of-range cursor: negative, ahead of server head, or behind the
        // retention floor when the stream has already advanced past the floor.
        if (
            afterRevision < 0L ||
            afterRevision > lastRevision ||
            (lastRevision > 0L && afterRevision + 1L < minRetained)
        ) {
            return@dbQuery SyncBatch(
                scope = scope,
                id = id,
                fromRevision = afterRevision,
                nextRevision = lastRevision,
                hasMore = false,
                resetRequired = true,
                changes = emptyList(),
            )
        }

        if (afterRevision == lastRevision) {
            return@dbQuery SyncBatch(
                scope = scope,
                id = id,
                fromRevision = afterRevision,
                nextRevision = lastRevision,
                hasMore = false,
                resetRequired = false,
                changes = emptyList(),
            )
        }

        // Fetch limit + 1 to determine hasMore accurately in one query.
        val rows = SyncChanges
            .selectAll()
            .where {
                (SyncChanges.scope eq scope) and
                    (SyncChanges.scopeId eq id) and
                    (SyncChanges.revision greater afterRevision)
            }
            .orderBy(SyncChanges.revision to SortOrder.ASC)
            .limit(coercedLimit + 1)
            .toList()

        val hasMore = rows.size > coercedLimit
        val pageRows = if (hasMore) rows.take(coercedLimit) else rows

        val messageIds = pageRows.mapNotNull { it[SyncChanges.messageId] }.distinct()
        val hydratedMessages = if (messageIds.isNotEmpty()) {
            loadHydratedMessages(messageIds)
        } else {
            emptyMap()
        }

        val changes = pageRows.mapNotNull { row ->
            val rev = row[SyncChanges.revision]
            val rawEvent = row[SyncChanges.event]
            val hydratedEvent = when (rawEvent) {
                is MessageEventResponse.Received -> {
                    val msgId = row[SyncChanges.messageId] ?: rawEvent.message.id
                    val liveMsg = hydratedMessages[msgId] ?: return@mapNotNull null
                    rawEvent.copy(message = liveMsg)
                }
                else -> rawEvent
            }
            SyncChange(revision = rev, event = hydratedEvent)
        }

        val nextRevision = if (pageRows.isNotEmpty()) {
            pageRows.last()[SyncChanges.revision]
        } else {
            afterRevision
        }

        SyncBatch(
            scope = scope,
            id = id,
            fromRevision = afterRevision,
            nextRevision = nextRevision,
            hasMore = hasMore,
            resetRequired = false,
            changes = changes,
        )
    }

    override suspend fun getSnapshot(
        scope: SyncScope,
        id: Uuid,
        limit: Int,
        conversationBuilder: suspend () -> List<ConversationResponse>,
        userEventsBuilder: suspend () -> List<RealtimeEvent>,
    ): SyncSnapshot = dbQuery {
        val coercedLimit = limit.coerceIn(1, 200)
        // Lock (or initialize and lock) the stream row for the duration of the
        // snapshot read so no concurrent writer can commit a state change on
        // this stream without its revision being reflected in the paired
        // snapshot revision — even on a brand-new stream with revision 0.
        val revision = lockOrCreateStreamRow(scope, id)

        when (scope) {
            SyncScope.CONVERSATION -> {
                val messageRows = Messages
                    .innerJoin(Users)
                    .leftJoin(
                        otherTable = ConversationParticipants,
                        onColumn = { Users.id },
                        otherColumn = { ConversationParticipants.userId },
                        additionalConstraint = { ConversationParticipants.conversationId eq id }
                    )
                    .selectAll()
                    .where { Messages.conversationId eq id }
                    .orderBy(Messages.id to SortOrder.DESC)
                    .limit(coercedLimit)
                    .toList()

                val replyIds = messageRows.mapNotNull { it[Messages.replyToMessageId]?.value }.distinct()
                val replyMap = if (replyIds.isNotEmpty()) {
                    Messages
                        .innerJoin(Users)
                        .leftJoin(
                            otherTable = ConversationParticipants,
                            onColumn = { Users.id },
                            otherColumn = { ConversationParticipants.userId },
                            additionalConstraint = { ConversationParticipants.conversationId eq id }
                        )
                        .selectAll()
                        .where { (Messages.id inList replyIds) and (Messages.conversationId eq id) }
                        .associate { it[Messages.id].value to it.toFilteredUser() }
                } else {
                    emptyMap()
                }

                val messages = messageRows.map { row ->
                    val reply = row[Messages.replyToMessageId]?.let { replyMap[it.value] }
                    row.toFilteredUser(reply)
                }

                SyncSnapshot(
                    scope = SyncScope.CONVERSATION,
                    id = id,
                    revision = revision,
                    messages = messages,
                    events = emptyList(),
                    conversations = emptyList(),
                )
            }

            SyncScope.USER -> {
                val conversations = conversationBuilder()
                val events = userEventsBuilder()
                SyncSnapshot(
                    scope = SyncScope.USER,
                    id = id,
                    revision = revision,
                    messages = emptyList(),
                    events = events,
                    conversations = conversations,
                )
            }
        }
    }

    override suspend fun advanceFloor(
        scope: SyncScope,
        id: Uuid,
        newMinRetainedRevision: Long,
    ): Int = dbQuery {
        val currentLast = lockOrCreateStreamRow(scope, id)
        val clampedFloor = newMinRetainedRevision.coerceIn(1L, currentLast + 1L)
        SyncStreams.update(
            where = { (SyncStreams.scope eq scope) and (SyncStreams.scopeId eq id) }
        ) {
            it[this.minRetainedRevision] = clampedFloor
            it[this.updatedAt] = Clock.System.now()
        }
        SyncChanges.deleteWhere {
            (SyncChanges.scope eq scope) and
                (SyncChanges.scopeId eq id) and
                (SyncChanges.revision less clampedFloor)
        }
    }

    private fun loadHydratedMessages(messageIds: List<Uuid>): Map<Uuid, Message> {
        val rows = Messages
            .innerJoin(Users)
            .leftJoin(
                otherTable = ConversationParticipants,
                onColumn = { Users.id },
                otherColumn = { ConversationParticipants.userId },
                additionalConstraint = { ConversationParticipants.conversationId eq Messages.conversationId }
            )
            .selectAll()
            .where { Messages.id inList messageIds }
            .toList()

        val replyIds = rows.mapNotNull { it[Messages.replyToMessageId]?.value }.distinct()
        val replyMap = if (replyIds.isNotEmpty()) {
            Messages
                .innerJoin(Users)
                .leftJoin(
                    otherTable = ConversationParticipants,
                    onColumn = { Users.id },
                    otherColumn = { ConversationParticipants.userId },
                    additionalConstraint = { ConversationParticipants.conversationId eq Messages.conversationId }
                )
                .selectAll()
                .where { Messages.id inList replyIds }
                .associate { it[Messages.id].value to it.toFilteredUser() }
        } else {
            emptyMap()
        }

        return rows.associate { row ->
            val reply = row[Messages.replyToMessageId]?.let { replyMap[it.value] }
            val msg = row.toFilteredUser(reply)
            msg.id to msg
        }
    }
}
