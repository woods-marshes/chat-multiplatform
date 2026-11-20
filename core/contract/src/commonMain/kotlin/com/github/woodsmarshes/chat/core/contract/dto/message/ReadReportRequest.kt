package com.github.woodsmarshes.chat.core.contract.dto.message

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ReadReportRequest(
    @ProtoNumber(1) val messageId: Uuid,
)
