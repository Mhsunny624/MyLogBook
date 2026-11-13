/*
 * Dialogs shown at start-up: a one-time welcome notice (unofficial app, estimates only) and an
 * "Update available" prompt when GitHub has a newer release.
 */
package com.example.mylogbook.ui.components

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.mylogbook.BuildConfig
import com.example.mylogbook.data.update.ReleaseInfo
import com.example.mylogbook.data.update.UpdateChecker

private const val KEY_WELCOME_SEEN = "welcome_seen_v1"

@Composable
fun StartupDialogs(prefs: SharedPreferences) {
    var showWelcome by remember { mutableStateOf(!prefs.getBoolean(KEY_WELCOME_SEEN, false)) }
    var update by remember { mutableStateOf<ReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        update = UpdateChecker.checkForUpdate(prefs, BuildConfig.VERSION_NAME)
    }

    if (showWelcome) {
        WelcomeNoticeDialog(onDismiss = {
            prefs.edit { putBoolean(KEY_WELCOME_SEEN, true) }
            showWelcome = false
        })
    } else {
        update?.let { UpdateAvailableDialog(it, onDismiss = { update = null }) }
    }
}

@Composable
fun WelcomeNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to MyLogBook") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A personal logbook for Biman cabin crew, made by a crew member.")
                Text("This is not an official Biman Bangladesh Airlines app.", fontWeight = FontWeight.SemiBold)
                Text("Rest times and pay figures are estimates based on the published rules. Always check against your roster, USD statement and payslip.")
                Text("Your entries stay on your phone. Turn on Auto Backup in Settings so you don't lose them.", fontSize = 13.sp)
                Text("Next: open Settings and enter your profile and Pay Settings (crew type, joining date, basic salary).", fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("I understand") } }
    )
}

@Composable
fun UpdateAvailableDialog(release: ReleaseInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${release.version} is out (you have ${BuildConfig.VERSION_NAME}).")
                Text("Download the new APK and install it over this one. Your entries are kept.", fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = release.apkUrl ?: release.pageUrl
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                onDismiss()
            }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
