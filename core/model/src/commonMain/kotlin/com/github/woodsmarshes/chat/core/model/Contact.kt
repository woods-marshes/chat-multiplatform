package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Contact(
    @ProtoNumber(1) val userId: Uuid,
    @ProtoNumber(2) val contactId: Uuid,
    @ProtoNumber(3) val status: ContactStatus,
    @ProtoNumber(4) val nickname: String? = null,
    @ProtoNumber(5) val alias: String? = null,
    @ProtoNumber(6) val createdAt: Instant,
    @ProtoNumber(7) val updatedAt: Instant
)

enum class ContactStatus {
    FRIEND,
    BLOCKED,
    BLOCKED_BY, // 只有客户端使用
    DELETED
}
