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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.SecondaryTabRow
import androidx.activity.compose.BackHandler
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
import com.example.mylogbook.domain.duty.BlockField
import com.example.mylogbook.domain.duty.fillBlockTimes
import com.example.mylogbook.domain.duty.isBlockDuration
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
    var schedDep: String = "",
    /** Which of Block off / Block on / Block time the app filled in (form only, not saved). */
    var autoField: BlockField = BlockField.NONE,
    /** +1 / -1 when the filled-in time is on the next / previous UTC day. */
    var autoDayShift: Int = 0
)

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightEntryScreen(onSaveSuccess: (String) -> Unit, vm: FlightLogViewModel, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val savedLogs by vm.allLogs.collectAsState()
    // ---- From your logbook history: recent trips, last flight number per route, aircraft regs.
    val flightHistory = remember(savedLogs) {
        savedLogs.filter { it.recordType == "Flight" && it.departure.isNotBlank() && it.arrival.isNotBlank() }
    }
    val recentTrips: List<List<ChainedSector>> = remember(flightHistory) {
        flightHistory.groupBy { it.flightDate }.values
            .map { day -> day.sortedBy { it.id } }
            .groupBy { day -> day.map { Triple(it.flightNo, it.departure, it.arrival) } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<List<Triple<String, String, String>>, List<List<FlightLog>>>> { it.value.size }
                .thenByDescending { e -> e.value.maxOf { day -> day.maxOf { it.id } } })
            .take(5)
            .map { e -> e.key.map { (no, dep, arr) -> ChainedSector(flightNo = no, departure = dep, arrival = arr) } }
    }
    fun lastFlightNo(dep: String, arr: String): String =
        flightHistory.filter { it.departure == dep && it.arrival == arr && it.flightNo.isNotBlank() }.maxByOrNull { it.id }?.flightNo.orEmpty()
    val knownRegs = remember(savedLogs) {
        savedLogs.sortedByDescending { it.id }.map { it.aircraftReg.trim() }.filter { it.length > 3 }.distinct().take(12)
    }

    val previewHomeBase = remember {
        context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE).getString("profile_base", "DAC") ?: "DAC"
    }

    val tabs = listOf("Flight", "Training", "Health Card", "Sick", "Document")

    val initialPage = (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % tabs.size)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    val coroutineScope = rememberCoroutineScope()

    var trainingType by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var healthMode by remember { mutableStateOf("Health Card") }
    var docType by remember { mutableStateOf("Passport") }   // Document tab: "Passport" or "Crew ID"
    var pendingWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
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
    var regExpanded by remember { mutableStateOf(false) }

    // Snapshot of everything the user can type, to tell whether Back would lose changes.
    fun formSignature(): String = listOf(
        trainingType, expiryDate, endDate, healthMode, docType, groundReason, logbookNumber, aircraftReg,
        dutyType, flightDate, dutyOnTime, dutyOffTime, totalDutyTime, crewNames, notes,
        sectors.joinToString("|") { "${it.flightNo},${it.departure},${it.arrival},${it.blockOff},${it.blockOn},${it.totalBlock},${it.schedDep}" }
    ).joinToString("¦")
    var baseline by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

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
            docType = if (firstLog.recordType == "Document") firstLog.trainingType.ifBlank { "Passport" } else "Passport"

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
                    // With both times saved, the block time was worked out from them: keep it updating.
                    autoField = if (isClockTime(log.blockOff) && isClockTime(log.blockOn)) BlockField.BLOCK else BlockField.NONE,
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
            trainingType = ""; expiryDate = ""; endDate = ""; dutyType = "Operating"; docType = "Passport"
        }
        dateError = false
        baseline = formSignature()
    }

    // Back with unsaved changes asks first instead of throwing the entry away.
    BackHandler(enabled = baseline != null && formSignature() != baseline) { confirmDiscard = true }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (vm.logsBeingEdited != null) "Discard changes?" else "Discard this entry?") },
            text = { Text("What you typed will not be saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }

    fun updateAutoDutyTime(on: String, off: String) {
        val calculated = calculateTimeDifference(on, off)
        if (calculated != null) totalDutyTime = calculated
    }

    // Any two of Block off / Block on / Block time fill in the third (domain/duty/BlockFill.kt).
    fun editBlock(index: Int, field: BlockField, value: String) {
        val s = sectors[index]
        val off = if (field == BlockField.OFF) value else s.blockOff
        val on = if (field == BlockField.ON) value else s.blockOn
        val blk = if (field == BlockField.BLOCK) value else s.totalBlock
        val r = fillBlockTimes(off, on, blk, s.autoField, field)
        sectors[index] = s.copy(blockOff = r.off, blockOn = r.on, totalBlock = r.block, autoField = r.auto, autoDayShift = r.dayShift)
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

        // Fixed tabs so all five (including Documents) are always visible.
        SecondaryTabRow(
            selectedTabIndex = actualCurrentPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                val icon = when(title) {
                    "Flight" -> Icons.Filled.AirplanemodeActive
                    "Training" -> Icons.Filled.School
                    "Health Card" -> Icons.Filled.LocalHospital
                    "Document" -> Icons.Filled.Badge
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
                    text = {
                        Text(
                            when (title) { "Document" -> "Documents"; "Health Card" -> "Health"; else -> title },
                            fontSize = 11.sp, maxLines = 1, softWrap = false
                        )
                    },
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
                if (currentRecordType == "Document") {
                    // Passport / Crew ID: only the expiry date matters (reminders), no number is stored.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = docType == "Passport", onClick = { docType = "Passport" }, label = { Text("Passport") })
                        FilterChip(selected = docType == "Crew ID", onClick = { docType = "Crew ID" }, label = { Text("Crew ID") })
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expiryDate, onValueChange = {},
                            label = { Text("$docType expiry date") },
                            modifier = Modifier.fillMaxWidth(), readOnly = true, singleLine = true
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { expiryPickerDialog.show() })
                    }
                    Text(
                        if (docType == "Passport") "Reminders 6 months, 3 months and 1 month before expiry, then daily from 7 days."
                        else "Reminders 30 days before expiry, then daily from 7 days.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (currentRecordType != "Flight" && currentRecordType != "Document") {
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
                                        // Suggests the regs you have flown before.
                                        val regMatches = knownRegs.filter { it.contains(aircraftReg.removePrefix("S2-"), ignoreCase = true) && it != aircraftReg }
                                        ExposedDropdownMenuBox(expanded = regExpanded && regMatches.isNotEmpty(), onExpandedChange = { regExpanded = it }, modifier = Modifier.weight(0.6f)) {
                                            OutlinedTextField(
                                                value = aircraftReg, onValueChange = { aircraftReg = it.uppercase(); regExpanded = true },
                                                label = { Text("Aircraft Reg") }, singleLine = true,
                                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth(),
                                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                                            )
                                            ExposedDropdownMenu(expanded = regExpanded && regMatches.isNotEmpty(), onDismissRequest = { regExpanded = false }) {
                                                regMatches.forEach { reg -> DropdownMenuItem(text = { Text(reg) }, onClick = { aircraftReg = reg; regExpanded = false }) }
                                            }
                                        }
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
                                    // Recent trips: one tap fills flight numbers and routes (only while no times are typed).
                                    val noTimesYet = sectors.all { it.blockOff.isBlank() && it.blockOn.isBlank() && it.totalBlock.isBlank() && it.schedDep.isBlank() }
                                    if (vm.logsBeingEdited == null && noTimesYet && recentTrips.isNotEmpty()) {
                                        Text("Recent trips", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            recentTrips.forEach { trip ->
                                                AssistChip(
                                                    onClick = {
                                                        sectors.clear()
                                                        trip.forEach { sectors.add(it.copy()) }
                                                    },
                                                    label = { Text(tripLabel(trip), maxLines = 1) }
                                                )
                                            }
                                        }
                                    }
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
                                                        // Fill the flight number from the last time you flew this route.
                                                        val suggested = if (sector.flightNo.isBlank() && newVal.length == 3) lastFlightNo(sector.departure, newVal) else ""
                                                        sectors[index] = sector.copy(arrival = newVal, flightNo = sector.flightNo.ifBlank { suggested })
                                                        if (index + 1 < sectors.size) sectors[index + 1] = sectors[index + 1].copy(departure = newVal)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (index == 0) {
                                                    // Scheduled departure of the first sector: used to calculate Duty On.
                                                    CompactTimeField("Sched Dep", sector.schedDep, { c -> sectors[index] = sector.copy(schedDep = c) }, Modifier.weight(1.25f)) { focusManager.moveFocus(FocusDirection.Next) }
                                                }
                                                CompactTimeField(if (sector.autoField == BlockField.OFF) "Block off (auto)" else "Block off", sector.blockOff, { c ->
                                                    editBlock(index, BlockField.OFF, c)
                                                }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                                CompactTimeField(if (sector.autoField == BlockField.ON) "Block on (auto)" else "Block on", sector.blockOn, { c ->
                                                    editBlock(index, BlockField.ON, c)
                                                }, Modifier.weight(1f)) { focusManager.moveFocus(FocusDirection.Next) }
                                            }
                                            // Block time is always editable: any two of the three fill in the third.
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                val hint = when {
                                                    sector.autoField == BlockField.ON && sector.autoDayShift > 0 -> "Block on filled in: next day (UTC)"
                                                    sector.autoField == BlockField.OFF && sector.autoDayShift < 0 -> "Block off filled in: day before (UTC). The date should be the block-off date."
                                                    sector.autoField == BlockField.ON -> "Block on filled in from block time"
                                                    sector.autoField == BlockField.OFF -> "Block off filled in from block time"
                                                    blockFromTimes -> ""
                                                    else -> "Enter any two: block off, block on, block time"
                                                }
                                                Text(
                                                    hint, fontSize = 12.sp, lineHeight = 15.sp,
                                                    color = if (sector.autoDayShift < 0) MaterialTheme.colorScheme.error else Color.Gray,
                                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                                )
                                                CompactTimeField(if (sector.autoField == BlockField.BLOCK) "Block time (auto)" else "Block time", sector.totalBlock, { c ->
                                                    editBlock(index, BlockField.BLOCK, c)
                                                }, Modifier.width(130.dp))
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
                                    Row(modifier = Modifier.padding(top = 6.dp)) {
                                        TextButton(onClick = {
                                            val prevArr = sectors.lastOrNull()?.arrival ?: ""
                                            sectors.add(ChainedSector(departure = prevArr))
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add sector")
                                        }
                                        // Reverse of the last sector, with the flight number you last used on that route.
                                        val last = sectors.lastOrNull()
                                        if (last != null && last.departure.isNotBlank() && last.arrival.isNotBlank()) {
                                            TextButton(onClick = {
                                                sectors.add(ChainedSector(
                                                    departure = last.arrival, arrival = last.departure,
                                                    flightNo = lastFlightNo(last.arrival, last.departure)
                                                ))
                                            }) {
                                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add return")
                                            }
                                        }
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

        // Saves the form (called directly, or from "Save anyway" in the warning dialog).
        fun doSave() {
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

        // Warnings for a flying day; empty when nothing looks wrong.
        fun flightWarnings(): List<String> {
            val w = mutableListOf<String>()
            val editingIds = vm.logsBeingEdited.orEmpty().map { it.id }.toSet()
            sectors.forEachIndexed { i, sec ->
                val n = if (sectors.size > 1) "Sector ${i + 1}: " else ""
                val block = getMinsFromTimeString(sec.totalBlock)
                if (block > 18 * 60) w += "${n}block time ${formatWithColon(normalizeTimeStr(sec.totalBlock))} is over 18 hours."
                if (isClockTime(sec.blockOff) && sec.blockOff == sec.blockOn) w += "${n}block off and block on are the same time."
                val fromTimes = calculateTimeDifference(sec.blockOff, sec.blockOn)
                if (fromTimes != null && isBlockDuration(sec.totalBlock) && normalizeTimeStr(sec.totalBlock) != fromTimes)
                    w += "${n}block time ${formatWithColon(sec.totalBlock)} doesn't match block off → block on (${formatWithColon(fromTimes)})."
                val dup = savedLogs.any {
                    it.id !in editingIds && it.recordType == "Flight" && it.flightDate == flightDate &&
                        it.flightNo == sec.flightNo && it.departure == sec.departure && it.arrival == sec.arrival
                }
                if (dup) w += "${n}${sec.flightNo} ${sec.departure}→${sec.arrival} on $flightDate is already in your logbook."
            }
            val lastOn = sectors.lastOrNull()?.blockOn.orEmpty()
            if (isClockTime(dutyOffTime) && isClockTime(lastOn)) {
                // Duty Off should be after the last block on (allowing for midnight).
                val gap = (getMinsFromTimeString(dutyOffTime) - getMinsFromTimeString(lastOn) + 24 * 60) % (24 * 60)
                if (gap > 12 * 60) w += "Duty Off ${formatWithColon(dutyOffTime)} is earlier than the last block on ${formatWithColon(lastOn)}."
            }
            return w
        }

        if (pendingWarnings.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { pendingWarnings = emptyList() },
                title = { Text("Check before saving") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pendingWarnings.forEach { Text("• $it", fontSize = 14.sp) }
                    }
                },
                confirmButton = { TextButton(onClick = { pendingWarnings = emptyList(); doSave() }) { Text("Save anyway") } },
                dismissButton = { TextButton(onClick = { pendingWarnings = emptyList() }) { Text("Fix") } }
            )
        }

        Button(
            onClick = {
                dateError = flightDate.isBlank() && activeRecordType != "Document"
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
                if (activeRecordType == "Document" && expiryDate.isBlank()) {
                    Toast.makeText(context, "Choose the $docType expiry date", Toast.LENGTH_SHORT).show()
                } else if (hasError) {
                    Toast.makeText(context, "Please complete required fields for all sectors", Toast.LENGTH_SHORT).show()
                } else if (groundedRangeBad) {
                    Toast.makeText(context, if (saveGrounded) "\"Until\" date cannot be before the \"Grounded from\" date" else "End date cannot be before the start date", Toast.LENGTH_LONG).show()
                } else if (activeRecordType == "Flight" && dutyType != "Day Off" && (!isValidTime(dutyOnTime) || !isValidTime(dutyOffTime))) {
                    Toast.makeText(context, "Times must be valid 4-digit HHMM", Toast.LENGTH_LONG).show()
                } else {
                    // Things that are probably typos: ask before saving, but allow it.
                    val warnings = if (activeRecordType == "Flight" && dutyType != "Day Off") flightWarnings() else emptyList()
                    if (warnings.isEmpty()) doSave() else pendingWarnings = warnings
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (vm.logsBeingEdited != null) "Update entry" else "Save entry", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** "BG202 LHR→ZYL→DAC" or "BG147/148 DAC→DXB→DAC" for the recent-trips chips. */
private fun tripLabel(trip: List<ChainedSector>): String {
    val numbers = trip.map { it.flightNo }.filter { it.isNotBlank() }.distinct()
    val flights = when {
        numbers.isEmpty() -> ""
        numbers.all { it.startsWith("BG") } -> "BG" + numbers.joinToString("/") { it.removePrefix("BG") }
        else -> numbers.joinToString("/")
    }
    val route = (listOf(trip.first().departure) + trip.map { it.arrival }).joinToString("→")
    return listOf(flights, route).filter { it.isNotBlank() }.joinToString(" ")
}
