/*
 * App entry point: shows Android's launch screen until the logbook has loaded, asks for
 * notification permission, schedules the daily expiry check, then shows the main navigation.
 */
package com.example.mylogbook

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mylogbook.domain.pay.migratePayPrefs
import com.example.mylogbook.ui.navigation.MainNavigationApp
import com.example.mylogbook.ui.theme.BimanFlightLogTheme
import com.example.mylogbook.viewmodel.FlightLogViewModel
import com.example.mylogbook.workers.ExpiryWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    // Same instance as viewModel() inside the composables (activity scope).
    private val vm: FlightLogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch screen: stays until the logbook is loaded (at most 2 s), then fades out.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !vm.loaded.value && SystemClock.uptimeMillis() - started < 2000 }
        splash.setOnExitAnimationListener { provider ->
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            val animators = mutableListOf<android.animation.Animator>(fade)
            if (icon != null) {
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.15f)
                animators += ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.15f)
            }
            AnimatorSet().apply {
                playTogether(animators)
                duration = 300
                interpolator = AccelerateInterpolator()
                doOnEnd { provider.remove() }
                start()
            }
        }

        val prefs = getSharedPreferences("FlightLogPrefs", MODE_PRIVATE)
        migratePayPrefs(prefs)

        // Notification permission (Android 13+); older versions don't need it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) startExpiryWorker()
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startExpiryWorker()
        }

        setContent {
            var themePreference by remember { mutableStateOf(prefs.getString("theme_pref", "System") ?: "System") }
            val isDark = when (themePreference) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            BimanFlightLogTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigationApp(
                        airlineName = "My Log Book",
                        prefs = prefs,
                        currentTheme = themePreference,
                        onThemeChange = { newTheme ->
                            prefs.edit { putString("theme_pref", newTheme) }
                            themePreference = newTheme
                        },
                        vm = vm
                    )
                }
            }
        }
    }

    private fun startExpiryWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyExpiryCheck",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
