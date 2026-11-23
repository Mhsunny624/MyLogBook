/*
 * Fills the missing one of Block off / Block on / Block time from the other two, so a sector
 * needs only two of them. Pure logic, used by the entry form (see FlightEntryScreen).
 */
package com.example.mylogbook.domain.duty

/** Which field the app filled in itself (it keeps it updated); typed fields are never overwritten. */
enum class BlockField { NONE, OFF, ON, BLOCK }

/**
 * Result of [fillBlockTimes]. [dayShift] is +1 when the filled-in Block on is on the next UTC day,
 * -1 when the filled-in Block off is on the previous UTC day, otherwise 0.
 */
data class BlockFill(
    val off: String, val on: String, val block: String,
    val auto: BlockField, val dayShift: Int = 0
)

/** "HHMM" block duration (00:01 to 23:59), as typed in the Block time box. */
fun isBlockDuration(value: String): Boolean {
    val digits = value.filter { it.isDigit() }
    if (digits.length != 4) return false
    val h = digits.substring(0, 2).toInt()
    val m = digits.substring(2, 4).toInt()
    return h in 0..23 && m in 0..59 && h * 60 + m > 0
}

private fun mins(value: String): Int {
    val d = value.filter { it.isDigit() }
    return d.substring(0, 2).toInt() * 60 + d.substring(2, 4).toInt()
}

private fun hhmm(totalMins: Int): String {
    val m = ((totalMins % 1440) + 1440) % 1440
    return String.format(java.util.Locale.US, "%02d%02d", m / 60, m % 60)
}

/**
 * Recalculates after the user typed into [edited]. [auto] is the field the app filled in before
 * this edit; typing into that field makes it a typed field.
 *
 * Priority: the pair that includes the field just edited wins, then Block off + Block on.
 * If the fields an auto value came from are no longer complete, the auto value is cleared.
 */
fun fillBlockTimes(off: String, on: String, block: String, auto: BlockField, edited: BlockField): BlockFill {
    val wasAuto = if (edited == auto) BlockField.NONE else auto
    // Still typing (1–3 digits): leave everything as it is until the time is complete.
    val editedValue = when (edited) { BlockField.OFF -> off; BlockField.ON -> on; BlockField.BLOCK -> block; BlockField.NONE -> "" }
    val typed = editedValue.count { it.isDigit() }
    if (typed in 1..3) return BlockFill(off, on, block, wasAuto)
    val offK = isClockTime(off) && wasAuto != BlockField.OFF
    val onK = isClockTime(on) && wasAuto != BlockField.ON
    val blkK = isBlockDuration(block) && wasAuto != BlockField.BLOCK

    fun fromOffOn(): BlockFill {
        val d = mins(on) - mins(off)
        return BlockFill(off, on, hhmm(if (d <= 0) d + 1440 else d), BlockField.BLOCK)
    }
    fun fromOffBlock(): BlockFill {
        val end = mins(off) + mins(block)
        return BlockFill(off, hhmm(end), block, BlockField.ON, if (end >= 1440) 1 else 0)
    }
    fun fromOnBlock(): BlockFill {
        val start = mins(on) - mins(block)
        return BlockFill(hhmm(start), on, block, BlockField.OFF, if (start < 0) -1 else 0)
    }

    return when {
        edited == BlockField.BLOCK && offK && blkK -> fromOffBlock()
        edited == BlockField.BLOCK && onK && blkK -> fromOnBlock()
        offK && onK -> fromOffOn()
        offK && blkK -> fromOffBlock()
        onK && blkK -> fromOnBlock()
        // Not enough typed values: drop a stale auto value.
        wasAuto == BlockField.OFF -> BlockFill("", on, block, BlockField.NONE)
        wasAuto == BlockField.ON -> BlockFill(off, "", block, BlockField.NONE)
        wasAuto == BlockField.BLOCK -> BlockFill(off, on, "", BlockField.NONE)
        else -> BlockFill(off, on, block, BlockField.NONE)
    }
}
