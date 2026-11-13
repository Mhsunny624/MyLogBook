/*
 * Biman rest rules: short rest after arriving at Dhaka and post-flight off duty at home base.
 * See docs/RULES.md for the rules in plain words.
 */
package com.example.mylogbook.domain.duty

import android.annotation.SuppressLint
import com.example.mylogbook.data.FlightLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ShortRestInfo(
    val durationText: String,
    val endTimeLocalText: String
)

data class ClaimOffInfo(
    val daysOff: Int,
    val calendarDays: Int,
    val endDateTimeText: String
)

fun calculateClaimOffForDay(dailyLogs: List<FlightLog>, allLogs: List<FlightLog>, homeBase: String): ClaimOffInfo? {
    val safeHomeBase = homeBase.trim().uppercase()
    if (safeHomeBase.isBlank()) return null

    val flightSectors = dailyLogs.filter { it.recordType == "Flight" }.sortedBy { it.id }
    if (flightSectors.isEmpty()) return null

    val lastLog = flightSectors.last()
    if (lastLog.arrival.uppercase() != safeHomeBase) return null

    val firstLog = flightSectors.first()
    if (firstLog.departure.uppercase() == safeHomeBase) return null

    val dutySourceLog = flightSectors.firstOrNull { it.dutyOff.isNotBlank() } ?: return null
    val dutyOnStr = dutySourceLog.dutyOn.filter { it.isDigit() }
    val dutyOffStr = dutySourceLog.dutyOff.filter { it.isDigit() }
    if (dutyOnStr.length != 4 || dutyOffStr.length != 4) return null

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    val sortedAllFlights = allLogs.filter { it.recordType == "Flight" }
        .sortedWith(compareBy({ try { sdf.parse(it.flightDate) } catch(_:Exception){ Date(0) } }, { it.id }))

    val lastLogIndex = sortedAllFlights.indexOfFirst { it.id == lastLog.id }
    if (lastLogIndex <= 0) return null

    var startFlight: FlightLog? = null
    for (i in lastLogIndex downTo 0) {
        val f = sortedAllFlights[i]
        if (f.departure.uppercase() == safeHomeBase) {
            startFlight = f
            break
        }
        if (i != lastLogIndex && f.arrival.uppercase() == safeHomeBase) {
            break
        }
    }
    if (startFlight == null) return null

    val sdfDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val startDate = try { sdfDateOnly.parse(startFlight.flightDate) } catch(_:Exception){ null }
    val endDate = try { sdfDateOnly.parse(dutySourceLog.flightDate) } catch(_:Exception){ null }

    val dutyOnDate = dayStartUtc(dutySourceLog.flightDate)
        ?.let { day -> dutyOnInstant(day, dutyOnStr, flightSectors.first()) }
        ?.let { Date(it) }

    val totalDutyStr = dutySourceLog.totalDuty.filter { it.isDigit() }
    val dutyMins = if (totalDutyStr.isNotBlank()) getMinsFromTimeString(totalDutyStr) else 0

    if (startDate == null || endDate == null || dutyOnDate == null) return null

    val diffMillis = endDate.time - startDate.time
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt() + 1

    if (diffDays <= 1) return null

    val daysOff = when {
        diffDays <= 3 -> 1
        diffDays <= 5 -> 2
        diffDays <= 9 -> 3
        else -> 4
    }

    return try {
        val trueDutyOffDate = Date(dutyOnDate.time + (dutyMins * 60L * 1000L))
        val shortRestMillis = dutyMins * 2L * 60L * 1000L
        val endOffDate = Date(trueDutyOffDate.time + shortRestMillis + (daysOff * 24L * 60 * 60 * 1000L))

        val sdfBst = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka") }
        val endDateTimeText = sdfBst.format(endOffDate)

        ClaimOffInfo(daysOff, diffDays, endDateTimeText)
    } catch (_: Exception) {
        null
    }
}

@SuppressLint("DefaultLocale")
fun calculateShortRest(flightLogs: List<FlightLog>): ShortRestInfo? {
    val flightSectors = flightLogs.filter { it.recordType == "Flight" }.sortedBy { it.id }
    if (flightSectors.isEmpty()) return null

    val lastLog = flightSectors.last()

    if (lastLog.arrival.uppercase() != "DAC") return null

    val dutySourceLog = flightSectors.firstOrNull { it.dutyOff.isNotBlank() } ?: return null

    val dutyOnStr = dutySourceLog.dutyOn.filter { it.isDigit() }
    val dutyOffStr = dutySourceLog.dutyOff.filter { it.isDigit() }
    val totalDutyStr = dutySourceLog.totalDuty.filter { it.isDigit() }
    val flightDateStr = dutySourceLog.flightDate

    if (dutyOnStr.length != 4 || dutyOffStr.length != 4 || totalDutyStr.isBlank() || flightDateStr.isBlank()) return null

    val dutyMins = getMinsFromTimeString(totalDutyStr)
    val shortRestDurationMins = dutyMins * 2

    try {
        val day = dayStartUtc(flightDateStr) ?: return null
        val dutyOnDate = Date(dutyOnInstant(day, dutyOnStr, flightSectors.first()) ?: return null)
        val trueDutyOffDate = Date(dutyOnDate.time + (dutyMins * 60L * 1000L))
        val shortRestEndDate = Date(trueDutyOffDate.time + (shortRestDurationMins * 60L * 1000L))

        val durHrs = shortRestDurationMins / 60
        val durMins = shortRestDurationMins % 60
        val durationText = String.format("%d hrs %02d mins", durHrs, durMins)

        val sdfLocal = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")
        }
        val endTimeLocalText = sdfLocal.format(shortRestEndDate)

        return ShortRestInfo(durationText, endTimeLocalText)
    } catch (_: Exception) {
        return null
    }
}
