package com.example.mylogbook.domain.duty

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockFillTest {

    @Test
    fun offPlusBlock_fillsOn_acrossMidnight() {
        val r = fillBlockTimes("2151", "", "0833", BlockField.NONE, BlockField.BLOCK)
        assertEquals("0624", r.on)
        assertEquals(BlockField.ON, r.auto)
        assertEquals(1, r.dayShift)
    }

    @Test
    fun onPlusBlock_fillsOff_onPreviousDay() {
        val r = fillBlockTimes("", "0624", "0833", BlockField.NONE, BlockField.ON)
        assertEquals("2151", r.off)
        assertEquals(BlockField.OFF, r.auto)
        assertEquals(-1, r.dayShift)
    }

    @Test
    fun offAndOn_fillBlock_andAutoValueFollowsEdits() {
        var r = fillBlockTimes("1224", "1730", "", BlockField.NONE, BlockField.ON)
        assertEquals("0506", r.block)
        assertEquals(BlockField.BLOCK, r.auto)
        r = fillBlockTimes("1200", r.on, r.block, r.auto, BlockField.OFF)
        assertEquals("0530", r.block)
    }

    @Test
    fun typingIntoAutoField_makesItTyped_andStillTypingChangesNothing() {
        val start = fillBlockTimes("2151", "", "0833", BlockField.NONE, BlockField.BLOCK)   // on = 0624 auto
        val typing = fillBlockTimes(start.off, start.on, "08", start.auto, BlockField.BLOCK)
        assertEquals("0624", typing.on)
        val typedOn = fillBlockTimes(start.off, "0630", start.block, start.auto, BlockField.ON)
        assertEquals("0839", typedOn.block)                                                    // off + on now win
        assertEquals(BlockField.BLOCK, typedOn.auto)
    }

    @Test
    fun clearingASource_clearsTheAutoValue() {
        val start = fillBlockTimes("2151", "", "0833", BlockField.NONE, BlockField.BLOCK)
        val cleared = fillBlockTimes(start.off, start.on, "", start.auto, BlockField.BLOCK)
        assertEquals("", cleared.on)
        assertEquals(BlockField.NONE, cleared.auto)
    }
}
