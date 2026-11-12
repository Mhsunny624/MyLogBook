/*
 * App entry point: asks for notification permission, schedules the daily expiry check and
 * shows the splash screen, then the main navigation.
 */
package com.example.mylogbook

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mylogbook.ui.navigation.MainNavigationApp
import com.example.mylogbook.ui.screens.splash.AnimatedSplashScreen
import com.example.mylogbook.ui.theme.BimanFlightLogTheme
import com.example.mylogbook.workers.ExpiryWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FlightLogPrefs", MODE_PRIVATE)

        // 1. Ask for Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    startExpiryWorker()
                }
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 12 and below don't need runtime permission for this
            startExpiryWorker()
        }

        setContent {
            var themePreference by remember { mutableStateOf(prefs.getString("theme_pref", "System") ?: "System") }
            val isDark = when (themePreference) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            var showSplash by remember { mutableStateOf(true) }

            BimanFlightLogTheme(
                darkTheme = isDark,
                dynamicColor = false
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    if (showSplash) {
                        AnimatedSplashScreen(
                            onTimeout = { showSplash = false }
                        )
                    } else {
                        MainNavigationApp(
                            airlineName = "My Log Book",
                            prefs = prefs,
                            currentTheme = themePreference,
                            onThemeChange = { newTheme ->
                                prefs.edit { putString("theme_pref", newTheme) }
                                themePreference = newTheme
                            }
                        )
                    }

                }
            }
        }
    }

    private fun startExpiryWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyExpiryCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
