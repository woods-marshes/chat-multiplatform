package com.github.woodsmarshes.chat.core.network.dto.events

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Server-held sync state pushed right after the websocket handshake: the
 * client compares it against its local per-conversation cursors and decides
 * between a small forward delta and a window jump (see MessageSyncCursor).
 */
@Serializable
sealed class SyncStateResponse : RealtimeEvent {

    @Serializable
    data class Anchor(
        @ProtoNumber(1) val entries: List<SyncAnchorEntry> = emptyList(),
        @ProtoNumber(2) val timestamp: Instant,
    ) : SyncStateResponse()
}

@Serializable
data class SyncAnchorEntry(
    @ProtoNumber(1) val conversationId: Uuid,
    /** Last assigned delivery seq for the conversation (= Conversations.next_seq). */
    @ProtoNumber(2) val lastSeq: Long,
    /** Server-computed unread: others' non-revoked messages after the reader's cursor. */
    @ProtoNumber(3) val unreadCount: Int,
    /** Newest message id; null for empty conversations. */
    @ProtoNumber(4) val lastMessageId: Uuid? = null,
)
