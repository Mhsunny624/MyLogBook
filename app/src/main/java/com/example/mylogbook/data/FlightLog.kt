package com.example.mylogbook.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the logbook. The same table holds every kind of record; [recordType] says which,
 * and some columns are reused by non-flight records (see the table below).
 *
 * All dates are "dd/MM/yyyy". All times are UTC "HHmm" strings (4 digits, no colon).
 *
 * | recordType    | Meaning of the reused columns                                               |
 * |---------------|-----------------------------------------------------------------------------|
 * | "Flight"      | One sector. dutyType = "Operating" / "DHC" / "Day Off". Duty times are saved |
 * |               | on the first sector of the day; schedDep only on sector 1.                  |
 * | "Training"    | trainingType = course name, flightDate = start, dutyOff = end date,          |
 * |               | totalDuty = 8 h per day, expiryDate = validity.                             |
 * | "Health Card" | expiryDate = card validity.                                                 |
 * |   (Grounded)  | trainingType = "Grounded", flightDate = from, dutyOff = until (blank while   |
 * |               | still grounded), dutyType = reason, expiryDate = "" (no expiry alerts).      |
 * | "Sick"        | expiryDate = cleared-for-duty date.                                         |
 *
 * Changing this class changes the database schema: bump the version in [AppDatabase] and add a
 * Migration in Migrations.kt, otherwise the app will refuse to open existing user data.
 */
@Entity(tableName = "flight_logs")
data class FlightLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordType: String,
    val trainingType: String,
    val expiryDate: String,
    val logbookNumber: String,
    val flightNo: String,      // e.g. "BG202"
    val aircraftReg: String,   // e.g. "S2-AJS"
    val departure: String,     // IATA code
    val arrival: String,       // IATA code
    val dutyType: String,
    val flightDate: String,
    val dutyOn: String,
    val dutyOff: String,
    val totalDuty: String,
    val blockOff: String,
    val blockOn: String,
    val totalBlock: String,
    val crewNames: String,
    val notes: String,
    @ColumnInfo(defaultValue = "") val schedDep: String = "",   // scheduled departure (UTC)
    val createdAt: Long = System.currentTimeMillis()
)
