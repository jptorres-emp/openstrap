package org.openstrap.app.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.Workout
import java.io.StringWriter
import java.time.ZoneId
import org.openstrap.app.export.CsvExporter.Companion.writeCsv

class CsvImportTest {
    private fun table(file: String) = CSV_TABLES.first { it.file == file }

    @Suppress("UNCHECKED_CAST")
    private fun <T> parse(file: String, header: String, line: String): T {
        val index = CsvImporter.parseLine(header).withIndex().associate { (i, n) -> n to i }
        return table(file).parse(CsvRow(index, CsvImporter.parseLine(line))) as T
    }

    @Test fun `quoted cells keep their commas and quotes`() {
        assertEquals(listOf("a", "b,c", "say \"hi\"", ""), CsvImporter.parseLine("a,\"b,c\",\"say \"\"hi\"\"\","))
    }

    @Test fun `every table's export header parses its own rows back`() {
        for (t in CSV_TABLES) {
            val cells = CsvImporter.parseLine(t.header)
            assertEquals("${t.file} has duplicate columns", cells.size, cells.toSet().size)
        }
    }

    @Test fun `sleep from an older export without day_epoch gets the day you woke up`() {
        val s: SleepSession = parse(
            "sleep_session.csv",
            "start,end,start_epoch,end_epoch,nap,score,avg_hr,deep_min,light_min,rem_min,awake_min,wake_count",
            "2026-10-17T19:20:00Z,2026-10-18T03:13:00Z,1792264800,1792293180,false,80,58,90,200,60,20,4",
        )
        assertEquals(1792264800L, s.start)
        assertEquals(80, s.score)
        assertTrue(s.day > 0)
        assertTrue(s.day <= s.end && s.end - s.day < 86_400)
    }

    @Test fun `sleep stages read their ISO times and stage names`() {
        val st: SleepStage = parse("sleep_stage.csv", "session_start_epoch,start,end,stage", "1792264800,2026-10-17T19:20:00Z,2026-10-17T19:45:00Z,deep")
        assertEquals(1792264800L, st.start)
        assertEquals(1792266300L, st.end)
        assertEquals(SleepStage.DEEP, st.stage)
    }

    @Test fun `a workout round-trips through its row`() {
        val w = Workout(1792300000, 1792303000, 1, 3000, 550, 140, 165, 98, 3.4f, 1.6f, 120, 48, null)
        val t = table("workout.csv")
        @Suppress("UNCHECKED_CAST")
        val row = (t as CsvTable<Workout>).row(w)
        val out = StringWriter().also { it.writeCsv(t.header, listOf(row)) }.toString().lines()
        val back: Workout = parse("workout.csv", out[0], out[1])
        assertEquals(w, back)
        assertNull(back.steps)
    }

    @Test fun `every table's key column is in its header`() {
        for (t in CSV_TABLES) assertTrue("${t.file} lacks ${t.key.epoch}", t.key.epoch in CsvImporter.parseLine(t.header))
    }

    private val utc = ZoneId.of("UTC")
    private val day = 1792195200L // 2026-10-17T00:00Z

    @Test fun `a backup covers each day it has rows on, from its first row to just after its last`() {
        val times = listOf(day - 3600, day + 600, day + 86_400 + 7200)
        assertEquals(
            listOf(day - 3600 to day, day to day + 86_400, day + 86_400 to day + 86_400 + 7201),
            CsvImporter.coveredWindows(times, utc),
        )
    }

    @Test fun `a day the backup has nothing on is left alone`() {
        val windows = CsvImporter.coveredWindows(listOf(day + 60, day + 2 * 86_400 + 60), utc)
        assertEquals(2, windows.size)
        assertTrue(windows.none { (from, to) -> day + 86_400 + 43_200 in from until to })
    }

    @Test fun `days follow the phone's zone`() {
        // 23:30 UTC is already the next day in Berlin, so one row covers part of that Berlin day only.
        val windows = CsvImporter.coveredWindows(listOf(day - 1800), ZoneId.of("Europe/Berlin"))
        assertEquals(listOf(day - 1800 to day - 1799), windows)
    }

    @Test fun `an empty table covers nothing`() {
        assertTrue(CsvImporter.coveredWindows(emptyList(), utc).isEmpty())
    }
}
