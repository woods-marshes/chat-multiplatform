package com.github.woodsmarshes.chat.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

/**
 * Logback appender that mirrors events into [AdminLogBuffer]. The
 * ThresholdFilter in logback.xml keeps only WARN+ events, so INFO chatter
 * never reaches the admin log endpoint.
 */
class AdminLogAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        AdminLogBuffer.add(
            level = event.level.levelStr,
            logger = event.loggerName,
            message = event.formattedMessage,
        )
    }
}