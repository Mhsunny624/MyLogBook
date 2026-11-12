/*
 * Single ViewModel shared by all screens: exposes the logbook as a StateFlow, saves/deletes
 * entries and writes the automatic JSON backup after each change.
 */
package com.example.mylogbook.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mylogbook.data.AppDatabase
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.data.backup.BackupJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FlightLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).flightLogDao()

    val allLogs: StateFlow<List<FlightLog>> = dao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var logsBeingEdited by mutableStateOf<List<FlightLog>?>(null)

    fun saveLog(log: FlightLog) {
        viewModelScope.launch {
            dao.insertLog(log)
            triggerAutoBackup()
        }
    }

    fun deleteLog(log: FlightLog) {
        viewModelScope.launch {
            dao.deleteLog(log)
            triggerAutoBackup()
        }
    }

    @SuppressLint("UseKtx")
    private fun triggerAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE)
            val folderUriStr = prefs.getString("auto_backup_folder", "")

            if (folderUriStr.isNullOrEmpty()) return@launch // Do nothing if folder isn't set

            try {
                val jsonRoot = BackupJson.build(allLogs.value, prefs, "My Log Book")

                val folderUri = folderUriStr.toUri()
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)

                var backupFile = documentFile?.findFile("FlightLogAutoBackup.json")
                if (backupFile == null) {
                    backupFile = documentFile?.createFile("application/json", "FlightLogAutoBackup.json")
                }

                backupFile?.uri?.let { fileUri ->
                    context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                        outputStream.write(jsonRoot.toString(2).toByteArray(Charsets.UTF_8))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
