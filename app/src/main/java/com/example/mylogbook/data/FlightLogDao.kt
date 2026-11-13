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

    /** Snapshot of every entry in insertion order (id ascending); used for backups. */
    @Query("SELECT * FROM flight_logs ORDER BY id ASC")
    suspend fun getAllLogsOnce(): List<FlightLog>

    @Query("SELECT * FROM flight_logs WHERE recordType IN ('Training', 'Health Card', 'Sick')")
    suspend fun getExpiringRecords(): List<FlightLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: FlightLog)

    @Delete
    suspend fun deleteLog(log: FlightLog)

    @Query("DELETE FROM flight_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    @Query("DELETE FROM flight_logs")
    suspend fun deleteAll()
}
