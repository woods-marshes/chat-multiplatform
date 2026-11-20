package com.github.woodsmarshes.chat.core.contract.dto.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AdminStatsResponse(
    @ProtoNumber(1) val userCount: Long,
    @ProtoNumber(2) val adminCount: Long,
    @ProtoNumber(3) val bannedUserCount: Long,
    @ProtoNumber(4) val conversationCount: Long,
    @ProtoNumber(5) val groupConversationCount: Long,
    @ProtoNumber(6) val privateConversationCount: Long,
    @ProtoNumber(7) val messageCount: Long,
    @ProtoNumber(8) val activeSessionCount: Long,
)
