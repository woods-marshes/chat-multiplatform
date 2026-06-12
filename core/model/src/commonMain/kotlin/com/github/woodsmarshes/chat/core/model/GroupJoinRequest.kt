package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class GroupJoinRequest(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val conversationId: Uuid,
    @ProtoNumber(3) val applicantId: Uuid,
    @ProtoNumber(4) val handledById: Uuid?,
    @ProtoNumber(5) val message: String?,
    @ProtoNumber(6) val status: RequestStatus,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant
)
