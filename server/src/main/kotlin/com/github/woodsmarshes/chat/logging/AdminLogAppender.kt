package com.github.woodsmarshes.chat.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase

/**
 * Logback appender that mirrors events into [AdminLogBuffer]. The
 * ThresholdFilter in logback.xml keeps only WARN+ events, so INFO chatter
 * never reaches the admin log endpoint. Throwable events keep a short
 * stack-trace excerpt in [AdminLogBuffer.Entry.detail] — the full trace
 * stays in the console log.
 */
class AdminLogAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        AdminLogBuffer.add(
            level = event.level.levelStr,
            logger = event.loggerName,
            message = event.formattedMessage,
            detail = event.throwableProxy?.let { throwableDetail(it) },
        )
    }

    private fun throwableDetail(proxy: IThrowableProxy): String =
        ThrowableProxyUtil.asString(proxy).take(MAX_DETAIL_CHARS)

    private companion object {
        const val MAX_DETAIL_CHARS = 2000
    }
}