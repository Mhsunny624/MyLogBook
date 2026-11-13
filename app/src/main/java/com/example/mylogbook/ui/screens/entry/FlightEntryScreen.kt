/*
 * Add / edit form with tabs Flight, Training, Health Card (incl. Grounded) and Sick.
 * A day of flying is entered as chained sectors and saved as one FlightLog per sector.
 */
package com.example.mylogbook.ui.screens.entry

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.data.TRAINING_OPTIONS
import com.example.mylogbook.domain.duty.calculateClaimOffForDay
import com.example.mylogbook.domain.duty.calculateShortRest
import com.example.mylogbook.domain.duty.calculateTimeDifference
import com.example.mylogbook.domain.duty.formatWithColon
import com.example.mylogbook.ui.components.DenseOutlinedTextField
import com.example.mylogbook.domain.duty.isClockTime
import com.example.mylogbook.domain.duty.getMinsFromTimeString
import com.example.mylogbook.domain.duty.isValidTime
import com.example.mylogbook.domain.duty.normalizeTimeStr
import com.example.mylogbook.ui.components.AirportAutoCompleteTextField
import com.example.mylogbook.ui.components.CompactTimeField
import com.example.mylogbook.ui.components.FlightNumberField
import com.example.mylogbook.ui.components.RestRequirementsContent
import com.example.mylogbook.viewmodel.FlightLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

data class ChainedSector(
    var id: Int = 0,
    var flightNo: String = "",
    var departure: String = "",
    var arrival: String = "",
    var blockOff: String = "",
    var blockOn: String = "",
    var totalBlock: String = "",
    var schedDep: String = ""
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightEntryScreen(onSaveSuccess: (String) -> Unit, vm: FlightLogViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedLogs by vm.allLogs.collectAsState()
    val previewHomeBase = remember {
        context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE).getString("profile_base", "DAC") ?: "DAC"
    }

    val tabs = listOf("Flight", "Training", "Health Card", "Sick")

    val initialPage = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % tabs.size)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    val coroutineScope = rememberCoroutineScope()

    var trainingType by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var healthMode by remember { mutableStateOf("Health Card") }
    var groundReason by remember { mutableStateOf("") }
    var logbookNumber by remember { mutableStateOf("") }
    var aircraftReg by remember { mutableStateOf("S2-") }
    var dutyType by remember { mutableStateOf("Operating") }

    var flightDate by remember { mutableStateOf("") }
    var dutyOnTime by remember { mutableStateOf("") }
    var dutyOffTime by remember { mutableStateOf("") }
    var totalDutyTime by remember { mutableStateOf("") }
    var crewNames by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val sectors = remember { mutableStateListOf(ChainedSector()) }

    var detailsExpanded by remember { mutableStateOf(true) }
    var sectorsExpanded by remember { mutableStateOf(true) }
    var dutyExpanded by remember { mutableStateOf(false) }
    var crewNotesExpanded by remember { mutableStateOf(false) }

    var dateError by remember { mutableStateOf(false) }
    var trainingTypeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(vm.logsBeingEdited) {
        val editingLogs = vm.logsBeingEdited
        if (!editingLogs.isNullOrEmpty()) {
            val firstLog = editingLogs.first()

            val targetIndex = tabs.indexOf(firstLog.recordType).takeIf { it >= 0 } ?: 0
            val currentActual = pagerState.currentPage % tabs.size
            var diff = targetIndex - currentActual
            if (diff > tabs.size / 2) diff -= tabs.size else if (diff < -(tabs.size / 2)) diff += tabs.size
            pagerState.scrollToPage(pagerState.currentPage + diff)

            trainingType = firstLog.trainingType
            expiryDate = firstLog.expiryDate
            logbookNumber = firstLog.logbookNumber
            aircraftReg = firstLog.aircraftReg

            dutyType = if (firstLog.recordType == "Day Off") "Day Off" else firstLog.dutyType.ifEmpty { "Operating" }

            flightDate = firstLog.flightDate
            endDate = if (firstLog.recordType == "Training" || (firstLog.recordType == "Health Card" && firstLog.trainingType == "Grounded")) firstLog.dutyOff else ""
            healthMode = if (firstLog.recordType == "Health Card" && firstLog.trainingType == "Grounded") "Grounded" else "Health Card"
            groundReason = if (healthMode == "Grounded") firstLog.dutyType else ""

            val dutySourceLog = editingLogs.firstOrNull { it.dutyOn.isNotBlank() } ?: firstLog
            dutyOnTime = dutySourceLog.dutyOn.filter { it.isDigit() }
            dutyOffTime = dutySourceLog.dutyOff.filter { it.isDigit() }
            totalDutyTime = dutySourceLog.totalDuty.filter { it.isDigit() }

            crewNames = firstLog.crewNames
            notes = firstLog.notes.replace(Regex("\\(Sector \\d+ of \\d+\\)$"), "").trim()

            sectors.clear()
            editingLogs.sortedBy { it.id }.forEach { log ->
                sectors.add(ChainedSector(
                    id = log.id,
                    flightNo = log.flightNo,
                    departure = log.departure,
                    arrival = log.arrival,
                    blockOff = log.blockOff.filter { it.isDigit() },
                    blockOn = log.blockOn.filter { it.isDigit() },
                    totalBlock = log.totalBlock.filter { it.isDigit() },
                    schedDep = log.schedDep.filter { it.isDigit() }
                ))
            }
        } else {
            val currentActual = pagerState.currentPage % tabs.size
            var diff = 0 - currentActual
            if (diff > tabs.size / 2) diff -= tabs.size else if (diff < -(tabs.size / 2)) diff += tabs.size
            pagerState.scrollToPage(pagerState.currentPage + diff)

            sectors.clear()
            sectors.add(ChainedSector())
            dutyOnTime = ""; dutyOffTime = ""; totalDutyTime = ""
            flightDate = ""; aircraftReg = "S2-"; logbookNumber = ""; notes = ""; crewNames = ""
            healthMode = "Health Card"; groundReason = ""
            trainingType = ""; expiryDate = ""; endDate = ""; dutyType = "Operating"
        }
        dateError = false
    }

    fun updateAutoDutyTime(on: String, off: String) {
        val calculated = calculateTimeDifference(on, off)
        if (calculated != null) totalDutyTime = calculated
    }

    fun updateAutoBlockTime(off: String, on: String, index: Int) {
        val calculated = calculateTimeDifference(off, on)
        if (calculated != null) sectors[index] = sectors[index].copy(totalBlock = calculated)
    }

    // Date pickers open on the date already in the field (today when empty).
    fun pickerStart(value: String, fallback: String = ""): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance()
        val text = value.ifBlank { fallback }
        try { SimpleDateFormat("dd/MM/yyyy", Locale.US).parse(text)?.let { cal.time = it } } catch (_: Exception) {}
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }
    fun formatPicked(y: Int, m: Int, d: Int) = String.format("%02d/%02d/%04d", d, m + 1, y)

    val (fy, fm, fd) = pickerStart(flightDate)
    val datePickerDialog = DatePickerDialog(context, { _: DatePicker, y: Int, m: Int, d: Int ->
        flightDate = formatPicked(y, m, d)
        dateError = false
    }, fy, fm, fd)

    val (ey, em, ed) = pickerStart(expiryDate, flightDate)
    val expiryPickerDialog = DatePickerDialog(context, { _: DatePicker, y: Int, m: Int, d: Int ->
        expiryDate = formatPicked(y, m, d)
    }, ey, em, ed)

    val (ny, nm, nd) = pickerStart(endDate, flightDate)
    val endDatePickerDialog = DatePickerDialog(context, { _: DatePicker, y: Int, m: Int, d: Int ->
        endDate = formatPicked(y, m, d)
    }, ny, nm, nd)

    val actualCurrentPage = pagerState.currentPage % tabs.size

    // ADD .systemBarsPadding() RIGHT HERE:
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Text(
            text = if (vm.logsBeingEdited != null) "Edit Log Entry" else "New Log Entry",
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)
        )

        SecondaryScrollableTabRow(
            selectedTabIndex = actualCurrentPage,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                val icon = when(title) {
                    "Flight" -> Icons.Filled.AirplanemodeActive
                    "Training" -> Icons.Filled.School
                    "Health Card" -> Icons.Filled.LocalHospital
                    else -> Icons.Filled.Sick
                }
                Tab(
                    selected = actualCurrentPage == index,
                    onClick = {
                        var pageDiff = index - actualCurrentPage
                        if (pageDiff > tabs.size / 2) pageDiff -= tabs.size
                        else if (pageDiff < -(tabs.size / 2)) pageDiff += tabs.size
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + pageDiff) }
                    },
                    text = { Text(title) },
                    icon = { Icon(icon, contentDescription = title) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val actualPage = page % tabs.size
            val currentRecordType = tabs[actualPage]

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentRecordType != "Flight") {
                    val isGrounded = currentRecordType == "Health Card" && healthMode == "Grounded"
                    if (currentRecordType == "Health Card") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = healthMode == "Health Card", onClick = { healthMode = "Health Card" }, label = { Text("Health Card") })
                            FilterChip(selected = healthMode == "Grounded", onClick = { healthMode = "Grounded" }, label = { Text("Grounded") })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = flightDate, onValueChange = {},
                                label = { Text(if (isGrounded) "Grounded from" else "Start Date") },
                                modifier = Modifier.fillMaxWidth(), readOnly = true, isError = dateError, singleLine = true
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { datePickerDialog.show() })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = endDate, onValueChange = {},
                                label = { Text(if (isGrounded) "Until" else "End Date") },
                                placeholder = { Text(if (isGrounded) "Ongoing" else "Same day") },
                                modifier = Modifier.fillMaxWidth(), readOnly = true, singleLine = true
                            )
                            Box(modifier = Modifier.matchParentSize().clickable { endDatePickerDialog.show() })
                        }
                    }
                    if (currentRecordType == "Training") {
                        val filteredTrainings = TRAINING_OPTIONS.filter { it.contains(trainingType, ignoreCase = true) }
                        ExposedDropdownMenuBox(expanded = trainingTypeExpanded, onExpandedChange = { trainingTypeExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = trainingType, onValueChange = { trainingType = it; trainingTypeExpanded = true }, label = { Text("Type of Training (Select or Type)") },
                                trailingIcon = { Text(if (trainingTypeExpanded) "▲" else "▼", modifier = Modifier.padding(end = 12.dp)) },
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), singleLine = true
                            )
                            if (filteredTrainings.isNotEmpty()) {
                                ExposedDropdownMenu(expanded = trainingTypeExpanded, onDismissRequest = { trainingTypeExpanded = false }) {
                                    filteredTrainings.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { trainingType = option; trainingTypeExpanded = false }) }
                                }
                            }
                        }
                    }
                    if (isGrounded) {
                        OutlinedTextField(
                            value = groundReason, onValueChange = { groundReason = it },
                            label = { Text("Reason (optional)") },
                            placeholder = { Text("e.g. Medical, BMI") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Medical", "BMI", "Administrative").forEach { r ->
                                FilterChip(selected = groundReason == r, onClick = { groundReason = if (groundReason == r) "" else r }, label = { Text(r, maxLines = 1, softWrap = false) })
                            }
                        }
                        if (endDate.isNotBlank()) {
                            TextButton(onClick = { endDate = "" }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Clear end date (still grounded)") }
                        } else {
                            Text("Leave \"Until\" blank while you are still grounded.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expiryDate, onValueChange = {},
                            label = { Text(if (currentRecordType == "Sick") "Cleared for Duty (End Date)" else "Valid Until (Expiry Date)") },
                            modifier = Modifier.fillMaxWidth(), readOnly = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { expiryPickerDialog.show() })
                    }
                    }
                }

                if (currentRecordType == "Flight") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().clickable { detailsExpanded = !detailsExpanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Flight details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (!detailsExpanded) {
                                        val summaryText = listOfNotNull(flightDate.ifEmpty { null }, if (logbookNumber.isNotEmpty()) "Logbook #$logbookNumber" else null, aircraftReg.ifEmpty { null }).joinToString(" • ").ifEmpty { "Not set" }
                                        Text(summaryText, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                Text(if (detailsExpanded) "▲" else "▼", fontSize = 14.sp)
                            }
                            if (detailsExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = flightDate,
                                        onValueChange = {},
                                        label = { Text(if (dutyType == "Day Off") "Date" else "Date Logged (Departure)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = true,
                                        isError = dateError,
                                        singleLine = true
                                    )
                                    Box(modifier = Modifier.matchParentSize().clickable { datePickerDialog.show() })
                                }

                                if (dutyType != "Day Off") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = logbookNumber, onValueChange = { logbookNumber = it }, label = { Text("Logbook #") }, modifier = Modifier.weight(0.4f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                        OutlinedTextField(value = aircraftReg, onValueChange = { aircraftReg = it.uppercase() }, label = { Text("Aircraft Reg") }, modifier = Modifier.weight(0.6f), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                ) {
                                    Text("Duty Type: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                                    FilterChip(selected = dutyType == "Operating", onClick = { dutyType = "Operating" }, label = { Text("Operating", maxLines = 1, softWrap = false) }, modifier = Modifier.padding(end = 6.dp))
                                    FilterChip(selected = dutyType == "DHC", onClick = { dutyType = "DHC" }, label = { Text("DHC", maxLines = 1, softWrap = false) }, modifier = Modifier.padding(end = 6.dp))
                                    FilterChip(selected = dutyType == "Day Off", onClick = { dutyType = "Day Off" }, label = { Text("Day Off", maxLines = 1, softWrap = false) })
                                }
                            }
                        }
                    }

                    if (dutyType != "Day Off") {
                        // ---------- Route & sectors ----------
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                val routeSummary = buildString {
                                    sectors.forEachIndexed { idx, s ->
                                        if (idx == 0) append(s.departure.ifBlank { "---" })
                                        append(" → ")
                                        append(s.arrival.ifBlank { "---" })
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().clickable { sectorsExpanded = !sectorsExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Route & sectors", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("$routeSummary • ${sectors.size} sector${if (sectors.size > 1) "s" else ""}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text(if (sectorsExpanded) "▲" else "▼", fontSize = 14.sp)
                                }
                                if (sectorsExpanded) {
                                    Text("All times in UTC", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                                    sectors.forEachIndexed { index, sector ->
                                        if (index > 0) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                        }
                                        val blockFromTimes = isClockTime(sector.blockOff) && isClockTime(sector.blockOn)
                                        Column(
                                            modifier = Modifier.padding(top = if (index == 0) 6.dp else 0.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Header: sector number + route, its block time, delete.
                                            Row(modifier = Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                                val route = if (sector.departure.isNotBlank() || sector.arrival.isNotBlank())
                                                    " · ${sector.departure.ifBlank { "---" }} → ${sector.arrival.ifBlank { "---" }}" else ""
                                                Text("Sector ${index + 1}$route", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f), maxLines = 1)
                                                if (blockFromTimes) {
                                                    Text("Block time ${formatWithColon(sector.totalBlock)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                if (sectors.size > 1) {
                                                    IconButton(onClick = { sectors.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove sector", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                                                    }
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                FlightNumberField(
                                                    value = sector.flightNo,
                                                    onValueChange = { sectors[index] = sector.copy(flightNo = it) },
                                                    modifier = Modifier.weight(1.6f)
                                                )
                                                if (index == 0) {
                                                    AirportAutoCompleteTextField(label = "From", value = sector.departure, isError = false, onValueChange = { sectors[index] = sector.copy(departure = it) }, modifier = Modifier.weight(1f))
                                                } else {
                                                    DenseOutlinedTextField(value = sector.departure, onValueChange = {}, readOnly = true, label = "From", modifier = Modifier.weight(1f))
                                                }
                                                AirportAutoCompleteTextField(
                                                    label = "To", value = sector.arrival, isError = false,
                                                    onValueChange = { newVal ->
                                                        sectors[index] = sector.copy(arrival = newVal)
                                                        if (index + 1 < sectors.size) sectors[index + 1] = sectors[index + 1].copy(departure = newVal)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (index == 0) {
                                                    // Scheduled departure of the first sector: used to calculate Duty On.
                                                    CompactTimeField("Sched. departure", sector.schedDep, { c -> sectors[index] = sector.copy(schedDep = c) }, Modifier.weight(1.15f)) { focusManager.moveFocus(FocusDirection.Next) }
                                                }
                                                CompactTimeField("Block off", sector.blockOff, { c ->
                                                    sectors[index] = sector.copy(blockOff = c)
                                                    updateAutoBlockTime(c, sector.blockOn, index)
                                                }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                                CompactTimeField("Block on", sector.blockOn, { c ->
                                                    sectors[index] = sector.copy(blockOn = c)
                                                    updateAutoBlockTime(sector.blockOff, c, index)
                                                }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                            }
                                            if (!blockFromTimes) {
                                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("No block off/on? Enter block time", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                                                    CompactTimeField("Block time", sector.totalBlock, { c -> sectors[index] = sector.copy(totalBlock = c) }, Modifier.width(110.dp))
                                                }
                                            }
                                        }
                                    }
                                    // Total of all sectors (multi-sector days only).
                                    if (sectors.size > 1) {
                                        val totalMins = sectors.sumOf { getMinsFromTimeString(it.totalBlock) }
                                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Total block time (${sectors.size} sectors)", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(String.format("%02d:%02d", totalMins / 60, totalMins % 60), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            val prevArr = sectors.lastOrNull()?.arrival ?: ""
                                            sectors.add(ChainedSector(departure = prevArr))
                                        },
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add sector")
                                    }
                                }
                            }
                        }

                        // ---------- Duty times ----------
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().clickable { dutyExpanded = !dutyExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Duty times", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        val dutySummary = if (dutyOnTime.length == 4 && dutyOffTime.length == 4) {
                                            "On ${formatWithColon(dutyOnTime)} • Off ${formatWithColon(dutyOffTime)}" +
                                                if (totalDutyTime.isNotBlank()) " • Total ${formatWithColon(totalDutyTime)}" else ""
                                        } else "Not set"
                                        Text(dutySummary, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text(if (dutyExpanded) "▲" else "▼", fontSize = 14.sp)
                                }
                                if (dutyExpanded) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CompactTimeField("Duty On", dutyOnTime, { c ->
                                            dutyOnTime = c
                                            updateAutoDutyTime(c, dutyOffTime)
                                        }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                        CompactTimeField("Duty Off", dutyOffTime, { c ->
                                            dutyOffTime = c
                                            updateAutoDutyTime(dutyOnTime, c)
                                        }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                    }
                                    val dutyFromTimes = dutyOnTime.length == 4 && isValidTime(dutyOnTime) && dutyOffTime.length == 4 && isValidTime(dutyOffTime)
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalButton(
                                            onClick = {
                                                // Duty On is always based on the SCHEDULED departure (delays don't move report time).
                                                val schedDep = sectors.firstOrNull()?.schedDep ?: ""
                                                val firstDep = sectors.firstOrNull()?.departure ?: ""
                                                val lastOn = sectors.lastOrNull()?.blockOn ?: ""
                                                if (schedDep.length != 4 || !isValidTime(schedDep)) {
                                                    Toast.makeText(context, "Please enter the scheduled departure (Sched) first.", Toast.LENGTH_SHORT).show()
                                                } else if (lastOn.length != 4 || !isValidTime(lastOn)) {
                                                    Toast.makeText(context, "Please enter the Block On time of the last sector first.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val reportOffsetMins = if (firstDep.uppercase() == "DAC") 105 else 75
                                                    val onMins = ((getMinsFromTimeString(schedDep) - reportOffsetMins) % 1440 + 1440) % 1440
                                                    val offMins = (getMinsFromTimeString(lastOn) + 45) % 1440
                                                    val newDutyOn = String.format("%02d%02d", onMins / 60, onMins % 60)
                                                    val newDutyOff = String.format("%02d%02d", offMins / 60, offMins % 60)
                                                    dutyOnTime = newDutyOn
                                                    dutyOffTime = newDutyOff
                                                    updateAutoDutyTime(newDutyOn, newDutyOff)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Calculate", fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (dutyFromTimes) {
                                            Text("Total ${formatWithColon(totalDutyTime)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            CompactTimeField("Total", totalDutyTime, { c -> totalDutyTime = c }, Modifier.width(110.dp))
                                        }
                                    }
                                    Text(
                                        "Calculate uses Sched minus 1:45 (DAC) or 1:15 (outstation), and the last Block On plus 0:45.",
                                        fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp)
                                    )

                                    // Live preview of short rest + post-flight off duty, using this (unsaved)
                                    // entry together with the trip's earlier saved sectors.
                                    val editingIds = vm.logsBeingEdited?.map { it.id }?.toSet() ?: emptySet()
                                    val previewLogs = sectors.mapIndexed { i, sec ->
                                        FlightLog(
                                            id = Int.MAX_VALUE - 100 + i,
                                            recordType = "Flight", trainingType = "", expiryDate = "", logbookNumber = "",
                                            flightNo = sec.flightNo, aircraftReg = "", departure = sec.departure, arrival = sec.arrival,
                                            dutyType = dutyType, flightDate = flightDate,
                                            dutyOn = if (i == 0) dutyOnTime else "",
                                            dutyOff = if (i == 0) dutyOffTime else "",
                                            totalDuty = if (i == 0) totalDutyTime else "",
                                            blockOff = sec.blockOff, blockOn = sec.blockOn, totalBlock = sec.totalBlock,
                                            crewNames = "", notes = ""
                                        )
                                    }
                                    val previewRest = if (dutyType != "Day Off" && flightDate.isNotBlank()) calculateShortRest(previewLogs) else null
                                    val previewClaimOff = if (dutyType != "Day Off" && flightDate.isNotBlank()) {
                                        calculateClaimOffForDay(previewLogs, savedLogs.filter { it.id !in editingIds } + previewLogs, previewHomeBase)
                                    } else null

                                    if (previewRest != null || previewClaimOff != null) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Hotel, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Rest Requirements", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                RestRequirementsContent(previewRest, previewClaimOff)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { crewNotesExpanded = !crewNotesExpanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (currentRecordType == "Flight") "Crew & notes" else "Notes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (!crewNotesExpanded) {
                                    val hasData = (currentRecordType == "Flight" && crewNames.isNotEmpty()) || notes.isNotEmpty()
                                    Text(if (hasData) "Added" else "Optional", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Text(if (crewNotesExpanded) "▲" else "▼", fontSize = 14.sp)
                        }
                        if (crewNotesExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            if (currentRecordType == "Flight" && dutyType != "Day Off") {
                                OutlinedTextField(value = crewNames, onValueChange = { crewNames = it }, label = { Text("Crew Members") }, modifier = Modifier.fillMaxWidth(), maxLines = 2, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(if (currentRecordType == "Flight") "Remarks / Notes / Details" else "Notes / Remarks") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        }
                    }
                }
            }
        }

        val activeRecordType = tabs[actualCurrentPage]
        val saveGrounded = activeRecordType == "Health Card" && healthMode == "Grounded"

        Button(
            onClick = {
                dateError = flightDate.isBlank()
                var hasError = dateError

                if (activeRecordType == "Flight" && dutyType != "Day Off") {
                    sectors.forEach { sec ->
                        if (sec.flightNo.isBlank() || sec.departure.isBlank() || sec.arrival.isBlank()) hasError = true
                        if (!isValidTime(sec.blockOff) || !isValidTime(sec.blockOn)) hasError = true
                    }
                }

                val groundedRangeBad = (saveGrounded || activeRecordType == "Training") && endDate.isNotBlank() && flightDate.isNotBlank() && run {
                    val sdfG = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                    try { sdfG.parse(endDate)!!.before(sdfG.parse(flightDate)) } catch (_: Exception) { false }
                }
                if (hasError) {
                    Toast.makeText(context, "Please complete required fields for all sectors", Toast.LENGTH_SHORT).show()
                } else if (groundedRangeBad) {
                    Toast.makeText(context, if (saveGrounded) "\"Until\" date cannot be before the \"Grounded from\" date" else "End date cannot be before the start date", Toast.LENGTH_LONG).show()
                } else if (activeRecordType == "Flight" && dutyType != "Day Off" && (!isValidTime(dutyOnTime) || !isValidTime(dutyOffTime))) {
                    Toast.makeText(context, "Times must be valid 4-digit HHMM", Toast.LENGTH_LONG).show()
                } else {
                    var calculatedTrainingDuty = ""
                    var finalEndDate = endDate
                    if (activeRecordType == "Training") {
                        try {
                            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                            val start = sdf.parse(flightDate)
                            val end = if (endDate.isNotBlank()) sdf.parse(endDate) else start
                            if (start != null && end != null) {
                                val diffInMillis = end.time - start.time
                                val days = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt() + 1
                                val totalMins = 1.coerceAtLeast(days) * 8 * 60
                                calculatedTrainingDuty = String.format("%02d%02d", totalMins / 60, totalMins % 60)
                                if (endDate.isBlank()) finalEndDate = flightDate
                            }
                        } catch (_: Exception) {
                            calculatedTrainingDuty = "0800"
                        }
                    }

                    // A flying day saves one record per sector; everything else (incl. Day Off) is one record.
                    val isFlyingDay = activeRecordType == "Flight" && dutyType != "Day Off"
                    val sectorsToSave = if (isFlyingDay) sectors.toList() else sectors.take(1)
                    val toSave = mutableListOf<FlightLog>()
                    sectorsToSave.forEachIndexed { index, sec ->

                        val savedRecordType = if (activeRecordType == "Flight" && dutyType == "Day Off") "Day Off" else activeRecordType
                        val savedFlightNo = if (activeRecordType == "Flight" && dutyType == "Day Off") "DAY OFF" else if (activeRecordType == "Flight") sec.flightNo else ""

                        val newLog = FlightLog(
                            id = sec.id,
                            recordType = savedRecordType,
                            trainingType = if (activeRecordType == "Training") trainingType else if (saveGrounded) "Grounded" else "",
                            expiryDate = if (saveGrounded) "" else if (activeRecordType == "Training" || activeRecordType == "Health Card" || activeRecordType == "Sick") expiryDate else "",
                            logbookNumber = if (activeRecordType == "Flight" && dutyType != "Day Off") logbookNumber else "",
                            flightNo = savedFlightNo,
                            aircraftReg = if (activeRecordType == "Flight" && dutyType != "Day Off") aircraftReg else "",
                            departure = if (activeRecordType == "Flight" && dutyType != "Day Off") sec.departure else "",
                            arrival = if (activeRecordType == "Flight" && dutyType != "Day Off") sec.arrival else "",
                            dutyType = if (activeRecordType == "Flight") dutyType else if (saveGrounded) groundReason.trim() else "",
                            flightDate = flightDate,
                            dutyOn = if (activeRecordType == "Flight" && index == 0 && dutyType != "Day Off") dutyOnTime else "",
                            dutyOff = if (activeRecordType == "Flight" && index == 0 && dutyType != "Day Off") dutyOffTime else if (activeRecordType == "Training") finalEndDate else if (saveGrounded) endDate else "",
                            totalDuty = if (activeRecordType == "Flight" && index == 0 && dutyType != "Day Off") totalDutyTime else if (activeRecordType == "Training") calculatedTrainingDuty else "",
                            blockOff = if (activeRecordType == "Flight" && dutyType != "Day Off") sec.blockOff else "",
                            blockOn = if (activeRecordType == "Flight" && dutyType != "Day Off") sec.blockOn else "",
                            totalBlock = if (activeRecordType == "Flight" && dutyType != "Day Off") normalizeTimeStr(sec.totalBlock) else "",
                            crewNames = if (activeRecordType == "Flight" && dutyType != "Day Off") crewNames else "",
                            notes = if (isFlyingDay && sectorsToSave.size > 1) "$notes (Sector ${index + 1} of ${sectorsToSave.size})".trim() else notes.trim(),
                            schedDep = if (activeRecordType == "Flight" && index == 0 && dutyType != "Day Off") sec.schedDep else ""
                        )
                        toSave += newLog
                    }

                    // Records of the original entry that are no longer part of it (removed sectors,
                    // or extra sectors when a flying day becomes a Day Off / another record type).
                    val keptIds = toSave.map { it.id }.filter { it != 0 }.toSet()
                    val deleteIds = vm.logsBeingEdited.orEmpty().map { it.id }.filter { it !in keptIds }
                    vm.saveEntries(toSave, deleteIds)


                    Toast.makeText(context, if (vm.logsBeingEdited != null) "Updated!" else "Saved!", Toast.LENGTH_SHORT).show()
                    vm.logsBeingEdited = null
                    onSaveSuccess(activeRecordType)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (vm.logsBeingEdited != null) "Update entry" else "Save entry", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
