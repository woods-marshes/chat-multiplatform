package com.github.woodsmarshes.chat.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.get
import com.github.woodsmarshes.chat.base.ServerConfig
import com.github.woodsmarshes.chat.base.hashing.HashingServiceImpl
import com.github.woodsmarshes.chat.base.jwt.TokenClaim
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.model.UserRole
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.repository.UserAuthInfo
import com.github.woodsmarshes.chat.repository.UserRepository
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.utils.Keys
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The audit flagged AuthService as having zero coverage while holding the
 * credential paths; these tests pin register/login/refresh semantics,
 * including that the issued token actually carries the userId claim.
 */
class AuthServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val userSettingRepository = mockk<UserSettingRepository>()

    private val tokenConfig = TokenConfig(
        issuer = "chat-server-test",
        audience = "API-test",
        realm = "Ktor Server Test",
        expiresIn = 3_600_000,
        secret = "test-secret-key-for-unit-tests-only",
    )
    private val service = AuthService(
        userRepository = userRepository,
        userSettingRepository = userSettingRepository,
        hashingService = HashingServiceImpl(),
        tokenService = TokenServiceImpl(),
        appConfig = ServerConfig(tokenConfig, databaseConfig = null, development = true),
    )

    private val userId = Uuid.random()
    private val now = Clock.System.now()

    private fun user() = User(
        id = userId,
        username = "alice",
        email = "alice@test.local",
        displayName = null,
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
        role = UserRole.MEMBER,
    )

    private fun issuedUserIdClaim(token: String): String? = runCatching {
        JWT.require(Algorithm.HMAC256(tokenConfig.secret))
            .withAudience(tokenConfig.audience)
            .withIssuer(tokenConfig.issuer)
            .build()
            .verify(token)
            .getClaim(Keys.USER_ID)
            .asString()
    }.getOrNull()

    @Test
    fun registerRejectsShortPasswordBeforeTouchingRepos() = runBlocking {
        val result = service.register(RegisterRequest("alice", "alice@test.local", "short"))

        assertEquals(AuthError.WeakPassword, result.getError())
        coVerify(exactly = 0) { userRepository.checkExists(any(), any()) }
        coVerify(exactly = 0) { userRepository.insertUser(any(), any(), any(), any(), any()) }
    }

    @Test
    fun registerRejectsDuplicateIdentity() = runBlocking {
        coEvery { userRepository.checkExists("alice@test.local", "alice") } returns true

        val result = service.register(RegisterRequest("alice", "alice@test.local", "longenough1"))

        assertEquals(AuthError.UserAlreadyExists, result.getError())
    }

    @Test
    fun registerCreatesUserSettingsAndVerifiableToken() = runBlocking {
        coEvery { userRepository.checkExists("alice@test.local", "alice") } returns false
        coEvery { userRepository.insertUser("alice", "alice@test.local", any(), any(), UserRole.MEMBER) } returns user()
        coEvery { userSettingRepository.initSettings(userId) } returns mockk()

        val result = service.register(RegisterRequest("alice", "alice@test.local", "longenough1"))

        val response = result.get()
        assertNotNull(response)
        assertEquals(userId, response.user.id)
        assertEquals(userId.toString(), issuedUserIdClaim(response.accessToken))
    }

    @Test
    fun registerFailsWhenSettingsRowCannotBeCreated() = runBlocking {
        coEvery { userRepository.checkExists("alice@test.local", "alice") } returns false
        coEvery { userRepository.insertUser(any(), any(), any(), any(), any()) } returns user()
        coEvery { userSettingRepository.initSettings(userId) } returns null

        assertEquals(AuthError.InsertionFailed, service.register(RegisterRequest("alice", "alice@test.local", "longenough1")).getError())
    }

    @Test
    fun loginRejectsUnknownEmail() = runBlocking {
        coEvery { userRepository.findAuthInfoByEmail("ghost@test.local") } returns null

        val result = service.login(LoginRequest("ghost@test.local", "whatever1"))

        assertEquals(AuthError.InvalidCredentials, result.getError())
    }

    @Test
    fun loginRejectsWrongPassword() = runBlocking {
        val correctHash = HashingServiceImpl().generateSaltedHash("correct-horse-1")
        coEvery { userRepository.findAuthInfoByEmail("alice@test.local") } returns UserAuthInfo(
            userId = userId,
            passwordHash = correctHash.hash,
            salt = correctHash.salt,
            domainUser = user(),
        )

        val result = service.login(LoginRequest("alice@test.local", "wrong-password"))

        assertEquals(AuthError.InvalidCredentials, result.getError())
    }

    @Test
    fun loginReturnsTokenForValidCredentials() = runBlocking {
        val password = "correct-horse-1"
        val correctHash = HashingServiceImpl().generateSaltedHash(password)
        coEvery { userRepository.findAuthInfoByEmail("alice@test.local") } returns UserAuthInfo(
            userId = userId,
            passwordHash = correctHash.hash,
            salt = correctHash.salt,
            domainUser = user(),
        )

        val result = service.login(LoginRequest("alice@test.local", password))

        val response = result.get()
        assertNotNull(response)
        assertEquals(userId.toString(), issuedUserIdClaim(response.accessToken))
    }

    @Test
    fun refreshRejectsGarbageToken() = runBlocking {
        assertEquals(AuthError.InvalidCredentials, service.refreshToken("not-a-jwt").getError())
    }

    @Test
    fun refreshRejectsTokenForUnknownUser() = runBlocking {
        val stale = TokenServiceImpl().generateToken(tokenConfig, TokenClaim(Keys.USER_ID, Uuid.random().toString()))
        coEvery { userRepository.getUserById(any()) } returns null

        assertEquals(AuthError.InvalidCredentials, service.refreshToken(stale).getError())
    }

    @Test
    fun refreshMintsANewTokenForTheSameIdentity() = runBlocking {
        val old = TokenServiceImpl().generateToken(tokenConfig, TokenClaim(Keys.USER_ID, userId.toString()))
        coEvery { userRepository.getUserById(userId) } returns user()

        val result = service.refreshToken(old)

        val response = result.get()
        assertNotNull(response)
        // JWS is deterministic: re-signing within the same millisecond can
        // reproduce the same string. What matters is that the new token is
        // valid and carries the same identity (asserted below).
        assertTrue(response.accessToken.isNotBlank())
        assertEquals(userId.toString(), issuedUserIdClaim(response.accessToken))
    }

    @Test
    fun verifyRejectsTokensSignedWithAnotherSecret() {
        val forged = TokenServiceImpl().generateToken(
            tokenConfig.copy(secret = "some-other-secret-value"),
            TokenClaim(Keys.USER_ID, userId.toString()),
        )

        assertNull(issuedUserIdClaim(forged))
    }
}
