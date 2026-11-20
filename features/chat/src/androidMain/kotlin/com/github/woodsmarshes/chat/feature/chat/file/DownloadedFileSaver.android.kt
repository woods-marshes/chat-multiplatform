package com.github.woodsmarshes.chat.feature.chat.file

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberDownloadedFileSaver(): suspend (fileName: String, bytes: ByteArray, mimeType: String?) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { fileName: String, bytes: ByteArray, mimeType: String? ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val safeName = fileName.replace(Regex("[^\\w.\\-() ]"), "_").ifBlank { "download" }
                    val type = mimeType ?: "application/octet-stream"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values =
                            ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                                put(MediaStore.Downloads.MIME_TYPE, type)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                        val uri =
                            context.contentResolver.insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values,
                            ) ?: return@runCatching false
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: return@runCatching false
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        context.contentResolver.update(uri, values, null, null)
                        true
                    } else {
                        val dir =
                            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                ?: context.filesDir
                        File(dir, safeName).writeBytes(bytes)
                        true
                    }
                }.getOrDefault(false)
            }
        }
    }
}
