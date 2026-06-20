package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ArticleStats(
    @ProtoNumber(1) val views: Int = 0,
    @ProtoNumber(2) val likes: Int = 0,
    @ProtoNumber(3) val comments: Int = 0,
    @ProtoNumber(4) val wordCount: Int = 0,
    @ProtoNumber(5) val readTimeMinutes: Int = 0,
    @ProtoNumber(6) val allowCollaboration: Boolean = true,
)
