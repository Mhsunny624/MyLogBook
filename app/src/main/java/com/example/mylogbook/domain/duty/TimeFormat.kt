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
