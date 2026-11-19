/*
 * Time constants and date helpers shared by the pay calculations. Times are stored in UTC and
 * allowance months also run 00:00 UTC to 00:00 UTC (Biman payroll rule).
 */
package com.example.mylogbook.domain.pay

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal const val MIN_MS = 60_000L

private const val HOUR_MS = 60 * MIN_MS

internal const val DAY_MS = 24 * HOUR_MS

internal val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private val BD: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

/** "dd/MM/yyyy" -> epoch millis of that day's midnight UTC, or null. */
fun parseDayUtc(date: String): Long? = try {
    SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = UTC; isLenient = false }
        .parse(date.trim())?.time
} catch (_: Exception) { null }

/** [start, end) of a calendar month in UTC, as epoch millis. Allowance months split at 00:00 UTC. */
fun monthBoundsUtc(year: Int, month0: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance(UTC).apply { clear(); set(year, month0, 1, 0, 0, 0) }
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}

/** Today's date (Bangladesh time) as midnight-UTC millis, matching parseDayUtc. */
fun todayDayUtcBd(now: Long = System.currentTimeMillis()): Long {
    val bd = Calendar.getInstance(BD).apply { timeInMillis = now }
    return Calendar.getInstance(UTC).apply {
        clear(); set(bd.get(Calendar.YEAR), bd.get(Calendar.MONTH), bd.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
