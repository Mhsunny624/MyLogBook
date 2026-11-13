package com.example.mylogbook.domain.duty

import com.example.mylogbook.data.FlightLog
import org.junit.Assert.assertEquals
import org.junit.Test

/** Midnight crossings within a duty day (fictional data, year 2030). */
class SectorTimesTest {

    private fun sector(
        id: Int, dep: String, arr: String, off: String = "", block: String = "",
        sched: String = "", dutyOn: String = "", dutyOff: String = "", totalDuty: String = ""
    ) = FlightLog(
        id = id, recordType = "Flight", trainingType = "", expiryDate = "", logbookNumber = "",
        flightNo = "BG000", aircraftReg = "", departure = dep, arrival = arr, dutyType = "Operating",
        flightDate = "10/01/2030", dutyOn = dutyOn, dutyOff = dutyOff, totalDuty = totalDuty,
        blockOff = off, blockOn = "", totalBlock = block, crewNames = "", notes = "", schedDep = sched
    )

    private val day = dayStartUtc("10/01/2030")!!
    private val minute = 60_000L

    @Test
    fun dutyOn_laterThanDeparture_isPreviousDay() {
        val first = sector(1, "DAC", "CCU", sched = "0030")
        assertEquals(day - 75 * minute, dutyOnInstant(day, "2245", first))   // 09/01 22:45Z
        assertEquals(day + 22 * 60 * minute, dutyOnInstant(day, "2200", sector(1, "DAC", "DXB", sched = "2345")))
    }

    @Test
    fun laterSector_afterMidnight_rollsToNextDay() {
        val logs = listOf(
            sector(1, "DAC", "ZYL", off = "2200", block = "0045"),
            sector(2, "ZYL", "LHR", off = "0030", block = "1100")
        )
        val starts = blockOffInstants(logs)
        assertEquals(day + 22 * 60 * minute, starts[1])
        assertEquals(day + (24 * 60 + 30) * minute, starts[2])   // 11/01 00:30Z, not 10/01
    }

    @Test
    fun shortRest_forEarlyMorningDeparture_endsSameDay() {
        // Duty On 22:45Z on 09/01 (04:45 BST 10/01), 6 h duty -> off 04:45Z, rest 12 h -> 16:45Z = 22:45 BST.
        val day1 = listOf(
            sector(1, "DAC", "CCU", sched = "0030", dutyOn = "2245", dutyOff = "0445", totalDuty = "0600"),
            sector(2, "CCU", "DAC")
        )
        assertEquals("10 Jan 2030, 22:45", calculateShortRest(day1)!!.endTimeLocalText)
    }
}
