/*
 * USD statement estimate: overseas allowance (70-hour rule) plus the previous month's
 * incidental allowance, rounded up to the next USD 5. See docs/RULES.md.
 */
package com.example.mylogbook.domain.pay

import com.example.mylogbook.data.FlightLog
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class OverseasRate(val monthly: Double, val hourly: Double, val basis: String)

/** Regular crew: AO 17/2018. Contractual crew: AO 17/2023 tiers by service length. */
fun overseasRate(rates: PayRates, year: Int, month0: Int): OverseasRate {
    val auto = if (rates.crewType == "Regular") {
        OverseasRate(1500.0, 21.0, "Regular crew rate")
    } else {
        val years = serviceYears(rates.joiningDate, year, month0)
        when {
            years == null -> OverseasRate(1500.0, 21.0, "Contractual 5+ yrs rate (add joining date in Settings)")
            years >= 5 -> OverseasRate(1500.0, 21.0, "Contractual, $years yrs service")
            years >= 3 -> OverseasRate(1200.0, 17.0, "Contractual, $years yrs service")
            else -> OverseasRate(1000.0, 14.0, "Contractual, $years yrs service")
        }
    }
    val custom = rates.oaMonthlyOverride != null || rates.oaHourlyOverride != null
    return OverseasRate(
        monthly = rates.oaMonthlyOverride ?: auto.monthly,
        hourly = rates.oaHourlyOverride ?: auto.hourly,
        basis = if (custom) "Custom rate from Settings" else auto.basis
    )
}

data class IncidentalResult(
    val minutes: Long, val usd: Double, val trips: Int, val estimated: Boolean, val shuttles: Int = 0
)

/**
 * Incidental earned in one month: time away overlapping that month (split at 00:00 UTC).
 * Shuttle trips (out and back on the same duty, no layover) earn no incidental; they are only counted.
 */
fun incidentalForMonth(trips: List<Trip>, year: Int, month0: Int, perDay: Double): IncidentalResult {
    val (monthStart, monthEnd) = monthBoundsUtc(year, month0)
    var awayMs = 0L
    var count = 0
    var shuttles = 0
    var estimated = false
    trips.forEach { t ->
        val overlap = min(t.end, monthEnd) - max(t.start, monthStart)
        if (overlap > 0 && t.shuttle) {
            shuttles++
        } else if (overlap > 0) {
            awayMs += overlap; count++
            if (t.estimated) estimated = true
        }
    }
    return IncidentalResult(awayMs / MIN_MS, awayMs.toDouble() / DAY_MS * perDay, count, estimated, shuttles)
}

/** Long stay waiver for a month: > 10 days away in one trip = 20 h, > 5 days = 10 h. */
fun longStayWaiverHours(trips: List<Trip>, year: Int, month0: Int): Int {
    val (monthStart, monthEnd) = monthBoundsUtc(year, month0)
    return trips.filter { it.end in monthStart until monthEnd }.maxOfOrNull {
        when {
            it.durationMs > 10 * DAY_MS -> 20
            it.durationMs > 5 * DAY_MS -> 10
            else -> 0
        }
    } ?: 0
}

data class UsdEstimate(
    val flyMins: Int,
    val minHours: Int,
    val rate: OverseasRate,
    val waiverHours: Int,
    val overseas: Double,
    val previousIncidental: IncidentalResult,
    val currentIncidental: IncidentalResult,
    val total: Double,
    val rounded: Double,
    val roundStep: Double,
    val incompleteTrips: Int,
    /** Flights on the last day of a month (this month's or the previous one's) with no Block off/on. */
    val monthEndMissingTimes: Int = 0
)

/**
 * USD statement for the given month: overseas allowance for this month's flying (flyMins, already
 * split at month ends by the Summary screen) + incidental earned in the PREVIOUS month.
 */
fun estimateUsd(
    logs: List<FlightLog>, homeBase: String, year: Int, month0: Int, flyMins: Int, rates: PayRates
): UsdEstimate {
    val scan = buildTrips(logs, homeBase)
    val rate = overseasRate(rates, year, month0)
    val waiver = longStayWaiverHours(scan.trips, year, month0)
    val diffHours = flyMins / 60.0 - rates.minHours
    val overseas = if (diffHours >= 0) {
        rate.monthly + diffHours * rate.hourly
    } else {
        rate.monthly - max(0.0, -diffHours - waiver) * rate.hourly
    }.coerceAtLeast(0.0)

    val prevYear = if (month0 == 0) year - 1 else year
    val prevMonth0 = if (month0 == 0) 11 else month0 - 1
    val previous = incidentalForMonth(scan.trips, prevYear, prevMonth0, rates.incidentalPerDay)
    val current = incidentalForMonth(scan.trips, year, month0, rates.incidentalPerDay)

    val total = overseas + previous.usd
    val rounded = if (rates.usdRoundStep > 0) ceil(total / rates.usdRoundStep - 1e-9) * rates.usdRoundStep else total

    val (windowStart, windowEnd) = monthBoundsUtc(prevYear, prevMonth0).first to monthBoundsUtc(year, month0).second
    val incomplete = scan.incompleteDays.count { it in (windowStart - DAY_MS) until windowEnd }

    val monthEndMissing = monthEndFlightsMissingTimes(logs, year, month0)

    return UsdEstimate(
        flyMins, rates.minHours, rate, waiver, overseas, previous, current, total, rounded,
        rates.usdRoundStep, incomplete, monthEndMissing
    )
}

/**
 * Flight sectors dated the last day of the previous month or of this month that have no valid
 * Block off and Block on. Without them the app cannot split the flight at 00:00 UTC.
 */
fun monthEndFlightsMissingTimes(logs: List<FlightLog>, year: Int, month0: Int): Int {
    val (monthStart, monthEnd) = monthBoundsUtc(year, month0)
    val lastDays = setOf(monthStart - DAY_MS, monthEnd - DAY_MS)
    return logs.count { log ->
        log.recordType == "Flight" && log.dutyType != "Day Off" &&
            log.departure.isNotBlank() && log.arrival.isNotBlank() &&
            parseDayUtc(log.flightDate)?.let { it in lastDays } == true &&
            !(hasClockTime(log.blockOff) && hasClockTime(log.blockOn))
    }
}

private fun hasClockTime(value: String): Boolean {
    val digits = value.filter { it.isDigit() }
    return digits.length == 4 && com.example.mylogbook.domain.duty.isValidTime(digits)
}
