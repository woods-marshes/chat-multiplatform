package com.github.woodsmarshes.web.storage

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.network.api.rest.ArticleApi
import com.github.woodsmarshes.chat.core.network.dto.article.CreateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.web.koinInject
import kotlin.uuid.Uuid

/**
 * Article data access layer. Delegates to the Ktor-based API client.
 */
object ArticleRepository {

    suspend fun listAll(): List<Article> = koinInject<ArticleApi>().listArticles()

    suspend fun getById(id: Uuid): Article? = try {
        koinInject<ArticleApi>().getArticle(id)
    } catch (e: Exception) {
        console.log("Failed to fetch article $id: ${e.message}")
        null
    }

    suspend fun save(id: Uuid, request: UpdateArticleRequest): Article =
        koinInject<ArticleApi>().saveArticle(id, request)

    suspend fun delete(id: Uuid) {
        koinInject<ArticleApi>().deleteArticle(id)
    }
}
