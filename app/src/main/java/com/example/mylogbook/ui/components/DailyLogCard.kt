/*
 * Card for one day in the Entries list: sectors, duty, rest requirements and the non-flight
 * records (training, health card, grounded, sick) with edit/delete actions.
 */
package com.example.mylogbook.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.duty.calculateClaimOffForDay
import com.example.mylogbook.domain.duty.calculateShortRest
import com.example.mylogbook.domain.duty.getMinsFromTimeString
import com.example.mylogbook.domain.duty.getRemainingTimeText
import com.example.mylogbook.domain.pay.groundedSpan
import com.example.mylogbook.viewmodel.FlightLogViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@SuppressLint("DefaultLocale")
@Composable
fun DailyLogCard(date: String, dailyLogs: List<FlightLog>, allLogs: List<FlightLog>, homeBase: String, vm: FlightLogViewModel, onEditClicked: () -> Unit) {
    val flightLogs = dailyLogs.filter { it.recordType == "Flight" }.sortedBy { it.id }
    val otherLogs = dailyLogs.filter { it.recordType != "Flight" }

    var dailyBlockMins = 0
    var monthSplitText = ""
    var isRestExpanded by remember { mutableStateOf(false) }

    val shortRestInfo = remember(dailyLogs) { calculateShortRest(dailyLogs) }
    val claimOffInfo = remember(dailyLogs, allLogs, homeBase) { calculateClaimOffForDay(dailyLogs, allLogs, homeBase) }

    val sdfUtc = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val bstZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")
    val baseMonthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthMinsMap = mutableMapOf<String, Int>()

    flightLogs.forEach { log ->
        val durMinsBlock = getMinsFromTimeString(log.totalBlock)
        dailyBlockMins += durMinsBlock

        val depDate = try { sdfUtc.parse(log.flightDate) } catch (_: Exception) { null }
        if (depDate != null && log.blockOff.isNotBlank() && durMinsBlock > 0) {
            val offMins = getMinsFromTimeString(log.blockOff)

            val calDepBd = Calendar.getInstance(bstZone).apply {
                timeInMillis = depDate.time
                add(Calendar.MINUTE, offMins)
            }
            val calArrBd = Calendar.getInstance(bstZone).apply {
                timeInMillis = calDepBd.timeInMillis
                add(Calendar.MINUTE, durMinsBlock)
            }

            if (calDepBd.get(Calendar.MONTH) == calArrBd.get(Calendar.MONTH)) {
                val mStr = baseMonthsList[calDepBd.get(Calendar.MONTH)]
                monthMinsMap[mStr] = (monthMinsMap[mStr] ?: 0) + durMinsBlock
            } else {
                val calNextMonth = calDepBd.clone() as Calendar
                calNextMonth.add(Calendar.MONTH, 1)
                calNextMonth.set(Calendar.DAY_OF_MONTH, 1)
                calNextMonth.set(Calendar.HOUR_OF_DAY, 0)
                calNextMonth.set(Calendar.MINUTE, 0)
                calNextMonth.set(Calendar.SECOND, 0)
                calNextMonth.set(Calendar.MILLISECOND, 0)

                val minsInDepMonth = ((calNextMonth.timeInMillis - calDepBd.timeInMillis) / 60000).toInt().coerceIn(0, durMinsBlock)
                val minsInArrMonth = durMinsBlock - minsInDepMonth

                val depMStr = baseMonthsList[calDepBd.get(Calendar.MONTH)]
                val arrMStr = baseMonthsList[calArrBd.get(Calendar.MONTH)]

                monthMinsMap[depMStr] = (monthMinsMap[depMStr] ?: 0) + minsInDepMonth
                monthMinsMap[arrMStr] = (monthMinsMap[arrMStr] ?: 0) + minsInArrMonth
            }
        }
    }

    if (monthMinsMap.size > 1) {
        val splits = monthMinsMap.map { (month, mins) -> "${String.format("%02d:%02d", mins / 60, mins % 60)} $month" }
        monthSplitText = "(${splits.joinToString(" / ")})"
    }

    val displayDate = if (date.length >= 5) date.substring(0, 5) else date

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                vm.logsBeingEdited = dailyLogs
                onEditClicked()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (flightLogs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        val routeParts = mutableListOf<String>()
                        flightLogs.forEachIndexed { index, log ->
                            if (index == 0) {
                                if (log.departure.isNotBlank()) routeParts.add(log.departure)
                                if (log.arrival.isNotBlank()) routeParts.add(log.arrival)
                            } else {
                                if (log.arrival.isNotBlank()) routeParts.add(log.arrival)
                            }
                        }
                        val mergedRoute = routeParts.joinToString("-")

                        Text(
                            text = "$displayDate • $mergedRoute",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        if (shortRestInfo != null || claimOffInfo != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (shortRestInfo != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .height(22.dp)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Short Rest",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.offset(y = 1.dp)
                                        )
                                    }
                                }
                                if (claimOffInfo != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .height(22.dp)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${claimOffInfo.daysOff} ${if (claimOffInfo.daysOff == 1) "Day" else "Days"} Off",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50),
                                            modifier = Modifier.offset(y = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val detailsText = flightLogs.map { it.notes.replace(Regex("\\(Sector \\d+ of \\d+\\)$"), "").trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(", ")

                        if (detailsText.isNotEmpty()) {
                            Text(detailsText, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (dailyBlockMins > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "➔ ${String.format("%02d:%02d", dailyBlockMins / 60, dailyBlockMins % 60)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (monthSplitText.isNotBlank()) {
                                    Text(
                                        text = monthSplitText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        if (shortRestInfo != null || claimOffInfo != null) {
                            IconButton(
                                onClick = { isRestExpanded = !isRestExpanded },
                                modifier = Modifier.size(28.dp).padding(start = 2.dp)
                            ) {
                                Text(if (isRestExpanded) "▲" else "▼", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(28.dp).padding(start = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Flight",
                                modifier = Modifier.size(18.dp),
                                tint = Color.Gray.copy(alpha = 0.8f)
                            )
                        }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete this entry?") },
                                text = { Text("This will permanently delete this flight log entry.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        flightLogs.forEach { vm.deleteLog(it) }
                                        showDeleteConfirm = false
                                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                                }
                            )
                        }
                    }
                }

                if (isRestExpanded && (shortRestInfo != null || claimOffInfo != null)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    Column(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RestRequirementsContent(shortRestInfo, claimOffInfo)
                    }
                }
            }

            otherLogs.forEachIndexed { index, log ->
                if (index > 0 || flightLogs.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val logTitle = when (log.recordType) {
                        "Training" -> {
                            val endStr = if (log.dutyOff.isNotBlank() && log.dutyOff != log.flightDate) " to ${log.dutyOff.take(5)}" else ""
                            "📚 ${log.trainingType.ifBlank { "Training" }}$endStr"
                        }
                        "Health Card" -> if (log.trainingType == "Grounded") "⛔ Grounded" else "🏥 Health Card"
                        "Sick" -> "🤒 Sick Leave"
                        else -> log.recordType
                    }
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        val isGroundedLog = log.recordType == "Health Card" && log.trainingType == "Grounded"
                        Text("$displayDate • $logTitle", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (log.recordType == "Sick") Color(0xFFE53935) else if (isGroundedLog) Color(0xFFEF6C00) else MaterialTheme.colorScheme.onSurface)
                        if (isGroundedLog) {
                            val span = groundedSpan(log)
                            val days = span?.let { ((it.second - it.first) / 86_400_000L).toInt() + 1 } ?: 0
                            val dayWord = if (days == 1) "day" else "days"
                            val periodText = if (log.dutyOff.isBlank()) "Since ${log.flightDate} (ongoing) • $days $dayWord so far"
                                else "${log.flightDate} – ${log.dutyOff} • $days $dayWord"
                            Text(periodText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                            if (log.dutyType.isNotBlank()) {
                                Text("Reason: ${log.dutyType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (log.expiryDate.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        append("Exp: ")
                                        withStyle(
                                            style = SpanStyle(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            append(log.expiryDate)
                                        }
                                    },
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                val remainingText = getRemainingTimeText(log.expiryDate)
                                if (remainingText.isNotEmpty()) {
                                    val isExpired = remainingText == "Expired"
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = remainingText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExpired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete Record",
                            modifier = Modifier.size(18.dp),
                            tint = Color.Gray.copy(alpha = 0.8f)
                        )
                    }
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete this entry?") },
                            text = { Text("This will permanently delete this record.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.deleteLog(log)
                                    showDeleteConfirm = false
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
        }
    }
}
