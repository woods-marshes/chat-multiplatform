package com.github.woodsmarshes.chat.service

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.woodsmarshes.chat.base.ServerConfig
import com.github.woodsmarshes.chat.base.hashing.HashingServiceImpl
import com.github.woodsmarshes.chat.base.jwt.TokenConfig
import com.github.woodsmarshes.chat.base.jwt.TokenServiceImpl
import com.github.woodsmarshes.chat.core.model.FriendRequestPolicy
import com.github.woodsmarshes.chat.core.model.ProfileVisibility
import com.github.woodsmarshes.chat.core.model.UserPreference
import com.github.woodsmarshes.chat.core.model.UserSetting
import com.github.woodsmarshes.chat.core.model.error.AuthError
import com.github.woodsmarshes.chat.core.network.dto.auth.LoginRequest
import com.github.woodsmarshes.chat.core.network.dto.auth.RegisterRequest
import com.github.woodsmarshes.chat.repository.AuthSessionRepository
import com.github.woodsmarshes.chat.repository.AuthSessionSourceImpl
import com.github.woodsmarshes.chat.repository.UserDataSourceImpl
import com.github.woodsmarshes.chat.repository.UserSettingDataSourceImpl
import com.github.woodsmarshes.chat.repository.UserSettingRepository
import com.github.woodsmarshes.chat.repository.database.schema.AuthSessions
import com.github.woodsmarshes.chat.repository.database.schema.Users
import com.github.woodsmarshes.chat.support.TestDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The A-8 refresh-token lifecycle on a real database: login issues an opaque
 * session, refresh rotates it (the old token dies immediately), and logout
 * revokes it.
 */
class AuthFlowDatabaseTest {

    private val authSessionRepository = AuthSessionSourceImpl()
    private val userRepository = UserDataSourceImpl()

    private val appConfig = ServerConfig(
        TokenConfig(
            issuer = "chat-server-test",
            audience = "API-test",
            realm = "Ktor Server Test",
            expiresIn = 3_600_000,
            secret = "test-secret-key-for-unit-tests-only",
        ),
        databaseConfig = null,
        development = true,
    )

    private val service = AuthService(
        userRepository = userRepository,
        userSettingRepository = UserSettingDataSourceImpl(),
        authSessionRepository = authSessionRepository,
        hashingService = HashingServiceImpl(),
        tokenService = TokenServiceImpl(),
        appConfig = appConfig,
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

    /**
     * Test fakes written as named classes with explicit forwarding: the Ktor
     * OpenAPI compiler extension crashes on anonymous `object : X by Y {}`
     * declarations, so delegation-by-syntax is avoided here.
     */
    private class NullSettingsRepository : UserSettingRepository {
        private val delegate = UserSettingDataSourceImpl()

        override suspend fun initSettings(userId: Uuid): UserSetting? = null
        override suspend fun getSettings(userId: Uuid) = delegate.getSettings(userId)
        override suspend fun updateSettings(
            userId: Uuid,
            allowSearch: Boolean?,
            allowStrangerChat: Boolean?,
            showOnlineStatus: Boolean?,
            profileVisibility: ProfileVisibility?,
            friendRequestPolicy: FriendRequestPolicy?,
            preferences: UserPreference?,
        ): Boolean = delegate.updateSettings(
            userId, allowSearch, allowStrangerChat, showOnlineStatus,
            profileVisibility, friendRequestPolicy, preferences,
        )
    }

    private class NeverCreateSessionsRepository(
        private val delegate: AuthSessionRepository,
    ) : AuthSessionRepository {
        override suspend fun createSession(userId: Uuid, tokenHash: String, expiresAt: Instant): Boolean = false
        override suspend fun findActiveSession(tokenHash: String) = delegate.findActiveSession(tokenHash)
        override suspend fun revokeSession(tokenHash: String) = delegate.revokeSession(tokenHash)
        override suspend fun deleteExpiredSessions(): Int = delegate.deleteExpiredSessions()
    }

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

    @Test
    fun failedSettingsInitializationLeavesNoPartialUserBehind() = runBlocking {
        val brokenService = AuthService(
            userRepository = userRepository,
            userSettingRepository = NullSettingsRepository(),
            authSessionRepository = authSessionRepository,
            hashingService = HashingServiceImpl(),
            tokenService = TokenServiceImpl(),
            appConfig = appConfig,
        )

        val result = brokenService.register(RegisterRequest("erin", "erin@test.local", "longenough1"))

        assertEquals(AuthError.InsertionFailed, result.getError())
        // The user row from the first step must have rolled back with it...
        val users = transaction(TestDb.database) {
            Users.selectAll().where { Users.email eq "erin@test.local" }.count()
        }
        assertEquals(0L, users)
        // ...and no refresh session may point at an account that never happened.
        assertEquals(0L, transaction(TestDb.database) { AuthSessions.selectAll().count() })
    }

    @Test
    fun failedSessionCreationLeavesNoPartialUserBehind() = runBlocking {
        val brokenService = AuthService(
            userRepository = userRepository,
            userSettingRepository = UserSettingDataSourceImpl(),
            authSessionRepository = NeverCreateSessionsRepository(authSessionRepository),
            hashingService = HashingServiceImpl(),
            tokenService = TokenServiceImpl(),
            appConfig = appConfig,
        )

        val result = brokenService.register(RegisterRequest("frank", "frank@test.local", "longenough1"))

        assertEquals(AuthError.InsertionFailed, result.getError())
        val leftovers = transaction(TestDb.database) {
            Users.selectAll().where { Users.email eq "frank@test.local" }.count()
        }
        assertEquals(0L, leftovers)
    }

    @Test
    fun concurrentDuplicateRegistrationsYieldExactlyOneUser() = runBlocking {
        coroutineScope {
            val results = (1..2).map { i ->
                async(Dispatchers.IO) {
                    service.register(RegisterRequest("race-$i", "race@test.local", "longenough1"))
                }
            }.map { it.await() }

            assertEquals(1, results.count { it.get() != null })
            val failures = results.mapNotNull { it.getError() }
            assertTrue(failures.all { it == AuthError.UserAlreadyExists }, "failures were $failures")
        }
        val users = transaction(TestDb.database) {
            Users.selectAll().where { Users.email eq "race@test.local" }.count()
        }
        assertEquals(1L, users)
    }
}
