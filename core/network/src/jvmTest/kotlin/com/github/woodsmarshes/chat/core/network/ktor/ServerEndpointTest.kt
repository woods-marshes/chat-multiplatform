package com.github.woodsmarshes.chat.core.network.ktor

import com.github.woodsmarshes.chat.core.datastore.ServerConfigStore
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerEndpointTest {

    private class FakeStore(
        var value: String? = null,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : ServerConfigStore {
        var writes = 0
        var reads = 0

        override suspend fun currentServerBaseUrl(): String? {
            reads++
            if (failRead) throw IOException("read failed")
            return value
        }

        override suspend fun setServerBaseUrl(url: String) {
            if (failWrite) throw IOException("write failed")
            writes++
            value = url
        }
    }

    private fun parse(raw: String): NetworkConfig =
        parseServerAddress(raw).getOrElse { error("expected '$raw' to parse: ${it.message}") }

    @Test
    fun bareHostDefaultsToTlsOnPort443() {
        assertEquals(NetworkConfig("example.com", 443, useTls = true), parse("example.com"))
    }

    @Test
    fun bareHostWithExplicitPortInfersTlsFrom443() {
        assertEquals(NetworkConfig("example.com", 443, useTls = true), parse("example.com:443"))
        assertEquals(NetworkConfig("192.168.1.5", 9051, useTls = false), parse("192.168.1.5:9051"))
    }

    @Test
    fun schemeWinsOverPortInference() {
        assertEquals(NetworkConfig("example.com", 80, useTls = false), parse("http://example.com"))
        assertEquals(NetworkConfig("example.com", 8443, useTls = true), parse("https://example.com:8443"))
        assertEquals(NetworkConfig("127.0.0.1", 9051, useTls = false), parse("http://127.0.0.1:9051/"))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(NetworkConfig("example.com", 443, useTls = true), parse("  example.com \t"))
    }

    @Test
    fun ipv6RequiresBracketsAndProducesBracketedUrls() {
        val parsed = parse("[::1]:9051")
        assertEquals("::1", parsed.host)
        assertEquals(9051, parsed.port)
        assertEquals("http://[::1]:9051", parsed.baseUrl)
        assertEquals("ws://[::1]:9051/ws", parsed.wsUrl)
        assertEquals("wss://[::1]:443/ws", NetworkConfig("::1", 443, useTls = true).wsUrl)
    }

    @Test
    fun unbracketedIpv6IsRejected() {
        assertTrue(parseServerAddress("::1").isFailure)
        assertTrue(parseServerAddress("[::1").isFailure)
    }

    @Test
    fun pathsQueriesFragmentsAndUserInfoAreRejected() {
        assertTrue(parseServerAddress("http://example.com/api").isFailure)
        assertTrue(parseServerAddress("http://example.com/?q=1").isFailure)
        assertTrue(parseServerAddress("http://example.com/#frag").isFailure)
        assertTrue(parseServerAddress("user:pass@example.com").isFailure)
        assertTrue(parseServerAddress("http://example.com//").isFailure)
    }

    @Test
    fun invalidPortsAndSchemesAreRejected() {
        assertTrue(parseServerAddress("example.com:0").isFailure)
        assertTrue(parseServerAddress("example.com:65536").isFailure)
        assertTrue(parseServerAddress("example.com:").isFailure)
        assertTrue(parseServerAddress("ftp://example.com").isFailure)
        assertTrue(parseServerAddress("   ").isFailure)
        assertTrue(parseServerAddress("host:port").isFailure)
    }

    @Test
    fun requiresHost() {
        assertTrue(parseServerAddress("http://").isFailure)
        assertTrue(parseServerAddress("http://:9051").isFailure)
    }

    @Test
    fun serverKeyIsStableAndDistinguishesEndpoints() {
        val a = NetworkConfig("example.com", 9051, useTls = false)
        val b = NetworkConfig("example.com", 9052, useTls = false)
        val c = NetworkConfig("example.com", 9051, useTls = true)

        assertEquals(serverKeyFor(a), serverKeyFor(a.copy()))
        assertTrue(serverKeyFor(a) != serverKeyFor(b))
        assertTrue(serverKeyFor(a) != serverKeyFor(c))
        assertTrue(Regex("^s[0-9a-f]{8}$").matches(serverKeyFor(a)))
    }

    @Test
    fun ensureLoadedAppliesPersistedSelection() = runTest {
        val store = FakeStore(value = "https://chat.example.com")
        val provider = ServerEndpointProvider(NetworkConfig.forDevelopment(), store)

        provider.ensureLoaded()

        assertEquals(NetworkConfig("chat.example.com", 443, useTls = true), provider.current)
        assertEquals("https://chat.example.com:443", provider.current.baseUrl)
    }

    @Test
    fun ensureLoadedIsIdempotent() = runTest {
        val store = FakeStore(value = "https://a.example.com")
        val provider = ServerEndpointProvider(NetworkConfig.forDevelopment(), store)

        provider.ensureLoaded()
        store.value = "https://b.example.com"
        provider.ensureLoaded()

        assertEquals(1, store.reads)
        assertEquals("a.example.com", provider.current.host)
    }

    @Test
    fun ensureLoadedKeepsSeedWhenStoreIsEmptyCorruptOrFailing() = runTest {
        val seed = NetworkConfig.forDevelopment()

        val empty = ServerEndpointProvider(seed, FakeStore(value = null))
        empty.ensureLoaded()
        assertEquals(seed, empty.current)

        val corrupt = ServerEndpointProvider(seed, FakeStore(value = "not a host:port:extra"))
        corrupt.ensureLoaded()
        assertEquals(seed, corrupt.current)

        val failing = ServerEndpointProvider(seed, FakeStore(failRead = true))
        failing.ensureLoaded()
        assertEquals(seed, failing.current)
    }

    @Test
    fun stageAppliesInMemoryAndCommitPersists() = runTest {
        val store = FakeStore()
        val provider = ServerEndpointProvider(NetworkConfig.forDevelopment(), store)

        val result = provider.stage("HTTPS://Chat.Example.com:8443/")

        val endpoint = assertNotNull(result.getOrNull())
        assertEquals(NetworkConfig("Chat.Example.com", 8443, useTls = true), endpoint)
        assertEquals(provider.current, endpoint)
        assertNull(store.value, "staging must not touch persistent storage")
        assertEquals(0, store.writes)

        provider.commitStaged()

        assertEquals("https://Chat.Example.com:8443", store.value)
        assertEquals(1, store.writes)
        assertEquals(serverKeyFor(endpoint), provider.currentServerKey)
    }

    @Test
    fun stageRejectsInvalidInputWithoutTouchingStoreOrMemory() = runTest {
        val seed = NetworkConfig.forDevelopment()
        val store = FakeStore()
        val provider = ServerEndpointProvider(seed, store)
        provider.ensureLoaded()

        val result = provider.stage("http://example.com/path")

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(seed, provider.current)
        assertNull(store.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun revertStagedRestoresThePreviouslyPersistedEndpoint() = runTest {
        val seed = NetworkConfig.forDevelopment()
        val store = FakeStore()
        val provider = ServerEndpointProvider(seed, store)

        provider.stage("https://other.example.com")
        assertEquals("other.example.com", provider.current.host)

        provider.revertStaged()

        assertEquals(seed, provider.current)
        assertNull(store.value, "a reverted selection must never be written")
        assertEquals(0, store.writes)
    }

    @Test
    fun revertStagedRestoresTheAddressLoadedFromStorage() = runTest {
        val store = FakeStore(value = "https://saved.example.com")
        val provider = ServerEndpointProvider(NetworkConfig.forDevelopment(), store)
        provider.ensureLoaded()

        provider.stage("https://typo.example.com")
        provider.revertStaged()

        assertEquals("saved.example.com", provider.current.host)
        assertEquals("https://saved.example.com", store.value)
    }

    @Test
    fun revertStagedIsNoOpWithoutAStagedSelection() = runTest {
        val seed = NetworkConfig.forDevelopment()
        val store = FakeStore()
        val provider = ServerEndpointProvider(seed, store)

        provider.commitStaged()
        provider.revertStaged()

        assertEquals(seed, provider.current)
        assertEquals(1, store.writes)
    }

    @Test
    fun commitStagedReportsWriteFailureButKeepsTheAuthenticatedEndpoint() = runTest {
        val seed = NetworkConfig.forDevelopment()
        val store = FakeStore(failWrite = true)
        val provider = ServerEndpointProvider(seed, store)

        provider.stage("https://chat.example.com")
        val result = provider.commitStaged()

        assertIs<IOException>(result.exceptionOrNull())
        assertEquals("chat.example.com", provider.current.host)
    }

    @Test
    fun stageMarksProviderLoadedSoLaterEnsureLoadedCannotRevertSelection() = runTest {
        val store = FakeStore(value = "https://stale.example.com")
        val provider = ServerEndpointProvider(NetworkConfig.forDevelopment(), store)

        provider.stage("https://fresh.example.com")
        provider.ensureLoaded()

        assertEquals("fresh.example.com", provider.current.host)
        assertEquals(0, store.reads)
    }
}
