package com.example.mylogbook.domain.duty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    @Test
    fun durationMinutes_lastTwoDigitsAreMinutes() {
        assertEquals(77 * 60 + 21, durationMinutes("7721"))
        assertEquals(120 * 60 + 30, durationMinutes("12030"))
        assertEquals(45, durationMinutes("45"))
        assertNull(durationMinutes("0175"))   // 75 minutes is not valid
        assertNull(durationMinutes(""))
    }

    @Test
    fun formatting() {
        assertEquals("77:21", formatDuration(77 * 60 + 21))
        assertEquals("-1:30", formatDuration(-90))
        assertEquals("77.35", decimalHoursText(77 * 60 + 21))
    }

    @Test
    fun clockDifference_crossesMidnight() {
        assertEquals(4 * 60 + 45, clockDifferenceMinutes("2330", "0415"))
        assertEquals(90, clockDifferenceMinutes("1000", "1130"))
        assertNull(clockDifferenceMinutes("2460", "0100"))
    }
}