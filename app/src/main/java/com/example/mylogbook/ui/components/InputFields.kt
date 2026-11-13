/*
 * Reusable text fields for the entry form: a compact outlined field, airport autocomplete,
 * time field and the flight number field with a fixed "BG" prefix.
 */
package com.example.mylogbook.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.data.GLOBAL_AIRPORTS

/**
 * Outlined text field about 44 dp tall instead of Material's 56 dp, used for the dense sector and
 * duty rows. The label sits on the border like a normal outlined field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DenseOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    prefix: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.padding(top = 6.dp),   // room for the label on the top border
        readOnly = readOnly,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                isError = isError,
                label = { Text(label, maxLines = 1) },
                prefix = prefix,
                colors = colors,
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 11.dp, bottom = 11.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirportAutoCompleteTextField(label: String, value: String, isError: Boolean, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val filteredAirports = GLOBAL_AIRPORTS.filter { it.contains(value, ignoreCase = true) }

    ExposedDropdownMenuBox(expanded = expanded && filteredAirports.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        DenseOutlinedTextField(
            value = value, onValueChange = { onValueChange(it.uppercase()); expanded = true },
            label = label, isError = isError,
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
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
    DenseOutlinedTextField(
        value = value,
        onValueChange = {
            val cleaned = it.filter { c -> c.isDigit() }.take(4)
            onValueChange(cleaned)
            if (cleaned.length == 4) onComplete()
        },
        label = label,
        modifier = modifier,
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
    DenseOutlinedTextField(
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
        label = "Flight number",
        prefix = if (isBiman) {
            { Text("BG ", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        } else null,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    )
}
