package com.github.woodsmarshes.chat.core.ui.utils

import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val strings get() = getLocaleStrings()

fun formatMessageTime(instant: Instant): String {
    val timeZone = TimeZone.currentSystemDefault()
    val msgLocal = instant.toLocalDateTime(timeZone)
    val nowLocal = Clock.System.now().toLocalDateTime(timeZone)

    val msgDate = msgLocal.date
    val nowDate = nowLocal.date
    val daysDiff = msgDate.daysUntil(nowDate) // 消息日期距今天多少天（正数表示过去）

    val hour = msgLocal.time.hour.toString().padStart(2, '0')
    val minute = msgLocal.time.minute.toString().padStart(2, '0')
    val timeStr = "$hour:$minute"

    return when (daysDiff) {
        0 -> timeStr
        1 -> strings.yesterdayFormat(timeStr)
        in 2..6 -> {
            val dayLabel = when (msgDate.dayOfWeek) {
                DayOfWeek.MONDAY -> strings.monday
                DayOfWeek.TUESDAY -> strings.tuesday
                DayOfWeek.WEDNESDAY -> strings.wednesday
                DayOfWeek.THURSDAY -> strings.thursday
                DayOfWeek.FRIDAY -> strings.friday
                DayOfWeek.SATURDAY -> strings.saturday
                DayOfWeek.SUNDAY -> strings.sunday
            }
            "$dayLabel $timeStr"
        }
        else -> {
            // 使用 monthDay 格式化月份和日期
            val month = msgDate.month.number.toString()
            val day = msgDate.day.toString()
            "${strings.monthDay(month, day)} $timeStr"
        }
    }
}
