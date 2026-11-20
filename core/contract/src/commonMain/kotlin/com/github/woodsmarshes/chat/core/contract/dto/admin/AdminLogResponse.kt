package com.github.woodsmarshes.chat.core.contract.dto.admin

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant

/** One recent log entry from the server's in-memory WARN+ ring buffer. */
@Serializable
data class AdminLogEntry(
    @ProtoNumber(1) val timestamp: Instant,
    @ProtoNumber(2) val level: String,
    @ProtoNumber(3) val logger: String,
    @ProtoNumber(4) val message: String,
    /** Short stack-trace excerpt when the event carried a throwable. */
    @ProtoNumber(5) val detail: String? = null,
)

@Serializable
data class AdminLogListResponse(
    @ProtoNumber(1) val items: List<AdminLogEntry>,
)
