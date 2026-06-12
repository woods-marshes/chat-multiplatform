package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class User(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val username: String,
    @ProtoNumber(3) val email: String?,
    @ProtoNumber(4) val displayName: String?,
    @ProtoNumber(5) val avatarUrl: String?,
    @ProtoNumber(6) val bio: String?,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val deletedAt: Instant?,
    @ProtoNumber(10) val role: UserRole = UserRole.MEMBER,
)

enum class UserRole {
    ADMIN, MEMBER, GUEST, BOT
}
