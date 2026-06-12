package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.core.model.MediaContent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class PendingUpload(
    val content: MediaContent,
    val physicalPaths: List<String>,
    val createdAt: Instant = Clock.System.now()
)

interface TemporaryUploadStore {
    fun register(media: MediaContent, physicalPaths: List<String>)
    fun retrieveAndConfirm(url: String): MediaContent?
    fun cleanExpiredFiles(expirationMinutes: Int = 30)
}

class TemporaryUploadStoreImpl : TemporaryUploadStore, AutoCloseable {
    private val pendingMap = ConcurrentHashMap<String, PendingUpload>()

    override fun register(media: MediaContent, physicalPaths: List<String>) {
        pendingMap[media.url] = PendingUpload(media, physicalPaths)
    }

    override fun retrieveAndConfirm(url: String): MediaContent? {
        return pendingMap.remove(url)?.content
    }

    override fun cleanExpiredFiles(expirationMinutes: Int) {
        val now = Clock.System.now()
        val iterator = pendingMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val info = entry.value
            if (now - info.createdAt > expirationMinutes.minutes) {
                info.physicalPaths.forEach { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                iterator.remove()
            }
        }
    }

    override fun close() {
        cleanExpiredFiles(0)
        pendingMap.clear()
    }
}
