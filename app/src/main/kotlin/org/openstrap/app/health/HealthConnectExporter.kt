package org.openstrap.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Percentage
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.SleepStage
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pushes locally stored data into Android Health Connect. Each record carries a stable
 * clientRecordId, so re-exporting the same range updates rather than duplicates.
 */
class HealthConnectExporter(private val context: Context, private val dao: HealthDao) {

    val permissions = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    private val device = Device(type = Device.TYPE_FITNESS_BAND, manufacturer = "Amazfit", model = "Helio Strap")

    private fun meta(id: String) = Metadata.autoRecorded(device = device, clientRecordId = "openstrap:$id", clientRecordVersion = 1)

    private fun offset(epochSeconds: Long): ZoneOffset =
        ZoneId.systemDefault().rules.getOffset(Instant.ofEpochSecond(epochSeconds))

    /** Exports everything in [from, to] (epoch seconds). Returns the number of records written. */
    suspend fun export(from: Long, to: Long): Int {
        val records = mutableListOf<Record>()

        // Heart rate: one record per hour with all samples in it.
        val hr = dao.heartRateOnce(from, to) + dao.activityOnce(from, to)
            .mapNotNull { m -> m.heartRate?.takeIf { it in 30..230 }?.let { org.openstrap.app.data.db.HeartRateSample(m.timestamp, it) } }
        hr.distinctBy { it.timestamp }.sortedBy { it.timestamp }.groupBy { it.timestamp / 3600 }.forEach { (hour, samples) ->
            val start = hour * 3600
            val end = samples.last().timestamp + 1 // not the end of the hour, which can be in the future
            records += HeartRateRecord(
                startTime = Instant.ofEpochSecond(start), startZoneOffset = offset(start),
                endTime = Instant.ofEpochSecond(end), endZoneOffset = offset(end),
                samples = samples.map { HeartRateRecord.Sample(Instant.ofEpochSecond(it.timestamp), it.bpm.toLong()) },
                metadata = meta("hr:$start"),
            )
        }

        for (m in dao.activityOnce(from, to)) {
            if (m.steps <= 0) continue
            records += StepsRecord(
                startTime = Instant.ofEpochSecond(m.timestamp), startZoneOffset = offset(m.timestamp),
                endTime = Instant.ofEpochSecond(m.timestamp + 60), endZoneOffset = offset(m.timestamp),
                count = m.steps.toLong(),
                metadata = meta("steps:${m.timestamp}"),
            )
        }

        for (s in dao.sleepSessionsOnce(from, to)) {
            // Rows synced before stage ends were made exclusive can be zero length; Health Connect rejects those,
            // and stages must stay inside the session.
            val stages = dao.sleepStagesOnce(s.start).mapNotNull {
                val start = it.start.coerceAtLeast(s.start)
                val end = maxOf(it.end, it.start + 60).coerceAtMost(s.end)
                if (end <= start) return@mapNotNull null
                SleepSessionRecord.Stage(
                    Instant.ofEpochSecond(start), Instant.ofEpochSecond(end),
                    when (it.stage) {
                        SleepStage.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
                        SleepStage.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
                        SleepStage.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
                        SleepStage.REM -> SleepSessionRecord.STAGE_TYPE_REM
                        else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
                    },
                )
            }
            records += SleepSessionRecord(
                startTime = Instant.ofEpochSecond(s.start), startZoneOffset = offset(s.start),
                endTime = Instant.ofEpochSecond(s.end), endZoneOffset = offset(s.end),
                stages = stages,
                metadata = meta("sleep:${s.start}"),
            )
        }

        for (o in dao.spo2Once(from, to)) {
            if (o.percent !in 70..100) continue
            records += OxygenSaturationRecord(
                time = Instant.ofEpochSecond(o.timestamp), zoneOffset = offset(o.timestamp),
                percentage = Percentage(o.percent.toDouble()),
                metadata = meta("spo2:${o.timestamp}"),
            )
        }

        for (h in dao.hrvOnce(from, to)) {
            if (h.milliseconds !in 1..200) continue // Health Connect's accepted RMSSD range
            records += HeartRateVariabilityRmssdRecord(
                time = Instant.ofEpochSecond(h.timestamp), zoneOffset = offset(h.timestamp),
                heartRateVariabilityMillis = h.milliseconds.toDouble(),
                metadata = meta("hrv:${h.timestamp}"),
            )
        }

        for (r in dao.restingHrOnce(from, to)) {
            records += RestingHeartRateRecord(
                time = Instant.ofEpochSecond(r.day), zoneOffset = offset(r.day),
                beatsPerMinute = r.bpm.toLong(),
                metadata = meta("rhr:${r.day}"),
            )
        }

        // Sleep that OpenStrap exported earlier may since have changed shape (a nap folded into the night),
        // so its old records in this range are replaced rather than updated. This only touches OpenStrap's records.
        client.deleteRecords(SleepSessionRecord::class, TimeRangeFilter.between(Instant.ofEpochSecond(from - 86_400), Instant.ofEpochSecond(to)))
        // Health Connect caps the batch size per call.
        records.chunked(500).forEach { client.insertRecords(it) }
        return records.size
    }
}
