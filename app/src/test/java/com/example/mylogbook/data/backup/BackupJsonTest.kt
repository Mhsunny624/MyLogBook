package com.example.mylogbook.data.backup

import com.example.mylogbook.data.FlightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupJsonTest {

    private fun log(
        type: String, date: String = "10/01/2030", flightNo: String = "", dep: String = "", arr: String = "",
        subtype: String = "", end: String = "", expiry: String = ""
    ) = FlightLog(
        recordType = type, trainingType = subtype, expiryDate = expiry, logbookNumber = "",
        flightNo = flightNo, aircraftReg = "", departure = dep, arrival = arr, dutyType = "",
        flightDate = date, dutyOn = "", dutyOff = end, totalDuty = "", blockOff = "", blockOn = "",
        totalBlock = "", crewNames = "", notes = ""
    )

    @Test
    fun chainSectors_restoresRouteOrder() {
        val a = log("Flight", flightNo = "BG1", dep = "DAC", arr = "ZYL")
        val b = log("Flight", flightNo = "BG1", dep = "ZYL", arr = "LHR")
        assertEquals(listOf(a, b), BackupJson.chainSectors(listOf(b, a)))
        val c = log("Flight", flightNo = "BG2", dep = "LHR", arr = "ZYL")
        val d = log("Flight", flightNo = "BG2", dep = "ZYL", arr = "DAC")
        assertEquals(listOf(c, d), BackupJson.chainSectors(listOf(d, c)))
    }

    @Test
    fun duplicateKey_keepsDifferentRecordsOnTheSameDay() {
        val card = log("Health Card", expiry = "10/01/2031")
        val grounded = log("Health Card", subtype = "Grounded", end = "20/01/2030")
        assertNotEquals(BackupJson.duplicateKey(card), BackupJson.duplicateKey(grounded))
        val t1 = log("Training", subtype = "SEP", end = "11/01/2030")
        val t2 = log("Training", subtype = "CRM", end = "10/01/2030")
        assertNotEquals(BackupJson.duplicateKey(t1), BackupJson.duplicateKey(t2))
        assertEquals(BackupJson.duplicateKey(t1), BackupJson.duplicateKey(t1.copy(notes = "edited")))
    }
}
