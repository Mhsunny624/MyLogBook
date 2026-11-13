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
 * Shape: { "version": 2, "airline": "...", "logs": [ {FlightLog fields + id, createdAt} ],
 *          "profile": { name, employeeId, rosterName }, "paySettings": { pay_* keys } }
 *
 * Logs are written in id order. The id is kept only to restore entries in the same order (a day's
 * sectors are ordered by id); restored entries get new ids.
 * Version 1 files (before Nov 2026) have no id and list the newest entries first.
 *
 * When you add a field to [FlightLog], add it to [logToJson] and [logFromJson] too. Read new
 * fields with optString(..., default) so older backup files still restore.
 */
object BackupJson {
    const val VERSION = 2

    fun build(logs: List<FlightLog>, prefs: SharedPreferences, airline: String): JSONObject {
        val jsonLogs = JSONArray()
        logs.sortedBy { it.id }.forEach { jsonLogs.put(logToJson(it)) }
        return JSONObject().apply {
            put("version", VERSION)
            put("airline", airline)
            put("exportedAt", System.currentTimeMillis())
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
        put("id", log.id)
        put("createdAt", log.createdAt)
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
        schedDep = obj.optString("schedDep", ""),
        createdAt = obj.optLong("createdAt", 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    /**
     * All logs of a backup, oldest first (the order they must be inserted in so a day's sectors
     * keep their order). Uses the saved ids when present; version 1 files are newest-first, so
     * they are reversed.
     */
    fun readLogs(root: JSONObject): List<FlightLog> {
        val arr = root.optJSONArray("logs") ?: throw IllegalArgumentException("No logs in file")
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
        if (items.isNotEmpty() && items.all { it.has("id") }) {
            return items.sortedBy { it.optInt("id") }.map { logFromJson(it) }
        }
        // Version 1: newest first, and sectors saved in the same millisecond may be in any order,
        // so put each day's sectors back in route order (DAC→ZYL, ZYL→LHR).
        val logs = items.reversed().map { logFromJson(it) }
        val flightsByDay = logs.filter { it.recordType == "Flight" }.groupBy { it.flightDate }
        val emittedDays = HashSet<String>()
        val result = ArrayList<FlightLog>(logs.size)
        for (log in logs) {
            if (log.recordType != "Flight") { result += log; continue }
            if (emittedDays.add(log.flightDate)) result += chainSectors(flightsByDay.getValue(log.flightDate))
        }
        return result
    }

    /** Orders one day's sectors so each departs from where the previous one arrived, if possible. */
    internal fun chainSectors(sectors: List<FlightLog>): List<FlightLog> {
        if (sectors.size < 2) return sectors
        val arrivals = sectors.map { it.arrival }.toSet()
        val first = sectors.firstOrNull { it.departure !in arrivals } ?: return sectors
        val remaining = sectors.toMutableList().apply { remove(first) }
        val chain = mutableListOf(first)
        while (remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { it.departure == chain.last().arrival } ?: return sectors
            chain += next; remaining.remove(next)
        }
        return chain
    }

    /**
     * Key used by "Add missing" restore to recognise an entry that is already in the app.
     * Flights: date + flight + route. Other records: date + type + subtype + end/expiry date, so
     * e.g. a Health Card and a Grounded record on the same day are both kept.
     */
    fun duplicateKey(log: FlightLog): String =
        if (log.recordType == "Flight") {
            listOf("F", log.flightDate, log.flightNo, log.departure, log.arrival).joinToString("|")
        } else {
            listOf(log.recordType, log.flightDate, log.trainingType, log.dutyOff, log.expiryDate).joinToString("|")
        }

    /** Restores the profile and pay settings saved in a backup (older backups may lack them). */
    fun restoreSettings(root: JSONObject, prefs: SharedPreferences) {
        root.optJSONObject("profile")?.let { profile ->
            prefs.edit {
                putString("profile_name", profile.optString("name", ""))
                putString("profile_id", profile.optString("employeeId", ""))
                putString("profile_roster_name", profile.optString("rosterName", ""))
            }
        }
        root.optJSONObject("paySettings")?.let { pay ->
            // Version 1 backups carry the old default USD rounding of 5; rounding is now off by default.
            if (root.optInt("version", 1) < 2 && pay.optString("pay_round_step") == "5") pay.remove("pay_round_step")
            restorePayPrefs(prefs, pay)
        }
    }
}
