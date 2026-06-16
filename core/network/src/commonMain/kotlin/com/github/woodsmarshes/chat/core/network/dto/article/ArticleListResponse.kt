package com.github.woodsmarshes.chat.core.network.dto.article

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Network response for article list endpoints.
 *
 * [content] is nullable so the list endpoint can omit the full Tiptap
 * JSON body to reduce payload size. Single-article (detail) endpoints
 * still return the full [Article] model.
 */
@Serializable
data class ArticleListResponse(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3)
    @Contextual
    val content: JsonElement? = null,
    @ProtoNumber(4) val author: ArticleAuthorDto,
    @ProtoNumber(5) val status: ArticleStatus,
    @ProtoNumber(6) val excerpt: String? = null,
    @ProtoNumber(7) val createdAt: Instant,
    @ProtoNumber(8) val updatedAt: Instant,
    @ProtoNumber(9) val publishedAt: Instant? = null,
    @ProtoNumber(10) val coverImage: String? = null,
    @ProtoNumber(11) val slug: String? = null,
)

/**
 * Author info embedded in an article list response.
 * Kept intentionally lean — no email / bio / role.
 */
@Serializable
data class ArticleAuthorDto(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val username: String,
    @ProtoNumber(3) val displayName: String? = null,
    @ProtoNumber(4) val avatarUrl: String? = null,
    @ProtoNumber(5) val createdAt: Instant,
    @ProtoNumber(6) val updatedAt: Instant,
    @ProtoNumber(7) val deletedAt: Instant? = null,
)
