package com.github.woodsmarshes.chat.core.contract.dto.admin

import com.github.woodsmarshes.chat.core.model.Conversation
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.SimpleUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One row of the admin conversation list. */
@Serializable
data class AdminConversationListItem(
    @ProtoNumber(1) val conversation: Conversation,
    @ProtoNumber(2) val participantCount: Long,
    @ProtoNumber(3) val messageCount: Long,
    /** Group display name for GROUP conversations; null for PRIVATE. */
    @ProtoNumber(4) val groupName: String? = null,
)

@Serializable
data class AdminConversationListResponse(
    @ProtoNumber(1) val items: List<AdminConversationListItem>,
    @ProtoNumber(2) val page: Int,
    @ProtoNumber(3) val limit: Int,
    @ProtoNumber(4) val total: Long,
)

/** A participant row in the admin conversation detail view. */
@Serializable
data class AdminConversationParticipant(
    @ProtoNumber(1) val user: SimpleUser,
    @ProtoNumber(2) val role: ConversationRole,
    @ProtoNumber(3) val joinedAt: Instant,
)

@Serializable
data class AdminConversationDetailResponse(
    @ProtoNumber(1) val conversation: Conversation,
    @ProtoNumber(2) val participantCount: Long,
    @ProtoNumber(3) val messageCount: Long,
    @ProtoNumber(4) val groupName: String? = null,
    @ProtoNumber(5) val participants: List<AdminConversationParticipant>,
)