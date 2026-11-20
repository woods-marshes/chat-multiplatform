package com.github.woodsmarshes.chat.feature.chat.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Composable
actual fun rememberDownloadedFileSaver(): suspend (fileName: String, bytes: ByteArray, mimeType: String?) -> Boolean =
    remember {
        { fileName: String, bytes: ByteArray, mimeType: String? ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val safeName = fileName.replace(Regex("[^\\w.\\-() ]"), "_").ifBlank { "download" }
                    val downloads =
                        Paths
                            .get(System.getProperty("user.home"), "Downloads")
                            .also { Files.createDirectories(it) }
                    val target = uniqueTarget(downloads, safeName)
                    Files.write(target, bytes)
                    runCatching { Desktop.getDesktop().open(target.toFile()) }
                    true
                }.getOrDefault(false)
            }
        }
    }

private fun uniqueTarget(
    dir: Path,
    fileName: String,
): Path {
    var candidate = dir.resolve(fileName)
    if (!Files.exists(candidate)) return candidate
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    var i = 1
    while (Files.exists(candidate)) {
        candidate = dir.resolve("$base ($i)$ext")
        i++
    }
    return candidate
}
