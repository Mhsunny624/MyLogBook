/*
 * Taka payslip estimate: fixed pay items plus flying allowance and flying overtime.
 */
package com.example.mylogbook.domain.pay

import kotlin.math.max

data class TakaEstimate(
    val hasBasic: Boolean,
    val fixedTotal: Double,
    val uniform: Double,
    val payrollHours: Double,
    val flyingAllowance: Double,
    val otPayrollHours: Double,
    val flyingOt: Double,
    val total: Double
)

/** Hours the way the payroll writes them: 77:21 -> 77.21 (multiplied as a plain number). */
fun payrollHours(mins: Int): Double = (mins / 60) + (mins % 60) / 100.0

/** Taka payslip estimate for a month's duty (paid with the next month's salary). */
fun estimateTaka(rates: PayRates, flyMins: Int): TakaEstimate {
    val basic = rates.basicSalary
    val fixed = basic * (1 + rates.houseRentPct / 100.0 + rates.specialPct / 100.0) +
        rates.aviationTk + rates.tradeTk + rates.jetTk
    val hours = payrollHours(flyMins)
    val flying = hours * rates.flyingTkPerHour
    val otHours = payrollHours(max(0, flyMins - rates.minHours * 60))
    val flyingOt = otHours * rates.flyingTkPerHour
    return TakaEstimate(
        hasBasic = basic > 0,
        fixedTotal = fixed,
        uniform = rates.uniformTk,
        payrollHours = hours,
        flyingAllowance = flying,
        otPayrollHours = otHours,
        flyingOt = flyingOt,
        total = fixed + rates.uniformTk + flying + flyingOt
    )
}
