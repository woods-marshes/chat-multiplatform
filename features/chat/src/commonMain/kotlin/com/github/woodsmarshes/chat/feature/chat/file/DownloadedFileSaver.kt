package com.github.woodsmarshes.chat.feature.chat.file

import androidx.compose.runtime.Composable

/**
 * Saves downloaded chat attachment bytes through the platform's file mechanism
 * (file manager on desktop, MediaStore on Android, browser download on web).
 *
 * @return true when the bytes were persisted or handed to the OS/browser.
 */
@Composable
expect fun rememberDownloadedFileSaver(): suspend (fileName: String, bytes: ByteArray, mimeType: String?) -> Boolean
