# MyLogBook

An Android logbook for airline cabin crew, built around the duty and pay rules of Biman Bangladesh Airlines.
It records flights, training, health card validity, grounded periods and sick leave. From those entries it works out:

- short rest after arriving in Dhaka
- post-flight off duty at home base
- duty on/off times from the scheduled departure
- monthly block and duty totals
- an estimate of the monthly USD allowance statement and the Taka payslip

Everything is stored on the phone. There's no server and no account.

> Pay figures are **estimates** based on the rules in [docs/RULES.md](docs/RULES.md). Rates can be changed in
> Settings → Pay Settings. This is a personal project and is not affiliated with Biman Bangladesh Airlines.

## Features

| Tab | What it does |
|---|---|
| **Logbook → Entries** | Days grouped by date: sectors, block and duty times, rest requirements, non-flight records. Tap to edit; delete asks for confirmation. |
| **Logbook → Summary** | Month or year totals (block, duty, flights, training, sick, grounded days) and, in month view, the USD and Taka estimates. |
| **Records** | Training and health card records with time remaining until expiry. |
| **Calculator** | Adds and subtracts hh:mm times. |
| **Settings** | Theme, profile, pay settings, JSON backup/restore (manual and automatic), monthly PDF report. |
| **Add (+)** | Entry form with tabs Flight / Training / Health Card (incl. Grounded) / Sick. A flying day is entered as chained sectors; duty times can be calculated from the scheduled departure, and a live preview shows the rest requirements before saving. |

Expiry notifications: a daily background job alerts 30 days before a training, health card or sick-leave date expires, then every day from 7 days to 0.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · Android SDK 23+ (target 37).
Single-activity app with one shared `FlightLogViewModel`, no dependency-injection framework.

## Build and run

1. Install Android Studio (with its bundled JDK 21) and open this folder.
2. Let Gradle sync, then **Run** on a device or emulator.

From the command line (Windows):

```bat
set JAVA_HOME=%USERPROFILE%\.jdks\jbr-21.0.11   & rem or Android Studio's bundled JBR
gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`install -r` updates the app in place and keeps the user's data.

## Project layout

```
app/src/main/java/com/example/mylogbook/
├── MainActivity.kt            entry point, schedules the expiry worker
├── data/                      storage
│   ├── FlightLog.kt           the single Room entity (field meanings per record type are documented here)
│   ├── FlightLogDao.kt
│   ├── AppDatabase.kt         database singleton (current schema version)
│   ├── Migrations.kt          one Migration per schema version bump
│   ├── ReferenceData.kt       airport and training suggestion lists
│   └── backup/BackupJson.kt   JSON backup format (manual and automatic backups)
├── domain/                    pure Kotlin rules, no Android UI (unit-tested)
│   ├── duty/
│   │   ├── TimeFormat.kt      HHmm / dd/MM/yyyy helpers
│   │   └── RestRules.kt       short rest + post-flight off duty
│   └── pay/
│       ├── PaySettings.kt     pay_* preferences, defaults, PayRates
│       ├── PayTime.kt         time constants, Bangladesh-time month boundaries
│       ├── Trips.kt           sectors -> trips away from base
│       ├── UsdAllowance.kt    overseas + incidental allowance estimate
│       ├── TakaPayroll.kt     Taka payslip estimate
│       └── Grounded.kt        grounded periods and days per month
├── export/PdfExporter.kt      monthly PDF report
├── ui/
│   ├── navigation/            bottom navigation, Add button, entry overlay, back handling
│   ├── components/            reusable cards and input fields
│   ├── screens/               one folder per screen: entry, logbook, records, settings, calculator, splash
│   └── theme/
├── viewmodel/FlightLogViewModel.kt
└── workers/ExpiryWorker.kt    daily expiry notifications

app/src/test/java/com/example/mylogbook/domain/   unit tests for the rules (fictional data)
docs/RULES.md                                     every duty and pay rule the app applies
```

Where to change things:

- **A duty rule** → `domain/duty/RestRules.kt`
- **A pay rule** → `domain/pay/*`
- **Default rates** → `PAY_DEFAULTS` in `domain/pay/PaySettings.kt`
- **What a card shows** → `ui/components/`
- **Form behaviour** → `ui/screens/entry/FlightEntryScreen.kt`

After changing a rule, update `docs/RULES.md` and the tests.

## Data and storage conventions

- **One table:** `flight_logs` holds every record type. Non-flight records reuse some columns; the mapping is in the KDoc of `data/FlightLog.kt`.
- **Formats:** dates are `dd/MM/yyyy` strings; times are 4-digit UTC `HHmm` strings. Rest end times are shown in Bangladesh time (UTC+6).
- **Preferences:** SharedPreferences file `FlightLogPrefs`:
  - `profile_name`, `profile_id`, `profile_roster_name`, `profile_base` (default `DAC`)
  - `theme_pref`
  - `auto_backup_folder`
  - `pay_*` (see `PAY_DEFAULTS`)

## Rules that protect user data — please keep them

1. **Do not change `applicationId` (`com.example.mylogbook`).** Android would treat it as a different app, and existing installs couldn't be updated without wiping data.
2. **Do not rename the database file** (`biman_flight_log_db`), the table, or `workers/ExpiryWorker` (WorkManager stores the class name).
3. **Any change to `FlightLog` changes the schema.** Bump `version` in `AppDatabase` and add a `Migration` in `Migrations.kt`. Never use `fallbackToDestructiveMigration()`, which deletes the logbook.
4. **When adding a `FlightLog` field,** add it to `BackupJson` too. Read it with `optString(..., default)` so old backup files still restore.
5. **Release builds** must always be signed with the same keystore. Keep the keystore out of git (`*.jks` is ignored).

## Tests

```bat
gradlew.bat :app:testDebugUnitTest
```

The tests in `app/src/test` cover service-length tiers, incidental time split at month ends, the 70-hour overseas rule (above, below, long-stay waiver), rounding, the Taka payroll arithmetic, grounded days, short rest and post-flight off duty. All test data is fictional.

## Known gaps / ideas

- Duty overtime, refused day-off and absence deductions are not estimated on the Taka card.
- The flight-number field assumes a `BG` prefix (other airlines' numbers can still be typed starting with letters).
- App icon and branding are placeholders.
