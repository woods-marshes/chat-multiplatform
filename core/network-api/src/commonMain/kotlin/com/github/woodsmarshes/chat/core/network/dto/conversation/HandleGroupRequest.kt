package com.github.woodsmarshes.chat.core.network.dto.conversation

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class HandleGroupRequest(
    @ProtoNumber(1) val groupJoinRequestId: Uuid,
    @ProtoNumber(2) val action: GroupJoinRequestAction,
    @ProtoNumber(3) val reason: String?,
)

@Serializable
enum class GroupJoinRequestAction {
    @ProtoNumber(1) APPROVE,
    @ProtoNumber(2) REJECT
}

