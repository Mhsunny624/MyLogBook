/*
 * Automatic backup to the folder the user picked in Settings (Storage Access Framework).
 *
 * After every change it writes FlightLogAutoBackup.json (the latest copy) and one dated copy per
 * day, FlightLogBackup-yyyy-MM-dd.json, keeping the last 14 days. The result is recorded in
 * preferences so Settings can show "Last auto backup" or an error.
 */
package com.example.mylogbook.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.mylogbook.data.FlightLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBackup {
    const val KEY_FOLDER = "auto_backup_folder"
    const val KEY_LAST_OK = "auto_backup_last_ok"        // millis of the last successful backup
    const val KEY_LAST_ERROR = "auto_backup_last_error"  // "" when the last attempt worked

    private const val LATEST_NAME = "FlightLogAutoBackup.json"
    private const val DAILY_PREFIX = "FlightLogBackup-"
    private const val KEEP_DAYS = 14

    private val lock = Mutex()   // one backup at a time; a newer one always runs after an older one

    suspend fun run(context: Context, prefs: SharedPreferences, logs: List<FlightLog>) = lock.withLock {
        val folderUri = prefs.getString(KEY_FOLDER, "").orEmpty()
        if (folderUri.isEmpty()) return@withLock
        try {
            val uri = folderUri.toUri()
            val hasPermission = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
            val folder = DocumentFile.fromTreeUri(context, uri)
            if (!hasPermission || folder == null || !folder.canWrite()) {
                fail(prefs, "No access to the backup folder. Choose the folder again in Settings.")
                return@withLock
            }
            val bytes = BackupJson.build(logs, prefs, "My Log Book").toString(2).toByteArray(Charsets.UTF_8)

            writeFile(context, folder, LATEST_NAME, bytes)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            writeFile(context, folder, "$DAILY_PREFIX$today.json", bytes)
            pruneOldDailyCopies(folder)

            prefs.edit { putLong(KEY_LAST_OK, System.currentTimeMillis()); putString(KEY_LAST_ERROR, "") }
        } catch (e: Exception) {
            fail(prefs, "Auto backup failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Writes [bytes] to [name], keeping that exact file name.
     *
     * 1. If the file exists, it is overwritten in place with truncation ("wt") and its size is
     *    checked afterwards. This keeps the name: deleting and re-creating it can make Android
     *    save "FlightLogAutoBackup (1).json" instead, leaving the real file stale (seen on 1.0.8).
     * 2. Otherwise (new file, or the in-place write could not be confirmed) the content goes into a
     *    temporary file that then takes the name, so a failed write never leaves a half-written file.
     * Leftover "name (1).json" copies and temporary files are removed once the file is confirmed.
     */
    private fun writeFile(context: Context, folder: DocumentFile, name: String, bytes: ByteArray) {
        val existing = folder.findFile(name)
        if (existing != null && existing.isFile) {
            val ok = try {
                write(context, existing, bytes, "wt")
                existing.length() == bytes.size.toLong()
            } catch (_: Exception) { false }
            if (ok) { removeStrayCopies(folder, name); return }
        }

        val tmpName = name.removeSuffix(".json") + "-writing.json"
        folder.findFile(tmpName)?.delete()
        val tmp = folder.createFile("application/json", tmpName) ?: throw IllegalStateException("Cannot create $tmpName")
        write(context, tmp, bytes, "wt")
        folder.findFile(name)?.delete()
        if (!tmp.renameTo(name) || tmp.name != name) {
            // Provider cannot rename (or picked another name): write the final file directly.
            val file = folder.findFile(name) ?: folder.createFile("application/json", name)
                ?: throw IllegalStateException("Cannot create $name")
            write(context, file, bytes, "wt")
            if (file.name != name) throw IllegalStateException("Android saved the backup as ${file.name}")
            tmp.delete()
        }
        removeStrayCopies(folder, name)
    }

    /** Deletes "name (1).json", "name (2).json" and "name-writing.json" left by earlier attempts. */
    private fun removeStrayCopies(folder: DocumentFile, name: String) {
        val base = name.removeSuffix(".json")
        val stray = Regex("^" + Regex.escape(base) + "( \\(\\d+\\)|-writing)\\.json$")
        folder.listFiles().forEach { f -> if (f.name?.let { stray.matches(it) } == true) f.delete() }
    }

    private fun write(context: Context, file: DocumentFile, bytes: ByteArray, mode: String) {
        context.contentResolver.openOutputStream(file.uri, mode)?.use { it.write(bytes) }
            ?: throw IllegalStateException("Cannot write ${file.name}")
    }

    private fun pruneOldDailyCopies(folder: DocumentFile) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        folder.listFiles().forEach { f ->
            val n = f.name ?: return@forEach
            if (!n.startsWith(DAILY_PREFIX) || !n.endsWith(".json")) return@forEach
            val date = try { fmt.parse(n.removePrefix(DAILY_PREFIX).removeSuffix(".json")) } catch (_: Exception) { null }
            if (date != null && date.time < cutoff) f.delete()
        }
    }

    private fun fail(prefs: SharedPreferences, message: String) {
        prefs.edit { putString(KEY_LAST_ERROR, message) }
    }
}
