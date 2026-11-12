/*
 * Room queries for the flight_logs table.
 */
package com.example.mylogbook.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightLogDao {
    @Query("SELECT * FROM flight_logs ORDER BY createdAt DESC")
    fun getAllLogs(): Flow<List<FlightLog>>

    @Query("SELECT * FROM flight_logs WHERE recordType IN ('Training', 'Health Card', 'Sick')")
    suspend fun getExpiringRecords(): List<FlightLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FlightLog)

    @Delete
    suspend fun deleteLog(log: FlightLog)
}
