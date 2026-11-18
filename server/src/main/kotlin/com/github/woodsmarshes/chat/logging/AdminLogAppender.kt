package com.github.woodsmarshes.chat.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase

/**
 * Logback appender that mirrors events into an [AdminLogBuffer]. The
 * target is selected by [bufferName] from logback.xml: "app" (default)
 * feeds the WARN+ application tail, "audit" feeds the dedicated
 * `admin.audit` channel. Throwable events keep a short stack-trace
 * excerpt in [AdminLogBuffer.Entry.detail] — the full trace stays in
 * the console log.
 */
class AdminLogAppender : AppenderBase<ILoggingEvent>() {
    /** Buffer selector set from logback.xml: "app" or "audit". */
    var bufferName: String = "app"

    override fun append(event: ILoggingEvent) {
        buffer().add(
            level = event.level.levelStr,
            logger = event.loggerName,
            message = event.formattedMessage,
            detail = event.throwableProxy?.let { throwableDetail(it) },
        )
    }

    private fun buffer(): AdminLogBuffer = when (bufferName) {
        "audit" -> AuditLogBuffer
        else -> AppLogBuffer
    }

    private fun throwableDetail(proxy: IThrowableProxy): String =
        ThrowableProxyUtil.asString(proxy).take(MAX_DETAIL_CHARS)

    private companion object {
        const val MAX_DETAIL_CHARS = 2000
    }
}