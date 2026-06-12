package com.github.woodsmarshes.chat.core.ui.utils

import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import com.github.woodsmarshes.chat.core.ui.utils.approximateMonthDay
import kotlin.time.Clock
import kotlin.time.Instant

private val strings get() = getLocaleStrings()

fun formatRelativeTime(instant: Instant?): String {
    if (instant == null) return ""

    val now = Clock.System.now()
    val diffMs = now.toEpochMilliseconds() - instant.toEpochMilliseconds()
    if (diffMs < 0) return strings.future

    val diffMinutes = diffMs / (60 * 1000L)
    if (diffMinutes < 1) return strings.justNow
    if (diffMinutes < 60) return strings.minutesAgo(diffMinutes.toInt().toString())

    val diffHours = diffMinutes / 60
    if (diffHours < 24) return strings.hoursAgo(diffHours.toInt().toString())

    val diffDays = diffHours / 24
    if (diffDays < 7) return strings.daysAgo(diffDays.toInt().toString())

    val seconds = instant.epochSeconds
    val totalDays = seconds / 86400
    val (month, day) = approximateMonthDay(totalDays)
    return strings.monthDay(month.toString(), day.toString())
}
