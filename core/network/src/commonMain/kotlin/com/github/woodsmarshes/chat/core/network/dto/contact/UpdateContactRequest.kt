package com.github.woodsmarshes.chat.core.network.dto.contact

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpdateContactRequest(
    @ProtoNumber(1) val nickname: String? = null,
    @ProtoNumber(2) val alias: String? = null
)
