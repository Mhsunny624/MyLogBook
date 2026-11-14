/*
 * Downloads a release APK from GitHub into the app's cache and hands it to Android's package
 * installer. Android always asks the user to confirm (one tap); it only installs the file if it is
 * signed with the same key as the installed app, so a wrong or damaged file is rejected.
 */
package com.example.mylogbook.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateInstaller {
    private const val DIR = "updates"

    /**
     * Downloads [url] to the cache, reporting progress 0..1 (or null while the size is unknown).
     * Returns the APK file after checking it is a newer version of this app.
     */
    suspend fun download(context: Context, url: String, version: String, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }             // remove older downloads
            val target = File(dir, "MyLogBook-v$version.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var lastReport = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); done += n
                        if (done - lastReport > 128 * 1024) {
                            lastReport = done
                            withContext(Dispatchers.Main) { onProgress(if (total > 0) done.toFloat() / total else null) }
                        }
                    }
                }
            }
            verify(context, target)
            target
        }

    /** The file must be this app (same package) and a higher version than the one installed. */
    private fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        val archive: PackageInfo = pm.getPackageArchiveInfo(apk.path, 0)
            ?: throw IllegalStateException("The downloaded file is not a valid app")
        val installed = pm.getPackageInfo(context.packageName, 0)
        if (archive.packageName != context.packageName) throw IllegalStateException("The downloaded file is a different app")
        if (versionCode(archive) <= versionCode(installed)) throw IllegalStateException("The downloaded version is not newer")
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    /** True if Android lets this app open the installer (the one-time "Install unknown apps" switch). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows MyLogBook to install apps. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Opens Android's installer for [apk]; the user taps "Update" and their data is kept. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
