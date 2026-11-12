/*
 * Pay settings card (crew type, joining date, basic salary and editable rates).
 */
package com.example.mylogbook.ui.screens.settings

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.mylogbook.domain.pay.PAY_DEFAULTS
import com.example.mylogbook.domain.pay.PAY_RATE_KEYS
import com.example.mylogbook.domain.pay.overseasRate
import com.example.mylogbook.domain.pay.parseDayUtc
import com.example.mylogbook.domain.pay.payPref
import com.example.mylogbook.domain.pay.payRatesFrom
import java.util.Calendar
import java.util.Locale

/** Settings card for the pay estimates shown on the Summary tab. */
@Composable
fun PaySettingsCard(prefs: SharedPreferences) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val fields = remember {
        mutableStateMapOf<String, String>().apply { PAY_DEFAULTS.keys.forEach { put(it, payPref(prefs, it)) } }
    }
    var showRates by remember { mutableStateOf(false) }
    // Joining date is typed as digits only (ddmmyyyy); the slashes are added on screen.
    var joiningDigits by remember {
        mutableStateOf(payPref(prefs, "pay_joining_date").filter { it.isDigit() }.take(8))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pay Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Used for the USD and Taka estimates on the Summary tab.",
                fontSize = 12.sp, color = Color.Gray
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Regular", "Contractual").forEach { type ->
                    val selected = (fields["pay_crew_type"] ?: "Contractual") == type
                    if (selected) {
                        Button(
                            onClick = { fields["pay_crew_type"] = type },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) { Text(type, fontWeight = FontWeight.Bold) }
                    } else {
                        OutlinedButton(
                            onClick = { fields["pay_crew_type"] = type },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text(type, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = joiningDigits,
                    onValueChange = { joiningDigits = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("Joining date", fontSize = 12.sp) },
                    placeholder = { Text("dd/mm/yyyy") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = DateSlashTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                PayField(fields, "pay_basic", "Basic salary (Tk)")
            }

            val now = Calendar.getInstance()
            val previewRate = overseasRate(
                payRatesFrom {
                    if (it == "pay_joining_date") formatJoiningDate(joiningDigits) else fields[it] ?: PAY_DEFAULTS[it] ?: ""
                },
                now.get(Calendar.YEAR), now.get(Calendar.MONTH)
            )
            Text(
                String.format(
                    Locale.US, "Overseas allowance: USD %,.0f for %s hrs, USD %,.2f per hour above/below (%s)",
                    previewRate.monthly, fields["pay_min_hours"] ?: "70", previewRate.hourly, previewRate.basis
                ),
                fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp
            )

            TextButton(onClick = { showRates = !showRates }) {
                Text(if (showRates) "Hide rates ▲" else "Edit rates ▼")
            }

            if (showRates) {
                Text(
                    "Leave the overseas fields blank to use Biman's rate for your crew type and service length.",
                    fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_oa_monthly", "Overseas USD/month")
                    PayField(fields, "pay_oa_hourly", "Overseas USD/hour")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_min_hours", "Monthly hours")
                    PayField(fields, "pay_incidental_day", "Incidental USD/day")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_flying_tk_hr", "Flying Tk/hour")
                    PayField(fields, "pay_round_step", "Round USD up to")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_hr_pct", "House rent %")
                    PayField(fields, "pay_special_pct", "Special allow. %")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_aviation", "Aviation (Tk)")
                    PayField(fields, "pay_trade", "Trade (Tk)")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PayField(fields, "pay_jet", "Jet (Tk)")
                    PayField(fields, "pay_uniform", "Uniform maint. (Tk)")
                }
                OutlinedButton(
                    onClick = { PAY_RATE_KEYS.forEach { fields[it] = PAY_DEFAULTS[it] ?: "" } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Reset rates to Biman defaults") }
            }

            Button(
                onClick = {
                    val joining = formatJoiningDate(joiningDigits)
                    if (joining.isNotEmpty() && parseDayUtc(joining) == null) {
                        Toast.makeText(context, "Joining date must be dd/mm/yyyy, e.g. 01/01/2020", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    fields["pay_joining_date"] = joining
                    prefs.edit { fields.forEach { (key, value) -> putString(key, value.trim()) } }
                    Toast.makeText(context, "Pay settings saved!", Toast.LENGTH_SHORT).show()
                    focusManager.clearFocus()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) { Text("Save Pay Settings") }
        }
    }
}

@Composable
private fun RowScope.PayField(
    fields: MutableMap<String, String>,
    key: String,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = fields[key] ?: "",
        onValueChange = { fields[key] = it },
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.weight(1f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

/** "01012020" -> "01/01/2020"; anything shorter is returned unchanged (so it fails validation). */
private fun formatJoiningDate(digits: String): String =
    if (digits.length == 8) "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}" else digits

/** Shows typed digits as dd/mm/yyyy, adding the slashes automatically while typing. */
private object DateSlashTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
