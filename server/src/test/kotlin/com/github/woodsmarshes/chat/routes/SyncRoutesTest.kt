package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.core.network.dto.events.SyncStateResponse
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class SyncRoutesTest {

    private suspend fun registerToken(client: HttpClient): String {
        val suffix = Uuid.random().toString().take(8)
        val response = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("sync-$suffix", "sync-$suffix@test.local", "password-123"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<AuthResponse>().accessToken
    }

    @Test
    fun syncEndpointsRequireAuthentication() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }
        val client = createClient {
            install(ContentNegotiation) { json(ProjectJson) }
        }

        client.get("/v1/sync/heads").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        client.get("/v1/sync/changes?scope=USER&id=${Uuid.random()}&afterRevision=0").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }

        client.get("/v1/sync/snapshot?scope=USER&id=${Uuid.random()}").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun headsReturnsAWellFormedJournalForAFreshUser() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }
        val client = createClient {
            install(ContentNegotiation) { json(ProjectJson) }
        }
        val token = registerToken(client)

        client.get("/v1/sync/heads") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
            // The wiring check: the heads payload decodes into the sealed
            // response type. Entry count is journal policy, not route wiring.
            body<SyncStateResponse.Heads>()
        }
    }
}
