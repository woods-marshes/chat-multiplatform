package com.github.woodsmarshes.chat.core.network.ktor

import com.github.woodsmarshes.chat.core.datastore.ServerConfigStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Parses a user-entered server address into a [NetworkConfig].
 *
 * Accepted forms:
 *  - `host` — bare host defaults to `https://host` (port 443)
 *  - `host:port` — TLS is inferred: port 443 means https, any other port http
 *  - `http://host[:port]` / `https://host[:port]` — scheme wins over inference
 *  - IPv6 hosts must be bracketed: `[::1]` or `[::1]:9051`
 *
 * A single trailing `/` is tolerated; any other path, query, fragment, or
 * userinfo is rejected because `V1` routes are absolute paths and a prefix
 * would produce 404s.
 */
fun parseServerAddress(raw: String): Result<NetworkConfig> = runCatching {
    var rest = raw.trim()
    require(rest.isNotEmpty()) { "Server address is empty" }

    var schemeTls: Boolean? = null
    val schemeEnd = rest.indexOf("://")
    if (schemeEnd >= 0) {
        val scheme = rest.substring(0, schemeEnd).lowercase()
        require(scheme == "http" || scheme == "https") {
            "Unsupported scheme '$scheme' (use http or https)"
        }
        schemeTls = scheme == "https"
        rest = rest.substring(schemeEnd + 3)
    }

    require(!rest.contains('?') && !rest.contains('#')) {
        "Server address must not contain query or fragment"
    }
    if (rest.endsWith("/")) rest = rest.dropLast(1)
    require(!rest.contains('/')) { "Server address must not contain a path" }
    require(!rest.contains('@')) { "Server address must not contain userinfo" }

    val host: String
    val explicitPort: Int?
    if (rest.startsWith("[")) {
        val close = rest.indexOf(']')
        require(close > 1) { "Malformed IPv6 address" }
        host = rest.substring(1, close)
        val tail = rest.substring(close + 1)
        explicitPort = when {
            tail.isEmpty() -> null
            tail.startsWith(":") -> parsePort(tail.substring(1))
            else -> throw IllegalArgumentException("Unexpected characters after IPv6 address")
        }
    } else {
        require(rest.count { it == ':' } <= 1) { "Malformed server address" }
        val colon = rest.indexOf(':')
        if (colon >= 0) {
            host = rest.substring(0, colon)
            explicitPort = parsePort(rest.substring(colon + 1))
        } else {
            host = rest
            explicitPort = null
        }
    }
    require(isValidHost(host)) { "Invalid host '$host'" }

    // Bare `host` and `host:443` default to TLS; any other explicit port
    // without a scheme is treated as plain HTTP (e.g. `192.168.1.5:9051`).
    val useTls = schemeTls ?: (explicitPort == null || explicitPort == 443)
    val port = explicitPort ?: if (useTls) 443 else 80
    NetworkConfig(host = host, port = port, useTls = useTls)
}

private fun parsePort(raw: String): Int {
    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) { "Invalid port '$raw'" }
    return port
}

private fun isValidHost(host: String): Boolean =
    host.isNotEmpty() && host.none { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '@' || it == '[' || it == ']' }

/**
 * Stable per-endpoint fingerprint used to namespace the per-user database
 * file (`chat_<serverKey>_<userId>.db`), so the same userId on two different
 * servers never shares local data. `String.hashCode` is consistent across
 * Kotlin/JVM, Kotlin/JS and Kotlin/Native.
 */
fun serverKeyFor(config: NetworkConfig): String =
    "s" + config.baseUrl.hashCode().toUInt().toString(16).padStart(8, '0')

/**
 * Holds the active server endpoint: seeded from the injected [NetworkConfig]
 * (the compile-time default — BuildKonfig on Compose clients,
 * `loadJsNetworkConfig()` on the JS web client), then overridden by the
 * persisted user selection in [ServerConfigDataSource].
 *
 * [ensureLoaded] must complete before the first authenticated session is
 * restored, otherwise startup requests would hit the seed server; the
 * caller (SessionManager) awaits it explicitly.
 *
 * [update] persists a validated address immediately; a later login failure
 * does NOT roll it back — that is intentional, the user may simply retry
 * with corrected credentials.
 */
class ServerEndpointProvider(
    private val seed: NetworkConfig,
    private val serverConfigDataSource: ServerConfigStore,
) {
    private val mutex = Mutex()
    private var loaded = false

    private val _config = MutableStateFlow(seed)
    val config: StateFlow<NetworkConfig> = _config.asStateFlow()

    /** Current endpoint snapshot; read on every request / connect attempt. */
    val current: NetworkConfig get() = _config.value

    /** Database-namespace fingerprint of [current]; see [serverKeyFor]. */
    val currentServerKey: String get() = serverKeyFor(current)

    /** Idempotent; safe to call concurrently. */
    suspend fun ensureLoaded() {
        mutex.withLock {
            if (loaded) return
            val stored = try {
                serverConfigDataSource.currentServerBaseUrl()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failing preferences read must not wedge session startup;
                // fall back to the compile-time seed.
                null
            }
            if (!stored.isNullOrBlank()) {
                // A corrupt stored value degrades to the seed instead of
                // breaking every request.
                parseServerAddress(stored).getOrNull()?.let { _config.value = it }
            }
            loaded = true
        }
    }

    /**
     * Validates [rawAddress], persists the normalized base URL, and switches
     * the in-memory endpoint atomically. Only meaningful while logged out —
     * callers must enforce that policy.
     */
    suspend fun update(rawAddress: String): Result<NetworkConfig> = mutex.withLock {
        val parsed = parseServerAddress(rawAddress).getOrElse {
            return@withLock Result.failure(it)
        }
        try {
            serverConfigDataSource.setServerBaseUrl(parsed.baseUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Result.failure(e)
        }
        _config.value = parsed
        loaded = true
        Result.success(parsed)
    }
}
