/*
 * Records tab: searchable list of training and health card records.
 */
package com.example.mylogbook.ui.screens.records

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.ui.components.DailyLogCard
import com.example.mylogbook.viewmodel.FlightLogViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(vm: FlightLogViewModel, title: String, allowedTypes: List<String>, onEditClicked: () -> Unit) {
    val logs by vm.allLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE)
    val homeBase = prefs.getString("profile_base", "DAC") ?: "DAC"

    val baseLogs = logs.filter { it.recordType in allowedTypes }
    val filteredLogs = baseLogs.filter {
        it.flightNo.contains(searchQuery, ignoreCase = true) ||
                it.departure.contains(searchQuery, ignoreCase = true) ||
                it.arrival.contains(searchQuery, ignoreCase = true) ||
                it.aircraftReg.contains(searchQuery, ignoreCase = true) ||
                it.trainingType.contains(searchQuery, ignoreCase = true)
    }

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val sortedLogs = filteredLogs.sortedByDescending { log ->
        try { sdf.parse(log.flightDate) } catch (_: Exception) { Date(0) }
    }

    val logsByDate = sortedLogs.groupBy { it.flightDate }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Search records...") }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), singleLine = true)

        if (sortedLogs.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No entries found.", color = Color.Gray) }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                logsByDate.forEach { (date, dailyLogs) ->
                    item {
                        DailyLogCard(date = date, dailyLogs = dailyLogs, allLogs = logs, homeBase = homeBase, vm = vm, onEditClicked = onEditClicked)
                    }
                }
            }
        }
    }
}
