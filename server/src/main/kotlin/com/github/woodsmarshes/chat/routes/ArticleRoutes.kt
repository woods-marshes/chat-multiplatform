package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleAuthorDto
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.chat.core.network.dto.article.CreateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.chat.exceptions.getOrThrow
import com.github.woodsmarshes.chat.service.ArticleService
import com.github.woodsmarshes.chat.utils.extractUserId
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.articleRoutes() {
    val articleService by inject<ArticleService>()

    // Public: list and view articles
    get<V1.Articles> { params ->
        val articles = articleService.listArticles(
            beforeId = params.beforeId,
            limit = params.limit,
            authorId = params.authorId,
        ).getOrThrow()
        call.respond(articles.map { it.toListResponse() })
    }

    get<V1.Articles.Id> { params ->
        val article = articleService.getArticle(params.id).getOrThrow()
        call.respond(article)
    }

    // Protected: create, update, delete
    authenticate {
        get<V1.Articles.My> { params ->
            val userId = call.extractUserId()
            val articles = articleService.listMyArticles(
                userId = userId,
                beforeId = params.parent.beforeId,
                limit = params.parent.limit
            ).getOrThrow()
            call.respond(articles.map { it.toListResponse() })
        }

        get<V1.Articles.My.Id> { params ->
            val userId = call.extractUserId()
            val article = articleService.getMyArticle(params.id, userId).getOrThrow()
            call.respond(article)
        }
        post<V1.Articles> {
            val userId = call.extractUserId()
            val req = call.receive<CreateArticleRequest>()
            val article = articleService.createArticle(
                userId = userId,
                title = req.title,
                content = req.content,
                excerpt = req.excerpt,
                status = req.status,
            ).getOrThrow()
            call.respond(article)
        }

        post<V1.Articles.CreateBlank> {
            val userId = call.extractUserId()
            val article = articleService.createArticle(
                userId = userId,
                title = "Untitled", // 默认标题
                content = kotlinx.serialization.json.JsonObject(emptyMap()), // 传入空 JSON 对象 {}
                excerpt = null,
                status = ArticleStatus.DRAFT // 默认设为草稿状态
            ).getOrThrow()

            call.respond(article)
        }

        put<V1.Articles.Id> { params ->
            val userId = call.extractUserId()
            val req = call.receive<UpdateArticleRequest>()
            val article = articleService.saveArticle(
                userId = userId,
                id = params.id,
                title = req.title,
                content = req.content,
                status = req.status,
                excerpt = req.excerpt,
            ).getOrThrow()
            call.respond(article)
        }

        delete<V1.Articles.Id> { params ->
            val userId = call.extractUserId()
            articleService.deleteArticle(userId = userId, id = params.id).getOrThrow()
            call.respond(mapOf("success" to true))
        }
    }
}

/**
 * List endpoints speak [ArticleListResponse], not the domain model.
 *
 * The two are not wire-compatible: the domain model numbers `deletedAt` as 11
 * while the DTO reads 11 as `slug`, so encoding an [Article] directly made the
 * client decode the deletion timestamp as the slug. Mapping here also keeps
 * the document body out of list responses, which is what the DTO documents.
 */
private fun Article.toListResponse(): ArticleListResponse = ArticleListResponse(
    id = id,
    title = title,
    content = null,
    author = ArticleAuthorDto(
        id = author.id,
        username = author.username,
        displayName = author.displayName,
        avatarUrl = author.avatarUrl,
        createdAt = author.createdAt,
        updatedAt = author.updatedAt,
        deletedAt = author.deletedAt,
    ),
    status = status,
    excerpt = excerpt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    publishedAt = publishedAt,
    coverImage = coverImage,
    slug = slug,
)
