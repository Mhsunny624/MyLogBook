/*
 * Settings tab: appearance, profile, pay settings, backup/restore and PDF export.
 */
package com.example.mylogbook.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mylogbook.BuildConfig
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.ui.components.UpdateStatus
import com.example.mylogbook.ui.components.rememberUpdateFlow
import com.example.mylogbook.data.backup.AutoBackup
import com.example.mylogbook.data.backup.BackupJson
import com.example.mylogbook.export.PdfExporter
import com.example.mylogbook.viewmodel.FlightLogViewModel
import com.example.mylogbook.workers.ExpiryWorker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@SuppressLint("UseKt")
@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    airlineName: String,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    vm: FlightLogViewModel
) {
    val context = LocalContext.current
    val allLogs by vm.allLogs.collectAsState()
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf(prefs.getString("profile_name", "") ?: "") }
    var staffId by remember { mutableStateOf(prefs.getString("profile_id", "") ?: "") }
    var rosterName by remember { mutableStateOf(prefs.getString("profile_roster_name", "") ?: "") }

    val calendar = Calendar.getInstance()
    val currentMonth = String.format(java.util.Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1)
    val currentYear = calendar.get(Calendar.YEAR).toString()

    var exportMonth by remember { mutableStateOf(currentMonth) }
    var exportYear by remember { mutableStateOf(currentYear) }

    val scope = rememberCoroutineScope()
    var backupStatusTick by remember { mutableIntStateOf(0) }   // bumps when a restore finishes
    var autoBackupTick by remember { mutableIntStateOf(0) }     // bumps when the auto backup status changes
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AutoBackup.KEY_LAST_OK || key == AutoBackup.KEY_LAST_ERROR || key == AutoBackup.KEY_FOLDER) autoBackupTick++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val logs = vm.snapshot()
                    val bytes = BackupJson.build(logs, prefs, airlineName).toString(2).toByteArray(Charsets.UTF_8)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                            ?: throw IllegalStateException("Cannot open the file")
                    }
                    Toast.makeText(context, "Backup saved: ${logs.size} entries", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val root = withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                            ?: throw IllegalStateException("Cannot open the file")
                        JSONObject(text)
                    }
                    val backupLogs = BackupJson.readLogs(root)
                    val current = vm.snapshot()
                    val existingKeys = current.map { BackupJson.duplicateKey(it) }.toHashSet()
                    val missing = backupLogs.filter { BackupJson.duplicateKey(it) !in existingKeys }
                    pendingRestore = PendingRestore(root, backupLogs, missing, current.size)
                } catch (_: Exception) {
                    Toast.makeText(context, "Restore failed: this is not a MyLogBook backup file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    pendingRestore?.let { pr ->
        fun finish(replaceAll: Boolean) {
            val logs = if (replaceAll) pr.backupLogs else pr.missing
            BackupJson.restoreSettings(pr.root, prefs)
            name = prefs.getString("profile_name", "") ?: ""
            staffId = prefs.getString("profile_id", "") ?: ""
            rosterName = prefs.getString("profile_roster_name", "") ?: ""
            pendingRestore = null
            if (logs.isEmpty() && !replaceAll) {
                Toast.makeText(context, "Nothing new to add: all entries are already in the app.", Toast.LENGTH_LONG).show()
                return
            }
            vm.importLogs(logs, replaceAll) { count ->
                backupStatusTick++
                Toast.makeText(context, if (replaceAll) "Restored $count entries" else "Added $count entries", Toast.LENGTH_LONG).show()
            }
        }
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The backup has ${pr.backupLogs.size} entries. The app has ${pr.currentCount} now.")
                    if (pr.currentCount > 0) {
                        Text("Replace all: the app's entries are deleted and replaced with the backup (exact copy).", fontSize = 13.sp)
                        Text("Add missing: keeps your entries and adds the ${pr.missing.size} entries that are not in the app.", fontSize = 13.sp)
                    }
                    Text("Profile and pay settings are also restored from the backup.", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { finish(replaceAll = true) }) { Text(if (pr.currentCount > 0) "Replace all" else "Restore") }
            },
            dismissButton = {
                Row {
                    if (pr.currentCount > 0) TextButton(onClick = { finish(replaceAll = false) }) { Text("Add missing") }
                    TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
                }
            }
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                prefs.edit { putString(AutoBackup.KEY_FOLDER, uri.toString()) }
                vm.runAutoBackup {
                    backupStatusTick++
                    val error = prefs.getString(AutoBackup.KEY_LAST_ERROR, "").orEmpty()
                    Toast.makeText(context, error.ifEmpty { "Auto backup folder set. A backup was saved there now." }, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not use that folder: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    // NEW: Triggers the system "Save As" menu for the PDF
    val pdfLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            PdfExporter.generateMonthlyReport(
                context = context,
                uri = uri,
                logs = allLogs,
                month = exportMonth,
                year = exportYear,
                name = name,
                staffId = staffId
            )
        }
    }

    // THE MAIN UI COLUMN
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("System", "Light", "Dark").forEach { themeOption ->
                        val isSelected = currentTheme == themeOption
                        if (isSelected) {
                            Button(
                                onClick = { onThemeChange(themeOption) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = Color.White
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(themeOption, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onThemeChange(themeOption) },
                                modifier = Modifier.weight(1f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ) {
                                Text(themeOption, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Profile Information", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rosterName,
                        onValueChange = { rosterName = it.uppercase() },
                        label = { Text("Roster Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                    OutlinedTextField(
                        value = staffId,
                        onValueChange = { staffId = it },
                        label = { Text("Staff / Crew ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        prefs.edit {
                            putString("profile_name", name)
                            putString("profile_id", staffId)
                            putString("profile_roster_name", rosterName)
                        }
                        Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) { Text("Save Profile") }
            }
        }

        key(backupStatusTick) { PaySettingsCard(prefs) }   // reload after a restore

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup & Restore", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Important: Backups are saved locally. To restore your logbook on a new phone, you must first manually transfer your backup file to the new device.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 16.sp
                    )
                }

                val currentFolder = prefs.getString(AutoBackup.KEY_FOLDER, "")
                // Re-read the status whenever a backup or restore finishes.
                val (lastOk, lastError) = remember(autoBackupTick) {
                    prefs.getLong(AutoBackup.KEY_LAST_OK, 0L) to prefs.getString(AutoBackup.KEY_LAST_ERROR, "").orEmpty()
                }
                if (currentFolder.isNullOrEmpty()) {
                    Text("Select a folder on your phone to automatically save a backup file every time you log a flight.", fontSize = 13.sp, color = Color.Gray)
                } else if (lastError.isNotEmpty()) {
                    Text("⚠️ $lastError", fontSize = 13.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                } else {
                    val lastText = if (lastOk > 0) SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(lastOk)) else "not yet"
                    Text("✅ Auto backup is on. Last backup: $lastText", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Text("Keeps FlightLogAutoBackup.json plus one dated copy per day for 14 days.", fontSize = 12.sp, color = Color.Gray)
                }

                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (currentFolder.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                ) {
                    Text(if (currentFolder.isNullOrEmpty()) "Set Auto-Backup Folder" else "Change Auto-Backup Folder")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { backupLauncher.launch("crew_logbook_manual_backup.json") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Manual Backup") }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("📂 Restore") }
                }
            }
        }

        // --- NEW DATA EXPORT CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data Export", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generate a monthly PDF report for your records or company submission.", fontSize = 14.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = exportMonth,
                        onValueChange = { if (it.length <= 2) exportMonth = it },
                        label = { Text("Month (MM)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = exportYear,
                        onValueChange = { if (it.length <= 4) exportYear = it },
                        label = { Text("Year (YYYY)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // This opens the save dialog with a default file name!
                        pdfLauncher.launch("Logbook_Report_${exportYear}_${exportMonth}.pdf")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Export Monthly Report (PDF)")
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val testRequest = OneTimeWorkRequestBuilder<ExpiryWorker>().build()
                        WorkManager.getInstance(context).enqueue(testRequest)
                        Toast.makeText(context, "Running expiry check — watch for a notification", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Send Test Expiry Notification Now")
                }
            }
        }

        // --- ABOUT: version and updates ---
        val updates = rememberUpdateFlow()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("MyLogBook ${BuildConfig.VERSION_NAME}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Not an official Biman Bangladesh Airlines app. Pay and rest figures are estimates.", fontSize = 12.sp, color = Color.Gray)
                UpdateStatus(updates, showCheckButton = true)
            }
        }
    }
}

/** A backup file that has been read and is waiting for the user to choose how to restore it. */
private class PendingRestore(
    val root: JSONObject,
    val backupLogs: List<FlightLog>,
    val missing: List<FlightLog>,
    val currentCount: Int
)
