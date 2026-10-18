package org.openstrap.app.export

import androidx.room.withTransaction
import org.openstrap.app.data.db.OpenStrapDatabase
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream

/**
 * Restores a zip made by [CsvExporter]. For each table, the days the backup holds are replaced by the backup's rows:
 * what the phone had in those days goes, so a restore also undoes rows that were stored at the wrong time. Days the
 * backup doesn't have, and anything after its last row, stay as they are. Runs as one transaction, so a zip that
 * fails part way changes nothing. Files this version doesn't know are skipped.
 */
class CsvImporter(private val db: OpenStrapDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** What one restore did: rows stored per file, local days replaced, and rows that couldn't be read. */
    data class Result(val rows: Map<String, Int>, val days: Int, val skipped: Int) {
        val total get() = rows.values.sum()
    }

    suspend fun importZip(input: InputStream): Result = db.withTransaction {
        val tables = CSV_TABLES.associateBy { it.file }
        val counts = linkedMapOf<String, Int>()
        val days = mutableSetOf<Long>()
        val cleared = mutableMapOf<String, List<Pair<Long, Long>>>()
        var skipped = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val table = tables[entry.name.substringAfterLast('/')] ?: continue
                // Only this entry's bytes, without closing the zip (a Reader's readLines would close it).
                val lines = zip.readBytes().toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) continue
                val (stored, bad, windows) = table.restore(lines)
                counts[table.file] = stored
                cleared[table.key.table] = windows
                days += windows.map { localDay(it.first, zone) }
                skipped += bad
            }
        }
        // Rows that hung off a parent the restore replaced go with it. Done last, as the zip may list children first.
        val sql = db.openHelper.writableDatabase
        for ((parent, children) in CHILDREN) for ((from, to) in cleared[parent].orEmpty()) for (child in children) {
            sql.execSQL(
                "DELETE FROM ${child.table} WHERE ${child.column} >= ? AND ${child.column} < ? " +
                    "AND ${child.column} NOT IN (SELECT ${child.parentColumn} FROM $parent)",
                arrayOf(from, to),
            )
        }
        // Throwing rolls back, so a zip that isn't ours leaves the database untouched.
        if (counts.isEmpty()) throw IllegalArgumentException("This isn't an OpenStrap export.")
        Result(counts, days.size, skipped)
    }

    /** Replaces this table's rows in the days [lines] cover; returns rows stored, rows unreadable, and those days. */
    private suspend fun <T> CsvTable<T>.restore(lines: List<String>): Triple<Int, Int, List<Pair<Long, Long>>> {
        val index = CsvImporter.parseLine(lines.first()).withIndex().associate { (i, name) -> name to i }
        var bad = 0
        val rows = mutableListOf<T>()
        val times = mutableListOf<Long>()
        for (line in lines.drop(1)) {
            runCatching {
                val cells = CsvRow(index, parseLine(line))
                val row = parse(cells)
                times += cells.long(key.epoch)
                rows += row
            }.onFailure { bad++ }
        }
        val windows = coveredWindows(times, zone)
        val sql = db.openHelper.writableDatabase
        for ((from, to) in windows) {
            sql.execSQL("DELETE FROM ${key.table} WHERE ${key.column} >= ? AND ${key.column} < ?", arrayOf(from, to))
        }
        rows.chunked(2_000).forEach { write(db.healthDao(), it) }
        return Triple(rows.size, bad, windows)
    }

    companion object {
        private class Child(val table: String, val column: String, val parentColumn: String)

        /** Tables keyed by a parent row's start, which mean nothing once that parent is gone. */
        private val CHILDREN = mapOf(
            "sleep_session" to listOf(Child("sleep_stage", "sessionStart", "start")),
            "workout" to listOf(Child("workout_hr", "workoutStart", "start"), Child("recovery_hr", "workoutStart", "start")),
        )

        private fun localDay(epoch: Long, zone: ZoneId): Long =
            Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

        /**
         * The stretches a backup speaks for, as [from, to) in epoch seconds: each local day it has a row on, starting
         * no earlier than its first row and ending just after its last. Clipping both ends keeps what the phone has
         * from before the backup started and from after it was made.
         */
        fun coveredWindows(times: List<Long>, zone: ZoneId): List<Pair<Long, Long>> {
            if (times.isEmpty()) return emptyList()
            val first = times.min()
            val last = times.max()
            return times.map { localDay(it, zone) }.distinct().sorted().map { day ->
                val next = Instant.ofEpochSecond(day).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toEpochSecond()
                maxOf(day, first) to minOf(next, last + 1)
            }
        }

        /** One CSV line into cells: commas separate, double quotes wrap a cell and "" inside is a quote. */
        fun parseLine(line: String): List<String> {
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                    else -> cell.append(c)
                }
                i++
            }
            cells += cell.toString()
            return cells
        }
    }
}
