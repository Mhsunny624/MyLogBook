/*
 * Helpers for the "HHmm" time strings and "dd/MM/yyyy" dates stored in FlightLog.
 */
package com.example.mylogbook.domain.duty

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun getMinsFromTimeString(timeStr: String): Int {
    val digits = timeStr.filter { it.isDigit() }
    if (digits.isEmpty()) return 0
    val padded = digits.padStart(4, '0')
    val h = padded.substring(0, padded.length - 2).toIntOrNull() ?: 0
    val m = padded.substring(padded.length - 2).toIntOrNull() ?: 0
    return (h * 60) + m
}

@SuppressLint("DefaultLocale")
fun normalizeTimeStr(timeStr: String): String {
    val totalMins = getMinsFromTimeString(timeStr)
    if (totalMins == 0 && timeStr.none { it.isDigit() }) return ""
    return String.format("%02d%02d", totalMins / 60, totalMins % 60)
}

fun isValidTime(timeStr: String): Boolean {
    if (timeStr.isEmpty()) return true
    val digits = timeStr.filter { it.isDigit() }
    if (digits.length != 4) return false
    val h = digits.substring(0, 2).toIntOrNull() ?: return false
    val m = digits.substring(2, 4).toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

@SuppressLint("DefaultLocale")
fun getRemainingTimeText(expiryDateStr: String): String {
    try {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val expiryDate = sdf.parse(expiryDateStr) ?: return ""

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)

        val expiryCal = Calendar.getInstance()
        expiryCal.time = expiryDate
        expiryCal.set(Calendar.HOUR_OF_DAY, 0); expiryCal.set(Calendar.MINUTE, 0); expiryCal.set(Calendar.SECOND, 0); expiryCal.set(Calendar.MILLISECOND, 0)

        if (expiryCal.timeInMillis < today.timeInMillis) return "Expired"

        var years = expiryCal.get(Calendar.YEAR) - today.get(Calendar.YEAR)
        var months = expiryCal.get(Calendar.MONTH) - today.get(Calendar.MONTH)
        var days = expiryCal.get(Calendar.DAY_OF_MONTH) - today.get(Calendar.DAY_OF_MONTH)

        if (days < 0) {
            months--
            val prevMonth = expiryCal.clone() as Calendar
            prevMonth.add(Calendar.MONTH, -1)
            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        if (months < 0) {
            years--
            months += 12
        }

        val parts = mutableListOf<String>()
        if (years > 0) parts.add(if (years == 1) "1 yr" else "$years yrs")
        if (months > 0) parts.add(if (months == 1) "1 mo" else "$months mos")
        if (days > 0 || parts.isEmpty()) parts.add(if (days == 1) "1 day" else "$days days")

        return parts.joinToString(" ") + " remaining"
    } catch (_: Exception) {
        return ""
    }
}

@SuppressLint("DefaultLocale")
fun calculateTimeDifference(startStr: String, endStr: String): String? {
    val startDigits = startStr.filter { it.isDigit() }
    val endDigits = endStr.filter { it.isDigit() }
    if (startDigits.length != 4 || endDigits.length != 4) return null
    val startH = startDigits.substring(0, 2).toIntOrNull() ?: return null
    val startM = startDigits.substring(2, 4).toIntOrNull() ?: return null
    val endH = endDigits.substring(0, 2).toIntOrNull() ?: return null
    val endM = endDigits.substring(2, 4).toIntOrNull() ?: return null
    if (startH !in 0..23 || startM !in 0..59 || endH !in 0..23 || endM !in 0..59) return null
    var diffMin = (endH * 60 + endM) - (startH * 60 + startM)
    if (diffMin < 0) diffMin += 24 * 60
    return String.format("%02d%02d", diffMin / 60, diffMin % 60)
}

@SuppressLint("DefaultLocale")
fun formatWithColon(timeStr: String): String {
    val digits = timeStr.filter { it.isDigit() }
    if (digits.length == 4) {
        val h = digits.substring(0, 2).toIntOrNull() ?: 0
        val m = digits.substring(2, 4).toIntOrNull() ?: 0
        val totalMins = (h * 60) + m
        return String.format("%02d:%02d", totalMins / 60, totalMins % 60)
    }
    return timeStr
}

/**
 * Duration typed as digits where the last two are minutes: "7721" -> 77:21, "12030" -> 120:30,
 * "45" -> 0:45. Returns total minutes, or null if empty or the minutes part is above 59.
 */
fun durationMinutes(digits: String): Int? {
    val d = digits.filter { it.isDigit() }
    if (d.isEmpty()) return null
    val mins = d.takeLast(2).toInt()
    val hours = if (d.length > 2) d.dropLast(2).toInt() else 0
    if (mins > 59) return null
    return hours * 60 + mins
}

/** Minutes as h:mm, with a leading "-" when negative: 4641 -> "77:21", -90 -> "-1:30". */
fun formatDuration(totalMins: Int): String {
    val sign = if (totalMins < 0) "-" else ""
    val m = kotlin.math.abs(totalMins)
    return String.format(Locale.US, "%s%d:%02d", sign, m / 60, m % 60)
}

/** Minutes as decimal hours with two places: 4641 -> "77.35". */
fun decimalHoursText(totalMins: Int): String = String.format(Locale.US, "%.2f", totalMins / 60.0)

/** Minutes from one UTC clock time to the next ("HHmm"), crossing midnight if needed: 2330 -> 0415 = 285. */
fun clockDifferenceMinutes(start: String, end: String): Int? {
    if (!isValidTime(start) || !isValidTime(end) || start.length != 4 || end.length != 4) return null
    val diff = getMinsFromTimeString(end) - getMinsFromTimeString(start)
    return if (diff < 0) diff + 24 * 60 else diff
}
