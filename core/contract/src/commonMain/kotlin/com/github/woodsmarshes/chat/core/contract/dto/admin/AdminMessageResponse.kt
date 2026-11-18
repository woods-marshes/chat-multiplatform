package com.github.woodsmarshes.chat.core.contract.dto.admin

import com.github.woodsmarshes.chat.core.model.Message
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Admin message history for one conversation. Revoked messages are included
 * (with blanked content and [Message.revokedAt] set) so moderators can see
 * the full timeline.
 */
@Serializable
data class AdminMessageListResponse(
    @ProtoNumber(1) val items: List<Message>,
    @ProtoNumber(2) val page: Int,
    @ProtoNumber(3) val limit: Int,
    @ProtoNumber(4) val total: Long,
)