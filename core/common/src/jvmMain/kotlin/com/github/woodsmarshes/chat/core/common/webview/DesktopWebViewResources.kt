package com.github.woodsmarshes.chat.core.common.webview

import com.github.woodsmarshes.chat.core.common.AppDispatchers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.withContext

/** Local shell and browser profile locations prepared before native creation. */
data class DesktopWebViewResources(val shellUrl: String, val dataDirectory: String)

/** Prepares immutable shell resources off the UI thread without storing article data. */
suspend fun prepareDesktopWebViewResources(
    shellName: String,
    content: ByteArray,
): DesktopWebViewResources = withContext(AppDispatchers.io) {
    prepareDesktopWebViewResources(
        Path.of(System.getProperty("user.home"), ".chat-multiplatform"),
        shellName,
        content,
    )
}

internal fun prepareDesktopWebViewResources(
    root: Path,
    shellName: String,
    content: ByteArray,
): DesktopWebViewResources {
    require(shellName.matches(Regex("[a-z][a-z0-9-]*")))
    // Sharing a writable profile is an application policy, not a WebView2 requirement.
    val profile = Files.createDirectories(root.resolve("webview"))
    val cache = Files.createDirectories(root.resolve("webview-shells"))
    val digest = MessageDigest.getInstance("SHA-256").digest(content)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val shell = cache.resolve("$shellName-$digest.html")
    if (!Files.exists(shell)) {
        val temporary = Files.createTempFile(cache, "$shellName-", ".tmp")
        try {
            Files.write(temporary, content)
            // Publish a complete file; concurrent writers use identical bytes.
            Files.move(temporary, shell, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
    return DesktopWebViewResources(
        shellUrl = shell.toAbsolutePath().toUri().toASCIIString(),
        dataDirectory = profile.toAbsolutePath().toString(),
    )
}
