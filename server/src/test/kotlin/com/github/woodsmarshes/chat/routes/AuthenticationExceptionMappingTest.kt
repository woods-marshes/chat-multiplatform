package com.github.woodsmarshes.chat.routes

import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticationExceptionMappingTest {

    /**
     * A correctly signed token that carries no userId claim must fail with 401
     * rather than falling through to the 500 catch-all: the signature proves
     * the caller holds a valid credential, so this is an auth problem, not an
     * internal error.
     */
    @Test
    fun validTokenWithoutUserIdClaimIsUnauthorized() = testApplication {
        environment { config = ApplicationConfig("application-test.conf") }

        val token = TokenServiceImpl().generateToken(
            TokenConfig(
                issuer = "chat-server-test",
                audience = "API-test",
                realm = "Ktor Server Test",
                expiresIn = 3_600_000,
                secret = "test-secret-key-for-unit-tests-only",
            ),
        )

        val response = client.get("/v1/contacts") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
