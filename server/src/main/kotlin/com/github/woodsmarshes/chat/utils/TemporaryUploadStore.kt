package com.github.woodsmarshes.chat.utils

import com.github.woodsmarshes.chat.core.model.MediaContent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PendingUpload(
    val content: MediaContent,
    val physicalPaths: List<String>,
    val uploaderId: Uuid? = null,
    val createdAt: Instant = Clock.System.now(),
    val confirmed: Boolean = false,
)

interface TemporaryUploadStore {
    fun register(media: MediaContent, physicalPaths: List<String>, uploaderId: Uuid? = null)
    fun retrieveAndConfirm(url: String, senderId: Uuid? = null): MediaContent?
    fun invalidate(url: String)
    fun cleanExpiredFiles(expirationMinutes: Int = 30)
}

class TemporaryUploadStoreImpl : TemporaryUploadStore, AutoCloseable {
    private val pendingMap = ConcurrentHashMap<String, PendingUpload>()

    override fun register(media: MediaContent, physicalPaths: List<String>, uploaderId: Uuid?) {
        pendingMap[media.url] = PendingUpload(
            content = media,
            physicalPaths = physicalPaths,
            uploaderId = uploaderId,
        )
    }

    override fun retrieveAndConfirm(url: String, senderId: Uuid?): MediaContent? {
        // Confirming must not be destructive: re-attaching the same URL (e.g.
        // forwarding) has to keep succeeding while the file remains on disk,
        // and the cleanup job only ever deletes uploads that never made it
        // into a message.
        // Authorization rules:
        // - If an uploader was recorded, only that exact authenticated sender
        //   may claim or confirm the entry (null sender is rejected).
        // - Private attachments (/v1/files/content/…) without a recorded owner
        //   are conservatively rejected for sending.
        // - Every registered physical file must still exist on disk so a
        //   cached entry whose bytes were deleted cannot be reused.
        var matched: MediaContent? = null
        pendingMap.computeIfPresent(url) { _, entry ->
            val ownerAllowed = when {
                entry.uploaderId != null -> senderId != null && entry.uploaderId == senderId
                url.startsWith(PRIVATE_FILE_URL_PREFIX) -> false
                else -> true
            }
            if (!ownerAllowed) {
                return@computeIfPresent entry
            }
            val filesPresent = entry.physicalPaths.all { path -> File(path).isFile }
            if (!filesPresent) {
                return@computeIfPresent null
            }
            matched = entry.content
            if (entry.confirmed) entry else entry.copy(confirmed = true)
        }
        return matched
    }

    override fun invalidate(url: String) {
        pendingMap.remove(url)
    }

    override fun cleanExpiredFiles(expirationMinutes: Int) {
        val now = Clock.System.now()
        val threshold = expirationMinutes.minutes
        for (url in pendingMap.keys.toList()) {
            pendingMap.computeIfPresent(url) { _, current ->
                if (now - current.createdAt <= threshold) {
                    return@computeIfPresent current
                }
                if (current.confirmed) {
                    // The file is referenced by a persisted message: keep the
                    // bytes, drop only the in-memory bookkeeping.
                    return@computeIfPresent null
                }
                val remainingPaths = current.physicalPaths.filter { path ->
                    try {
                        val file = File(path)
                        if (file.exists() && !file.delete() && file.exists()) {
                            true
                        } else {
                            false
                        }
                    } catch (_: Exception) {
                        true
                    }
                }
                if (remainingPaths.isEmpty()) {
                    null
                } else {
                    current.copy(physicalPaths = remainingPaths)
                }
            }
        }
    }

    override fun close() {
        cleanExpiredFiles(0)
        pendingMap.clear()
    }
}
