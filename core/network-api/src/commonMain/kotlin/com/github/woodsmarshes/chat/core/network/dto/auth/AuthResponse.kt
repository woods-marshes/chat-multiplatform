package com.github.woodsmarshes.chat.core.network.dto.auth

import com.github.woodsmarshes.chat.core.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AuthResponse(
    @ProtoNumber(1) val user: User,
    @ProtoNumber(2) val accessToken: String,
    /**
     * Opaque rotating refresh token: exchangeable at /v1/auth/refresh and
     * revocable at /v1/auth/logout. Older servers may omit it.
     */
    @ProtoNumber(3) val refreshToken: String? = null,
)