package com.github.woodsmarshes.chat.core.network.dto.events

import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.network.dto.conversation.ConversationResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Scope of an append-only sync journal stream.
 */
@Serializable
enum class SyncScope {
    USER,
    CONVERSATION,
}

/**
 * High-water revision for one sync stream.
 */
@Serializable
data class SyncHead(
    @ProtoNumber(1) val scope: SyncScope,
    @ProtoNumber(2) val id: Uuid,
    @ProtoNumber(3) val revision: Long,
)

/**
 * Server-held sync state pushed over the websocket as a wakeup hint: the
 * client compares [Heads.entries] against its local per-stream cursors and
 * pulls forward deltas or snapshots over REST.
 */
@Serializable
sealed class SyncStateResponse : RealtimeEvent {

    @Serializable
    data class Heads(
        @ProtoNumber(1) val entries: List<SyncHead> = emptyList(),
        @ProtoNumber(2) val timestamp: Instant,
    ) : SyncStateResponse()
}

/**
 * One persisted entry in a sync journal stream. [event] reuses the existing
 * [RealtimeEvent] hierarchy and must never wrap another [SyncStateResponse].
 */
@Serializable
data class SyncChange(
    @ProtoNumber(1) val revision: Long,
    @ProtoNumber(2) val event: RealtimeEvent,
)

/**
 * Incremental delta response for a single stream.
 *
 * When [resetRequired] is true, the caller's `afterRevision` fell behind the
 * stream's retention floor (or ahead of its current head), [changes] is empty,
 * and the client must recover via [SyncSnapshot].
 */
@Serializable
data class SyncBatch(
    @ProtoNumber(1) val scope: SyncScope,
    @ProtoNumber(2) val id: Uuid,
    @ProtoNumber(3) val fromRevision: Long,
    @ProtoNumber(4) val nextRevision: Long,
    @ProtoNumber(5) val hasMore: Boolean,
    @ProtoNumber(6) val resetRequired: Boolean,
    @ProtoNumber(7) val changes: List<SyncChange> = emptyList(),
)

/**
 * Consistent point-in-time snapshot of a stream paired with the exact
 * [revision] at which the state was read.
 *
 * - For [SyncScope.CONVERSATION], [messages] holds the latest messages (up to
 *   the requested limit) in chronological/newest-first order.
 * - For [SyncScope.USER], [conversations] holds the user's current conversation
 *   summaries (including membership, read cursors, unread counts, and personal
 *   settings), and [events] carries auxiliary state events (such as contacts
 *   and personal settings) needed to rebuild user-scoped state.
 */
@Serializable
data class SyncSnapshot(
    @ProtoNumber(1) val scope: SyncScope,
    @ProtoNumber(2) val id: Uuid,
    @ProtoNumber(3) val revision: Long,
    @ProtoNumber(4) val messages: List<Message> = emptyList(),
    @ProtoNumber(5) val events: List<RealtimeEvent> = emptyList(),
    @ProtoNumber(6) val conversations: List<ConversationResponse> = emptyList(),
)
