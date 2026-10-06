package com.github.woodsmarshes.chat

import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.*
import org.koin.core.annotation.KoinInternalApi
import org.koin.ktor.ext.getKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(KoinInternalApi::class)
class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }

        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    /**
     * Canary for Koin graph integrity: this anonymous route resolves
     * ConversationLifecycleService -> EventBusImpl through Koin. If any
     * constructor parameter of those singletons loses its Koin registration
     * (as happened with EventBusImpl's MeterRegistry in 8fc6d68), the first
     * request throws during instance creation and the route 500s — while
     * boot, /health and non-event-bus routes stay green.
     */
    @Test
    fun koinGraphResolvesEventBusDependentServices() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }

        client.get("/v1/conversations/search?keyword=a").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    /**
     * Full auth round-trip with a token the server itself minted. Unlike a
     * hand-minted token (see FileRoutesTest), this fails the moment the
     * verifier's issuer/audience/secret drifts from the issuer's config —
     * a drift that otherwise surfaces only as unexplained 401s in a live
     * environment, because decoding a JWT never reveals which key signed it.
     */
    @Test
    fun authenticatedRoutesAcceptServerIssuedTokens() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }

        val client = createClient {
            install(ContentNegotiation) { json(ProjectJson) }
        }

        val suffix = Uuid.random().toString().take(8)
        val registered = client.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("e2e-$suffix", "e2e-$suffix@test.local", "password-123"))
        }
        assertEquals(HttpStatusCode.OK, registered.status)
        val token = registered.body<AuthResponse>().accessToken

        client.get("/v1/auth/verify") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.get("/v1/users/me/conversations") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    /**
     * Resolves every definition the running app registers — including the
     * runtime-provided ones (Logger, Database, ServerConfig, ...) that a
     * static module-list check would have to stub. Any definition whose
     * constructor parameters are not all registered (as happened with
     * EventBusImpl's MeterRegistry in 8fc6d68) fails here at test time
     * instead of on the first business request in production.
     */
    @Test
    fun everyKoinDefinitionResolves() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }

        // A request first: testApplication boots the application lazily,
        // and the Koin plugin is only installed afterwards.
        client.get("/")

        val koin = application.getKoin()
        val failures = koin.instanceRegistry.instances.values.mapNotNull { factory ->
            val type = factory.beanDefinition.primaryType
            try {
                koin.get<Any>(type)
                null
            } catch (e: Throwable) {
                "${type.qualifiedName}: ${e::class.simpleName}: ${e.message}"
            }
        }

        assertTrue(failures.isEmpty(), "Unresolvable Koin definitions:\n${failures.joinToString("\n")}")
    }

}
