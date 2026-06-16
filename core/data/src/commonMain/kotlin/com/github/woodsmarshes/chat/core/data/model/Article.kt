package com.github.woodsmarshes.chat.core.data.model

import com.github.woodsmarshes.chat.core.model.ui.ArticleAuthorUi
import com.github.woodsmarshes.chat.core.model.ui.ArticleListUiModel
import com.github.woodsmarshes.chat.core.model.Article as baseArticle
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import io.github.woodsmarshes.chat.db.Article
import io.github.woodsmarshes.chat.db.GetArticleByIdWithAuthor
import io.github.woodsmarshes.chat.db.KeyedArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListAllArticlesWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorAndStatusWithAuthor
import io.github.woodsmarshes.chat.db.ListArticlesByAuthorWithAuthor
import io.github.woodsmarshes.chat.db.UserEntity

fun baseArticle.toDBArticle() = Article(
    id = this.id,
    title = this.title,
    content = this.content,
    author_id = this.author.id,
    status = this.status,
    excerpt = this.excerpt,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
    published_at = this.publishedAt,
    cover_image = this.coverImage,
    deleted_at = null,
    slug = this.slug,
    stats = null,
)

/**
 * Converts a network [ArticleListResponse] to a local database [Article] entity.
 * [content] stays null on purpose — list endpoints do not return the body.
 */
fun ArticleListResponse.toArticle(): Article = Article(
    id = this.id,
    title = this.title,
    content = this.content,
    author_id = this.author.id,
    status = this.status,
    excerpt = this.excerpt,
    created_at = this.createdAt,
    updated_at = this.updatedAt,
    published_at = this.publishedAt,
    cover_image = this.coverImage,
    deleted_at = null,
    slug = this.slug,
    stats = null,
)

/**
 * Extracts the author from an [ArticleListResponse] as a [UserEntity]
 * so it can be cached in the local user table.
 */
fun ArticleListResponse.toUserEntity(): UserEntity = UserEntity(
    id = this.author.id,
    username = this.author.username,
    email = null,
    display_name = this.author.displayName,
    avatar = this.author.avatarUrl,
    bio = null,
    created_at = this.author.createdAt,
    updated_at = this.author.updatedAt,
    deleted_at = this.author.deletedAt,
    role = com.github.woodsmarshes.chat.core.model.UserRole.MEMBER,
)

fun ListAllArticlesWithAuthor.toArticleListUiModel(): ArticleListUiModel = ArticleListUiModel(
    id = this.id,
    title = this.title,
    authorId = this.author_id,
    authorUsername = this.username,
    authorDisplayName = this.display_name,
    authorAvatar = this.avatar,
    status = this.status,
    excerpt = this.excerpt,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    publishedAt = this.published_at,
    coverImage = this.cover_image,
    slug = this.slug,
)

fun ListArticlesByAuthorWithAuthor.toArticleListUiModel(): ArticleListUiModel = ArticleListUiModel(
    id = this.id,
    title = this.title,
    authorId = this.author_id,
    authorUsername = this.username,
    authorDisplayName = this.display_name,
    authorAvatar = this.avatar,
    status = this.status,
    excerpt = this.excerpt,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    publishedAt = this.published_at,
    coverImage = this.cover_image,
    slug = this.slug,
)

fun ListArticlesByAuthorAndStatusWithAuthor.toArticleListUiModel(): ArticleListUiModel = ArticleListUiModel(
    id = this.id,
    title = this.title,
    authorId = this.author_id,
    authorUsername = this.username,
    authorDisplayName = this.display_name,
    authorAvatar = this.avatar,
    status = this.status,
    excerpt = this.excerpt,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    publishedAt = this.published_at,
    coverImage = this.cover_image,
    slug = this.slug,
)

fun KeyedArticlesWithAuthor.toArticleListUiModel(): ArticleListUiModel = ArticleListUiModel(
    id = this.id,
    title = this.title,
    authorId = this.author_id,
    authorUsername = this.username,
    authorDisplayName = this.display_name,
    authorAvatar = this.avatar,
    status = this.status,
    excerpt = this.excerpt,
    createdAt = this.created_at,
    updatedAt = this.updated_at,
    publishedAt = this.published_at,
    coverImage = this.cover_image,
    slug = this.slug,
)

fun GetArticleByIdWithAuthor.toCoreArticle(): baseArticle? {
    val content = this.content ?: return null
    return baseArticle(
        id = this.id,
        title = this.title,
        content = content,
        author = com.github.woodsmarshes.chat.core.model.SimpleUser(
            id = this.author_user_id,
            username = this.username,
            displayName = this.display_name,
            avatarUrl = this.avatar,
            createdAt = this.author_created_at,
            updatedAt = this.author_updated_at,
            deletedAt = this.author_deleted_at,
            role = this.role,
        ),
        status = this.status,
        excerpt = this.excerpt,
        createdAt = this.created_at,
        updatedAt = this.updated_at,
        publishedAt = this.published_at,
        coverImage = this.cover_image,
        deletedAt = this.deleted_at,
        slug = this.slug,
        stats = this.stats ?: com.github.woodsmarshes.chat.core.model.ArticleStats(),
    )
}

fun List<ListAllArticlesWithAuthor>.toArticleListUiModels(): List<ArticleListUiModel> =
    map { it.toArticleListUiModel() }
