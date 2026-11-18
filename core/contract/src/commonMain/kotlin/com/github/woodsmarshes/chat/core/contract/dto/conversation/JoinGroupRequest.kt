package com.github.woodsmarshes.chat.core.contract.dto.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class JoinGroupRequest(
    @ProtoNumber(1) val message: String? = null
)
