/*
 * Logbook tab: "Entries" list grouped by day and the "$৳ Allowances" view (internally "Summary") (month / year totals,
 * grounded days and pay estimates).
 */
package com.example.mylogbook.ui.screens.logbook

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.duty.blockOffInstants
import com.example.mylogbook.domain.duty.dutyOnInstant
import com.example.mylogbook.domain.duty.getMinsFromTimeString
import com.example.mylogbook.domain.pay.groundedDaysInMonth
import com.example.mylogbook.domain.pay.sectorBlockOffs
import com.example.mylogbook.ui.components.DailyLogCard
import com.example.mylogbook.ui.components.MonthlySummaryCard
import com.example.mylogbook.ui.components.PayEstimateSection
import com.example.mylogbook.ui.components.YearAllowanceTable
import com.example.mylogbook.ui.components.SingleLineSummaryCard
import com.example.mylogbook.ui.components.StatCard
import com.example.mylogbook.viewmodel.FlightLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(
    vm: FlightLogViewModel,
    activeSubTab: String,
    onSubTabChange: (String) -> Unit,
    onEditClicked: () -> Unit
) {
    val logs by vm.allLogs.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE)
    val homeBase = prefs.getString("profile_base", "DAC") ?: "DAC"
    val rosterName = prefs.getString("profile_roster_name", "") ?: ""

    val calendar = Calendar.getInstance()
    val currentYearStr = calendar.get(Calendar.YEAR).toString()
    val baseMonthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val fullMonths = mapOf("Jan" to "January", "Feb" to "February", "Mar" to "March", "Apr" to "April", "May" to "May", "Jun" to "June", "Jul" to "July", "Aug" to "August", "Sep" to "September", "Oct" to "October", "Nov" to "November", "Dec" to "December")
    val currentMonthStr = baseMonthsList[calendar.get(Calendar.MONTH)]

    var selectedYear by remember { mutableStateOf(currentYearStr) }
    var selectedMonth by remember { mutableStateOf(currentMonthStr) }
    var isYearMode by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (activeSubTab == "Entries") 0 else 1,
        pageCount = { 2 }
    )

    LaunchedEffect(pagerState.currentPage) {
        val newTab = if (pagerState.currentPage == 0) "Entries" else "Summary"
        if (activeSubTab != newTab) {
            onSubTabChange(newTab)
        }
    }

    fun onPrevious() {
        if (isYearMode) {
            selectedYear = ((selectedYear.toIntOrNull() ?: 2026) - 1).toString()
        } else {
            val currentIndex = baseMonthsList.indexOf(selectedMonth)
            if (currentIndex > 0) {
                selectedMonth = baseMonthsList[currentIndex - 1]
            } else {
                selectedMonth = baseMonthsList.last()
                selectedYear = ((selectedYear.toIntOrNull() ?: 2026) - 1).toString()
            }
        }
    }

    fun onNext() {
        if (isYearMode) {
            selectedYear = ((selectedYear.toIntOrNull() ?: 2026) + 1).toString()
        } else {
            val currentIndex = baseMonthsList.indexOf(selectedMonth)
            if (currentIndex in 0 until baseMonthsList.lastIndex) {
                selectedMonth = baseMonthsList[currentIndex + 1]
            } else {
                selectedMonth = baseMonthsList.first()
                selectedYear = ((selectedYear.toIntOrNull() ?: 2026) + 1).toString()
            }
        }
    }

    var totalBlockMins = 0; var totalDutyMins = 0
    val monthlyBlockMins = IntArray(12)
    val monthlyDutyMins = IntArray(12)
    var trainingCount = 0; var sickCount = 0

    val displayLogs = mutableListOf<FlightLog>()
    val displayedFlightDates = mutableSetOf<String>()

    val sdfUtc = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    // Allowance months run 00:00 UTC to 00:00 UTC (Biman payroll rule), so every month split here is in UTC.
    val monthZone = java.util.TimeZone.getTimeZone("UTC")

    // Real block-off instants (later sectors after UTC midnight roll to the next day) and each
    // day's first sector (decides whether Duty On was on the previous UTC day).
    val blockStarts = remember(logs) { blockOffInstants(logs) }
    // Sectors without a Block off: estimated from Duty On, so the month split still has a start time.
    val estimatedStarts = remember(logs, homeBase) { sectorBlockOffs(logs, homeBase) }
    val firstSectorByDate = remember(logs) {
        logs.filter { it.recordType == "Flight" }.groupBy { it.flightDate }.mapValues { (_, g) -> g.minBy { it.id } }
    }

    logs.forEach { log ->
        val depDate = try { sdfUtc.parse(log.flightDate) } catch (_: Exception) { null }
        if (depDate != null) {
            val calDepBd = Calendar.getInstance(monthZone).apply { time = depDate }
            val depMonthIdx = calDepBd.get(Calendar.MONTH)
            val depMonthStr = baseMonthsList[depMonthIdx]
            val depYearStr = calDepBd.get(Calendar.YEAR).toString()

            val matchDep = (selectedYear == depYearStr) && (selectedMonth == "Whole Year" || selectedMonth == depMonthStr)
            var includeInDisplay = false

            if (log.recordType == "Flight") {
                val durMinsBlock = getMinsFromTimeString(log.totalBlock)

                val calDepTimeBd = Calendar.getInstance(monthZone).apply {
                    timeInMillis = blockStarts[log.id] ?: estimatedStarts[log.id] ?: depDate.time
                }

                val calArrBd = calDepTimeBd.clone() as Calendar
                calArrBd.add(Calendar.MINUTE, durMinsBlock)

                val arrMonthIdx = calArrBd.get(Calendar.MONTH)
                val arrMonthStr = baseMonthsList[arrMonthIdx]
                val arrYearStr = calArrBd.get(Calendar.YEAR).toString()

                val matchArr = (selectedYear == arrYearStr) && (selectedMonth == "Whole Year" || selectedMonth == arrMonthStr)

                if (matchDep || matchArr) includeInDisplay = true

                if (calDepTimeBd.get(Calendar.MONTH) == calArrBd.get(Calendar.MONTH) && calDepTimeBd.get(Calendar.YEAR) == calArrBd.get(Calendar.YEAR)) {
                    if (matchDep) {
                        totalBlockMins += durMinsBlock
                        monthlyBlockMins[depMonthIdx] += durMinsBlock
                    }
                } else {
                    val calNextMonth = calDepTimeBd.clone() as Calendar
                    calNextMonth.add(Calendar.MONTH, 1)
                    calNextMonth.set(Calendar.DAY_OF_MONTH, 1)
                    calNextMonth.set(Calendar.HOUR_OF_DAY, 0)
                    calNextMonth.set(Calendar.MINUTE, 0)
                    calNextMonth.set(Calendar.SECOND, 0)
                    calNextMonth.set(Calendar.MILLISECOND, 0)

                    val minsInDepMonth = ((calNextMonth.timeInMillis - calDepTimeBd.timeInMillis) / 60000).toInt().coerceIn(0, durMinsBlock)
                    val minsInArrMonth = durMinsBlock - minsInDepMonth

                    if (matchDep) {
                        totalBlockMins += minsInDepMonth
                        monthlyBlockMins[depMonthIdx] += minsInDepMonth
                    }
                    if (matchArr) {
                        totalBlockMins += minsInArrMonth
                        monthlyBlockMins[arrMonthIdx] += minsInArrMonth
                    }
                }

                val durMinsDuty = getMinsFromTimeString(log.totalDuty)
                val dutyOnAt = if (durMinsDuty > 0) dutyOnInstant(depDate.time, log.dutyOn, firstSectorByDate[log.flightDate]) else null
                if (dutyOnAt != null) {
                    val calOnBd = Calendar.getInstance(monthZone).apply { timeInMillis = dutyOnAt }

                    val calOffBd = calOnBd.clone() as Calendar
                    calOffBd.add(Calendar.MINUTE, durMinsDuty)

                    val onMonthIdx = calOnBd.get(Calendar.MONTH)
                    val onMonthStr = baseMonthsList[onMonthIdx]
                    val onYearStr = calOnBd.get(Calendar.YEAR).toString()

                    val offMonthIdx = calOffBd.get(Calendar.MONTH)
                    val offMonthStr = baseMonthsList[offMonthIdx]
                    val offYearStr = calOffBd.get(Calendar.YEAR).toString()

                    val matchOn = (selectedYear == onYearStr) && (selectedMonth == "Whole Year" || selectedMonth == onMonthStr)
                    val matchOff = (selectedYear == offYearStr) && (selectedMonth == "Whole Year" || selectedMonth == offMonthStr)

                    if (calOnBd.get(Calendar.MONTH) == calOffBd.get(Calendar.MONTH) && calOnBd.get(Calendar.YEAR) == calOffBd.get(Calendar.YEAR)) {
                        if (matchOn) {
                            totalDutyMins += durMinsDuty
                            monthlyDutyMins[onMonthIdx] += durMinsDuty
                        }
                    } else {
                        val calNextMonth = calOnBd.clone() as Calendar
                        calNextMonth.add(Calendar.MONTH, 1)
                        calNextMonth.set(Calendar.DAY_OF_MONTH, 1)
                        calNextMonth.set(Calendar.HOUR_OF_DAY, 0)
                        calNextMonth.set(Calendar.MINUTE, 0)
                        calNextMonth.set(Calendar.SECOND, 0)
                        calNextMonth.set(Calendar.MILLISECOND, 0)

                        val minsInOnMonth = ((calNextMonth.timeInMillis - calOnBd.timeInMillis) / 60000).toInt().coerceIn(0, durMinsDuty)
                        val minsInOffMonth = durMinsDuty - minsInOnMonth

                        if (matchOn) {
                            totalDutyMins += minsInOnMonth
                            monthlyDutyMins[onMonthIdx] += minsInOnMonth
                        }
                        if (matchOff) {
                            totalDutyMins += minsInOffMonth
                            monthlyDutyMins[offMonthIdx] += minsInOffMonth
                        }
                    }
                } else {
                    if (matchDep) {
                        totalDutyMins += durMinsDuty
                        monthlyDutyMins[depMonthIdx] += durMinsDuty
                    }
                }
            } else if (log.recordType != "Document") {   // passports / crew IDs live under Records only
                if (matchDep) {
                    includeInDisplay = true
                    if (log.recordType == "Training") {
                        trainingCount++
                        if (log.totalDuty.isNotBlank()) {
                            val dutyVal = getMinsFromTimeString(log.totalDuty)
                            totalDutyMins += dutyVal
                            monthlyDutyMins[depMonthIdx] += dutyVal
                        }
                    }
                    if (log.recordType == "Sick") sickCount++
                }
            }

            if (includeInDisplay) {
                displayLogs.add(log)
                if (log.recordType == "Flight") displayedFlightDates.add(log.flightDate)
            }
        }
    }

    val flightCount = displayedFlightDates.size

    val summaryTitle = if (selectedMonth == "Whole Year") {
        "Year $selectedYear Summary"
    } else {
        "${fullMonths[selectedMonth] ?: selectedMonth}, $selectedYear Totals"
    }

    val sortedDisplayLogs = displayLogs.sortedByDescending { log ->
        try { sdfUtc.parse(log.flightDate) } catch (_: Exception) { Date(0) }
    }

    val groupedLogs = sortedDisplayLogs.groupBy { log ->
        try {
            val date = sdfUtc.parse(log.flightDate)
            val cal = Calendar.getInstance(monthZone).apply { time = date ?: Date() }
            val monthIndex = cal.get(Calendar.MONTH)
            val monthStr = baseMonthsList[monthIndex]
            val monthName = fullMonths[monthStr] ?: monthStr
            val year = cal.get(Calendar.YEAR)
            "$monthName $year"
        } catch (_: Exception) { "Unknown Date" }
    }

    // Filter active month indices to avoid gap caused by empty LazyColumn items
    val activeMonthIndices = baseMonthsList.indices.filter { idx ->
        monthlyBlockMins[idx] > 0 || monthlyDutyMins[idx] > 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val greeting = if (rosterName.isNotBlank()) "Welcome, $rosterName" else "My Logbook"
        Text(
            text = greeting,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp)
        )

        SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Entries", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("$৳ Allowances", fontWeight = FontWeight.Bold) }
            )
        }

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onPrevious() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous"
                )
            }

            Surface(
                onClick = {
                    if (isYearMode) {
                        isYearMode = false
                        selectedMonth = currentMonthStr
                    } else {
                        isYearMode = true
                        selectedMonth = "Whole Year"
                    }
                },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = if (isYearMode) selectedYear else "${fullMonths[selectedMonth] ?: selectedMonth} $selectedYear",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            IconButton(onClick = { onNext() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next"
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (page == 0) {
                    // --- ENTRIES TAB ---
                    if (isYearMode) {
                        item {
                            Text(
                                text = "Monthly Breakdown",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        if (activeMonthIndices.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No flight hours logged for $selectedYear.", color = Color.Gray)
                                }
                            }
                        } else {
                            items(activeMonthIndices) { monthIdx ->
                                val monthShort = baseMonthsList[monthIdx]
                                val monthFull = fullMonths[monthShort] ?: monthShort
                                MonthlySummaryCard(
                                    monthName = monthFull,
                                    blockMins = monthlyBlockMins[monthIdx],
                                    dutyMins = monthlyDutyMins[monthIdx],
                                    onClick = {
                                        selectedMonth = monthShort
                                        isYearMode = false
                                    }
                                )
                            }
                        }
                    } else {
                        if (groupedLogs.isNotEmpty()) {
                            groupedLogs.forEach { (monthYear, logsInGroup) ->
                                item {
                                    Text(
                                        text = monthYear,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                val logsByDate = logsInGroup.groupBy { it.flightDate }
                                logsByDate.forEach { (date, dailyLogs) ->
                                    item {
                                        DailyLogCard(
                                            date = date,
                                            dailyLogs = dailyLogs,
                                            allLogs = logs,
                                            homeBase = homeBase,
                                            vm = vm,
                                            onEditClicked = onEditClicked
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No entries logged for this period.", color = Color.Gray)
                                }
                            }
                        }
                    }
                } else {
                    // --- SUMMARY TAB ---
                    if (isYearMode) {
                        item {
                            Text(
                                text = "Year $selectedYear Summary",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        item {
                            SingleLineSummaryCard(
                                title = "Annual Total Block",
                                value = String.format("%d hrs %02d mins", totalBlockMins / 60, totalBlockMins % 60)
                            )
                        }
                        item {
                            SingleLineSummaryCard(
                                title = "Annual Total Duty",
                                value = String.format("%d hrs %02d mins", totalDutyMins / 60, totalDutyMins % 60)
                            )
                        }
                        item {
                            YearAllowanceTable(
                                logs = logs,
                                homeBase = homeBase,
                                year = selectedYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                                monthlyBlockMins = monthlyBlockMins
                            )
                        }

                        item {
                            Text(
                                text = "Monthly Breakdown",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }

                        if (activeMonthIndices.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No flight hours logged for $selectedYear.", color = Color.Gray)
                                }
                            }
                        } else {
                            items(activeMonthIndices) { monthIdx ->
                                val monthShort = baseMonthsList[monthIdx]
                                val monthFull = fullMonths[monthShort] ?: monthShort
                                MonthlySummaryCard(
                                    monthName = monthFull,
                                    blockMins = monthlyBlockMins[monthIdx],
                                    dutyMins = monthlyDutyMins[monthIdx],
                                    onClick = {
                                        selectedMonth = monthShort
                                        isYearMode = false
                                    }
                                )
                            }
                        }
                    } else {
                        item {
                            Column {
                                Text(summaryTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Months split at 00:00 UTC, as on the Biman payroll.", fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard("Flights", "$flightCount", Modifier.weight(1f))
                                StatCard("Training", "$trainingCount", Modifier.weight(1f))
                                StatCard("Sick", "$sickCount", Modifier.weight(1f))
                            }
                        }
                        val groundedDays = groundedDaysInMonth(
                            logs,
                            selectedYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                            baseMonthsList.indexOf(selectedMonth)
                        )
                        if (groundedDays > 0) {
                            item {
                                SingleLineSummaryCard(
                                    title = "Grounded",
                                    value = "$groundedDays ${if (groundedDays == 1) "day" else "days"}"
                                )
                            }
                        }

                        item {
                            SingleLineSummaryCard(
                                title = "Total Block Hours",
                                value = String.format("%d hrs %02d mins", totalBlockMins / 60, totalBlockMins % 60)
                            )
                        }
                        item {
                            SingleLineSummaryCard(
                                title = "Total Duty Hours",
                                value = String.format("%d hrs %02d mins", totalDutyMins / 60, totalDutyMins % 60)
                            )
                        }
                        item {
                            PayEstimateSection(
                                logs = logs,
                                homeBase = homeBase,
                                year = selectedYear.toIntOrNull() ?: calendar.get(Calendar.YEAR),
                                month0 = baseMonthsList.indexOf(selectedMonth),
                                flyMins = totalBlockMins
                            )
                        }
                    }
                }
            }
        }
    }
}
