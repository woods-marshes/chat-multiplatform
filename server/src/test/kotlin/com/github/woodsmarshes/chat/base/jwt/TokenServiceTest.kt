package com.github.woodsmarshes.chat.base.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.SignatureVerificationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenServiceTest {

    private val service: TokenService = TokenServiceImpl()

    private val config = TokenConfig(
        issuer = "test-issuer",
        audience = "test-audience",
        realm = "test-realm",
        expiresIn = 60_000L,
        secret = "test-secret"
    )

    @Test
    fun `generated token carries issuer, audience and custom claims`() {
        val token = service.generateToken(config, TokenClaim("userId", "user-123"))

        val decoded = JWT.require(Algorithm.HMAC256(config.secret))
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
            .verify(token)

        assertEquals("test-issuer", decoded.issuer)
        assertEquals(listOf("test-audience"), decoded.audience)
        assertEquals("user-123", decoded.getClaim("userId").asString())
    }

    @Test
    fun `multiple claims are all present in the token`() {
        val token = service.generateToken(
            config,
            TokenClaim("userId", "u1"),
            TokenClaim("role", "admin")
        )

        val decoded = JWT.require(Algorithm.HMAC256(config.secret))
            .withIssuer(config.issuer)
            .build()
            .verify(token)

        assertEquals("u1", decoded.getClaim("userId").asString())
        assertEquals("admin", decoded.getClaim("role").asString())
    }

    @Test
    fun `token with no claims still verifies`() {
        val token = service.generateToken(config)

        val decoded = JWT.require(Algorithm.HMAC256(config.secret))
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .build()
            .verify(token)

        assertEquals("test-issuer", decoded.issuer)
        assertNull(decoded.getClaim("userId").asLong())
    }

    @Test
    fun `expiresAt is roughly now plus expiresIn`() {
        val before = System.currentTimeMillis()

        val token = service.generateToken(config)
        val decoded = JWT.require(Algorithm.HMAC256(config.secret)).build().verify(token)
        val expiresAt = decoded.expiresAt.time

        // expiresIn = 60s; allow a few seconds of skew between "before" and signing.
        assertTrue(expiresAt in (before + 55_000)..(before + 65_000))
    }

    @Test
    fun `verification fails with a wrong secret`() {
        val token = service.generateToken(config)

        assertFailsWith<SignatureVerificationException> {
            JWT.require(Algorithm.HMAC256("different-secret"))
                .withIssuer(config.issuer)
                .build()
                .verify(token)
        }
    }

    @Test
    fun `verification fails with a wrong issuer`() {
        val token = service.generateToken(config)

        assertFailsWith<JWTVerificationException> {
            JWT.require(Algorithm.HMAC256(config.secret))
                .withIssuer("other-issuer")
                .build()
                .verify(token)
        }
    }

    @Test
    fun `expired token is rejected by the verifier`() {
        val expiredConfig = config.copy(expiresIn = -1_000L)
        val token = service.generateToken(expiredConfig, TokenClaim("userId", "u1"))

        assertFailsWith<TokenExpiredException> {
            JWT.require(Algorithm.HMAC256(config.secret))
                .withIssuer(config.issuer)
                .acceptExpiresAt(0)
                .build()
                .verify(token)
        }
    }

    @Test
    fun `tampered payload breaks the signature`() {
        val token = service.generateToken(config, TokenClaim("userId", "u1"))
        val parts = token.split(".")
        val tampered = parts[0] + "." + parts[1].dropLast(2) + "xy" + "." + parts[2]

        assertFailsWith<JWTVerificationException> {
            JWT.require(Algorithm.HMAC256(config.secret)).build().verify(tampered)
        }
    }
}
