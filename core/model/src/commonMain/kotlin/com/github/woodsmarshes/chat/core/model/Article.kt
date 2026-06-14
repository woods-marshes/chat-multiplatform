package com.github.woodsmarshes.chat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Represents an article in the writing platform.
 *
 * [content] stores the TipTap/ProseMirror editor output as a JSON string.
 */
@Serializable
enum class ArticleStatus {
    DRAFT,
    PUBLISHED,
}

@Serializable
data class Article(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val content: JsonElement,
    @ProtoNumber(4) val author: SimpleUser,
    @ProtoNumber(5) val status: ArticleStatus = ArticleStatus.DRAFT,
    @ProtoNumber(6) val excerpt: String? = null,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val publishedAt: Instant? = null,

    @ProtoNumber(10) val coverImage: String? = null,              // 封面图
    @ProtoNumber(11) val deletedAt: Instant? = null,               // 软删除时间
    @ProtoNumber(12) val slug: String? = null,                     // 友好 URL 别名
    @ProtoNumber(13) val stats: ArticleStats = ArticleStats(),     // 统计信息（JSONB）
)
