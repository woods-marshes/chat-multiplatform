package com.github.woodsmarshes.chat.core.contract.dto.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpdateBanRequest(
    @ProtoNumber(1) val banned: Boolean,
)
