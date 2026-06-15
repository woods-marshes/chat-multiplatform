package com.github.woodsmarshes.chat.core.network.dto.article

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.serialization.JsonElementSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpdateArticleRequest(
    @ProtoNumber(1) val title: String? = null,
    @ProtoNumber(2)
    @Contextual
    val content: JsonElement? = null,
    @ProtoNumber(3) val excerpt: String? = null,
    @ProtoNumber(4) val status: ArticleStatus,
)
