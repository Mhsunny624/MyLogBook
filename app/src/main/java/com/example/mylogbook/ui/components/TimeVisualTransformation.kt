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

/**
 * Shows typed duration digits with a colon before the last two (minutes): "7721" -> "77:21",
 * "12030" -> "120:30". Unlike [TimeVisualTransformation], hours can have any number of digits.
 */
class DurationVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val n = raw.length
        if (n <= 2) return TransformedText(text, OffsetMapping.Identity)
        val out = raw.substring(0, n - 2) + ":" + raw.substring(n - 2)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = if (offset <= n - 2) offset else offset + 1
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= n - 2 -> offset
                offset == n - 1 -> n - 2
                else -> (offset - 1).coerceAtMost(n)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
