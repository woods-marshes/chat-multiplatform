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

    suspend fun listAll(): List<ArticleListResponse> = koinInject<ArticleApi>().listArticles()

    /** 获取当前登录用户的全部文章（依赖已携带的 JWT）。失败时返回空列表。 */
    suspend fun listMy(): List<ArticleListResponse> = try {
        koinInject<ArticleApi>().listMyArticles()
    } catch (e: Exception) {
        console.log("Failed to fetch my articles: ${e.message}")
        emptyList()
    }

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

    suspend fun delete(id: Uuid) {
        koinInject<ArticleApi>().deleteArticle(id)
    }
}
