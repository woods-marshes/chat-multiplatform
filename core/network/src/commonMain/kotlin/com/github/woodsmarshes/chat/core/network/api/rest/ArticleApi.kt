package com.github.woodsmarshes.chat.core.network.api.rest

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.chat.core.network.dto.article.CreateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

class ArticleApi(
    private val client: HttpClient
) {

    suspend fun listArticles(
        beforeId: Uuid? = null,
        limit: Int = 50,
        authorId: Uuid? = null,
    ): List<ArticleListResponse> {
        return client.get(
            V1.Articles(
                beforeId = beforeId,
                limit = limit,
                authorId = authorId
            )
        ).body()
    }

    suspend fun getArticle(id: Uuid): Article {
        return client.get(V1.Articles.Id(id = id)).body()
    }

    suspend fun listMyArticles(
        beforeId: Uuid? = null,
        limit: Int = 50
    ): List<ArticleListResponse> {
        return client.get(V1.Articles.My(parent = V1.Articles(beforeId = beforeId, limit = limit))).body()
    }

    suspend fun getMyArticle(id: Uuid): Article {
        return client.get(V1.Articles.My.Id(id = id)).body()
    }

    suspend fun saveArticle(
        id: Uuid,
        request: UpdateArticleRequest
    ): Article {
        return client.put(V1.Articles.Id(id = id)) {
            setBody(request)
        }.body()
    }

    suspend fun deleteArticle(id: Uuid) {
        client.delete(V1.Articles.Id(id = id))
    }
}
