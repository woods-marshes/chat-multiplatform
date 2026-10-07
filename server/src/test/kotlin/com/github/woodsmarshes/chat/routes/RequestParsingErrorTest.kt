package com.github.woodsmarshes.chat.routes

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Request-parsing failures (malformed JSON, wrong shape) are client errors
 * and must come back as 400 — the catch-all handler used to report them as
 * 500, polluting server-error metrics with client mistakes.
 */
class RequestParsingErrorTest {

    @Test
    fun malformedJsonBodyIsABadRequestNotAServerError() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{ not valid json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun jsonBodyWithMissingFieldsIsABadRequest() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        // Valid JSON, but LoginRequest requires email and password.
        val response = client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"wrong": "shape"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
