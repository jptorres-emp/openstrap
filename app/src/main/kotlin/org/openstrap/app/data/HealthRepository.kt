package org.openstrap.app.data

import kotlinx.coroutines.flow.Flow
import org.openstrap.app.data.db.ActivityMinute
import org.openstrap.app.data.db.BreathRate
import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.HeartRateSample
import org.openstrap.app.data.db.HrvSample
import org.openstrap.app.data.db.PaiSample
import org.openstrap.app.data.db.RestingHeartRate
import org.openstrap.app.data.db.SkinTemperature
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.data.db.Spo2Sample
import org.openstrap.app.data.db.StressSample
import java.time.LocalDate
import java.time.ZoneId

/** A [from, to] window in epoch seconds covering one local calendar day. */
data class DayWindow(val from: Long, val to: Long) {
    companion object {
        fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayWindow {
            val from = date.atStartOfDay(zone).toEpochSecond()
            val to = date.plusDays(1).atStartOfDay(zone).toEpochSecond() - 1
            return DayWindow(from, to)
        }

        /** Sleep that *ended* on [date]: from 18:00 the previous evening until 18:00 on [date]. */
        fun nightEndingOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayWindow {
            val to = date.atTime(18, 0).atZone(zone).toEpochSecond()
            return DayWindow(to - 24 * 3600, to)
        }
    }
}

class HealthRepository(private val dao: HealthDao) {
    private val recoveryEngine = RecoveryEngine(dao)

    /** Readiness and charge for [date]; heavy, so call it off the main thread. */
    suspend fun recovery(date: LocalDate, profile: org.openstrap.app.data.settings.UserProfile): RecoveryResult =
        recoveryEngine.compute(date, profile)

    /** Emits whenever new heart-rate, stress or sleep data lands, so derived scores can refresh. */
    fun changes(): Flow<Unit> = kotlinx.coroutines.flow.combine(dao.latestHeartRateTime(), dao.latestStressTime(), dao.latestSleepEnd()) { _, _, _ -> }

    fun heartRate(w: DayWindow): Flow<List<HeartRateSample>> = dao.heartRate(w.from, w.to)
    fun activity(w: DayWindow): Flow<List<ActivityMinute>> = dao.activity(w.from, w.to)
    fun sleepSessions(w: DayWindow): Flow<List<SleepSession>> = dao.sleepSessions(w.from, w.to)
    fun sleepStages(sessionStart: Long): Flow<List<SleepStage>> = dao.sleepStages(sessionStart)
    fun spo2(w: DayWindow): Flow<List<Spo2Sample>> = dao.spo2(w.from, w.to)
    fun stress(w: DayWindow): Flow<List<StressSample>> = dao.stress(w.from, w.to)
    fun hrv(w: DayWindow): Flow<List<HrvSample>> = dao.hrv(w.from, w.to)
    fun restingHr(from: Long, to: Long): Flow<List<RestingHeartRate>> = dao.restingHr(from, to)
    fun skinTemperature(w: DayWindow): Flow<List<SkinTemperature>> = dao.skinTemperature(w.from, w.to)
    fun breathRate(w: DayWindow): Flow<List<BreathRate>> = dao.breathRate(w.from, w.to)
    fun latestPai(before: Long): Flow<PaiSample?> = dao.latestPai(before)
}
