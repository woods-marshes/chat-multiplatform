package com.github.woodsmarshes.web.storage

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.chat.core.network.dto.article.CreateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.web.koinInject
import kotlin.uuid.Uuid

/**
 * Article data access layer. Delegates to the Ktor-based API client.
 */
object ArticleRepository {

    suspend fun listAll(
        beforeId: Uuid? = null,
        limit: Int = 20,
    ): List<ArticleListResponse> = koinInject<ArticleApi>().listArticles(
        beforeId = beforeId,
        limit = limit,
    )

    /** 获取当前登录用户的文章（依赖已携带的 JWT）。失败时返回空列表。 */
    suspend fun listMy(
        beforeId: Uuid? = null,
        limit: Int = 20,
    ): List<ArticleListResponse> = try {
        koinInject<ArticleApi>().listMyArticles(
            beforeId = beforeId,
            limit = limit,
        )
    } catch (e: Exception) {
        console.log("Failed to fetch my articles: ${e.message}")
        emptyList()
    }

    /** 获取指定作者的文章列表。 */
    suspend fun listByAuthor(
        authorId: Uuid,
        beforeId: Uuid? = null,
        limit: Int = 20,
    ): List<ArticleListResponse> = koinInject<ArticleApi>().listArticles(
        authorId = authorId,
        beforeId = beforeId,
        limit = limit,
    )

    suspend fun getById(id: Uuid): Article? = try {
        koinInject<ArticleApi>().getArticle(id)
    } catch (e: Exception) {
        console.log("Failed to fetch article $id: ${e.message}")
        null
    }

    /** 获取当前登录用户的指定文章。找不到或不属于该用户时返回 null。 */
    suspend fun getMy(id: Uuid): Article? = try {
        koinInject<ArticleApi>().getMyArticle(id)
    } catch (e: Exception) {
        console.log("Failed to fetch my article $id: ${e.message}")
        null
    }

    suspend fun save(id: Uuid, request: UpdateArticleRequest): Article =
        koinInject<ArticleApi>().saveArticle(id, request)

    suspend fun createBlank(): Article = koinInject<ArticleApi>().createBlank()

    suspend fun delete(id: Uuid) {
        koinInject<ArticleApi>().deleteArticle(id)
    }
}
