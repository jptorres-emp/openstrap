package org.openstrap.app.export

import org.openstrap.app.data.db.HealthDao
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a zip made by [CsvExporter] back into the database. Every row is keyed by its time, so importing a backup
 * over data that's already there fills gaps without duplicating anything. Files the zip doesn't have are skipped,
 * and so are files this version doesn't know.
 */
class CsvImporter(private val dao: HealthDao) {

    /** What one import did: rows stored per file, and rows that couldn't be read. */
    data class Result(val rows: Map<String, Int>, val skipped: Int) {
        val total get() = rows.values.sum()
    }

    suspend fun importZip(input: InputStream): Result {
        val tables = CSV_TABLES.associateBy { it.file }
        val counts = linkedMapOf<String, Int>()
        var skipped = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val table = tables[entry.name.substringAfterLast('/')] ?: continue
                // Only this entry's bytes, without closing the zip (a Reader's readLines would close it).
                val lines = zip.readBytes().toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) continue
                val (stored, bad) = table.import(lines)
                counts[table.file] = stored
                skipped += bad
            }
        }
        if (counts.isEmpty()) throw IllegalArgumentException("This isn't an OpenStrap export.")
        return Result(counts, skipped)
    }

    private suspend fun <T> CsvTable<T>.import(lines: List<String>): Pair<Int, Int> {
        val index = parseLine(lines.first()).withIndex().associate { (i, name) -> name to i }
        var bad = 0
        val rows = lines.drop(1).mapNotNull { line ->
            runCatching { parse(CsvRow(index, parseLine(line))) }.getOrElse { bad++; null }
        }
        rows.chunked(2_000).forEach { write(dao, it) }
        return rows.size to bad
    }

    companion object {
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
