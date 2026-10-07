package com.github.woodsmarshes.chat.core.network.ktor

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.model.AuthToken
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Expiry of a JWT, decoded from its payload without verifying the signature. */
fun jwtExpiryEpochMs(token: String): Long? = runCatching {
    val payload = token.substringAfter('.').substringBefore('.')
    val json = decodeBase64Url(payload).decodeToString()
    Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
}.getOrNull()

/** Minimal base64url decoder — enough for reading JWT payload claims. */
private fun decodeBase64Url(data: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    var bits = 0
    var value = 0
    val out = ArrayList<Byte>(data.length * 3 / 4)
    for (c in data.trimEnd('=')) {
        val index = alphabet.indexOf(c)
        if (index < 0) continue
        value = (value shl 6) or index
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((value shr bits) and 0xFF).toByte())
        }
    }
    return out.toByteArray()
}

private const val REFRESH_MARGIN_MS = 60_000L

/**
 * Signals that the server rejected a refresh token with HTTP 401 Unauthorized,
 * indicating the refresh session is permanently dead.
 */
class RefreshUnauthorizedException(
    message: String = "Refresh token rejected with 401 Unauthorized",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Single-flight JWT refresher shared by the bearer plugin (REST 401s) and the
 * websocket loop (pre-handshake expiry check).
 *
 * Works against an independent rotating refresh token (A-8): the access JWT
 * lives for one hour, the opaque refresh token for thirty days, and every
 * refresh rotates it server-side.
 *
 * Every refresh attempt binds the `(generation, refreshToken)` snapshot
 * observed when the attempt starts:
 *  - A successful refresh only updates [tokens] if that exact session
 *    generation and refresh token are still current, preventing a late refresh
 *    from overwriting a newer login.
 *  - A 401 refresh rejection only clears [tokens] if that exact session
 *    generation and refresh token are still current, preventing a failed
 *    refresh from an old session from clearing a newly logged-in account or a
 *    same-account re-login.
 */
class TokenRefresher(private val tokens: AuthTokenDataSource) {

    private sealed interface Outcome {
        data class Success(val response: AuthResponse) : Outcome
        /** The server rejected the refresh token: the session is dead. */
        data object Dead : Outcome
        /** Offline, timeout or 5xx — stored tokens are kept untouched. */
        data object Transient : Outcome
    }

    private sealed interface RefreshResolution {
        data class Refreshed(val accessToken: String, val refreshToken: String) : RefreshResolution
        data object DeadOrSuperseded : RefreshResolution
        data object Transient : RefreshResolution
    }

    private class InFlightRefresh(
        val generation: Long,
        val refreshToken: String,
        val deferred: CompletableDeferred<RefreshResolution>,
    )

    private val mutex = Mutex()
    private var inFlight: InFlightRefresh? = null

    /**
     * Test seam for the refresh HTTP call; production uses the default Ktor
     * `/v1/auth/refresh` request with [AuthCircuitBreaker].
     */
    internal var executeRefreshRequest: suspend (client: HttpClient, refreshToken: String) -> AuthResponse =
        { client, refreshToken ->
            client.post(V1.Auth.Refresh()) {
                // Circuit-break the bearer plugin: without this the refresh
                // call would carry (and 401-retry on) the same dead token.
                attributes.put(AuthCircuitBreaker, Unit)
                header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }.body()
        }

    /**
     * The stored access token if still fresh, otherwise a freshly refreshed
     * one. Returns null when the refresh was rejected with 401 (credentials
     * dead — the stored session has been cleared if still current) or when the
     * session generation was superseded while refreshing.
     */
    suspend fun currentOrRefreshed(client: HttpClient): String? =
        currentOrRefreshedWith { refreshToken -> executeRefreshRequest(client, refreshToken) }

    suspend fun currentOrRefreshedWith(
        refreshRequest: suspend (refreshToken: String) -> AuthResponse,
    ): String? {
        val snapshot = tokens.currentSnapshot()
        val jwt = snapshot.jwtToken ?: return null
        val expiry = snapshot.expiryTimestamp
        val now = Clock.System.now().toEpochMilliseconds()
        if (expiry == null || expiry - now > REFRESH_MARGIN_MS) return jwt

        // No refresh token (legacy session from before A-8): keep using the
        // long-lived access token until the server rejects it with a 401.
        val refresh = snapshot.refreshToken
        if (refresh.isNullOrEmpty()) return jwt

        return when (val resolution = refreshForSnapshot(snapshot.generation, refresh, null, refreshRequest)) {
            is RefreshResolution.Refreshed -> resolution.accessToken
            RefreshResolution.DeadOrSuperseded -> null
            RefreshResolution.Transient -> {
                // Keep using the old access token only if the same session
                // generation is still active.
                val latest = tokens.currentSnapshot()
                if (latest.generation == snapshot.generation) jwt else null
            }
        }
    }

    /**
     * Refresh after a REST 401. Null means the credentials are dead or the
     * failing request belonged to a superseded credential generation; the
     * bearer plugin then lets the original request fail with its 401 instead
     * of refreshing or replaying under a different session's credentials.
     *
     * When [requestGeneration] matches the active session and
     * [requestAccessToken] differs from the currently stored access token,
     * an earlier refresh in the same session has already rotated the token and
     * the rotated token pair is returned without issuing a duplicate refresh.
     */
    suspend fun refreshAfter401(
        client: HttpClient,
        requestGeneration: Long? = null,
        requestAccessToken: String? = null,
    ): BearerTokensResult? =
        refreshAfter401With(
            requestGeneration = requestGeneration,
            requestAccessToken = requestAccessToken,
        ) { refreshToken ->
            executeRefreshRequest(client, refreshToken)
        }

    suspend fun refreshAfter401With(
        requestGeneration: Long? = null,
        requestAccessToken: String? = null,
        refreshRequest: suspend (refreshToken: String) -> AuthResponse,
    ): BearerTokensResult? {
        val snapshot = tokens.currentSnapshot()
        if (requestGeneration != null && snapshot.generation != requestGeneration) {
            return null
        }
        val currentJwt = snapshot.jwtToken
        val refresh = snapshot.refreshToken
        if (currentJwt.isNullOrEmpty() || refresh.isNullOrEmpty()) return null

        if (requestAccessToken != null && requestAccessToken != currentJwt) {
            return BearerTokensResult(currentJwt, refresh)
        }

        return when (
            val resolution = refreshForSnapshot(
                expectedGeneration = snapshot.generation,
                expectedRefreshToken = refresh,
                requestAccessToken = requestAccessToken,
                refreshRequest = refreshRequest,
            )
        ) {
            is RefreshResolution.Refreshed -> BearerTokensResult(resolution.accessToken, resolution.refreshToken)
            RefreshResolution.DeadOrSuperseded -> null
            RefreshResolution.Transient -> null
        }
    }

    private suspend fun refreshForSnapshot(
        expectedGeneration: Long,
        expectedRefreshToken: String,
        requestAccessToken: String?,
        refreshRequest: suspend (refreshToken: String) -> AuthResponse,
    ): RefreshResolution {
        val (flight, isOwner) = mutex.withLock {
            val latest = tokens.currentSnapshot()
            if (latest.generation != expectedGeneration) {
                return RefreshResolution.DeadOrSuperseded
            }
            val latestRefresh = latest.refreshToken
            val latestJwt = latest.jwtToken
            if (latestRefresh != expectedRefreshToken ||
                (requestAccessToken != null && !latestJwt.isNullOrEmpty() && latestJwt != requestAccessToken)
            ) {
                return if (!latestJwt.isNullOrEmpty() && !latestRefresh.isNullOrEmpty()) {
                    RefreshResolution.Refreshed(latestJwt, latestRefresh)
                } else {
                    RefreshResolution.DeadOrSuperseded
                }
            }

            val existing = inFlight
            if (existing != null &&
                existing.generation == expectedGeneration &&
                existing.refreshToken == expectedRefreshToken &&
                !existing.deferred.isCompleted
            ) {
                existing to false
            } else {
                val created = InFlightRefresh(
                    generation = expectedGeneration,
                    refreshToken = expectedRefreshToken,
                    deferred = CompletableDeferred(),
                )
                inFlight = created
                created to true
            }
        }

        if (!isOwner) {
            return flight.deferred.await()
        }

        return try {
            val outcome = performRefreshRequest(expectedRefreshToken, refreshRequest)
            val resolution = withContext(NonCancellable) {
                when (outcome) {
                    is Outcome.Success -> {
                        val newAccess = outcome.response.accessToken
                        val newRefresh = outcome.response.refreshToken ?: expectedRefreshToken
                        val updated = tokens.updateTokenIfCurrent(
                            expectedGeneration = expectedGeneration,
                            expectedRefreshToken = expectedRefreshToken,
                            token = AuthToken(
                                jwtToken = newAccess,
                                refreshToken = newRefresh,
                                expiryTimestamp = jwtExpiryEpochMs(newAccess),
                            ),
                        )
                        if (updated) {
                            RefreshResolution.Refreshed(newAccess, newRefresh)
                        } else {
                            RefreshResolution.DeadOrSuperseded
                        }
                    }
                    Outcome.Dead -> {
                        tokens.clearIfCurrent(
                            expectedGeneration = expectedGeneration,
                            expectedRefreshToken = expectedRefreshToken,
                        )
                        RefreshResolution.DeadOrSuperseded
                    }
                    Outcome.Transient -> RefreshResolution.Transient
                }
            }
            flight.deferred.complete(resolution)
            resolution
        } catch (t: Throwable) {
            flight.deferred.completeExceptionally(t)
            throw t
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === flight) {
                        inFlight = null
                    }
                }
            }
        }
    }

    private suspend fun performRefreshRequest(
        refreshToken: String,
        refreshRequest: suspend (refreshToken: String) -> AuthResponse,
    ): Outcome {
        return try {
            val response = refreshRequest(refreshToken)
            Outcome.Success(response)
        } catch (e: RefreshUnauthorizedException) {
            Outcome.Dead
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized) Outcome.Dead else Outcome.Transient
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Transient
        }
    }
}

/** Platform-neutral carrier for the bearer plugin's refreshed token pair. */
data class BearerTokensResult(val accessToken: String, val refreshToken: String)
