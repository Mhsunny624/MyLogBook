/*
 * Pay settings (stored as "pay_*" strings in SharedPreferences "FlightLogPrefs") and the
 * PayRates they produce. Defaults are the Biman rates; the user can edit them in Settings.
 */
package com.example.mylogbook.domain.pay

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Calendar
import kotlin.math.max
import org.json.JSONObject

/** All pay settings keys with their default values (stored as strings in "FlightLogPrefs"). */
val PAY_DEFAULTS: Map<String, String> = linkedMapOf(
    "pay_crew_type" to "Contractual",
    "pay_joining_date" to "",
    "pay_basic" to "",
    "pay_oa_monthly" to "",   // blank = Biman rate by crew type / service length
    "pay_oa_hourly" to "",    // blank = Biman rate by crew type / service length
    "pay_min_hours" to "70",
    "pay_incidental_day" to "12",
    "pay_round_step" to "5",
    "pay_flying_tk_hr" to "160",
    "pay_hr_pct" to "55",
    "pay_special_pct" to "15",
    "pay_aviation" to "2708",
    "pay_trade" to "2594",
    "pay_jet" to "1100",
    "pay_uniform" to "1000"
)

/** Keys that hold rates (reset by "Reset rates to Biman defaults"); personal fields are not reset. */
val PAY_RATE_KEYS: List<String> = PAY_DEFAULTS.keys.filter {
    it != "pay_crew_type" && it != "pay_joining_date" && it != "pay_basic"
}

data class PayRates(
    val crewType: String,
    val joiningDate: String,
    val basicSalary: Double,
    val oaMonthlyOverride: Double?,
    val oaHourlyOverride: Double?,
    val minHours: Int,
    val incidentalPerDay: Double,
    val usdRoundStep: Double,
    val flyingTkPerHour: Double,
    val houseRentPct: Double,
    val specialPct: Double,
    val aviationTk: Double,
    val tradeTk: Double,
    val jetTk: Double,
    val uniformTk: Double
)

fun payPref(prefs: SharedPreferences, key: String): String =
    prefs.getString(key, null) ?: PAY_DEFAULTS[key] ?: ""

fun payRatesFrom(get: (String) -> String): PayRates {
    fun num(key: String): Double? = get(key).trim().replace(",", "").toDoubleOrNull()
    fun numOr(key: String): Double = num(key) ?: PAY_DEFAULTS[key]?.toDoubleOrNull() ?: 0.0
    return PayRates(
        crewType = get("pay_crew_type").ifBlank { "Contractual" },
        joiningDate = get("pay_joining_date").trim(),
        basicSalary = num("pay_basic") ?: 0.0,
        oaMonthlyOverride = num("pay_oa_monthly"),
        oaHourlyOverride = num("pay_oa_hourly"),
        minHours = numOr("pay_min_hours").toInt(),
        incidentalPerDay = numOr("pay_incidental_day"),
        usdRoundStep = numOr("pay_round_step"),
        flyingTkPerHour = numOr("pay_flying_tk_hr"),
        houseRentPct = numOr("pay_hr_pct"),
        specialPct = numOr("pay_special_pct"),
        aviationTk = numOr("pay_aviation"),
        tradeTk = numOr("pay_trade"),
        jetTk = numOr("pay_jet"),
        uniformTk = numOr("pay_uniform")
    )
}

fun loadPayRates(prefs: SharedPreferences): PayRates = payRatesFrom { payPref(prefs, it) }

/** Pay settings for the JSON backup. */
fun payPrefsToJson(prefs: SharedPreferences): JSONObject = JSONObject().apply {
    PAY_DEFAULTS.keys.forEach { key -> prefs.getString(key, null)?.let { put(key, it) } }
}

/** Restores pay settings from a JSON backup (older backups simply have none). */
fun restorePayPrefs(prefs: SharedPreferences, json: JSONObject) {
    prefs.edit {
        PAY_DEFAULTS.keys.forEach { key -> if (json.has(key)) putString(key, json.optString(key, "")) }
    }
}

/** Completed years of service by the last day of the given month. */
fun serviceYears(joiningDate: String, year: Int, month0: Int): Int? {
    val joinMs = parseDayUtc(joiningDate) ?: return null
    val join = Calendar.getInstance(UTC).apply { timeInMillis = joinMs }
    val lastDay = Calendar.getInstance(UTC).apply { clear(); set(year, month0, 1) }
        .getActualMaximum(Calendar.DAY_OF_MONTH)
    var years = year - join.get(Calendar.YEAR)
    val jm = join.get(Calendar.MONTH)
    if (month0 < jm || (month0 == jm && lastDay < join.get(Calendar.DAY_OF_MONTH))) years--
    return max(0, years)
}
