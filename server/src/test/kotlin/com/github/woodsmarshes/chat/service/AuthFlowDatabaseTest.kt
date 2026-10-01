package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.base.ServerConfig
import com.github.woodsmarshes.chat.base.hashing.HashingServiceImpl
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.AuthSessionSourceImpl
import com.github.woodsmarshes.chat.repository.UserDataSourceImpl
import com.github.woodsmarshes.chat.repository.UserSettingDataSourceImpl
import com.github.woodsmarshes.chat.support.TestDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The A-8 refresh-token lifecycle on a real database: login issues an opaque
 * session, refresh rotates it (the old token dies immediately), and logout
 * revokes it.
 */
class AuthFlowDatabaseTest {

    private val authSessionRepository = AuthSessionSourceImpl()
    private val userRepository = UserDataSourceImpl()

    private val service = AuthService(
        userRepository = userRepository,
        userSettingRepository = UserSettingDataSourceImpl(),
        authSessionRepository = authSessionRepository,
        hashingService = HashingServiceImpl(),
        tokenService = TokenServiceImpl(),
        appConfig = ServerConfig(
            TokenConfig(
                issuer = "chat-server-test",
                audience = "API-test",
                realm = "Ktor Server Test",
                expiresIn = 3_600_000,
                secret = "test-secret-key-for-unit-tests-only",
            ),
            databaseConfig = null,
            development = true,
        ),
    )

    @BeforeTest
    fun freshDatabase() {
        TestDb.reset()
    }

    private suspend fun register(name: String) = service.register(
        RegisterRequest(name, "$name@test.local", "longenough1"),
    ).get()!!

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    @Test
    fun loginIssuesRefreshTokenThatRotatesOnRefresh(): Unit = runBlocking {
        val register = register("alice")
        val firstRefresh = assertNotNull(register.refreshToken)

        val login = service.login(LoginRequest("alice@test.local", "longenough1")).get()!!
        val secondRefresh = assertNotNull(login.refreshToken)
        assertNotEquals(firstRefresh, secondRefresh)

        val refreshed = service.refreshSession(secondRefresh).get()!!
        val thirdRefresh = assertNotNull(refreshed.refreshToken)
        assertNotEquals(secondRefresh, thirdRefresh)

        // Rotation revokes the presented token and mints a fresh one. The
        // repo is queried by hash, so raw tokens are hashed before lookup.

        assertNull(authSessionRepository.findActiveSession(sha256(secondRefresh)))
        assertNotNull(authSessionRepository.findActiveSession(sha256(thirdRefresh)))

        assertEquals(register.user.id, refreshed.user.id)
    }

    @Test
    fun refreshReplaysAreRejectedAfterRotation() = runBlocking {
        val register = register("bob")
        val refreshToken = assertNotNull(register.refreshToken)

        service.refreshSession(refreshToken).get()!!

        // A replay of the rotated-away token must not mint anything.
        assertEquals(AuthError.InvalidCredentials, service.refreshSession(refreshToken).getError())
    }

    @Test
    fun logoutRevokesTheRefreshToken() = runBlocking {
        val register = register("carol")
        val refreshToken = assertNotNull(register.refreshToken)

        service.logoutSession(refreshToken).get()

        assertNull(authSessionRepository.findActiveSession(sha256(refreshToken)))
        assertEquals(AuthError.InvalidCredentials, service.refreshSession(refreshToken).getError())
    }

    @Test
    fun liveSessionsAreNotSweptByTheCleanup(): Unit = runBlocking {
        val register = register("dave")
        val refreshToken = assertNotNull(register.refreshToken)

        val deleted = authSessionRepository.deleteExpiredSessions()

        // Sessions are live, so nothing is swept...
        assertEquals(0, deleted)
        assertNotNull(authSessionRepository.findActiveSession(sha256(refreshToken)))
    }
}
