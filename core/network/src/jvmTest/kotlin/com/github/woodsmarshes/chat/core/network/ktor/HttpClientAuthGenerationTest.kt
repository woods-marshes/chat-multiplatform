package com.github.woodsmarshes.chat.core.network.ktor

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.datastore.UserSettingDataSource
import com.github.woodsmarshes.chat.core.datastore.di.createPreferenceDataSources
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.model.User
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.core.network.serialization.ProjectProtobuf
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToByteArray
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Verifies the real Ktor [createHttpClient] + `Auth` `bearer` plugin pipeline:
 *  - An in-flight protected business request sent by User A that receives 401
 *    after User B has logged in does NOT trigger a refresh of User B's token
 *    and does NOT replay User A's request with User B's `Authorization` header.
 *  - Concurrent 401s within the same credential generation refresh only once
 *    and replay subsequent stale-token requests from that generation with the
 *    already-rotated token.
 *  - A 401 during `/v1/auth/refresh` clears both token and user settings in
 *    DataStore even when `AuthRepositoryImpl` has never been instantiated.
 */
@OptIn(ExperimentalEncodingApi::class, InternalAPI::class)
class HttpClientAuthGenerationTest {

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

    private fun freshJwt(label: String): String {
        val expSec = (Clock.System.now().toEpochMilliseconds() / 1000L) + 3600L
        val header = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode("""{"sub":"$label","exp":$expSec}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private class RecordedRequest(
        val path: String,
        val authorization: String?,
    )

    private class ScriptedHttpEngine(
        override val dispatcher: CoroutineDispatcher,
        private val handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, ByteArray>,
    ) : HttpClientEngineBase("test-scripted") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf(HttpTimeoutCapability)

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val reqCallContext = callContext()
            val (status, bytes) = handler(data)
            return HttpResponseData(
                statusCode = status,
                requestTime = GMTDate(),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.ProtoBuf.toString())
                    if (status == HttpStatusCode.Unauthorized) {
                        append(HttpHeaders.WWWAuthenticate, "Bearer")
                    }
                },
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel(bytes),
                callContext = reqCallContext,
            )
        }
    }

    private class Harness(
        val storeScope: CoroutineScope,
        val authTokenDataSource: AuthTokenDataSource,
        val userSettingDataSource: UserSettingDataSource,
        val tokenRefresher: TokenRefresher,
        val client: HttpClient,
        private val tempDir: java.nio.file.Path,
    ) {
        suspend fun commitLogin(user: User, accessToken: String, refreshToken: String) {
            val epoch = authTokenDataSource.beginAuthRequest()
            authTokenDataSource.withCredentialLock {
                authTokenDataSource.commitSessionLocked(
                    requestEpoch = epoch,
                    user = user,
                    token = AuthToken(
                        jwtToken = accessToken,
                        refreshToken = refreshToken,
                        expiryTimestamp = jwtExpiryEpochMs(accessToken),
                    ),
                )
            }
        }

        fun close() {
            client.close()
            storeScope.cancel()
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    private fun createHarness(
        dispatcher: CoroutineDispatcher,
        handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, ByteArray>,
    ): Harness {
        val tempDir = Files.createTempDirectory("http-auth-gen-test-")
        val storeFile = tempDir.resolve("test.preferences_pb").toFile()
        val storeScope = CoroutineScope(SupervisorJob() + dispatcher)
        val (authTokenDataSource, userSettingDataSource) = createPreferenceDataSources(
            filePath = storeFile.absolutePath,
            scope = storeScope,
            json = ProjectJson,
        )
        val tokenRefresher = TokenRefresher(authTokenDataSource)
        val engine = ScriptedHttpEngine(dispatcher, handler)
        val client = createHttpClient(
            httpClientEngine = engine,
            config = NetworkConfig.forDevelopment(),
            authTokenDataSource = authTokenDataSource,
            tokenRefresher = tokenRefresher,
            httpEventBus = HttpEventBusImpl(),
        )
        return Harness(
            storeScope = storeScope,
            authTokenDataSource = authTokenDataSource,
            userSettingDataSource = userSettingDataSource,
            tokenRefresher = tokenRefresher,
            client = client,
            tempDir = tempDir,
        )
    }

    @Test
    fun inFlightRequestReturning401AfterAccountSwitchDoesNotRefreshOrReplayAsNewUser() = runBlocking {
        val dispatcher = Dispatchers.Default
        val recordedRequests = mutableListOf<RecordedRequest>()
        val firstRequestInFlight = CompletableDeferred<Unit>()
        val releaseFirstRequestAs401 = CompletableDeferred<Unit>()

        val h = createHarness(dispatcher) { req ->
            val path = req.url.encodedPath
            val auth = req.headers[HttpHeaders.Authorization]
            synchronized(recordedRequests) {
                recordedRequests += RecordedRequest(path, auth)
            }
            when (path) {
                "/v1/protected-resource" -> {
                    firstRequestInFlight.complete(Unit)
                    releaseFirstRequestAs401.await()
                    HttpStatusCode.Unauthorized to ByteArray(0)
                }
                "/v1/auth/refresh" -> {
                    error("Must not call /v1/auth/refresh for User B when User A's stale request gets 401")
                }
                else -> HttpStatusCode.OK to ByteArray(0)
            }
        }

        try {
            val aliceJwt = freshJwt("alice-1")
            val bobJwt = freshJwt("bob-1")

            // 1. Alice is logged in (generation 1).
            h.commitLogin(user1, aliceJwt, "refresh-alice-1")
            assertEquals(1L, h.authTokenDataSource.currentSnapshot().generation)

            // 2. Alice sends a business request that suspends in flight.
            val aliceRequestDeferred = async(dispatcher) {
                runCatching { h.client.get("/v1/protected-resource") }
            }
            firstRequestInFlight.await()
            synchronized(recordedRequests) {
                assertEquals(1, recordedRequests.size)
                assertEquals("Bearer $aliceJwt", recordedRequests.single().authorization)
            }

            // 3. While Alice's request is suspended, Bob logs in (generation 2).
            h.commitLogin(user2, bobJwt, "refresh-bob-1")
            assertEquals(2L, h.authTokenDataSource.currentSnapshot().generation)

            // 4. Alice's in-flight request now receives 401 Unauthorized.
            releaseFirstRequestAs401.complete(Unit)

            val result = aliceRequestDeferred.await()
            val ex = assertFailsWith<ClientRequestException> {
                result.getOrThrow()
            }
            assertEquals(HttpStatusCode.Unauthorized, ex.response.status)

            // Must NOT have refreshed Bob's token or replayed Alice's request with Bob's Authorization header.
            synchronized(recordedRequests) {
                assertEquals(
                    1,
                    recordedRequests.size,
                    "stale 401 must not trigger /v1/auth/refresh or replay the request as Bob: $recordedRequests",
                )
            }
            assertEquals(bobJwt, h.authTokenDataSource.jwtToken.first())
            assertEquals("refresh-bob-1", h.authTokenDataSource.refreshToken.first())
            assertEquals(user2, h.userSettingDataSource.user.first())
            assertTrue(h.authTokenDataSource.sessionSnapshot.first().isLoggedIn)
        } finally {
            h.close()
        }
    }

    @Test
    fun sameGeneration401RefreshesOnceAndReusesRotatedTokenForConcurrent401s() = runBlocking {
        val dispatcher = Dispatchers.Default
        val aliceJwt1 = freshJwt("alice-v1")
        val aliceJwt2 = freshJwt("alice-v2")
        val bothInitialSent = CompletableDeferred<Unit>()
        val releaseFirst401 = CompletableDeferred<Unit>()
        val releaseSecond401 = CompletableDeferred<Unit>()
        val lock = Any()
        var initialResourceCalls = 0
        var refreshCalls = 0
        val replayAuthorizations = mutableListOf<String?>()

        val h = createHarness(dispatcher) { req ->
            val path = req.url.encodedPath
            val auth = req.headers[HttpHeaders.Authorization]
            when (path) {
                "/v1/protected-resource" -> {
                    if (auth == "Bearer $aliceJwt1") {
                        val callIndex = synchronized(lock) { ++initialResourceCalls }
                        if (callIndex == 2) {
                            bothInitialSent.complete(Unit)
                        }
                        if (callIndex == 1) {
                            releaseFirst401.await()
                        } else {
                            releaseSecond401.await()
                        }
                        HttpStatusCode.Unauthorized to ByteArray(0)
                    } else {
                        synchronized(lock) {
                            replayAuthorizations += auth
                        }
                        HttpStatusCode.OK to ByteArray(0)
                    }
                }
                "/v1/auth/refresh" -> {
                    synchronized(lock) { refreshCalls++ }
                    assertEquals("Bearer refresh-alice-1", auth)
                    val bodyBytes = ProjectProtobuf.encodeToByteArray(
                        AuthResponse(
                            accessToken = aliceJwt2,
                            refreshToken = "refresh-alice-2",
                            user = user1,
                        )
                    )
                    HttpStatusCode.OK to bodyBytes
                }
                else -> HttpStatusCode.NotFound to ByteArray(0)
            }
        }

        try {
            h.commitLogin(user1, aliceJwt1, "refresh-alice-1")

            val req1 = async(dispatcher) { h.client.get("/v1/protected-resource") }
            val req2 = async(dispatcher) { h.client.get("/v1/protected-resource") }
            bothInitialSent.await()

            // First request gets 401 -> refreshes token to aliceJwt2 and replays req1.
            releaseFirst401.complete(Unit)
            assertEquals(HttpStatusCode.OK, req1.await().status)
            assertEquals(1, synchronized(lock) { refreshCalls })

            // Second request (sent with aliceJwt1 in the same generation) now gets 401 ->
            // reuses aliceJwt2 without issuing a second /v1/auth/refresh call.
            releaseSecond401.complete(Unit)
            assertEquals(HttpStatusCode.OK, req2.await().status)

            synchronized(lock) {
                assertEquals(1, refreshCalls, "same-generation rotated token must be reused without a second refresh")
                assertEquals<List<String?>>(listOf("Bearer $aliceJwt2", "Bearer $aliceJwt2"), replayAuthorizations)
            }
        } finally {
            releaseFirst401.complete(Unit)
            releaseSecond401.complete(Unit)
            h.close()
        }
    }

    @Test
    fun fourOhOneRefreshFailureClearsBothTokenAndUserWithoutAuthRepositoryInstance() = runBlocking {
        val dispatcher = Dispatchers.Default
        val h = createHarness(dispatcher) { req ->
            when (req.url.encodedPath) {
                "/v1/protected-resource" -> HttpStatusCode.Unauthorized to ByteArray(0)
                "/v1/auth/refresh" -> HttpStatusCode.Unauthorized to ByteArray(0)
                else -> HttpStatusCode.OK to ByteArray(0)
            }
        }

        try {
            val aliceJwt = freshJwt("alice")
            h.commitLogin(user1, aliceJwt, "refresh-alice")
            assertTrue(h.authTokenDataSource.sessionSnapshot.first().isLoggedIn)

            assertFailsWith<ClientRequestException> {
                h.client.get("/v1/protected-resource")
            }

            val snapAfter401 = h.authTokenDataSource.sessionSnapshot.first()
            assertFalse(snapAfter401.isLoggedIn, "401 on refresh must clear session")
            assertNull(snapAfter401.jwtToken)
            assertNull(snapAfter401.refreshToken)
            assertNull(snapAfter401.user, "user setting must be cleared atomically even without AuthRepositoryImpl")
            assertNull(h.userSettingDataSource.user.first())
        } finally {
            h.close()
        }
    }

    @Test
    fun requestBoundToUserAPausedBeforeLoadTokensAndResumedAfterUserBLoginIsRefusedInsteadOfSendingAsUserB() = runBlocking {
        val dispatcher = Dispatchers.Default
        val recordedRequests = mutableListOf<RecordedRequest>()
        val boundToAliceAndPaused = CompletableDeferred<Unit>()
        val resumeRequestPreparation = CompletableDeferred<Unit>()

        val h = createHarness(dispatcher) { req ->
            synchronized(recordedRequests) {
                recordedRequests += RecordedRequest(
                    path = req.url.encodedPath,
                    authorization = req.headers[HttpHeaders.Authorization],
                )
            }
            HttpStatusCode.OK to ByteArray(0)
        }

        try {
            val aliceJwt = freshJwt("alice-1")
            val bobJwt = freshJwt("bob-1")

            // 1. Alice is logged in (generation 1).
            h.commitLogin(user1, aliceJwt, "refresh-alice-1")
            assertEquals(1L, h.authTokenDataSource.currentSnapshot().generation)

            // 2. Start a request under Alice; pause immediately after RequestAuthBindingPlugin
            //    binds Alice's generation (1) and before Auth's loadTokens selects Authorization.
            val staleRequestDeferred = async(dispatcher) {
                runCatching {
                    h.client.get("/v1/protected-resource") {
                        attributes.put(
                            OnRequestAuthBoundHookKey,
                            RequestAuthBoundHook {
                                boundToAliceAndPaused.complete(Unit)
                                resumeRequestPreparation.await()
                            },
                        )
                    }
                }
            }
            boundToAliceAndPaused.await()

            // 3. Bob logs in (generation 2) while Alice's request is still in preparation.
            h.commitLogin(user2, bobJwt, "refresh-bob-1")
            assertEquals(2L, h.authTokenDataSource.currentSnapshot().generation)

            // 4. Resume Alice's request preparation; it must be refused before transmission
            //    and must NEVER be sent with Bob's Authorization header.
            resumeRequestPreparation.complete(Unit)

            val result = staleRequestDeferred.await()
            val ex = assertFailsWith<StaleRequestGenerationException> {
                result.getOrThrow()
            }
            assertEquals(1L, ex.requestGeneration)
            assertEquals(2L, ex.currentGeneration)

            synchronized(recordedRequests) {
                assertTrue(
                    recordedRequests.isEmpty(),
                    "request bound to Alice must not be transmitted after Bob logs in: $recordedRequests",
                )
            }

            // 5. A new request created under Bob's session succeeds with Bob's Authorization header.
            val bobResponse = h.client.get("/v1/protected-resource")
            assertEquals(HttpStatusCode.OK, bobResponse.status)
            synchronized(recordedRequests) {
                assertEquals(1, recordedRequests.size)
                assertEquals("Bearer $bobJwt", recordedRequests.single().authorization)
            }
        } finally {
            resumeRequestPreparation.complete(Unit)
            h.close()
        }
    }
}
