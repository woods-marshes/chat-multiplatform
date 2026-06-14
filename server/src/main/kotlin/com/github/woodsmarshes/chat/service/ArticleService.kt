package com.github.woodsmarshes.chat.service

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.model.error.ArticleError
import com.github.woodsmarshes.chat.repository.ArticleRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.woodsmarshes.chat.utils.ExcerptUtils
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

class ArticleService(
    private val articleRepository: ArticleRepository,
) {
    suspend fun createArticle(
        userId: Uuid,
        title: String,
        content: JsonElement,
        excerpt: String?,
        status: ArticleStatus,
    ): Result<Article, ArticleError> = coroutineBinding {
        articleRepository.create(
            authorId = userId,
            title = title,
            content = content,
            status = status,
            excerpt = excerpt
        )
            ?: Err(ArticleError.OperationFailed).bind<Article>()
    }

    suspend fun updateArticle(
        userId: Uuid,
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus?,
        excerpt: String?,
    ): Result<Article, ArticleError> = coroutineBinding {
        val existing = articleRepository.getById(id)
            ?: Err(ArticleError.NotFound).bind<Article>()

        if (existing.author.id != userId) {
            Err(ArticleError.PermissionDenied).bind<Article>()
        }

        val updated = articleRepository.update(id, title, content, status, excerpt)
        if (!updated) {
            Err(ArticleError.OperationFailed).bind<Article>()
        }

        articleRepository.getById(id)
            ?: Err(ArticleError.OperationFailed).bind()
    }

    suspend fun getArticle(id: Uuid): Result<Article, ArticleError> = coroutineBinding {
        articleRepository.getById(id)
            ?: Err(ArticleError.NotFound).bind()
    }

    suspend fun listArticles(offset: Long = 0, limit: Int = 50): Result<List<Article>, ArticleError> = coroutineBinding {
        articleRepository.listAll(offset, limit)
    }


    suspend fun saveArticle(
        userId: Uuid,
        id: Uuid,
        title: String?,
        content: JsonElement?,
        status: ArticleStatus,
        excerpt: String?,
    ): Result<Article, ArticleError> = coroutineBinding {
        val existing = articleRepository.getById(id)
        if (existing != null && existing.author.id != userId) {
            Err(ArticleError.PermissionDenied).bind<Article>()
        }

        val resolvedExcerpt = excerpt?.takeIf { it.isNotBlank() }
            ?: ExcerptUtils.generateFromTipTap(content, maxLength = 150)

        articleRepository.save(
            existing = existing,
            id = id,
            userId = userId,
            title = title,
            content = content,
            status = status,
            excerpt = resolvedExcerpt
        ) ?: Err(ArticleError.OperationFailed).bind()
    }

    suspend fun deleteArticle(userId: Uuid, id: Uuid): Result<Unit, ArticleError> = coroutineBinding {
        val existing = articleRepository.getById(id)
            ?: Err(ArticleError.NotFound).bind<Article>()

        if (existing.author.id != userId) {
            Err(ArticleError.PermissionDenied).bind<Unit>()
        }

        val deleted = articleRepository.delete(id)
        if (!deleted) {
            Err(ArticleError.OperationFailed).bind()
        }
    }
}
