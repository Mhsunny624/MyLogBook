/*
 * Turns logbook sectors into trips away from base (Block Off at base -> Block On at base),
 * used by the incidental allowance and the long-stay waiver.
 */
package com.example.mylogbook.domain.pay

import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.duty.dutyOnInstant
import com.example.mylogbook.domain.duty.getMinsFromTimeString
import com.example.mylogbook.domain.duty.isValidTime

/** One rotation away from base: Block Off at base -> Block On back at base (epoch millis, UTC). */
data class Trip(val start: Long, val end: Long, val estimated: Boolean) {
    val durationMs: Long get() = end - start
}

data class TripScan(val trips: List<Trip>, val incompleteDays: List<Long>)

private data class SectorTimes(
    val dep: String, val arr: String, val day: Long,
    val off: Long?, val on: Long?, val offEstimated: Boolean, val onEstimated: Boolean
)

private fun validHhmm(value: String): Boolean {
    val digits = value.filter { it.isDigit() }
    return digits.length == 4 && isValidTime(digits)
}

private fun atOrAfter(time: Long, floor: Long?): Long {
    var t = time
    if (floor != null) while (t < floor) t += DAY_MS
    return t
}

/**
 * Works out each sector's Block Off / Block On instant. Missing block times are estimated from
 * the duty times with the Biman duty rules: Duty On = departure - 1h45m (base) or - 1h15m (outstation),
 * Duty Off = Block On + 45m.
 */
private fun sectorTimes(logs: List<FlightLog>, homeBase: String): List<SectorTimes> {
    val base = homeBase.uppercase()
    val flights = logs.filter {
        it.recordType == "Flight" && it.departure.isNotBlank() && it.arrival.isNotBlank()
    }
    val days = flights.groupBy { it.flightDate }
        .mapNotNull { (date, group) -> parseDayUtc(date)?.let { it to group.sortedBy { log -> log.id } } }
        .sortedBy { it.first }

    val result = mutableListOf<SectorTimes>()
    for ((day, group) in days) {
        val dutyLog = group.firstOrNull { validHhmm(it.dutyOn) }
        val dutyOnAt = dutyLog?.let { dutyOnInstant(day, it.dutyOn, group.first()) }
        val dutyMins = dutyLog?.let { getMinsFromTimeString(it.totalDuty) } ?: 0
        val dutyOffAt = if (dutyOnAt != null && dutyMins > 0) dutyOnAt + dutyMins * MIN_MS else null

        var cursor: Long? = null
        group.forEachIndexed { index, s ->
            val dep = s.departure.uppercase()
            val arr = s.arrival.uppercase()
            val blockMs = getMinsFromTimeString(s.totalBlock) * MIN_MS
            val isFirst = index == 0
            val isLast = index == group.lastIndex
            var off: Long? = null
            var on: Long? = null
            var offEst = false
            var onEst = false

            if (validHhmm(s.blockOff)) off = atOrAfter(day + getMinsFromTimeString(s.blockOff) * MIN_MS, cursor)
            if (validHhmm(s.blockOn)) on = atOrAfter(day + getMinsFromTimeString(s.blockOn) * MIN_MS, off ?: cursor)

            if (off == null && on != null && blockMs > 0) off = on - blockMs
            if (off == null && isFirst) {
                if (validHhmm(s.schedDep)) {
                    off = day + getMinsFromTimeString(s.schedDep) * MIN_MS; offEst = true
                } else if (dutyOnAt != null) {
                    off = dutyOnAt + (if (dep == base) 105 else 75) * MIN_MS; offEst = true
                }
            }
            if (on == null) {
                if (off != null && !offEst && blockMs > 0) {
                    on = off + blockMs
                } else if (isLast && dutyOffAt != null) {
                    on = dutyOffAt - 45 * MIN_MS; onEst = true
                } else if (off != null && blockMs > 0) {
                    on = off + blockMs; onEst = true
                }
            }
            if (off == null && on != null && blockMs > 0) { off = on - blockMs; offEst = onEst }

            result += SectorTimes(dep, arr, day, off, on, offEst, onEst)
            cursor = on ?: off ?: cursor
        }
    }
    return result
}

/** Every rotation that leaves base and returns to base, including same-day turnarounds. */
fun buildTrips(logs: List<FlightLog>, homeBase: String): TripScan {
    val base = homeBase.uppercase()
    val trips = mutableListOf<Trip>()
    val incomplete = mutableListOf<Long>()
    var openStart: Long? = null
    var openEst = false
    var openDay: Long? = null
    var open = false

    for (s in sectorTimes(logs, homeBase)) {
        if (s.dep == base) {
            if (open) openDay?.let { incomplete += it }  // left base again without a logged return
            open = true; openStart = s.off; openEst = s.offEstimated; openDay = s.day
        }
        if (s.arr == base && open) {
            val start = openStart
            val end = s.on
            if (start != null && end != null && end > start) {
                trips += Trip(start, end, openEst || s.onEstimated)
            } else {
                incomplete += s.day
            }
            open = false; openStart = null; openDay = null
        }
    }
    return TripScan(trips, incomplete)
}
