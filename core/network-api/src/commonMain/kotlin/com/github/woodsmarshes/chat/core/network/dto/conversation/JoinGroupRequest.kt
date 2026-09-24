package com.github.woodsmarshes.chat.core.network.dto.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class JoinGroupRequest(
    @ProtoNumber(1) val message: String? = null
)
