/*
 * Turns the stored "dd/MM/yyyy" + "HHmm" (UTC) strings of a day's sectors into real instants.
 *
 * All sectors of a duty day share the departure date, so a later sector that leaves after UTC
 * midnight (e.g. block off 0030 after a 2200 sector) is moved to the next day, and a Duty On that
 * is later in the day than the first departure (e.g. Duty On 2245 for a 0030 departure) belongs to
 * the previous day.
 */
package com.example.mylogbook.domain.duty

import com.example.mylogbook.data.FlightLog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS

/** True for a complete, valid "HHmm" clock time. */
fun isClockTime(value: String): Boolean {
    val digits = value.filter { it.isDigit() }
    return digits.length == 4 && isValidTime(digits)
}

/** "dd/MM/yyyy" -> midnight UTC of that date, or null. */
fun dayStartUtc(date: String): Long? = try {
    SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        .parse(date.trim())?.time
} catch (_: Exception) { null }

/**
 * Duty On instant for a duty day. [firstSector] is the day's first sector; its scheduled departure
 * (or block off) tells whether Duty On was on the previous UTC day.
 */
fun dutyOnInstant(dayUtc: Long, dutyOn: String, firstSector: FlightLog?): Long? {
    if (!isClockTime(dutyOn)) return null
    val onMins = getMinsFromTimeString(dutyOn)
    val refMins = firstSector?.let {
        when {
            isClockTime(it.schedDep) -> getMinsFromTimeString(it.schedDep)
            isClockTime(it.blockOff) -> getMinsFromTimeString(it.blockOff)
            else -> null
        }
    }
    val sameDay = dayUtc + onMins * MINUTE_MS
    return if (refMins != null && onMins > refMins) sameDay - DAY_MS else sameDay
}

/**
 * Block off instant of every flight sector (by log id). Sectors of a day are taken in id order;
 * each one starts at or after the previous one ends. A sector without a block off time is placed
 * right after the previous sector (or at 00:00 UTC for the first one).
 */
fun blockOffInstants(logs: List<FlightLog>): Map<Int, Long> {
    val result = HashMap<Int, Long>()
    logs.filter { it.recordType == "Flight" }.groupBy { it.flightDate }.forEach { (date, group) ->
        val day = dayStartUtc(date) ?: return@forEach
        var cursor: Long? = null
        group.sortedBy { it.id }.forEach { s ->
            var off: Long? = if (isClockTime(s.blockOff)) day + getMinsFromTimeString(s.blockOff) * MINUTE_MS else null
            val floor = cursor
            if (off != null && floor != null) while (off < floor) off += DAY_MS
            if (off == null) off = cursor ?: day
            result[s.id] = off
            cursor = off + getMinsFromTimeString(s.totalBlock) * MINUTE_MS
        }
    }
    return result
}
