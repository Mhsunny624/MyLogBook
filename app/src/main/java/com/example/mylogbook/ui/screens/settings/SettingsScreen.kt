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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.mylogbook.data.backup.BackupJson
import com.example.mylogbook.export.PdfExporter
import com.example.mylogbook.viewmodel.FlightLogViewModel
import com.example.mylogbook.workers.ExpiryWorker
import java.util.Calendar
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

    val backupLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val jsonRoot = BackupJson.build(allLogs, prefs, airlineName)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonRoot.toString(2).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup saved successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                if (jsonString != null) {
                    val jsonRoot = JSONObject(jsonString)

                    BackupJson.restoreSettings(jsonRoot, prefs)

                    val jsonLogs = jsonRoot.getJSONArray("logs")
                    var restoredCount = 0

                    for (i in 0 until jsonLogs.length()) {
                        val obj = jsonLogs.getJSONObject(i)

                        val importedLog = BackupJson.logFromJson(obj)

                        val isDuplicate = allLogs.any { existing ->
                            existing.flightDate == importedLog.flightDate &&
                                    existing.recordType == importedLog.recordType &&
                                    existing.flightNo == importedLog.flightNo &&
                                    existing.departure == importedLog.departure &&
                                    existing.arrival == importedLog.arrival
                        }

                        if (!isDuplicate) {
                            vm.saveLog(importedLog)
                            restoredCount++
                        }
                    }

                    if (restoredCount > 0) {
                        Toast.makeText(context, "Successfully imported $restoredCount new entries!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No new entries found to import.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Restore failed: Invalid backup file", Toast.LENGTH_LONG).show()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            prefs.edit { putString("auto_backup_folder", uri.toString()) }
            Toast.makeText(context, "Auto-Backup folder set! All future entries will save here.", Toast.LENGTH_LONG).show()
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

        PaySettingsCard(prefs)

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

                val currentFolder = prefs.getString("auto_backup_folder", "")
                if (currentFolder.isNullOrEmpty()) {
                    Text("Select a folder on your phone to automatically save a backup file every time you log a flight.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    Text("✅ Auto-Backup is Active.", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
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
    }
}
