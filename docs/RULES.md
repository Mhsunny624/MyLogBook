# Duty and pay rules used by MyLogBook

These are the Biman Bangladesh Airlines cabin-crew rules the app applies. Code references are given so you can find the implementation. Pay results are estimates only. All rates are defaults that can be edited in Settings → Pay Settings.

Times are stored in UTC. Bangladesh Standard Time (BST) is UTC+6. Home base is `DAC` (Dhaka).

## 1. Duty times (`ui/screens/entry/FlightEntryScreen.kt`, "Calculate" button)

- **Duty On** = scheduled departure of the first sector − **1 h 45 min** when departing from base (DAC), − **1 h 15 min** at an outstation. It is always based on the *scheduled* departure, not the actual block-off time.
- **Duty Off** = block-on of the last sector + **45 min**.
- **Total duty** = Duty Off − Duty On (crossing midnight is handled).
- **Training records** count 8 h of duty per calendar day.

## 2. Short rest (`domain/duty/RestRules.kt` → `calculateShortRest`)

- Applies when the day's last sector **arrives at DAC**, whatever the number of sectors.
- Minimum rest = **2 × the duty time**, starting at Duty Off (Duty On + total duty).
- The end time is shown in BST.

## 3. Post-flight off duty at home base (`calculateClaimOffForDay`)

- Applies when a trip that started from home base ends back at home base, and the trip covered more than one calendar day.
- Calendar days away (departure date to return date, inclusive) give the days off:

  | Days away | Days off |
  |---|---|
  | 2–3 | 1 |
  | 4–5 | 2 |
  | 6–9 | 3 |
  | 10+ | 4 |

- **Off duty starts after the short rest ends.** End = Duty Off + 2 × duty + days off.

## 4. Block hours across a month end (`ui/screens/logbook/LogbookScreen.kt`)

Monthly block and duty totals use **UTC**, as the Biman payroll does. A sector flown across 00:00 UTC at the end of a month is split there: the minutes before midnight count in the old month, and the rest count in the **next** month.
- If a sector has no Block off, its start is estimated from Duty On (+1:45 at base, +1:15 at an outstation), or the start of its UTC day as a last resort.
- A flight dated the last day of a month without Block off / Block on cannot be split reliably, so the USD card shows a warning (`monthEndFlightsMissingTimes`).

## 5. Overseas allowance (USD) (`domain/pay/UsdAllowance.kt`)

- A monthly amount for **70 flying hours**, plus or minus an hourly rate for each hour above or below 70.
  - Hours are real hours and minutes: 77:21 = 77.35 h.
  - **Deadhead (DHC) block time is not flying hours:** it is left out of the 70-hour count and of the Taka flying allowance, but still shown in the logbook totals. Time away from base on DHC still counts for incidental, and DHC duty time still counts as duty hours.
  - The allowance is never negative.

| Crew | Monthly (70 h) | Per hour ± |
|---|---|---|
| Regular | USD 1,500 | USD 21 |
| Contractual, 5+ years' service | USD 1,500 | USD 21 |
| Contractual, 3–4 years | USD 1,200 | USD 17 |
| Contractual, under 3 years | USD 1,000 | USD 14 |

- **Service length** = completed years from the joining date to the last day of the month (`serviceYears`).
- **Long-stay waiver:** if one trip ending in the month lasted more than 5 days, 10 shortfall hours are waived. More than 10 days waives 20 hours.
- The deduction assumes the crew member was rostered for 70 h. Hours lost to cancellations, re-routing, aircraft change, company training or VVIP duty should not be deducted. The app can't know these, so the card shows a note.

## 6. Incidental allowance (USD) (`domain/pay/Trips.kt`, `incidentalForMonth`)

- **USD 12 per day away**, prorated on the exact time away: hours away ÷ 24 × 12.
  - A trip runs from block-off at base to block-on back at base.
  - **Shuttles earn no incidental.** A shuttle is a rotation whose outbound and return sectors are in the same logbook day (out and back on one duty, no layover), domestic or international. The card lists how many shuttles were left out (`Trip.shuttle`).
- A trip that crosses a month end is **split at 00:00 UTC** between the two months.
- **Missing block times:** they are estimated from the duty times (Duty On + 1:45 / 1:15, Duty Off − 0:45), and the result is marked as estimated.
- **Paid one month later:** the incidental earned in month M appears on the USD statement of month M+1.

## 7. USD statement total

Total = overseas allowance for the month + incidental earned in the previous month. **No rounding is applied for now.** Past statements fit rounding to the nearest USD 5, but this is to be confirmed from the latest documents. A rounding step can be set in Pay Settings (0 = off).

## 8. Taka payslip (`domain/pay/TakaPayroll.kt`)

| Item | Rule (default) |
|---|---|
| Basic salary | entered by the user |
| House rent | 55 % of basic |
| Special benefit | 15 % of basic |
| Aviation allowance | Tk 2,708 |
| Trade allowance | Tk 2,594 |
| Jet allowance | Tk 1,100 |
| Uniform allowance | Tk 1,000 |
| Flying allowance | flying hours × Tk 160 |
| Flying overtime | hours above 70 × Tk 160 (extra) |

The payroll writes hours as `HH.MM` and multiplies that like a plain number. For example, 77:21 is treated as 77.21 × 160, not 77.35 × 160. The app copies this (`payrollHours`).

Flying hours are paid with the following month's salary.

These items are not estimated:
- duty overtime (rate = basic ÷ 208 per hour; the payable-hours rule is not modelled)
- refused day-off and absence (fixed total ÷ days in month × days)
- provident fund, tax and loans

## 9. Grounded periods (`domain/pay/Grounded.kt`)

- **How it's stored:** a Health Card record with subtype "Grounded", with from/until dates and an optional reason. A blank "until" means still grounded, and the days are counted up to today (BST).
- **No expiry alerts** are sent for grounded records.
- **Summary:** the grounded days in the selected month are shown there. When flying hours fall short of 70, the USD card also notes them.

## Sources

- Biman Admin Orders 17/2018 (overseas and incidental allowance) and 17/2023 (contractual crew tiers)
- the crew member's own monthly USD statements and payslips, used to check the arithmetic
