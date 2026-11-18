package com.github.woodsmarshes.chat.logging

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * In-memory ring buffer of recent log entries for the admin monitoring
 * endpoints. Logback cannot be injected through Koin (it is configured
 * from logback.xml before the DI graph exists), so the buffers are
 * process-wide singletons that [AdminLogAppender] writes into and
 * [com.github.woodsmarshes.chat.service.AdminService] reads from.
 *
 * Two instances exist: [AppLogBuffer] mirrors WARN+ application logs
 * (a live tail, not an audit log) and [AuditLogBuffer] captures the
 * `admin.audit` channel, so a WARN storm cannot evict audit records.
 * Entries are lost on restart.
 */
class AdminLogBuffer(private val capacity: Int) {
    data class Entry(
        val timestamp: Instant,
        val level: String,
        val logger: String,
        val message: String,
        /** First stack-trace lines when the event carried a throwable; null otherwise. */
        val detail: String? = null,
    )

    private val entries = ConcurrentLinkedDeque<Entry>()
    private val size = AtomicInteger(0)

    fun add(level: String, logger: String, message: String, detail: String? = null) {
        entries.addLast(Entry(timestamp = Clock.System.now(), level = level, logger = logger, message = message, detail = detail))
        size.incrementAndGet()
        while (size.get() > capacity) {
            if (entries.pollFirst() != null) size.decrementAndGet() else break
        }
    }

    /** Newest first; [level] filters by exact level name (e.g. "ERROR"). */
    fun list(level: String?, limit: Int): List<Entry> {
        val max = limit.coerceIn(1, capacity)
        val result = ArrayList<Entry>(max)
        val iterator = entries.descendingIterator()
        while (iterator.hasNext() && result.size < max) {
            val entry = iterator.next()
            if (level.isNullOrBlank() || entry.level == level.uppercase()) result.add(entry)
        }
        return result
    }

    fun clear() {
        entries.clear()
        size.set(0)
    }
}

/** WARN+ application log tail behind GET /v1/admin/logs. */
val AppLogBuffer = AdminLogBuffer(capacity = 500)

/** `admin.audit` channel behind GET /v1/admin/audit-logs. */
val AuditLogBuffer = AdminLogBuffer(capacity = 2000)