/*
 * Calculator tab: adds and subtracts hh:mm times.
 */
package com.example.mylogbook.ui.screens.calculator

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.domain.duty.getMinsFromTimeString
import com.example.mylogbook.ui.components.TimeVisualTransformation

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCalculatorScreen() {
    val focusManager = LocalFocusManager.current

    // We now use a mutable list of strings so you can have as many rows as you want!
    val timeRows = remember { mutableStateListOf("", "") }
    var isAddition by remember { mutableStateOf(true) } // true for Add, false for Subtract

    // Calculate Result dynamically across all entered rows
    val resultText = remember(timeRows.toList(), isAddition) {
        val validMinsList = timeRows.map { getMinsFromTimeString(it) }.filter { it > 0 }

        if (validMinsList.isNotEmpty()) {
            var totalMins = validMinsList.first()
            for (i in 1 until validMinsList.size) {
                totalMins = if (isAddition) totalMins + validMinsList[i] else totalMins - validMinsList[i]
            }

            if (totalMins < 0) {
                val positiveMins = kotlin.math.abs(totalMins)
                "- ${String.format("%02d:%02d", positiveMins / 60, positiveMins % 60)}"
            } else {
                String.format("%02d:%02d", totalMins / 60, totalMins % 60)
            }
        } else {
            "--:--"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Time Calculator", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Toggle between Addition and Subtraction
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAddition) {
                        Button(
                            onClick = { isAddition = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add (+)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isAddition = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add (+)", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (!isAddition) {
                        Button(
                            onClick = { isAddition = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Subtract (-)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { isAddition = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Subtract (-)", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // Dynamic Rows of Times
                timeRows.forEachIndexed { index, timeVal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = timeVal,
                            onValueChange = { newVal ->
                                val cleaned = newVal.filter { c -> c.isDigit() }.take(4)
                                timeRows[index] = cleaned

                                // AUTO-CLOSE KEYBOARD: If 4 digits are entered, drop the cursor/hide keyboard!
                                if (cleaned.length == 4) {
                                    focusManager.clearFocus()
                                }
                            },
                            label = { Text("Time ${index + 1} (HHMM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = TimeVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            )
                        )

                        // Show a delete button if there are more than 2 rows
                        if (timeRows.size > 2) {
                            IconButton(
                                onClick = { timeRows.removeAt(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove Row",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Button to add another time row
                OutlinedButton(
                    onClick = { timeRows.add("") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Row", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add another time")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // The Result Display
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Result", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        text = resultText,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Clear Button
                OutlinedButton(
                    onClick = {
                        timeRows.clear()
                        timeRows.add("")
                        timeRows.add("")
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear All")
                }
            }
        }
    }
}
