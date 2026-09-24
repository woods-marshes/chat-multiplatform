package com.github.woodsmarshes.chat.core.network.dto.user

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpdateProfileRequest(
    @ProtoNumber(1) val displayName: String? = null,
    @ProtoNumber(2) val avatarUrl: String? = null,
    @ProtoNumber(3) val bio: String? = null
)
