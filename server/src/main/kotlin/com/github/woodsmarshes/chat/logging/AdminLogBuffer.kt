package com.github.woodsmarshes.chat.logging

import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory ring buffer of recent WARN+ log entries for the admin
 * monitoring endpoint. Logback cannot be injected through Koin (it is
 * configured from logback.xml before the DI graph exists), so the buffer
 * is a process-wide singleton that [AdminLogAppender] writes into and
 * [com.github.woodsmarshes.chat.service.AdminService] reads from.
 *
 * Entries are lost on restart — this is a live tail, not an audit log.
 */
object AdminLogBuffer {
    data class Entry(
        val timestamp: Instant,
        val level: String,
        val logger: String,
        val message: String,
        /** First stack-trace lines when the event carried a throwable; null otherwise. */
        val detail: String? = null,
    )

    private const val CAPACITY = 500

    private val entries = ConcurrentLinkedDeque<Entry>()

    fun add(level: String, logger: String, message: String, detail: String? = null) {
        entries.addLast(Entry(timestamp = Clock.System.now(), level = level, logger = logger, message = message, detail = detail))
        while (entries.size > CAPACITY) {
            entries.pollFirst()
        }
    }

    /** Newest first; [level] filters by exact level name (e.g. "ERROR"). */
    fun list(level: String?, limit: Int): List<Entry> {
        val snapshot = entries.toList().asReversed()
        val filtered = if (level.isNullOrBlank()) snapshot else snapshot.filter { it.level == level.uppercase() }
        return filtered.take(limit.coerceIn(1, CAPACITY))
    }
}