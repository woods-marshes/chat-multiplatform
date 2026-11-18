package com.github.woodsmarshes.chat.core.contract.dto.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class LoginRequest(
    @ProtoNumber(1) val email: String,
    @ProtoNumber(2) val password: String,
    @ProtoNumber(3) val deviceName: String? = null,
)