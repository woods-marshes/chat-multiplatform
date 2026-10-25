package com.github.woodsmarshes.chat.core.network.dto.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class InviteUserRequest(
    @ProtoNumber(1) val targetUserId: Uuid
)
