package com.github.woodsmarshes.chat.core.network.dto.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class InviteUsersRequest(
    @ProtoNumber(1) val userIds: List<Uuid>
)
