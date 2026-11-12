package com.example.mylogbook.data.backup

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mylogbook.data.FlightLog
import com.example.mylogbook.domain.pay.payPrefsToJson
import com.example.mylogbook.domain.pay.restorePayPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON backup format, shared by the manual backup/restore in Settings and the automatic backup
 * written by [com.example.mylogbook.viewmodel.FlightLogViewModel] after every save or delete.
 *
 * Shape: { "version": 1, "airline": "...", "logs": [ {FlightLog fields} ],
 *          "profile": { name, employeeId, rosterName }, "paySettings": { pay_* keys } }
 *
 * When you add a field to [FlightLog], add it to [logToJson] and [logFromJson] too. Read new
 * fields with optString(..., default) so older backup files still restore.
 */
object BackupJson {
    const val VERSION = 1

    fun build(logs: List<FlightLog>, prefs: SharedPreferences, airline: String): JSONObject {
        val jsonLogs = JSONArray()
        logs.forEach { jsonLogs.put(logToJson(it)) }
        return JSONObject().apply {
            put("version", VERSION)
            put("airline", airline)
            put("logs", jsonLogs)
            put("profile", JSONObject().apply {
                put("name", prefs.getString("profile_name", ""))
                put("employeeId", prefs.getString("profile_id", ""))
                put("rosterName", prefs.getString("profile_roster_name", ""))
            })
            put("paySettings", payPrefsToJson(prefs))
        }
    }

    fun logToJson(log: FlightLog): JSONObject = JSONObject().apply {
        put("recordType", log.recordType)
        put("trainingType", log.trainingType)
        put("expiryDate", log.expiryDate)
        put("logbookNumber", log.logbookNumber)
        put("flightNo", log.flightNo)
        put("aircraftReg", log.aircraftReg)
        put("departure", log.departure)
        put("arrival", log.arrival)
        put("dutyType", log.dutyType)
        put("flightDate", log.flightDate)
        put("dutyOn", log.dutyOn)
        put("dutyOff", log.dutyOff)
        put("totalDuty", log.totalDuty)
        put("blockOff", log.blockOff)
        put("blockOn", log.blockOn)
        put("totalBlock", log.totalBlock)
        put("crewNames", log.crewNames)
        put("notes", log.notes)
        put("schedDep", log.schedDep)
    }

    /** A log read from a backup, with id = 0 so Room assigns a new one on insert. */
    fun logFromJson(obj: JSONObject): FlightLog = FlightLog(
        id = 0,
        recordType = obj.optString("recordType", "Flight"),
        trainingType = obj.optString("trainingType", ""),
        expiryDate = obj.optString("expiryDate", ""),
        logbookNumber = obj.optString("logbookNumber", ""),
        flightNo = obj.optString("flightNo", ""),
        aircraftReg = obj.optString("aircraftReg", ""),
        departure = obj.optString("departure", ""),
        arrival = obj.optString("arrival", ""),
        dutyType = obj.optString("dutyType", ""),
        flightDate = obj.optString("flightDate", ""),
        dutyOn = obj.optString("dutyOn", ""),
        dutyOff = obj.optString("dutyOff", ""),
        totalDuty = obj.optString("totalDuty", ""),
        blockOff = obj.optString("blockOff", ""),
        blockOn = obj.optString("blockOn", ""),
        totalBlock = obj.optString("totalBlock", ""),
        crewNames = obj.optString("crewNames", ""),
        notes = obj.optString("notes", ""),
        schedDep = obj.optString("schedDep", "")
    )

    /** Restores the profile and pay settings saved in a backup (older backups may lack them). */
    fun restoreSettings(root: JSONObject, prefs: SharedPreferences) {
        root.optJSONObject("profile")?.let { profile ->
            prefs.edit {
                putString("profile_name", profile.optString("name", ""))
                putString("profile_id", profile.optString("employeeId", ""))
                putString("profile_roster_name", profile.optString("rosterName", ""))
            }
        }
        root.optJSONObject("paySettings")?.let { restorePayPrefs(prefs, it) }
    }
}
