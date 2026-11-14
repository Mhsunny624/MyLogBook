/*
 * Daily WorkManager job that notifies about training / health card expiries (30 days before,
 * then every day from 7 days to 0) and the end of sick leave (the day before and the day itself). Keep this class name and package: WorkManager stores it.
 */
package com.example.mylogbook.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mylogbook.R
import com.example.mylogbook.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExpiryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).flightLogDao()
        val records = dao.getExpiringRecords()

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        records.forEach { record ->
            if (record.expiryDate.isBlank()) return@forEach
            val expDate = try { sdf.parse(record.expiryDate) } catch (_: Exception) { null } ?: return@forEach
            // Whole calendar days from today to the expiry date (both at local midnight).
            val days = Math.round((expDate.time - today.timeInMillis) / 86_400_000.0).toInt()

            // A heads-up at 30 days (passport: 6, 3 and 1 months), then every day from 7 days to the day itself.
            val isPassport = record.recordType == "Document" && record.trainingType != "Crew ID"
            val headsUp = if (isPassport) setOf(180, 90, 30) else setOf(30)
            if (days !in headsUp && days !in 0..7) return@forEach
            val whenText = when (days) { 0 -> "today"; 1 -> "tomorrow"; 180 -> "in 6 months"; 90 -> "in 3 months"; else -> "in $days days" }
            val (title, message) = when (record.recordType) {
                "Training" -> "Training expiring" to "Your ${record.trainingType.ifBlank { "training" }} expires $whenText (${record.expiryDate})."
                "Sick" -> {
                    if (days > 1) return@forEach   // only remind the day before and on the day
                    "Sick leave ending" to "You are cleared for duty $whenText (${record.expiryDate})."
                }
                "Document" -> if (isPassport) "Passport expiring" to "Your passport expires $whenText (${record.expiryDate}). Many countries need 6 months' validity."
                              else "Crew ID expiring" to "Your crew ID expires $whenText (${record.expiryDate})."
                else -> "Health card expiring" to "Your health card expires $whenText (${record.expiryDate})."
            }
            showNotification(record.id, title, message)
        }
        return Result.success()
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val channelId = "expiry_alerts_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the Notification Channel (Required for modern Android)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Expiry Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming document and training expiries"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // We can change this to a custom icon later!
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}
