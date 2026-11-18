package com.github.woodsmarshes.chat.core.contract.dto.admin

import com.github.woodsmarshes.chat.core.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

@Serializable
data class AdminUserListItem(
    @ProtoNumber(1) val user: User,
    @ProtoNumber(2) val activeSessionCount: Int,
)

@Serializable
data class AdminUserListResponse(
    @ProtoNumber(1) val items: List<AdminUserListItem>,
    @ProtoNumber(2) val page: Int,
    @ProtoNumber(3) val limit: Int,
    @ProtoNumber(4) val total: Long,
)

@Serializable
data class AdminUserDetailResponse(
    @ProtoNumber(1) val user: User,
    @ProtoNumber(2) val activeSessionCount: Int,
    @ProtoNumber(3) val conversationCount: Long,
    @ProtoNumber(4) val messageCount: Long,
    @ProtoNumber(5) val lastActiveAt: Instant?,
)