/*
 * Calculator tab (all times UTC):
 *  - "Add / Subtract": sums durations, each row with its own + / − sign. Hours can go above 99
 *    (e.g. monthly totals). Shows the result as h:mm and in decimal hours.
 *  - "Between times": duration from one UTC clock time to another, crossing midnight if needed.
 */
package com.example.mylogbook.ui.screens.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.domain.duty.clockDifferenceMinutes
import com.example.mylogbook.domain.duty.decimalHoursText
import com.example.mylogbook.domain.duty.durationMinutes
import com.example.mylogbook.domain.duty.formatDuration
import com.example.mylogbook.domain.duty.isValidTime
import com.example.mylogbook.ui.components.DurationVisualTransformation
import com.example.mylogbook.ui.components.TimeVisualTransformation

/** One row of the Add / Subtract mode: digits typed (HHMM or HHHMM) and its sign. */
private data class CalcRow(val digits: String = "", val plus: Boolean = true)

private val CalcRowListSaver = listSaver<MutableList<CalcRow>, String>(
    save = { rows -> rows.map { "${if (it.plus) "+" else "-"}${it.digits}" } },
    restore = { saved -> mutableStateListOf(*saved.map { CalcRow(it.drop(1), it.startsWith("+")) }.toTypedArray()) }
)

@Composable
fun TimeCalculatorScreen() {
    var mode by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Time Calculator", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Add / Subtract", "Between times").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { mode = i },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                ) { Text(label, maxLines = 1) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (mode == 0) SumCalculator() else BetweenTimesCalculator()
            }
        }
        Text("All times in UTC", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Add / Subtract
// ---------------------------------------------------------------------------------------------

@Composable
private fun SumCalculator() {
    val focusManager = LocalFocusManager.current
    val rows = rememberSaveable(saver = CalcRowListSaver) { mutableStateListOf(CalcRow(), CalcRow()) }

    val parsed = rows.map { durationMinutes(it.digits) }
    val validRows = rows.indices.filter { rows[it].digits.isNotEmpty() && parsed[it] != null }
    val total: Int? = if (validRows.isEmpty()) null else validRows.sumOf { i ->
        val mins = parsed[i]!!
        if (i == 0 || rows[i].plus) mins else -mins
    }

    rows.forEachIndexed { index, row ->
        val invalid = row.digits.isNotEmpty() && parsed[index] == null
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // First row is the starting value; later rows get their own + / − switch.
            if (index == 0) {
                Spacer(Modifier.width(48.dp))
            } else {
                FilledTonalButton(
                    onClick = { rows[index] = row.copy(plus = !row.plus) },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (row.plus) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        contentColor = if (row.plus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (row.plus) "+" else "−", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = row.digits,
                onValueChange = { v -> rows[index] = row.copy(digits = v.filter { it.isDigit() }.take(5)) },
                label = { Text("Time ${index + 1} (h:mm)") },
                placeholder = { Text("e.g. 7721") },
                isError = invalid,
                supportingText = if (invalid) { { Text("Minutes must be 00–59") } } else null,
                modifier = Modifier.weight(1f),
                singleLine = true,
                visualTransformation = DurationVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            if (rows.size > 2) {
                IconButton(onClick = { rows.removeAt(index) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove row", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Quick add: common duty buffers (report 1:45 at base / 1:15 outstation, 0:45 after block on).
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Quick add", fontSize = 12.sp, color = Color.Gray)
        listOf("045" to "+0:45", "115" to "+1:15", "145" to "+1:45").forEach { (digits, label) ->
            AssistChip(
                onClick = {
                    val emptyIndex = rows.indexOfFirst { it.digits.isEmpty() }
                    if (emptyIndex >= 0) rows[emptyIndex] = CalcRow(digits, plus = true)
                    else rows.add(CalcRow(digits, plus = true))
                },
                label = { Text(label) }
            )
        }
    }

    OutlinedButton(onClick = { rows.add(CalcRow()) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add another time")
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

    ResultBlock(
        text = total?.let { formatDuration(it) },
        detail = total?.let { "${decimalHoursText(it)} hrs (decimal)" }
    )

    OutlinedButton(
        onClick = {
            rows.clear(); rows.add(CalcRow()); rows.add(CalcRow())
            focusManager.clearFocus()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Clear All") }
}

// ---------------------------------------------------------------------------------------------
// Between times
// ---------------------------------------------------------------------------------------------

@Composable
private fun BetweenTimesCalculator() {
    val focusManager = LocalFocusManager.current
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }

    val startOk = start.length == 4 && isValidTime(start)
    val endOk = end.length == 4 && isValidTime(end)
    val diff = if (startOk && endOk) clockDifferenceMinutes(start, end) else null
    val crossesMidnight = diff != null && end.toInt() < start.toInt()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockField("From (UTC)", start, { start = it }, Modifier.weight(1f)) { focusManager.clearFocus() }
        ClockField("To (UTC)", end, { end = it }, Modifier.weight(1f)) { focusManager.clearFocus() }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

    ResultBlock(
        text = diff?.let { formatDuration(it) },
        detail = diff?.let {
            "${decimalHoursText(it)} hrs (decimal)" + if (crossesMidnight) " • crosses midnight" else ""
        }
    )

    OutlinedButton(
        onClick = { start = ""; end = ""; focusManager.clearFocus() },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Clear") }
}

@Composable
private fun ClockField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, onDone: () -> Unit) {
    val invalid = value.length == 4 && !isValidTime(value)
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        placeholder = { Text("HHMM") },
        isError = invalid,
        supportingText = if (invalid) { { Text("Use 0000–2359") } } else null,
        modifier = modifier,
        singleLine = true,
        visualTransformation = TimeVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

// ---------------------------------------------------------------------------------------------

/** Big result with decimal hours underneath and a copy button. */
@Composable
private fun ResultBlock(text: String?, detail: String?) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Result", fontSize = 14.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text ?: "--:--",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (text != null) {
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Time", text))
                    Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy result", tint = Color.Gray)
                }
            }
        }
        if (detail != null) Text(detail, fontSize = 13.sp, color = Color.Gray)
    }
}
