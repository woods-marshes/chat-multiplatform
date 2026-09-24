package com.github.woodsmarshes.chat.core.network.dto.contact

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class HandleContactRequest(
    @ProtoNumber(1) val action: ContactRequestAction,
    @ProtoNumber(2) val remark: String?
)

@Serializable
enum class ContactRequestAction {
    @ProtoNumber(1) APPROVE,
    @ProtoNumber(2) REJECT,
    @ProtoNumber(3) CANCEL
}