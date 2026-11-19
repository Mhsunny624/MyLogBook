package com.example.mylogbook.domain.pay

import com.example.mylogbook.data.FlightLog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pay rules checked with made-up logbook entries (year 2030, fictional data).
 * Expected values are worked out by hand from the rules in docs/RULES.md.
 * Allowance months split at 00:00 UTC.
 */
class PayCalculatorTest {

    private fun sector(
        id: Int, date: String, dep: String, arr: String, off: String, on: String, block: String
    ) = FlightLog(
        id = id, recordType = "Flight", trainingType = "", expiryDate = "", logbookNumber = "",
        flightNo = "BG000", aircraftReg = "", departure = dep, arrival = arr, dutyType = "Operating",
        flightDate = date, dutyOn = "", dutyOff = "", totalDuty = "",
        blockOff = off, blockOn = on, totalBlock = block, crewNames = "", notes = ""
    )

    private fun grounded(id: Int, from: String, until: String) = FlightLog(
        id = id, recordType = "Health Card", trainingType = "Grounded", expiryDate = "", logbookNumber = "",
        flightNo = "", aircraftReg = "", departure = "", arrival = "", dutyType = "Medical",
        flightDate = from, dutyOn = "", dutyOff = until, totalDuty = "",
        blockOff = "", blockOn = "", totalBlock = "", crewNames = "", notes = ""
    )

    // Trip 1: away 02/03 10:00Z -> 03/03 14:00Z = 28 h, all in March.
    // Trip 2: away 31/03 12:00Z -> 01/04 12:00Z = 12 h in March + 12 h in April (split at 00:00 UTC).
    private val logs = listOf(
        sector(1, "02/03/2030", "DAC", "DXB", "1000", "1430", "0430"),
        sector(2, "03/03/2030", "DXB", "DAC", "1000", "1400", "0400"),
        sector(3, "31/03/2030", "DAC", "JED", "1200", "1800", "0600"),
        sector(4, "01/04/2030", "JED", "DAC", "0600", "1200", "0600")
    )

    private fun rates(joining: String = "01/01/2020", basic: String = "20000") = payRatesFrom { key ->
        when (key) {
            "pay_crew_type" -> "Contractual"
            "pay_joining_date" -> joining
            "pay_basic" -> basic
            else -> PAY_DEFAULTS[key] ?: ""
        }
    }

    @Test
    fun serviceYears_countsCompletedYears() {
        assertEquals(10, serviceYears("01/01/2020", 2030, 3))
        assertEquals(2, serviceYears("15/06/2027", 2030, 3))
    }

    @Test
    fun contractualTiers_followServiceLength() {
        assertEquals(1500.0, overseasRate(rates("01/01/2020"), 2030, 3).monthly, 0.001)
        assertEquals(1200.0, overseasRate(rates("01/03/2027"), 2030, 3).monthly, 0.001)
        assertEquals(17.0, overseasRate(rates("01/03/2027"), 2030, 3).hourly, 0.001)
        assertEquals(1000.0, overseasRate(rates("15/06/2027"), 2030, 3).monthly, 0.001)
    }

    @Test
    fun incidental_isHoursAway_splitAtUtcMidnight() {
        val trips = buildTrips(logs, "DAC").trips
        assertEquals(2, trips.size)
        val march = incidentalForMonth(trips, 2030, 2, 12.0)
        assertEquals(2, march.trips)
        assertEquals(40 * 60L, march.minutes)          // 28 h + 12 h
        assertEquals(20.0, march.usd, 0.001)           // 40 / 24 x 12
        val april = incidentalForMonth(trips, 2030, 3, 12.0)
        assertEquals(6.0, april.usd, 0.001)            // 12 / 24 x 12
    }

    @Test
    fun usd_aboveSeventyHours_addsHourlyRate_andPreviousIncidental() {
        val usd = estimateUsd(logs, "DAC", 2030, 3, 75 * 60, rates())
        assertEquals(1605.0, usd.overseas, 0.001)      // 1500 + 5 x 21
        assertEquals(20.0, usd.previousIncidental.usd, 0.001)
        assertEquals(1625.0, usd.total, 0.001)
        assertEquals(1625.0, usd.rounded, 0.001)       // no rounding by default
    }

    @Test
    fun usd_belowSeventyHours_deductsHourlyRate() {
        val usd = estimateUsd(logs, "DAC", 2030, 4, 60 * 60 + 30, rates())
        assertEquals(1300.5, usd.overseas, 0.001)      // 1500 - 9.5 x 21
        assertEquals(1306.5, usd.total, 0.001)         // + April incidental 6.00
        assertEquals(1306.5, usd.rounded, 0.001)
    }

    @Test
    fun longStay_waivesTenShortfallHours() {
        val longTrip = listOf(
            sector(10, "05/06/2030", "DAC", "LHR", "0100", "1300", "1200"),
            sector(11, "11/06/2030", "LHR", "DAC", "1000", "2000", "1000")
        )
        val usd = estimateUsd(longTrip, "DAC", 2030, 5, 55 * 60, rates())
        assertEquals(10, usd.waiverHours)
        assertEquals(1395.0, usd.overseas, 0.001)      // 1500 - (15 - 10) x 21
    }

    @Test
    fun taka_multipliesPayrollHoursAsPlainNumber() {
        val taka = estimateTaka(rates(), 75 * 60 + 30)
        assertEquals(75.30, taka.payrollHours, 0.0001)
        assertEquals(12048.0, taka.flyingAllowance, 0.001)  // 75.30 x 160
        assertEquals(848.0, taka.flyingOt, 0.001)           // 5.30 x 160
        assertEquals(40402.0, taka.fixedTotal, 0.001)       // 20000 x 1.70 + 2708 + 2594 + 1100
        assertEquals(54298.0, taka.total, 0.001)            // + uniform 1000
    }

    @Test
    fun groundedDays_splitAcrossMonths_andOngoing() {
        val period = listOf(grounded(900, "15/09/2030", "05/10/2030"))
        assertEquals(16, groundedDaysInMonth(period, 2030, 8))
        assertEquals(5, groundedDaysInMonth(period, 2030, 9))
        val today = parseDayUtc("12/11/2030")!!
        val ongoing = listOf(grounded(901, "01/11/2030", ""))
        assertEquals(12, groundedDaysInMonth(ongoing, 2030, 10, today))
        assertEquals(0, groundedDaysInMonth(ongoing, 2030, 11, today))
    }

    @Test
    fun shuttle_sameDayOutAndBack_earnsNoIncidental() {
        val withShuttle = logs + listOf(
            // Out and back on one duty (same logbook day): a shuttle.
            sector(20, "10/03/2030", "DAC", "SHJ", "0300", "0800", "0500"),
            sector(21, "10/03/2030", "SHJ", "DAC", "0930", "1430", "0500")
        )
        val trips = buildTrips(withShuttle, "DAC").trips
        assertEquals(1, trips.count { it.shuttle })
        val march = incidentalForMonth(trips, 2030, 2, 12.0)
        assertEquals(2, march.trips)                   // the two layover trips only
        assertEquals(1, march.shuttles)
        assertEquals(20.0, march.usd, 0.001)           // unchanged by the shuttle
    }

    @Test
    fun flyingHours_splitAtUtcMidnight_andMonthEndFlightWithoutTimesIsFlagged() {
        // 31/10 21:50Z -> 01/11 06:20Z: 2:10 in October, 6:20 in November.
        val bounds = monthBoundsUtc(2030, 9)
        val off = parseDayUtc("31/10/2030")!! + (21 * 60 + 50) * MIN_MS
        assertEquals(130L, (bounds.second - off) / MIN_MS)

        val noTimes = listOf(sector(30, "31/10/2030", "YYZ", "FCO", "", "", "0830"))
        assertEquals(1, monthEndFlightsMissingTimes(noTimes, 2030, 9))   // October: its own last day
        assertEquals(1, monthEndFlightsMissingTimes(noTimes, 2030, 10))  // November: previous month's last day
        assertEquals(0, monthEndFlightsMissingTimes(noTimes, 2030, 8))
        val withTimes = listOf(sector(31, "31/10/2030", "YYZ", "FCO", "2150", "0620", "0830"))
        assertEquals(0, monthEndFlightsMissingTimes(withTimes, 2030, 9))
    }
}
