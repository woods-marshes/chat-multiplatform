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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
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
 * Single-flight JWT refresher shared by the bearer plugin (REST 401s) and the
 * websocket loop (pre-handshake expiry check).
 *
 * Works against an independent rotating refresh token (A-8): the access JWT
 * lives for one hour, the opaque refresh token for thirty days, and every
 * refresh rotates it server-side.
 */
class TokenRefresher(private val tokens: AuthTokenDataSource) {

    private sealed interface Outcome {
        data class Success(val response: AuthResponse) : Outcome
        /** The server rejected the refresh token: the session is dead. */
        data object Dead : Outcome
        /** Offline, timeout or 5xx — stored tokens are kept untouched. */
        data object Transient : Outcome
    }

    private val mutex = Mutex()

    /**
     * The stored access token if still fresh, otherwise a freshly refreshed
     * one. Returns null only when the refresh was rejected with 401
     * (credentials dead — the stored session has been cleared).
     */
    suspend fun currentOrRefreshed(client: HttpClient): String? = mutex.withLock {
        val jwt = tokens.jwtToken.first() ?: return null
        val expiry = tokens.expiryTimestamp.first()
        val now = Clock.System.now().toEpochMilliseconds()
        if (expiry == null || expiry - now > REFRESH_MARGIN_MS) return jwt

        // No refresh token (legacy session from before A-8): keep using the
        // long-lived access token until the server rejects it with a 401.
        val refresh = tokens.refreshToken.first() ?: return jwt

        when (val outcome = refreshLocked(client, refresh)) {
            is Outcome.Success -> {
                persist(outcome.response)
                outcome.response.accessToken
            }
            Outcome.Dead -> null
            Outcome.Transient -> jwt // the old token may still be accepted
        }
    }

    /**
     * Refresh after a REST 401. Null means the credentials are dead; the
     * bearer plugin then lets the original request fail with its 401.
     */
    suspend fun refreshAfter401(client: HttpClient): BearerTokensResult? = mutex.withLock {
        val refresh = tokens.refreshToken.first() ?: return null
        when (val outcome = refreshLocked(client, refresh)) {
            is Outcome.Success -> {
                persist(outcome.response)
                BearerTokensResult(outcome.response.accessToken, outcome.response.refreshToken ?: refresh)
            }
            Outcome.Dead -> null
            Outcome.Transient -> null
        }
    }

    /** MUST be called under [mutex]. */
    private suspend fun refreshLocked(client: HttpClient, refreshToken: String): Outcome {
        return try {
            val response: AuthResponse = client.post(V1.Auth.Refresh()) {
                // Circuit-break the bearer plugin: without this the refresh
                // call would carry (and 401-retry on) the same dead token.
                attributes.put(AuthCircuitBreaker, Unit)
                header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }.body()
            Outcome.Success(response)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized) Outcome.Dead else Outcome.Transient
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Transient
        }
    }

    private suspend fun persist(response: AuthResponse) {
        tokens.setToken(
            AuthToken(
                jwtToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiryTimestamp = jwtExpiryEpochMs(response.accessToken),
            )
        )
    }
}

/** Platform-neutral carrier for the bearer plugin's refreshed token pair. */
data class BearerTokensResult(val accessToken: String, val refreshToken: String)
