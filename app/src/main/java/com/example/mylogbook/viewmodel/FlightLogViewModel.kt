/*
 * Single ViewModel shared by all screens: exposes the logbook as a StateFlow, saves/deletes
 * entries and writes the automatic JSON backup after each change.
 */
package com.example.mylogbook.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.mylogbook.data.AppDatabase
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.data.backup.AutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FlightLogViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.flightLogDao()

    val allLogs: StateFlow<List<FlightLog>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var logsBeingEdited by mutableStateOf<List<FlightLog>?>(null)

    /**
     * Saves one entry (a day's sectors, or a single record) in one transaction, in list order, so
     * new sectors get ascending ids and keep their order. [deleteIds] are removed in the same
     * transaction (e.g. sectors removed while editing). Then writes one auto backup.
     */
    fun saveEntries(toSave: List<FlightLog>, deleteIds: Collection<Int> = emptyList()) {
        viewModelScope.launch {
            db.withTransaction {
                if (deleteIds.isNotEmpty()) dao.deleteByIds(deleteIds.toList())
                toSave.forEach { dao.insertLog(it) }
            }
            backupNow()
        }
    }

    fun saveLog(log: FlightLog) = saveEntries(listOf(log))

    fun deleteLogs(logs: List<FlightLog>) {
        if (logs.isEmpty()) return
        viewModelScope.launch {
            dao.deleteByIds(logs.map { it.id })
            backupNow()
        }
    }

    fun deleteLog(log: FlightLog) = deleteLogs(listOf(log))

    /**
     * Restores logs from a backup (already in insertion order). With [replaceAll] the current
     * logbook is deleted first, in the same transaction. Calls [onDone] on the main thread with
     * the number of entries added.
     */
    fun importLogs(logs: List<FlightLog>, replaceAll: Boolean, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            db.withTransaction {
                if (replaceAll) dao.deleteAll()
                logs.forEach { dao.insertLog(it.copy(id = 0)) }
            }
            backupNow()
            onDone(logs.size)
        }
    }

    /** Every entry, read straight from the database (not the UI list, which can lag behind). */
    suspend fun snapshot(): List<FlightLog> = dao.getAllLogsOnce()

    /** Runs an auto backup now (e.g. right after the folder is chosen). */
    fun runAutoBackup(onDone: () -> Unit = {}) {
        viewModelScope.launch { backupNow(); onDone() }
    }

    private suspend fun backupNow() = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val prefs = context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE)
        AutoBackup.run(context, prefs, dao.getAllLogsOnce())
    }
}
