package com.github.woodsmarshes.chat.core.network.dto.events

import com.github.woodsmarshes.chat.core.model.error.DomainError
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SocketErrorResponse(
    @ProtoNumber(1) val requestId: String?, // 关联客户端发起的请求ID（如果是推送类错误，可能为空）
    @ProtoNumber(2) val code: Int,          // 错误码 (参考 HTTP 状态码或自定义)
    @ProtoNumber(3) val message: String,
    @ProtoNumber(4) val error: DomainError? = null,
) : RealtimeEvent