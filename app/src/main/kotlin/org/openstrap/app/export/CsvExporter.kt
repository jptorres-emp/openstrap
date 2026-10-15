package org.openstrap.app.export

import org.openstrap.app.data.db.HealthDao
import java.io.OutputStream
import java.io.Writer
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes every table as a CSV file inside one zip. Timestamps are ISO-8601 UTC plus raw epoch seconds. */
class CsvExporter(private val dao: HealthDao) {

    suspend fun exportZip(out: OutputStream, from: Long = 0, to: Long = Long.MAX_VALUE / 2) {
        ZipOutputStream(out).use { zip ->
            val w = zip.writer()
            fun entry(name: String, header: String, rows: List<List<Any?>>) {
                zip.putNextEntry(ZipEntry(name))
                w.writeCsv(header, rows)
                w.flush()
                zip.closeEntry()
            }

            entry("heart_rate.csv", "time,epoch,bpm,source",
                dao.heartRateOnce(from, to).map { listOf(iso(it.timestamp), it.timestamp, it.bpm, it.source) })
            entry("activity_minute.csv", "time,epoch,steps,intensity,kind,heart_rate",
                dao.activityOnce(from, to).map { listOf(iso(it.timestamp), it.timestamp, it.steps, it.intensity, it.kind, it.heartRate) })
            val sessions = dao.sleepSessionsOnce(from, to)
            entry("sleep_session.csv", "start,end,start_epoch,end_epoch",
                sessions.map { listOf(iso(it.start), iso(it.end), it.start, it.end) })
            entry("sleep_stage.csv", "session_start_epoch,start,end,stage",
                sessions.flatMap { s -> dao.sleepStagesOnce(s.start) }
                    .map { listOf(it.sessionStart, iso(it.start), iso(it.end), stageName(it.stage)) })
            entry("spo2.csv", "time,epoch,percent",
                dao.spo2Once(from, to).map { listOf(iso(it.timestamp), it.timestamp, it.percent) })
            entry("stress.csv", "time,epoch,value",
                dao.stressOnce(from, to).map { listOf(iso(it.timestamp), it.timestamp, it.value) })
            entry("hrv.csv", "time,epoch,milliseconds",
                dao.hrvOnce(from, to).map { listOf(iso(it.timestamp), it.timestamp, it.milliseconds) })
            entry("resting_hr.csv", "day,epoch,bpm",
                dao.restingHrOnce(from, to).map { listOf(iso(it.day), it.day, it.bpm) })
        }
    }

    companion object {
        fun iso(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).toString()

        fun stageName(stage: Int) = when (stage) {
            0 -> "awake"; 1 -> "light"; 2 -> "deep"; 3 -> "rem"; else -> "unknown"
        }

        internal fun Writer.writeCsv(header: String, rows: List<List<Any?>>) {
            append(header).append('\n')
            for (row in rows) {
                row.joinTo(this, ",") { v -> v?.toString()?.let(::escape) ?: "" }
                append('\n')
            }
        }

        private fun escape(s: String): String =
            if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
