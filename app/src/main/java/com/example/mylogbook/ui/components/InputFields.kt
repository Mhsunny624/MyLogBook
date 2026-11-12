/*
 * Reusable text fields for the entry form: airport autocomplete, compact time field and the
 * flight number field with a fixed "BG" prefix.
 */
package com.example.mylogbook.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.mylogbook.data.GLOBAL_AIRPORTS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportAutoCompleteTextField(label: String, value: String, isError: Boolean, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val filteredAirports = GLOBAL_AIRPORTS.filter { it.contains(value, ignoreCase = true) }

    ExposedDropdownMenuBox(expanded = expanded && filteredAirports.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value, onValueChange = { onValueChange(it.uppercase()); expanded = true },
            label = { Text(label) }, isError = isError, singleLine = true,
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        ExposedDropdownMenu(expanded = expanded && filteredAirports.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filteredAirports.forEach { airport ->
                DropdownMenuItem(text = { Text(airport) }, onClick = { onValueChange(airport); expanded = false })
            }
        }
    }
}

/** Short time field (HH:MM typed as 4 digits) used in the sector and duty rows. */
@Composable
internal fun CompactTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            val cleaned = it.filter { c -> c.isDigit() }.take(4)
            onValueChange(cleaned)
            if (cleaned.length == 4) onComplete()
        },
        label = { Text(label, maxLines = 1) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = TimeVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    )
}

/**
 * Flight number with a fixed "BG" prefix: type only the digits (202 -> BG202).
 * A number that doesn't start with BG (another airline) is shown and edited as typed.
 */
@Composable
internal fun FlightNumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val isBiman = value.isEmpty() || value.startsWith("BG")
    val shown = if (isBiman) value.removePrefix("BG") else value
    OutlinedTextField(
        value = shown,
        onValueChange = { input ->
            val clean = input.uppercase().filter { it.isLetterOrDigit() }.take(7)
            onValueChange(
                when {
                    clean.isEmpty() -> ""
                    clean.first().isDigit() -> "BG$clean"
                    else -> clean
                }
            )
        },
        label = { Text("Flight number", maxLines = 1) },
        prefix = if (isBiman) {
            { Text("BG", fontWeight = FontWeight.SemiBold) }
        } else null,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    )
}
