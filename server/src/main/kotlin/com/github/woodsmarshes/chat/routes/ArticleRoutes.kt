package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.api.V1
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
    get<V1.Articles> {
        val articles = articleService.listArticles().getOrThrow()
        call.respond(articles)
    }

    get<V1.Articles.Id> { params ->
        val article = articleService.getArticle(params.id).getOrThrow()
        call.respond(article)
    }

    // Protected: create, update, delete
    authenticate {
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
