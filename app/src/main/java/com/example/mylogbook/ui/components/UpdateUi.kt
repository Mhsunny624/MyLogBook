/*
 * Update flow shared by the About card in Settings and the start-up "Update available" dialog:
 * check GitHub -> download inside the app (progress) -> Android's installer (one tap).
 */
package com.example.mylogbook.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mylogbook.BuildConfig
import com.example.mylogbook.data.update.ReleaseInfo
import com.example.mylogbook.data.update.UpdateChecker
import com.example.mylogbook.data.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    /** Downloaded; waiting for the one-time "Install unknown apps" permission or the user's tap. */
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateState
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

@Stable
class UpdateFlow(private val context: Context, private val scope: CoroutineScope) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Manual check (always asks GitHub). */
    fun check() {
        state = UpdateState.Checking
        scope.launch {
            val latest = UpdateChecker.fetchLatest()
            state = when {
                latest == null -> UpdateState.Failed("Couldn't reach GitHub. Check your internet connection.")
                UpdateChecker.isNewer(latest.version, BuildConfig.VERSION_NAME) -> UpdateState.Available(latest)
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Background check every time the app starts; only surfaces a newer version (silent when offline). */
    fun checkQuietly(prefs: SharedPreferences) {
        scope.launch {
            UpdateChecker.checkForUpdate(prefs, BuildConfig.VERSION_NAME, force = true)?.let { state = UpdateState.Available(it) }
        }
    }

    fun download(release: ReleaseInfo) {
        val url = release.apkUrl ?: run { state = UpdateState.Failed("This release has no APK attached.", release); return }
        state = UpdateState.Downloading(release, null)
        scope.launch {
            state = try {
                val apk = UpdateInstaller.download(context, url, release.version) { p -> state = UpdateState.Downloading(release, p) }
                UpdateState.Ready(release, apk).also { install(it) }
            } catch (e: Exception) {
                UpdateState.Failed(e.localizedMessage ?: "Download failed", release)
            }
        }
    }

    /** Opens the installer, or the permission screen the first time. */
    fun install(ready: UpdateState.Ready) {
        if (UpdateInstaller.canInstall(context)) UpdateInstaller.install(context, ready.apk)
        else UpdateInstaller.openInstallPermissionSettings(context)
    }

    fun dismiss() { state = UpdateState.Idle }
}

@Composable
fun rememberUpdateFlow(): UpdateFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateFlow(context.applicationContext, scope) }
}

/** First lines of the release notes, without Markdown symbols. */
fun shortNotes(notes: String, maxLines: Int = 6): String =
    notes.lines().map { it.replace(Regex("[#*>`]"), "").trim() }
        .filter { it.isNotBlank() && !it.startsWith("[") }
        .take(maxLines).joinToString("\n")

/** Status text and buttons for the current update state (used in Settings → About and the dialog). */
@Composable
fun UpdateStatus(flow: UpdateFlow, showCheckButton: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = flow.state) {
            UpdateState.Idle -> {}
            UpdateState.Checking -> Text("Checking for updates…", fontSize = 13.sp, color = Color.Gray)
            UpdateState.UpToDate -> Text("✅ You're up to date.", fontSize = 13.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
            is UpdateState.Available -> {
                Text("Version ${s.release.version} is available", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val notes = shortNotes(s.release.notes)
                if (notes.isNotBlank()) Text(notes, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp)
                Button(onClick = { flow.download(s.release) }, modifier = Modifier.fillMaxWidth()) { Text("Install update") }
            }
            is UpdateState.Downloading -> {
                Text("Downloading version ${s.release.version}…", fontSize = 13.sp)
                if (s.progress != null) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            is UpdateState.Ready -> {
                if (UpdateInstaller.canInstall(context)) {
                    Text("Downloaded. Tap Install, then Update on the next screen. Your entries are kept.", fontSize = 13.sp)
                    Button(onClick = { flow.install(s) }, modifier = Modifier.fillMaxWidth()) { Text("Install version ${s.release.version}") }
                } else {
                    Text("One-time step: allow MyLogBook to install apps (turn the switch on), come back, then tap Install.", fontSize = 13.sp)
                    OutlinedButton(onClick = { UpdateInstaller.openInstallPermissionSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("Open setting") }
                    Button(onClick = { flow.install(s) }, modifier = Modifier.fillMaxWidth()) { Text("Install version ${s.release.version}") }
                }
            }
            is UpdateState.Failed -> {
                Text("⚠️ ${s.message}", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                s.release?.let { r -> OutlinedButton(onClick = { flow.download(r) }, modifier = Modifier.fillMaxWidth()) { Text("Try again") } }
            }
        }
        if (showCheckButton && flow.state !is UpdateState.Downloading && flow.state !is UpdateState.Checking &&
            flow.state !is UpdateState.Available && flow.state !is UpdateState.Ready) {
            OutlinedButton(onClick = { flow.check() }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
        }
    }
}

/** Start-up dialog shown when a newer version was found in the background. */
@Composable
fun UpdateAvailableDialog(flow: UpdateFlow) {
    val s = flow.state
    if (s is UpdateState.Idle || s is UpdateState.Checking || s is UpdateState.UpToDate) return
    val title = (s as? UpdateState.Available)?.release?.version ?: (s as? UpdateState.Downloading)?.release?.version
        ?: (s as? UpdateState.Ready)?.release?.version ?: (s as? UpdateState.Failed)?.release?.version
    AlertDialog(
        onDismissRequest = { if (s !is UpdateState.Downloading) flow.dismiss() },
        title = { Text(if (title != null) "Update available: v$title" else "Update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A new version of MyLogBook is ready. You have ${BuildConfig.VERSION_NAME}; your entries are kept when you update.", fontSize = 13.sp, color = Color.Gray)
                UpdateStatus(flow, showCheckButton = false)
            }
        },
        confirmButton = {},
        dismissButton = {
            if (s !is UpdateState.Downloading) TextButton(onClick = { flow.dismiss() }) { Text("Later") }
        }
    )
}
