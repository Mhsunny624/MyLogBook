package com.example.mylogbook.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Room schema migrations. Every database version bump needs one here, registered in
 * AppDatabase.getDatabase(). Never use fallbackToDestructiveMigration(): it deletes the logbook.
 */

/** v4 -> v5: adds the scheduled departure column without touching existing entries. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN schedDep TEXT NOT NULL DEFAULT ''")
    }
}
