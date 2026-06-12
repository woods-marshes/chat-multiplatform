package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class ContactRequest(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val senderId: Uuid,
    @ProtoNumber(3) val receiverId: Uuid,
    @ProtoNumber(4) val message: String?,
    @ProtoNumber(5) val status: RequestStatus,
    @ProtoNumber(6) val createdAt: Instant,
    @ProtoNumber(7) val updatedAt: Instant
)

