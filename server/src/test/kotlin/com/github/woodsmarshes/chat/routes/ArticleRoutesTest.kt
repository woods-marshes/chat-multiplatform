package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.model.Article
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.dto.article.ArticleListResponse
import com.github.woodsmarshes.chat.core.network.dto.article.CreateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.article.UpdateArticleRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ArticleRoutesTest {

    private suspend fun registerToken(client: HttpClient): String {
        val suffix = Uuid.random().toString().take(8)
        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("article-$suffix", "article-$suffix@test.local", "password-123"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<AuthResponse>().accessToken
    }

    @Test
    fun listIsPublicWhileMutationsRequireAuthentication() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }
        val client = createClient {
            install(ContentNegotiation) { json(ProjectJson) }
        }

        client.get("/v1/articles").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.post("/v1/articles") {
            contentType(ContentType.Application.Json)
            setBody(CreateArticleRequest(title = "nope", content = JsonObject(emptyMap())))
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        client.post("/v1/articles/create-blank").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun authorRoundTripCreatesListsUpdatesAndDeletes() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }
        val client = createClient {
            install(ContentNegotiation) { json(ProjectJson) }
        }
        val token = registerToken(client)

        val created = client.post("/v1/articles") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateArticleRequest(
                    title = "route-test",
                    content = JsonObject(emptyMap()),
                    status = ArticleStatus.DRAFT,
                )
            )
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }.body<Article>()

        client.get("/v1/articles/my") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val mine = body<List<ArticleListResponse>>()
            assertTrue(mine.any { it.id == created.id }, "created article must appear in the author's list")
        }

        client.put("/v1/articles/${created.id}") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateArticleRequest(title = "route-test-v2", status = ArticleStatus.DRAFT))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("route-test-v2", body<Article>().title)
        }

        client.delete("/v1/articles/${created.id}") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.get("/v1/articles/${created.id}").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }
}
