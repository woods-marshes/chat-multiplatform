package com.github.woodsmarshes.chat.core.network.dto.contact

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class AddContactRequest(
    @ProtoNumber(1) val targetId: Uuid,
    @ProtoNumber(2) val message: String? = null,
)
