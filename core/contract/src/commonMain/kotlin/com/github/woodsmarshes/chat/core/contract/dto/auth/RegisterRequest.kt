package com.github.woodsmarshes.chat.core.contract.dto.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class RegisterRequest(
    @ProtoNumber(1) val username: String,
    @ProtoNumber(2) val email: String,
    @ProtoNumber(3) val password: String,
    @ProtoNumber(4) val deviceName: String? = null,
)
