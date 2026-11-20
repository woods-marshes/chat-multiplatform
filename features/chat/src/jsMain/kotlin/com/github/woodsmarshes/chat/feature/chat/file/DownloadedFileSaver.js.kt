package com.github.woodsmarshes.chat.feature.chat.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberDownloadedFileSaver(): suspend (fileName: String, bytes: ByteArray, mimeType: String?) -> Boolean =
    remember {
        { fileName: String, bytes: ByteArray, mimeType: String? ->
            runCatching {
                // Kotlin/JS ByteArray is a JS Int8Array, which Blob accepts
                // directly as a blob part — no manual typed-array copy needed.
                // The js() snippet parser does not support '??', so resolve
                // the default MIME type on the Kotlin side first.
                val mime = mimeType ?: "application/octet-stream"
                val url =
                    js("URL.createObjectURL(new Blob([bytes], { type: mime }))")
                val anchor = js("document.createElement('a')")
                anchor.href = url
                anchor.download = fileName
                anchor.click()
                js("URL.revokeObjectURL(url)")
                true
            }.getOrDefault(false)
        }
    }
