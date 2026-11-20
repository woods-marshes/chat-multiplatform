package com.github.woodsmarshes.chat.core.datastore

import com.github.woodsmarshes.chat.core.datastore.di.createPreferenceDataSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The selected server address is a device-level setting: every credential
 * cleanup path must leave it untouched so the login screen can offer the last
 * server after logout.
 */
class ServerConfigPersistenceTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun sources(
        dir: Path,
        scope: CoroutineScope,
    ) = createPreferenceDataSources(
        filePath = dir.resolve("test.preferences_pb").toString(),
        scope = scope,
        json = json,
    )

    @Test
    fun serverSelectionSurvivesEveryCredentialCleanupPath() =
        runBlocking {
            val dir = Files.createTempDirectory("server-config-cleanup-")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val sources = sources(dir, scope)
                val url = "https://chat.example.com:8443"
                sources.serverConfigDataSource.setServerBaseUrl(url)

                val capturedRefresh =
                    sources.authTokenDataSource.withCredentialLock {
                        sources.authTokenDataSource.invalidateAndClearLocked()
                    }
                assertNull(capturedRefresh)
                assertEquals(url, sources.serverConfigDataSource.currentServerBaseUrl())

                sources.authTokenDataSource.clearLoginSession()
                assertEquals(url, sources.serverConfigDataSource.currentServerBaseUrl())
            } finally {
                scope.cancel()
                dir.toFile().deleteRecursively()
            }
        }

    @Test
    fun serverSelectionSurvivesProcessRestart() =
        runBlocking {
            val dir = Files.createTempDirectory("server-config-restart-")
            val url = "http://192.168.1.5:9051"
            try {
                val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                try {
                    sources(dir, firstScope).serverConfigDataSource.setServerBaseUrl(url)
                } finally {
                    firstScope.cancel()
                }

                val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                try {
                    assertEquals(url, sources(dir, secondScope).serverConfigDataSource.currentServerBaseUrl())
                } finally {
                    secondScope.cancel()
                }
            } finally {
                dir.toFile().deleteRecursively()
            }
        }
}
