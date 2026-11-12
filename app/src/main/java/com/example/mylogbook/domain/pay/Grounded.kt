/*
 * Grounded periods (Health Card records with trainingType = "Grounded") and how many grounded
 * days fall in a month.
 */
package com.example.mylogbook.domain.pay

import com.example.mylogbook.data.FlightLog
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

fun isGroundedRecord(log: FlightLog): Boolean = log.recordType == "Health Card" && log.trainingType == "Grounded"

/** Inclusive [from, until] day range of a grounded record; ongoing runs to today. */
fun groundedSpan(log: FlightLog, today: Long = todayDayUtcBd()): Pair<Long, Long>? {
    val from = parseDayUtc(log.flightDate) ?: return null
    val until = if (log.dutyOff.isBlank()) max(from, today) else (parseDayUtc(log.dutyOff) ?: return null)
    return if (until < from) null else from to until
}

/** Number of grounded days falling in the given month (overlapping periods are not double counted). */
fun groundedDaysInMonth(logs: List<FlightLog>, year: Int, month0: Int, today: Long = todayDayUtcBd()): Int {
    val cal = Calendar.getInstance(UTC).apply { clear(); set(year, month0, 1) }
    val monthStart = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    val monthLastDay = cal.timeInMillis - DAY_MS
    val days = HashSet<Long>()
    logs.filter { isGroundedRecord(it) }.forEach { log ->
        val (from, until) = groundedSpan(log, today) ?: return@forEach
        var d = max(from, monthStart)
        val end = min(until, monthLastDay)
        while (d <= end) { days.add(d); d += DAY_MS }
    }
    return days.size
}
