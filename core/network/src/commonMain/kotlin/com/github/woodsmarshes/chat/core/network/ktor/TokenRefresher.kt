package com.github.woodsmarshes.chat.core.network.ktor

import com.github.woodsmarshes.chat.core.datastore.AuthTokenDataSource
import com.github.woodsmarshes.chat.core.network.api.V1
import com.github.woodsmarshes.chat.core.network.dto.auth.AuthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
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
 * Failure semantics are deliberate: only a 401 from the refresh call proves
 * the credentials are dead — that clears the stored session so the logged-out
 * state tears everything down. Any other failure (offline, timeout, 5xx)
 * keeps the current token, which the server may still accept.
 */
class TokenRefresher(private val tokens: AuthTokenDataSource) {

    private val mutex = Mutex()

    /**
     * The stored token if still fresh, otherwise a refreshed one. Returns
     * null only when the refresh was rejected with 401 (credentials dead).
     */
    suspend fun currentOrRefreshed(client: HttpClient): String? = mutex.withLock {
        val jwt = tokens.jwtToken.first() ?: return null
        val expiry = tokens.expiryTimestamp.first()
        val now = Clock.System.now().toEpochMilliseconds()
        if (expiry == null || expiry - now > REFRESH_MARGIN_MS) return jwt
        refreshLocked(client, jwt)
    }

    /** Refresh after a 401; null means the credentials are dead. */
    suspend fun refreshAfter401(client: HttpClient): Pair<String, String>? = mutex.withLock {
        val jwt = tokens.jwtToken.first() ?: return null
        refreshLocked(client, jwt)?.let { Pair(it, it) }
    }

    /**
     * MUST be called under [mutex]. The refresh endpoint authenticates with
     * the outgoing (now expired) JWT, which the server exchanges for a fresh
     * one — the sliding-renewal contract.
     */
    private suspend fun refreshLocked(client: HttpClient, current: String): String? {
        return try {
            val response: AuthResponse = client.post(V1.Auth.Refresh()) {
                // Circuit-break the bearer plugin: without this the refresh
                // call would carry (and 401-retry on) the same dead token.
                attributes.put(AuthCircuitBreaker, Unit)
                header(HttpHeaders.Authorization, "Bearer $current")
            }.body()
            tokens.setToken(
                com.github.woodsmarshes.chat.core.model.AuthToken(
                    jwtToken = response.accessToken,
                    refreshToken = null,
                    expiryTimestamp = jwtExpiryEpochMs(response.accessToken),
                )
            )
            response.accessToken
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.Unauthorized) {
                tokens.clearAuthToken()
                null
            } else {
                current // transient server issue: keep the old credentials
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            current // offline/timeout: the server may still accept the old token
        }
    }
}
