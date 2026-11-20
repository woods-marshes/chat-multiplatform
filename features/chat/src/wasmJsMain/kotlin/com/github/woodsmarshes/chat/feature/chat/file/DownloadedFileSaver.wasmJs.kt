package com.github.woodsmarshes.chat.feature.chat.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Browser file download is not available on the wasmJs target yet: creating a
 * JS Blob requires typed-array interop that Kotlin/Wasm does not expose through
 * kotlinx-browser. Returning false surfaces the standard failure snackbar.
 */
@Composable
actual fun rememberDownloadedFileSaver(): suspend (fileName: String, bytes: ByteArray, mimeType: String?) -> Boolean =
    remember {
        { _: String, _: ByteArray, _: String? -> false }
    }
