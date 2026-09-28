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
    val createdAt: Instant = Clock.System.now(),
    val confirmed: Boolean = false,
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
        // Confirming must not be destructive: re-attaching the same URL (e.g.
        // forwarding) has to keep succeeding, and the cleanup job only ever
        // deletes uploads that never made it into a message.
        return pendingMap.computeIfPresent(url) { _, entry ->
            if (entry.confirmed) entry else entry.copy(confirmed = true)
        }?.content
    }

    override fun cleanExpiredFiles(expirationMinutes: Int) {
        val now = Clock.System.now()
        val iterator = pendingMap.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (now - entry.createdAt <= expirationMinutes.minutes) continue

            if (entry.confirmed) {
                // The file is referenced by a persisted message: keep the
                // bytes, drop only the in-memory bookkeeping.
                iterator.remove()
            } else {
                entry.physicalPaths.forEach { path ->
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
