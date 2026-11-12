/*
 * "Short Rest Requirement" and "Post-Flight Off Duty" block, shared by the day card and the
 * live preview on the entry form.
 */
package com.example.mylogbook.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.domain.duty.ClaimOffInfo
import com.example.mylogbook.domain.duty.ShortRestInfo

/**
 * Short rest and post-flight off duty details, shared by the log card and the entry screen preview.
 * Post-flight off duty starts after the short rest ends.
 */
@Composable
fun RestRequirementsContent(shortRestInfo: ShortRestInfo?, claimOffInfo: ClaimOffInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shortRestInfo != null) {
            Text("Short Rest Requirement", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            RestDetailRow("Minimum rest", shortRestInfo.durationText)
            RestDetailRow("Rest ends (BST)", shortRestInfo.endTimeLocalText)
        }

        if (shortRestInfo != null && claimOffInfo != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
        }

        if (claimOffInfo != null) {
            Text("Post-Flight Off Duty (Home Base)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
            RestDetailRow(
                "Time away from base",
                "${claimOffInfo.calendarDays} calendar ${if (claimOffInfo.calendarDays == 1) "day" else "days"}"
            )
            RestDetailRow(
                "Days off entitled",
                "${claimOffInfo.daysOff} ${if (claimOffInfo.daysOff == 1) "day" else "days"}"
            )
            RestDetailRow("Off duty ends (BST)", claimOffInfo.endDateTimeText, Color(0xFF4CAF50))
        }
    }
}

@Composable
private fun RestDetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
