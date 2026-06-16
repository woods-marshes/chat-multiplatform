package com.github.woodsmarshes.chat.core.ui.utils

import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val strings get() = getLocaleStrings()

/**
 * 格式化相对时间（如 "刚刚"、"5分钟前"、"2小时前"、"3天前"、"月/日"）
 */
fun formatRelativeTime(instant: Instant?): String {
    if (instant == null) return ""

    val now = Clock.System.now()
    val duration = now - instant  // 结果可能是负的（未来）
    if (duration.isNegative()) return strings.future

    val totalMilliseconds = duration.inWholeMilliseconds
    val diffMinutes = totalMilliseconds / (60 * 1000)

    return when {
        diffMinutes < 1 -> strings.justNow
        diffMinutes < 60 -> strings.minutesAgo(diffMinutes.toInt().toString())
        diffMinutes < 60 * 24 -> {
            val hours = (diffMinutes / 60).toInt()
            strings.hoursAgo(hours.toString())
        }
        diffMinutes < 60 * 24 * 7 -> {
            val days = (diffMinutes / (60 * 24)).toInt()
            strings.daysAgo(days.toString())
        }
        else -> {
            // 超过7天，显示月份和日期（注意：需要时区转换）
            val timeZone = TimeZone.currentSystemDefault()
            val localDate = instant.toLocalDateTime(timeZone).date
            val month = localDate.month.number.toString()
            val day = localDate.day.toString()
            strings.monthDay(month, day)
        }
    }
}
