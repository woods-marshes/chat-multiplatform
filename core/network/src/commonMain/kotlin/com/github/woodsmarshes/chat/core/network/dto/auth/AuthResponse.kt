package com.github.woodsmarshes.chat.core.network.dto.auth

import com.github.woodsmarshes.chat.core.model.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AuthResponse(
    @ProtoNumber(1) val user: User,
    @ProtoNumber(2) val accessToken: String,
)