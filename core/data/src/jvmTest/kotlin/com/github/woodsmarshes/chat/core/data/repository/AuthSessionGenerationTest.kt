package com.github.woodsmarshes.chat.core.data.repository

import com.github.michaelbull.result.Ok
import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.datastore.di.createPreferenceDataSources
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.api.rest.AuthApi
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.ktor.BearerTokensResult
import com.github.woodsmarshes.chat.core.network.ktor.RefreshUnauthorizedException
import com.github.woodsmarshes.chat.core.network.ktor.TokenRefresher
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies credential generation and validity protection across
 * [AuthRepositoryImpl], [TokenRefresher], [AuthTokenDataSource], and
 * [UserSettingDataSource]:
 *  - Stale login responses arriving after `logout()` (or a newer login) are
 *    rejected, do not write user/DB/token state, do not reactivate the
 *    session, and best-effort revoke the unused server refresh token.
 *  - A refresh request that succeeds after a new login has committed does not
 *    overwrite the new session's credentials.
 *  - A refresh request that fails with 401 after a same-account re-login (or
 *    cross-account login) does not clear the new session's credentials, while
 *    a 401 refresh for the still-current session clears both token and user.
 *  - `logout()` captures the outgoing session's refresh token and clears local
 *    state before awaiting server revocation, so a new login completing while
 *    server revocation is in flight is never cleared or revoked.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class AuthSessionGenerationTest {

    private val user1Id = Uuid.parse("00000000-0000-0000-0000-000000000101")
    private val user2Id = Uuid.parse("00000000-0000-0000-0000-000000000202")
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val user1 = User(
        id = user1Id,
        username = "alice",
        email = "alice@example.com",
        displayName = "Alice",
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private val user2 = User(
        id = user2Id,
        username = "bob",
        email = "bob@example.com",
        displayName = "Bob",
        avatarUrl = null,
        bio = null,
        createdAt = now,
        updatedAt = now,
        deletedAt = null,
    )

    private fun makeJwt(label: String, expEpochSeconds: Long): String {
        val header = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"sub":"$label","exp":$expEpochSeconds}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private fun freshJwt(label: String): String {
        val expSec = (Clock.System.now().toEpochMilliseconds() / 1000L) + 3600L
        return makeJwt(label, expSec)
    }

    private fun expiredJwt(label: String): String {
        val expSec = (Clock.System.now().toEpochMilliseconds() / 1000L) - 60L
        return makeJwt(label, expSec)
    }

    private class Harness(
        val storeScope: CoroutineScope,
        val authTokenDataSource: AuthTokenDataSource,
        val userSettingDataSource: UserSettingDataSource,
        val authApi: AuthApi,
        val authRepository: AuthRepositoryImpl,
        val tokenRefresher: TokenRefresher,
        private val tempDir: java.nio.file.Path,
    ) {
        fun close() {
            storeScope.cancel()
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    private fun createHarness(dispatcher: kotlinx.coroutines.CoroutineDispatcher): Harness {
        val tempDir = Files.createTempDirectory("auth-gen-test-")
        val storeFile = tempDir.resolve("test.preferences_pb").toFile()
        val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val (authTokenDataSource, userSettingDataSource) = createPreferenceDataSources(
            filePath = storeFile.absolutePath,
            scope = storeScope,
            json = ProjectJson,
        )
        val authApi = mockk<AuthApi>(relaxed = true)
        val tokenRefresher = TokenRefresher(authTokenDataSource)
        val authRepository = AuthRepositoryImpl(
            authTokenDataSource = authTokenDataSource,
            userSettingDataSource = userSettingDataSource,
            authApi = authApi,
        )
        return Harness(
            storeScope = storeScope,
            authTokenDataSource = authTokenDataSource,
            userSettingDataSource = userSettingDataSource,
            authApi = authApi,
            authRepository = authRepository,
            tokenRefresher = tokenRefresher,
            tempDir = tempDir,
        )
    }

    @Test
    fun staleLoginResponseAfterLogoutDoesNotReactivateSessionOrWriteDatabase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = createHarness(dispatcher)
        try {
            val loginEntered = CompletableDeferred<Unit>()
            val loginGate = CompletableDeferred<AuthResponse>()
            val revokedRefreshTokens = mutableListOf<String>()

            coEvery { h.authApi.login("alice@example.com", "secret") } coAnswers {
                loginEntered.complete(Unit)
                loginGate.await()
            }
            coEvery { h.authApi.logout(any()) } coAnswers {
                revokedRefreshTokens += firstArg<String>()
            }

            // 1. Start old login request; wait until it is in flight on the network.
            val loginDeferred = async(dispatcher) {
                h.authRepository.login("alice@example.com", "secret")
            }
            runCurrent()
            loginEntered.await()

            // 2. Logout occurs while the login request is still in flight.
            h.authRepository.logout()
            runCurrent()

            // 3. Old login response finally arrives.
            val staleJwt = freshJwt("alice-stale")
            loginGate.complete(
                AuthResponse(
                    accessToken = staleJwt,
                    refreshToken = "refresh-alice-stale",
                    user = user1,
                )
            )
            runCurrent()

            val loginResult = loginDeferred.await()
            assertTrue(loginResult.isErr, "superseded login after logout must return Err")
            assertFalse(h.authRepository.observeIsLoggedIn().first(), "session must remain logged out")
            assertNull(h.authTokenDataSource.jwtToken.first(), "stale JWT must not be persisted")
            assertNull(h.authTokenDataSource.refreshToken.first(), "stale refresh token must not be persisted")
            assertNull(h.userSettingDataSource.user.first(), "stale user must not be persisted")
            assertEquals(
                listOf("refresh-alice-stale"),
                revokedRefreshTokens,
                "late login response after logout must have its server refresh session revoked",
            )
        } finally {
            h.close()
        }
    }

    @Test
    fun staleRefreshSuccessAfterNewAccountLoginDoesNotOverwriteNewCredentials() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = createHarness(dispatcher)
        try {
            val aliceJwt1 = expiredJwt("alice-v1")
            val bobJwt1 = freshJwt("bob-v1")
            val aliceJwt2 = freshJwt("alice-v2-refreshed")

            coEvery { h.authApi.login("alice@example.com", "pw1") } returns AuthResponse(
                accessToken = aliceJwt1,
                refreshToken = "refresh-alice-1",
                user = user1,
            )
            coEvery { h.authApi.login("bob@example.com", "pw2") } returns AuthResponse(
                accessToken = bobJwt1,
                refreshToken = "refresh-bob-1",
                user = user2,
            )

            // 1. Alice logs in first.
            assertEquals(Ok(user1), h.authRepository.login("alice@example.com", "pw1"))
            runCurrent()
            val aliceGen = h.authTokenDataSource.currentSnapshot().generation

            val refreshEntered = CompletableDeferred<Unit>()
            val refreshGate = CompletableDeferred<AuthResponse>()

            // 2. Old refresh request for Alice starts and suspends in flight.
            val refreshDeferred = async(dispatcher) {
                h.tokenRefresher.refreshAfter401With { refreshToken ->
                    assertEquals("refresh-alice-1", refreshToken)
                    refreshEntered.complete(Unit)
                    refreshGate.await()
                }
            }
            runCurrent()
            refreshEntered.await()

            // 3. Bob logs in while Alice's refresh is still in flight.
            assertEquals(Ok(user2), h.authRepository.login("bob@example.com", "pw2"))
            runCurrent()
            val bobGen = h.authTokenDataSource.currentSnapshot().generation
            assertNotEquals(aliceGen, bobGen)
            assertEquals(bobJwt1, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-bob-1", h.authTokenDataSource.refreshToken.first())
            assertEquals(user2, h.userSettingDataSource.user.first())

            // Verify currentOrRefreshed for Bob is not blocked by Alice's hung refresh.
            assertEquals(
                bobJwt1,
                h.tokenRefresher.currentOrRefreshedWith { error("must not refresh fresh token") },
            )

            // 4. Alice's old refresh request succeeds and returns late.
            refreshGate.complete(
                AuthResponse(
                    accessToken = aliceJwt2,
                    refreshToken = "refresh-alice-2",
                    user = user1,
                )
            )
            runCurrent()

            val refreshResult: BearerTokensResult? = refreshDeferred.await()
            assertNull(refreshResult, "superseded refresh must return null instead of stale tokens")
            assertEquals(bobJwt1, h.authTokenDataSource.jwtToken.first(), "Bob's JWT must not be overwritten")
            assertEquals("refresh-bob-1", h.authTokenDataSource.refreshToken.first(), "Bob's refresh token must not be overwritten")
            assertEquals(user2, h.userSettingDataSource.user.first(), "Bob's user must remain active")
            assertEquals(bobGen, h.authTokenDataSource.currentSnapshot().generation)
            assertTrue(h.authRepository.observeIsLoggedIn().first())
        } finally {
            h.close()
        }
    }

    @Test
    fun staleRefreshFailureAfterSameAccountReloginDoesNotClearNewSessionWhileActiveSession401Clears() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = createHarness(dispatcher)
        try {
            val aliceSession1Jwt = expiredJwt("alice-s1")
            val aliceSession2Jwt = freshJwt("alice-s2")

            var loginCall = 0
            coEvery { h.authApi.login("alice@example.com", "pw1") } coAnswers {
                loginCall++
                if (loginCall == 1) {
                    AuthResponse(
                        accessToken = aliceSession1Jwt,
                        refreshToken = "refresh-alice-s1",
                        user = user1,
                    )
                } else {
                    AuthResponse(
                        accessToken = aliceSession2Jwt,
                        refreshToken = "refresh-alice-s2",
                        user = user1,
                    )
                }
            }

            // 1. Alice logs in (Session 1).
            assertEquals(Ok(user1), h.authRepository.login("alice@example.com", "pw1"))
            runCurrent()
            val session1Gen = h.authTokenDataSource.currentSnapshot().generation

            val refreshEntered = CompletableDeferred<Unit>()
            val allowRefreshFail = CompletableDeferred<Unit>()

            // 2. Old refresh request for Alice's Session 1 starts (via currentOrRefreshed on expired JWT).
            val oldRefreshDeferred = async(dispatcher) {
                h.tokenRefresher.currentOrRefreshedWith { refreshToken ->
                    assertEquals("refresh-alice-s1", refreshToken)
                    refreshEntered.complete(Unit)
                    allowRefreshFail.await()
                    throw RefreshUnauthorizedException()
                }
            }
            runCurrent()
            refreshEntered.await()

            // 3. Same account (user1, identical userId) logs in again -> Session 2.
            assertEquals(Ok(user1), h.authRepository.login("alice@example.com", "pw1"))
            runCurrent()
            val session2Gen = h.authTokenDataSource.currentSnapshot().generation
            assertNotEquals(
                session1Gen,
                session2Gen,
                "same-account re-login must advance credential session generation",
            )
            assertEquals(aliceSession2Jwt, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-alice-s2", h.authTokenDataSource.refreshToken.first())

            // 4. Old refresh for Session 1 fails with 401 and returns.
            allowRefreshFail.complete(Unit)
            runCurrent()

            assertNull(oldRefreshDeferred.await())
            assertTrue(
                h.authRepository.observeIsLoggedIn().first(),
                "failed refresh from Session 1 must NOT clear same-account Session 2",
            )
            assertEquals(aliceSession2Jwt, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-alice-s2", h.authTokenDataSource.refreshToken.first())
            assertEquals(user1, h.userSettingDataSource.user.first())
            assertEquals(session2Gen, h.authTokenDataSource.currentSnapshot().generation)

            // 5. Now if a 401 refresh happens for the CURRENT Session 2 itself, it MUST clear the session.
            val currentSessionRefreshResult = h.tokenRefresher.refreshAfter401With { refreshToken ->
                assertEquals("refresh-alice-s2", refreshToken)
                throw RefreshUnauthorizedException()
            }
            runCurrent()
            assertNull(currentSessionRefreshResult)
            assertFalse(
                h.authRepository.observeIsLoggedIn().first(),
                "401 on the current session's refresh token must clear the session",
            )
            assertNull(h.authTokenDataSource.jwtToken.first())
            assertNull(h.authTokenDataSource.refreshToken.first())
            assertNull(h.userSettingDataSource.user.first())
        } finally {
            h.close()
        }
    }

    @Test
    fun logoutRevokesCapturedRefreshTokenAndDoesNotClobberNewLoginDuringServerRevocation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val h = createHarness(dispatcher)
        try {
            val aliceJwt = freshJwt("alice")
            val bobJwt = freshJwt("bob")
            val logoutRequestEntered = CompletableDeferred<String>()
            val allowLogoutRequestFinish = CompletableDeferred<Unit>()
            val serverRevokedTokens = mutableListOf<String>()

            coEvery { h.authApi.login("alice@example.com", "pw1") } returns AuthResponse(
                accessToken = aliceJwt,
                refreshToken = "refresh-alice",
                user = user1,
            )
            coEvery { h.authApi.login("bob@example.com", "pw2") } returns AuthResponse(
                accessToken = bobJwt,
                refreshToken = "refresh-bob",
                user = user2,
            )
            coEvery { h.authApi.logout(any()) } coAnswers {
                val token = firstArg<String>()
                serverRevokedTokens += token
                logoutRequestEntered.complete(token)
                allowLogoutRequestFinish.await()
            }

            // 1. Alice is logged in.
            assertEquals(Ok(user1), h.authRepository.login("alice@example.com", "pw1"))
            runCurrent()
            assertTrue(h.authRepository.observeIsLoggedIn().first())

            // 2. Alice initiates logout; local state clears immediately while server revocation suspends.
            val logoutJob: Job = async(dispatcher) {
                h.authRepository.logout()
            }
            runCurrent()
            assertEquals("refresh-alice", logoutRequestEntered.await())
            assertFalse(
                h.authRepository.observeIsLoggedIn().first(),
                "local credentials must be cleared before awaiting server revocation",
            )

            // 3. While Alice's server logout call is still in flight, Bob logs in.
            assertEquals(Ok(user2), h.authRepository.login("bob@example.com", "pw2"))
            runCurrent()
            assertTrue(h.authRepository.observeIsLoggedIn().first())
            assertEquals(bobJwt, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-bob", h.authTokenDataSource.refreshToken.first())
            assertEquals(user2, h.userSettingDataSource.user.first())

            // 4. Alice's server logout call finishes; Bob's session must remain untouched.
            allowLogoutRequestFinish.complete(Unit)
            runCurrent()
            logoutJob.join()

            assertEquals(listOf("refresh-alice"), serverRevokedTokens)
            assertTrue(
                h.authRepository.observeIsLoggedIn().first(),
                "completing Alice's server logout must not clear Bob's newly logged-in session",
            )
            assertEquals(bobJwt, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-bob", h.authTokenDataSource.refreshToken.first())
            assertEquals(user2, h.userSettingDataSource.user.first())
        } finally {
            h.close()
        }
    }
}
