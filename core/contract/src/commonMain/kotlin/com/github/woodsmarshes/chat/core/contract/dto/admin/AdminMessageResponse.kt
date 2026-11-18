package com.github.woodsmarshes.chat.core.contract.dto.admin

import com.github.woodsmarshes.chat.core.model.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * One page of admin message history for a conversation, newest first.
 * Cursor-paged: pass the oldest loaded message id as `beforeId` to fetch
 * the next (older) page; [hasMore] tells whether another page exists.
 * Revoked messages are included (with blanked content and
 * [Message.revokedAt] set) so moderators can see the full timeline.
 */
@Serializable
data class AdminMessageListResponse(
    @ProtoNumber(1) val items: List<Message>,
    @ProtoNumber(2) val hasMore: Boolean,
)