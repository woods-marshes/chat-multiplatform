package com.github.woodsmarshes.chat.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The observability endpoints must boot with the app and stay open in
 * development: /metrics carries the Prometheus registry (JVM + Hikari +
 * HTTP timers) and /health pings the database.
 */
class ObservabilityEndpointsTest {

    @Test
    fun metricsEndpointServesPrometheusScrape() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val response = client.get("/metrics")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue("jvm_memory" in body || "jvm_gc" in body, "JVM metrics must be present")
        assertTrue("ktor_http_server_requests" in body || "http_server" in body, "HTTP timers expected")
    }

    @Test
    fun healthEndpointReportsDatabaseStatus() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }
}
