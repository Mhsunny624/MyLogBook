/*
 * Shows a typed "HHmm" value as "HH:mm" in text fields.
 */
package com.example.mylogbook.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class TimeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 4) text.text.substring(0, 4) else text.text
        var output = ""
        for (i in trimmed.indices) { output += trimmed[i]; if (i == 1) output += ":" }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 1) return offset; if (offset <= 4) return offset + 1; return 5
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 1) return offset; if (offset <= 2) return 1; if (offset <= 5) return offset - 1; return 4
            }
        }
        return TransformedText(AnnotatedString(output), offsetMapping)
    }
}
