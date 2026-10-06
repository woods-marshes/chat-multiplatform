package com.github.woodsmarshes.chat

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the /metrics gate contract: the endpoint is open while no token is
 * configured (development), and requires the matching X-Metrics-Token
 * header once metrics.token is set (production, via METRICS_TOKEN). A
 * blank configured value counts as unconfigured — compose deployments
 * without the variable must not lock the endpoint behind an empty secret.
 */
class ObservabilityTest {

    @Test
    fun `metrics is gated when a token is configured`() = testApplication {
        environment {
            // MapApplicationConfig declares no ktor.application.modules, so
            // the module must be loaded explicitly via the builder below.
            config = MapApplicationConfig(
                "ktor.development" to "true",
                "database.type" to "h2",
                "postgres.url" to "jdbc:postgresql://localhost:5432/chat_db",
                "postgres.username" to "postgres",
                "postgres.password" to "test-only-not-used-by-h2",
                "jwt.issuer" to "chat-server-test",
                "jwt.audience" to "API-test",
                "jwt.realm" to "Ktor Server Test",
                "jwt.secret" to "test-secret-key-for-unit-tests-only",
                "metrics.token" to "test-scrape-token",
            )
        }
        application { module() }

        client.get("/metrics").apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
        client.get("/metrics") {
            header("X-Metrics-Token", "test-scrape-token")
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun `metrics stays open without a configured token`() = testApplication {
        environment {
            config = ApplicationConfig("application-test.conf")
        }

        client.get("/metrics").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
