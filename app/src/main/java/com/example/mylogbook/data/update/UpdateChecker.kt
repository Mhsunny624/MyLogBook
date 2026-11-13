/*
 * Checks GitHub for a newer release of the app (public API, no account, no data sent apart from
 * the request itself). Used to show "Update available" to crew who installed the APK by hand.
 */
package com.example.mylogbook.data.update

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,      // e.g. "1.1.0" (tag without the leading "v")
    val pageUrl: String,      // GitHub release page
    val apkUrl: String?,      // direct APK download, if attached
    val notes: String
)

object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/Mhsunny624/MyLogBook/releases/latest"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    /** True if [candidate] is a higher version than [current] ("1.10.0" > "1.9.2"). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Latest published release, or null when offline / no release / any error. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            ReleaseInfo(
                version = json.optString("tag_name").removePrefix("v"),
                pageUrl = json.optString("html_url"),
                apkUrl = apk,
                notes = json.optString("body")
            )
        } catch (_: Exception) { null }
    }

    /**
     * Returns a newer release if one exists, checking at most every 12 hours unless [force].
     * Never throws.
     */
    suspend fun checkForUpdate(prefs: SharedPreferences, currentVersion: String, force: Boolean = false): ReleaseInfo? {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return null
        val latest = fetchLatest() ?: return null
        prefs.edit { putLong(KEY_LAST_CHECK, now) }
        return if (isNewer(latest.version, currentVersion)) latest else null
    }
}
