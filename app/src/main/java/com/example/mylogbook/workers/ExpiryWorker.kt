/*
 * Daily WorkManager job that notifies about training / health card expiries (30 days before,
 * then every day from 7 days to 0). Keep this class name and package: WorkManager stores it.
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
import java.util.Locale
import java.util.concurrent.TimeUnit

class ExpiryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).flightLogDao()
        val records = dao.getExpiringRecords()

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val today = Date()

        records.forEach { record ->
            if (record.expiryDate.isNotBlank()) {
                try {
                    val expDate = sdf.parse(record.expiryDate)
                    if (expDate != null) {
                        val diffMillis = expDate.time - today.time
                        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

                        // Trigger notifications exactly at 30 days, 7 days, and 1 day remaining
                        // Trigger a heads-up at 30 days, then EVERY day from 7 days down to 0
                        if (diffDays == 30L || diffDays in 0L..7L) {
                            val title = if (record.recordType == "Training") "Training Expiring Soon!" else "Health Card Expiring!"
                            val name = if (record.recordType == "Training") record.trainingType else "Health Card"
                            val message = "Your $name expires in $diffDays days (${record.expiryDate})."

                            showNotification(record.id, title, message)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore parsing errors for empty/invalid dates
                }
            }
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
