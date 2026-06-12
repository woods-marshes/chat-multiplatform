package com.github.woodsmarshes.chat.core.ui.utils

import com.github.woodsmarshes.chat.core.ui.resources.getLocaleStrings
import kotlin.time.Clock
import kotlin.time.Instant

private val strings get() = getLocaleStrings()

fun formatMessageTime(instant: Instant): String {
    val now = Clock.System.now()
    val msgEpoch = instant.epochSeconds
    val curEpoch = now.epochSeconds

    val secondsInDay = (msgEpoch % 86400 + 86400) % 86400
    val hour = ((secondsInDay / 3600) % 24).toInt()
    val minute = ((secondsInDay % 3600) / 60).toInt()
    val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    val msgDay = msgEpoch / 86400
    val curDay = curEpoch / 86400
    val dayDiff = (curDay - msgDay).toInt()

    return when {
        dayDiff == 0 -> timeStr
        dayDiff == 1 -> strings.yesterdayFormat(timeStr)
        dayDiff in 2..6 -> {
            val dow = ((msgDay + 3) % 7 + 7) % 7
            val dayLabel = when (dow.toInt()) {
                0 -> strings.monday
                1 -> strings.tuesday
                2 -> strings.wednesday
                3 -> strings.thursday
                4 -> strings.friday
                5 -> strings.saturday
                6 -> strings.sunday
                else -> ""
            }
            "$dayLabel $timeStr"
        }
        else -> {
            val (month, day) = approximateMonthDay(msgDay)
            "$month/$day $timeStr"
        }
    }
}

internal fun approximateMonthDay(msgDaysSinceEpoch: Long): Pair<Int, Int> {
    // Algorithm: convert days since epoch to approximate month/day
    // Works for dates 1970-2100 (ignores leap year adjustments for simplicity)
    val daysInYear = 365L
    val year = 1970 + (msgDaysSinceEpoch / daysInYear).toInt()
    val dayInYear = msgDaysSinceEpoch - (year - 1970).toLong() * daysInYear

    val months = listOf(31L, 28L, 31L, 30L, 31L, 30L, 31L, 31L, 30L, 31L, 30L, 31L)
    var remaining = dayInYear
    var month = 1
    for (days in months) {
        if (remaining <= days) break
        remaining -= days
        month++
    }
    return month.coerceIn(1, 12) to (remaining + 1).toInt()
}
