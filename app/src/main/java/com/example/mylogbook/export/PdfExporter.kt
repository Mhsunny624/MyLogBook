/*
 * Builds the monthly PDF report offered in Settings -> Data Export.
 */
package com.example.mylogbook.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.duty.calculateClaimOffForDay
import com.example.mylogbook.domain.duty.calculateShortRest

object PdfExporter {
    fun generateMonthlyReport(
        context: Context,
        uri: Uri,
        logs: List<FlightLog>,
        month: String,
        year: String,
        name: String,
        staffId: String
    ) {
        try {
            val prefs = context.getSharedPreferences("FlightLogPrefs", Context.MODE_PRIVATE)
            val homeBase = prefs.getString("profile_base", "DAC") ?: "DAC"

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            // Draw Header
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 20f
            paint.color = Color.BLACK
            canvas.drawText("MONTHLY FLIGHT & DUTY REPORT", 120f, 60f, paint)

            // Draw Crew Info
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 14f
            canvas.drawText("Name: $name", 50f, 100f, paint)
            canvas.drawText("Staff ID: $staffId", 50f, 125f, paint)
            canvas.drawText("Period: $month / $year", 400f, 100f, paint)

            // Draw Table Headers
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 12f
            var yPosition = 170f
            canvas.drawText("Date", 50f, yPosition, paint)
            canvas.drawText("Flight/Duty", 120f, yPosition, paint)
            canvas.drawText("Routing", 220f, yPosition, paint)
            canvas.drawText("Block", 380f, yPosition, paint)
            canvas.drawText("Duty", 460f, yPosition, paint)

            // Draw Header Line
            yPosition += 10f
            canvas.drawLine(50f, yPosition, 545f, yPosition, paint)
            yPosition += 20f

            // NEW BULLETPROOF SORTING:
            // 1. Sorts chronologically by day
            // 2. Puts Flights before other logs
            // 3. Puts Sector 1 (which has a Duty On time) before Sector 2 (which doesn't)
            val filteredLogs = logs.filter {
                it.flightDate.contains(month) && it.flightDate.contains(year)
            }.sortedWith(compareBy(
                { it.flightDate.take(2).toIntOrNull() ?: 0 },
                { it.recordType != "Flight" },
                { it.dutyOn.isEmpty() },
                { it.id }
            ))

            val groupedLogs = filteredLogs.groupBy { it.flightDate }

            var monthlyTotalBlockMins = 0
            var monthlyTotalDutyMins = 0

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (groupedLogs.isEmpty()) {
                canvas.drawText("No records logged for this period.", 50f, yPosition, paint)
            } else {
                for ((date, dayLogs) in groupedLogs) {

                    // 1. Date
                    canvas.drawText(date.take(5), 50f, yPosition, paint)

                    // 2. Flight/Duty
                    val flights = dayLogs.map { log ->
                        if (log.recordType == "Training") {
                            val baseType = if (log.trainingType.isNotEmpty()) log.trainingType else "Training"
                            val rawEndDate = log.dutyOff.takeIf { it.isNotBlank() }
                                ?: log.arrival.takeIf { it.isNotBlank() }
                                ?: log.dutyType.takeIf { it.isNotBlank() }
                                ?: ""
                            if (rawEndDate.isNotEmpty()) {
                                "$baseType to ${rawEndDate.take(5)}"
                            } else {
                                baseType
                            }
                        } else if (log.flightNo.isNotEmpty()) {
                            log.flightNo
                        } else {
                            log.recordType
                        }
                    }.filter { it.isNotEmpty() }.joinToString("/")

                    canvas.drawText(flights, 120f, yPosition, paint)

                    // 3. Routing
                    var combinedRouting = "-"
                    if (dayLogs.isNotEmpty() && dayLogs.first().departure.isNotEmpty()) {
                        combinedRouting = dayLogs.first().departure
                        dayLogs.forEach { log ->
                            if (log.arrival.isNotEmpty()) combinedRouting += "-${log.arrival}"
                        }
                    }
                    canvas.drawText(combinedRouting, 220f, yPosition, paint)

                    // 4. Block
                    var totalBlockMins = 0
                    dayLogs.forEach { log ->
                        val clean = log.totalBlock.filter { it.isDigit() }
                        if (clean.length >= 3) {
                            val padded = clean.padStart(4, '0')
                            val h = padded.substring(0, 2).toIntOrNull() ?: 0
                            val m = padded.substring(2, 4).toIntOrNull() ?: 0
                            totalBlockMins += (h * 60 + m)
                        }
                    }
                    val blockDisplay = if (totalBlockMins > 0) {
                        String.format(java.util.Locale.US, "%02d:%02d", totalBlockMins / 60, totalBlockMins % 60)
                    } else {
                        "-"
                    }
                    canvas.drawText(blockDisplay, 380f, yPosition, paint)

                    // 5. Duty
                    var maxDutyMins = 0
                    var dutyDisplay = "-"
                    var hasValidDuty = false
                    dayLogs.forEach { log ->
                        val clean = log.totalDuty.filter { it.isDigit() }
                        if (clean.length >= 3) {
                            val padded = clean.padStart(4, '0')
                            val h = padded.substring(0, 2).toIntOrNull() ?: 0
                            val m = padded.substring(2, 4).toIntOrNull() ?: 0
                            val mins = h * 60 + m
                            if (mins > maxDutyMins) {
                                maxDutyMins = mins
                                hasValidDuty = true
                            }
                        } else if (log.totalDuty.isNotEmpty() && log.totalDuty != "-") {
                            if (dutyDisplay == "-") dutyDisplay = log.totalDuty
                        }
                    }

                    if (hasValidDuty) {
                        dutyDisplay = String.format(java.util.Locale.US, "%02d:%02d", maxDutyMins / 60, maxDutyMins % 60)
                    }
                    canvas.drawText(dutyDisplay, 460f, yPosition, paint)

                    monthlyTotalBlockMins += totalBlockMins
                    monthlyTotalDutyMins += maxDutyMins

                    // --- NEW: Detailed Sub-line for Short Rest and Claim Off ---
                    val shortRestInfo = calculateShortRest(dayLogs)
                    val claimOffInfo = calculateClaimOffForDay(dayLogs, logs, homeBase)

                    val badges = mutableListOf<String>()
                    if (shortRestInfo != null) {
                        badges.add("Short Rest ends ${shortRestInfo.endTimeLocalText}")
                    }
                    if (claimOffInfo != null) {
                        badges.add("${claimOffInfo.daysOff} Days Off ends ${claimOffInfo.endDateTimeText}")
                    }

                    if (badges.isNotEmpty()) {
                        yPosition += 14f

                        paint.textSize = 10f
                        paint.color = Color.DKGRAY
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)

                        canvas.drawText("↳ " + badges.joinToString("   •   "), 120f, yPosition, paint)

                        paint.textSize = 12f
                        paint.color = Color.BLACK
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                        yPosition += 18f
                    } else {
                        yPosition += 25f
                    }
                }

                // --- THE FOOTER ---
                yPosition += 10f
                canvas.drawLine(50f, yPosition, 545f, yPosition, paint)

                yPosition += 20f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("MONTH TOTALS:", 50f, yPosition, paint)

                val totalBlockDisplay = if (monthlyTotalBlockMins > 0) {
                    String.format(java.util.Locale.US, "%02d:%02d", monthlyTotalBlockMins / 60, monthlyTotalBlockMins % 60)
                } else "-"
                canvas.drawText(totalBlockDisplay, 380f, yPosition, paint)

                val totalDutyDisplay = if (monthlyTotalDutyMins > 0) {
                    String.format(java.util.Locale.US, "%02d:%02d", monthlyTotalDutyMins / 60, monthlyTotalDutyMins % 60)
                } else "-"
                canvas.drawText(totalDutyDisplay, 460f, yPosition, paint)

                yPosition += 80f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("Signature: ___________________________", 320f, yPosition, paint)
            }

            pdfDocument.finishPage(page)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            Toast.makeText(context, "PDF Exported Successfully!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
