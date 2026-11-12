package com.example.mylogbook.domain.duty

import com.example.mylogbook.data.FlightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Short rest and post-flight off duty, with made-up entries (year 2030). */
class RestRulesTest {

    private fun sector(
        id: Int, date: String, dep: String, arr: String,
        dutyOn: String = "", dutyOff: String = "", totalDuty: String = ""
    ) = FlightLog(
        id = id, recordType = "Flight", trainingType = "", expiryDate = "", logbookNumber = "",
        flightNo = "BG000", aircraftReg = "", departure = dep, arrival = arr, dutyType = "Operating",
        flightDate = date, dutyOn = dutyOn, dutyOff = dutyOff, totalDuty = totalDuty,
        blockOff = "", blockOn = "", totalBlock = "", crewNames = "", notes = ""
    )

    @Test
    fun shortRest_isTwiceTheDuty_afterArrivingDhaka() {
        // Duty 02:00Z -> 12:00Z (10 h) ending in DAC: rest 20 h, ends 08:00Z next day = 14:00 BST.
        val day = listOf(
            sector(1, "10/01/2030", "DAC", "CGP", "0200", "1200", "1000"),
            sector(2, "10/01/2030", "CGP", "SHJ"),
            sector(3, "10/01/2030", "SHJ", "DAC")
        )
        val rest = calculateShortRest(day)!!
        assertEquals("20 hrs 00 mins", rest.durationText)
        assertEquals("11 Jan 2030, 14:00", rest.endTimeLocalText)
    }

    @Test
    fun noShortRest_whenNotEndingInDhaka() {
        assertNull(calculateShortRest(listOf(sector(1, "10/01/2030", "DAC", "DXB", "0200", "0800", "0600"))))
    }

    @Test
    fun postFlightOff_dependsOnDaysAway_andStartsAfterShortRest() {
        // Left DAC 01/01, back 04/01 (4 calendar days -> 2 days off).
        // Last duty 04/01 02:00Z + 8 h = 10:00Z; short rest 16 h -> 05/01 02:00Z; + 2 days = 07/01 02:00Z = 08:00 BST.
        val all = listOf(
            sector(1, "01/01/2030", "DAC", "DXB", "0200", "0800", "0600"),
            sector(2, "04/01/2030", "DXB", "DAC", "0200", "1000", "0800")
        )
        val info = calculateClaimOffForDay(listOf(all[1]), all, "DAC")!!
        assertEquals(4, info.calendarDays)
        assertEquals(2, info.daysOff)
        assertEquals("07 Jan 2030, 08:00", info.endDateTimeText)
    }
}
