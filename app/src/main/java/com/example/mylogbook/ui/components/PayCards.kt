/*
 * USD and Taka pay estimate cards on the $৳ Allowances tab (month view).
 */
package com.example.mylogbook.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.pay.TakaEstimate
import com.example.mylogbook.domain.pay.UsdEstimate
import com.example.mylogbook.domain.pay.estimateTaka
import com.example.mylogbook.domain.pay.estimateUsd
import com.example.mylogbook.domain.pay.groundedDaysInMonth
import com.example.mylogbook.domain.pay.loadPayRates
import com.example.mylogbook.domain.pay.payrollHours
import java.util.Locale
import kotlin.math.abs

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private fun usd(v: Double) = String.format(Locale.US, "USD %,.2f", v)

private fun tk(v: Double) = String.format(Locale.US, "Tk %,.2f", v)

private fun money(v: Double) = String.format(Locale.US, "%,.2f", v)

private fun hm(mins: Long) = String.format(Locale.US, "%d:%02d", mins / 60, mins % 60)

private fun plain(v: Double) = if (v % 1.0 == 0.0) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

/**
 * Pay estimates shown at the top of the $৳ Allowances tab for one month.
 * [flyMins] = that month's block minutes, already split at month ends (Bangladesh time).
 */
@Composable
fun PayEstimateSection(logs: List<FlightLog>, homeBase: String, year: Int, month0: Int, flyMins: Int) {
    if (month0 !in 0..11) return
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE) }
    val rates = loadPayRates(prefs)
    val usdEstimate = remember(logs, homeBase, year, month0, flyMins, rates) {
        estimateUsd(logs, homeBase, year, month0, flyMins, rates)
    }
    val takaEstimate = remember(rates, flyMins) { estimateTaka(rates, flyMins) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HoursProgressCard(usdEstimate, year, month0)
        UsdPayCard(usdEstimate, month0, groundedDaysInMonth(logs, year, month0))
        TakaPayCard(takaEstimate, rates.flyingTkPerHour, rates.houseRentPct, rates.specialPct, month0)
    }
}

@Composable
private fun PayCardFrame(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun PayLine(label: String, value: String, detail: String? = null, bold: Boolean = false) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                label, fontSize = 14.sp, modifier = Modifier.weight(1f),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
            Text(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
        }
        if (!detail.isNullOrBlank()) {
            Text(detail, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PayNote(text: String) {
    Text(text, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
}

@Composable
private fun UsdPayCard(e: UsdEstimate, month0: Int, groundedDays: Int = 0) {
    val month = MONTH_NAMES[month0]
    val prevMonth = MONTH_NAMES[(month0 + 11) % 12]
    val nextMonth = MONTH_NAMES[(month0 + 1) % 12]
    val diffMins = e.flyMins - e.minHours * 60

    PayCardFrame {
        Text("Expected USD payment", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("$month flying + $prevMonth incidental", fontSize = 12.sp, color = Color.Gray)
        Text(usd(e.rounded), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (e.roundStep > 0 && abs(e.rounded - e.total) > 0.004) {
            PayNote("Calculated ${money(e.total)}, rounded up to the next USD ${plain(e.roundStep)}")
        }

        HorizontalDivider()

        val overseasDetail = buildString {
            append("${hm(e.flyMins.toLong())} flying hrs")
            if (diffMins >= 0) {
                append(" → ${hm(diffMins.toLong())} above ${e.minHours} × USD ${plain(e.rate.hourly)}")
            } else {
                append(" → ${hm(-diffMins.toLong())} below ${e.minHours}")
                if (e.waiverHours > 0) append(", ${e.waiverHours} hrs long-stay waiver")
                append(" × USD ${plain(e.rate.hourly)} deducted")
            }
            append("\nBase USD ${plain(e.rate.monthly)} for ${e.minHours} hrs • ${e.rate.basis}")
        }
        PayLine("Overseas allowance ($month)", money(e.overseas), overseasDetail)

        val prev = e.previousIncidental
        val incidentalDetail = buildString {
            append("${hm(prev.minutes)} away from base ÷ 24 × USD 12 per day")
            append(" • ${prev.trips} trip${if (prev.trips == 1) "" else "s"}")
            if (prev.shuttles > 0) append(" (+${prev.shuttles} shuttle${if (prev.shuttles == 1) "" else "s"}, not counted)")
            if (prev.estimated) append("\nSome block times estimated from duty times")
        }
        PayLine("Incidental ($prevMonth)", money(prev.usd), incidentalDetail)

        HorizontalDivider()

        val cur = e.currentIncidental
        PayLine(
            "$month incidental so far",
            money(cur.usd),
            "${hm(cur.minutes)} away • paid with the $nextMonth statement" +
                if (cur.shuttles > 0) "\n${cur.shuttles} shuttle${if (cur.shuttles == 1) "" else "s"} not counted" else ""
        )

        if (diffMins < 0 && groundedDays > 0) {
            PayNote("Grounded $groundedDays ${if (groundedDays == 1) "day" else "days"} this month.")
        }
        if (diffMins < 0) {
            PayNote("Deduction assumes you were rostered for ${e.minHours} hrs. No deduction applies for cancellations, re-routing, aircraft change, company training or VVIP duty.")
        }
        if (e.incompleteTrips > 0) {
            PayNote("${e.incompleteTrips} trip${if (e.incompleteTrips == 1) "" else "s"} missing departure or return times - not counted in incidental.")
        }
        if (e.monthEndMissingTimes > 0) {
            PayNote("${e.monthEndMissingTimes} flight${if (e.monthEndMissingTimes == 1) "" else "s"} on the last day of a month ${if (e.monthEndMissingTimes == 1) "has" else "have"} no Block off / Block on. Add them so the hours are split correctly at 00:00 UTC.")
        }
        PayNote("Estimate based on Biman Admin Orders 17/2018 and 17/2023. Rates can be changed in Settings → Pay Settings.")
    }
}

@Composable
private fun TakaPayCard(t: TakaEstimate, tkPerHour: Double, hrPct: Double, specialPct: Double, month0: Int) {
    val month = MONTH_NAMES[month0]
    val nextMonth = MONTH_NAMES[(month0 + 1) % 12]

    PayCardFrame {
        Text("Estimated Taka payslip", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("$month duty • paid with the $nextMonth salary", fontSize = 12.sp, color = Color.Gray)

        if (!t.hasBasic) {
            Text("Add your basic salary in Settings → Pay Settings to see this estimate.", fontSize = 14.sp)
            return@PayCardFrame
        }

        Text(tk(t.total), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        PayLine(
            "Fixed salary & allowances", money(t.fixedTotal),
            "Basic + house rent ${plain(hrPct)}% + special ${plain(specialPct)}% + aviation, trade, jet"
        )
        PayLine("Uniform maintenance", money(t.uniform))
        PayLine(
            "Flying allowance", money(t.flyingAllowance),
            String.format(Locale.US, "%.2f hrs × Tk %s", t.payrollHours, plain(tkPerHour))
        )
        if (t.flyingOt > 0) {
            PayLine(
                "Flying hour OT", money(t.flyingOt),
                String.format(Locale.US, "%.2f hrs above limit × Tk %s", t.otPayrollHours, plain(tkPerHour))
            )
        }
        PayNote("Not included: duty overtime, refused day-off, absence deductions, CPF, tax and loans.")
    }
}

/**
 * Flying hours against the monthly minimum (70 h by default) with a progress bar, so a shortfall
 * is visible before the month ends. A long-stay waiver lowers the hours needed.
 */
@Composable
private fun HoursProgressCard(e: UsdEstimate, year: Int, month0: Int) {
    val targetMins = (e.minHours - e.waiverHours).coerceAtLeast(0) * 60
    val flown = e.flyMins
    val reached = flown >= targetMins
    val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val isCurrentMonth = now.get(java.util.Calendar.YEAR) == year && now.get(java.util.Calendar.MONTH) == month0
    val daysLeft = if (isCurrentMonth) now.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - now.get(java.util.Calendar.DAY_OF_MONTH) else 0
    // A finished month that fell short is shown as a shortfall, not "to go".
    val isPast = year < now.get(java.util.Calendar.YEAR) ||
        (year == now.get(java.util.Calendar.YEAR) && month0 < now.get(java.util.Calendar.MONTH))
    val green = Color(0xFF4CAF50)
    val barColor = when {
        reached -> green
        isPast -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    PayCardFrame {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Flying hours", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("${hm(flown.toLong())} / ${hm(targetMins.toLong())}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        LinearProgressIndicator(
            progress = { if (targetMins == 0) 1f else (flown.toFloat() / targetMins).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Text(
            when {
                reached -> "Target reached: ${hm((flown - targetMins).toLong())} above"
                isPast -> "Short by ${hm((targetMins - flown).toLong())}"
                else -> "${hm((targetMins - flown).toLong())} to go" + if (isCurrentMonth) " • $daysLeft ${if (daysLeft == 1) "day" else "days"} left this month" else ""
            },
            fontSize = 13.sp,
            color = when {
                reached -> green
                isPast -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.SemiBold
        )
        if (e.waiverHours > 0) {
            PayNote("Target lowered from ${e.minHours} hrs by the ${e.waiverHours} hrs long-stay waiver.")
        }
    }
}

/**
 * Year view: block hours, expected USD statement and incidental earned for each month.
 * [monthlyBlockMins] = block minutes per month, already split at month ends (Bangladesh time).
 */
@Composable
fun YearAllowanceTable(logs: List<FlightLog>, homeBase: String, year: Int, monthlyBlockMins: IntArray) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE) }
    val rates = loadPayRates(prefs)
    val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val lastMonth = when {
        year < now.get(java.util.Calendar.YEAR) -> 11
        year == now.get(java.util.Calendar.YEAR) -> now.get(java.util.Calendar.MONTH)
        else -> -1
    }
    data class Row3(val month0: Int, val block: Int, val usd: Double, val incidental: Double)
    val rows = remember(logs, homeBase, year, monthlyBlockMins.toList(), rates) {
        (0..lastMonth).mapNotNull { m ->
            val e = estimateUsd(logs, homeBase, year, m, monthlyBlockMins[m], rates)
            val inc = e.currentIncidental.usd
            // Skip months with nothing logged (before you started using the app).
            if (monthlyBlockMins[m] == 0 && inc == 0.0 && e.previousIncidental.usd == 0.0) null
            else Row3(m, monthlyBlockMins[m], e.rounded, inc)
        }
    }
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    PayCardFrame {
        Text("Allowances by month", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        if (rows.isEmpty()) {
            Text("No months with flying in $year.", fontSize = 13.sp, color = Color.Gray)
            return@PayCardFrame
        }
        @Composable
        fun line(a: String, b: String, c: String, d: String, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
            val w = if (bold) FontWeight.Bold else FontWeight.Normal
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(a, modifier = Modifier.weight(0.8f), fontSize = 13.sp, fontWeight = w, color = color)
                Text(b, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = w, color = color, textAlign = TextAlign.End)
                Text(c, modifier = Modifier.weight(1.3f), fontSize = 13.sp, fontWeight = w, color = color, textAlign = TextAlign.End)
                Text(d, modifier = Modifier.weight(1.2f), fontSize = 13.sp, fontWeight = w, color = color, textAlign = TextAlign.End)
            }
        }
        line("Month", "Block", "USD", "Incidental", bold = true, color = Color.Gray)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        rows.forEach { r ->
            val under = r.block < rates.minHours * 60
            line(names[r.month0], hm(r.block.toLong()), money(r.usd), money(r.incidental),
                color = if (under && r.month0 != lastMonth) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        line("Total", hm(rows.sumOf { it.block }.toLong()), money(rows.sumOf { it.usd }), money(rows.sumOf { it.incidental }), bold = true)
        PayNote("USD = expected statement for that month (its flying + the previous month's incidental). Incidental = earned that month, paid the month after. Months under ${rates.minHours} hrs are shown in red.")
    }
}
