package org.openstrap.app.data.db

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.openstrap.app.data.DayKey
import java.io.File
import java.time.ZoneId

/**
 * The fork's own database version 20 (jptorres-emp/openstrap up to 1.1.5): the original's version 19 plus a `weight`
 * table. The original's own version 20 added `daily_energy` and two calorie columns to `app_workout` instead, and its
 * migrations from 20 on expect them. [repair] turns a fork 20 into the original's 20 with the weight table kept, before
 * Room opens the file, so the upgrade then runs the original's migrations as they are and nothing is lost. The weight's
 * days, still local midnights there, become [DayKey]s, as the original's version 28 did for its own tables
 * ([DayKeyMigration]); version 33 adds the same table for everyone else ([WeightEntry]).
 */
object ForkDatabase {
    /** The version number the fork and the original both used, for different schemas. */
    const val FORK_VERSION = 20

    /** The original's `daily_energy` at version 20, as its schema file 20.json has it. */
    const val CREATE_DAILY_ENERGY = "CREATE TABLE IF NOT EXISTS `daily_energy` (`day` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, `totalKcal` REAL NOT NULL, `activeKcal` REAL NOT NULL, `bmrKcal` REAL NOT NULL, " +
        "`minutesWithData` INTEGER NOT NULL, `partial` INTEGER NOT NULL, `workoutKcal` REAL NOT NULL, " +
        "`walkingKcal` REAL NOT NULL, `hourly` TEXT NOT NULL, PRIMARY KEY(`day`))"

    /**
     * Repairs [file] when it's a fork 20 (version 20, a `weight` table and no `daily_energy`); true when it did. One
     * transaction: a failure leaves the file as it was, and Room's own upgrade then stops on it without losing data.
     */
    fun repair(file: File, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!file.isFile) return false
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
        try {
            if (db.version != FORK_VERSION || !hasTable(db, "weight") || hasTable(db, "daily_energy")) return false
            db.beginTransaction()
            try {
                db.execSQL(CREATE_DAILY_ENERGY)
                for (column in listOf("activeKcal", "totalKcal")) {
                    if (!hasColumn(db, "app_workout", column)) db.execSQL("ALTER TABLE `app_workout` ADD COLUMN `$column` REAL")
                }
                val moved = weightDays(db, zone)
                db.setTransactionSuccessful()
                Log.i(TAG, "fork database 20 repaired: daily_energy added, app_workout's calories added, $moved weight days moved")
            } finally {
                db.endTransaction()
            }
            return true
        } finally {
            db.close()
        }
    }

    /** Each weight row under its date's [DayKey]; two rows that become one date keep the newer. Returns the rows moved. */
    private fun weightDays(db: SQLiteDatabase, zone: ZoneId): Int {
        data class Row(val day: Long, val kg: Double, val updatedAt: Long)
        val rows = db.rawQuery("SELECT `day`, `kg`, `updatedAt` FROM `weight`", null).use { c ->
            buildList { while (c.moveToNext()) add(Row(c.getLong(0), c.getDouble(1), c.getLong(2))) }
        }
        val kept = rows.groupBy { DayKey.of(DayKey.fromLegacy(it.day, zone)) }.mapValues { (_, same) -> same.maxBy { it.updatedAt } }
        val unchanged = kept.count { (key, r) -> key == r.day }
        if (unchanged == rows.size) return 0
        db.execSQL("DELETE FROM `weight`")
        for ((key, r) in kept) {
            db.execSQL("INSERT INTO `weight` (`day`, `kg`, `updatedAt`) VALUES (?, ?, ?)", arrayOf<Any>(key, r.kg, r.updatedAt))
        }
        return rows.size - unchanged
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            generateSequence { if (c.moveToNext()) c.getString(name) else null }.any { it == column }
        }

    private const val TAG = "ForkDatabase"
}
