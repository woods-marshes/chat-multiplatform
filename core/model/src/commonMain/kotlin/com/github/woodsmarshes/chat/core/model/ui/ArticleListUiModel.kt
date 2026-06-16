package com.github.woodsmarshes.chat.core.model.ui

import com.github.woodsmarshes.chat.core.model.ArticleStatus
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * UI model for article list display.
 *
 * Omits [content] (the full Tiptap JSON body) to keep list payloads lean.
 * Author fields are flattened so the list can bind directly without
 * additional lookups.
 */
data class ArticleListUiModel(
    val id: Uuid,
    val title: String,
    val authorId: Uuid,
    val authorUsername: String,
    val authorDisplayName: String?,
    val authorAvatar: String?,
    val status: ArticleStatus,
    val excerpt: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
    val coverImage: String?,
    val slug: String?,
)

/**
 * Minimal author info shown in article lists.
 * Does not expose email, bio, or role — those belong on a profile page.
 */
data class ArticleAuthorUi(
    val id: Uuid,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
)
