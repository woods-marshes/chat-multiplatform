package com.github.woodsmarshes.chat.core.contract.dto.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AuthSessionInfo(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val deviceName: String?,
    @ProtoNumber(3) val userAgent: String?,
    @ProtoNumber(4) val ipAddress: String?,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val lastUsedAt: Instant?,
    @ProtoNumber(7) val expiresAt: Instant,
    @ProtoNumber(8) val revokedAt: Instant?,
    @ProtoNumber(9) val isCurrent: Boolean = false,
)

@Serializable
data class AuthSessionListResponse(
    @ProtoNumber(1) val items: List<AuthSessionInfo>,
    @ProtoNumber(2) val currentSessionId: Uuid? = null,
)
