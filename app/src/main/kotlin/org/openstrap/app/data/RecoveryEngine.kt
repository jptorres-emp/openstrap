package org.openstrap.app.data

import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.settings.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the Charge screens show for one day. */
data class RecoveryResult(
    val date: LocalDate,
    val readiness: Recovery.Readiness,
    val charge: Recovery.ChargeDay,
    /** Readiness for the 7 days ending on the selected one (null where there was no sleep). */
    val readinessTrend: List<Pair<LocalDate, Int?>>,
    /** Training load (TRIMP) so far on the selected day, and the usual (chronic) daily load. */
    val load: Float,
    val chronicLoad: Float?,
    val restingHr: Float,
    val maxHr: Float,
    /** True when max HR came from the user's age rather than a fallback. */
    val maxHrFromAge: Boolean,
    val stressThreshold: Recovery.StressThreshold,
    /** The main sleep that ended on [date], as the model saw it; null without one. */
    val tonight: Recovery.Night? = null,
    /** For the Breathing card: tonight's desaturation events per hour against the user's own nights. */
    val breathingDisturbances: Recovery.BreathingDisturbances? = null,
    /** Acute:chronic load ratio for the Load screen (contested; not part of Readiness). */
    val acwr: Float? = null,
    /** Sleep need in minutes (by age, or the user's own). */
    val sleepNeed: Int = Recovery.SLEEP_NEED_MINUTES,
)

/** Loads what [Recovery] needs from the database and runs it. All work happens off the main thread by the caller. */
class RecoveryEngine(private val dao: HealthDao) {

    suspend fun compute(date: LocalDate, profile: UserProfile, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis() / 1000): RecoveryResult {
        val day = DayWindow.of(date, zone)
        val from = DayWindow.of(date.minusDays(Recovery.BASELINE_NIGHTS.toLong() + 1), zone).from
        val to = day.to

        // Live heart rate is stored several times a minute; the model works in minutes.
        val hr = Summaries.bucketAverage(Summaries.heartRateSeries(dao.heartRateOnce(from, to), dao.activityOnce(from, to)), 60)
        val sessions = dao.sleepSessionsOnce(from, to)
        val hrv = dao.hrvOnce(from, to)
        val skin = dao.skinTemperatureOnce(from, to)
        val breath = dao.breathRateOnce(from, to)
        val strapResting = dao.restingHrOnce(from, to)
        val pai = dao.paiOnce(from, to).lastOrNull()
        val sleepSpo2 = dao.sleepSpo2Once(from, to)
        // A night's summary uses the sleep result's day rule: the date of belongTime + 2 h.
        val osaByMorning = dao.osaResultsOnce(from - 86_400, to).associateBy {
            Instant.ofEpochSecond(it.belongTime + 2 * 3600).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()
        }

        // One main sleep per morning: the longest non-nap session ending that day.
        val mains = sessions.filter { !it.nap }
            .groupBy { morningOf(it, zone) }
            .mapValues { (_, v) -> v.maxBy { it.end - it.start } }
        val strapNights = dao.strapReadinessOnce(from, to).associateBy { it.day }
        // Naps count toward a day's total sleep for the debt; a nap belongs to the day it started.
        val napsByDay = sessions.filter { it.nap }.groupBy { Instant.ofEpochSecond(it.start).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond() }
            .mapValues { (_, v) -> v.sumOf { asleepMinutes(it) } }
        val nights = mains.map { (morning, s) ->
            val stages = dao.sleepStagesOnce(s.start).map { Triple(it.start, it.end, it.stage) }
            night(morning, s, hr, hrv, skin, breath, sleepSpo2, osaByMorning[morning]).copy(
                strapHrv = strapNights[morning]?.sleepHrv?.toFloat(),
                strapRestingHr = strapNights[morning]?.sleepRestingHr?.toFloat(),
                wasoMinutes = Recovery.wasoMinutes(stages),
                napMinutes = napsByDay[morning] ?: 0,
            )
        }.sortedBy { it.day }

        // Daytime resting HR: the strap's daily values when it has them, else the nightly lowest plus a little.
        val restingHr = strapResting.takeLast(14).map { it.bpm.toFloat() }.median()
            ?: nights.takeLast(14).mapNotNull { it.restingHr }.median()?.plus(3f)
            ?: 60f
        val age = profile.age()
        // The second-highest value, so one artefact can't set it: per-minute HR, and the strap's daily exercise maximum.
        val observedPeak = listOfNotNull(
            hr.map { it.second }.sortedDescending().getOrNull(1),
            dao.dailyMaxHrOnce(from, to).map { it.bpm }.sortedDescending().getOrNull(1),
        ).maxOrNull()
        val maxHr = Recovery.maxHeartRate(age, pai?.maxHeartRate, observedPeak)
        val female = profile.sex == UserProfile.Sex.Female

        // Daily training load for the 28 days before the selected one, skipping days the strap was mostly off.
        val sleepRanges = sessions.map { it.start..it.end }
        val asleep = { t: Long -> sleepRanges.any { t in it } }
        val loadDays = (28 downTo 1).mapNotNull { back ->
            val w = DayWindow.of(date.minusDays(back.toLong()), zone)
            val series = hr.filter { it.first in w.from..w.to }
            if (series.size < MIN_WORN_MINUTES) null else w.from to Recovery.dailyLoad(series, restingHr, maxHr, female, asleep)
        }
        val loads = loadDays.map { it.second }

        // Likely causes for a body-signal alert: the user's tags that morning or the day before, and a hard previous day.
        val tags = dao.dayTagsOnce(from, to).groupBy({ it.day }, { it.tag })
        val hardDays = loadDays.indices.filter { k ->
            k >= 7 && Recovery.hardDay(loadDays[k].second, Recovery.ewmaLoads(loads.take(k)).second)
        }.map { loadDays[it].first }
        val causes = { morning: Long ->
            val before = morning - 86_400 + 3600
            val noted = tags.filterKeys { it == morning || it in before - 7200..before + 7200 }.values.flatten()
                .distinct().map { DayTag.of(it)?.label?.lowercase() ?: it }
            noted + listOfNotNull("a hard training day".takeIf { hardDays.any { d -> d in before - 7200..before + 7200 } })
        }
        val todayLoad = Recovery.dailyLoad(hr.filter { it.first in day.from..day.to }, restingHr, maxHr, female, asleep)

        val midnight = day.from
        val tonight = nights.lastOrNull { it.day == midnight }
        val sleepNeed = Recovery.sleepNeed(age, profile.sleepNeedMinutes)
        val readiness = Recovery.readiness(tonight, nights, loads, causes, sleepNeed)
        val trend = (6 downTo 0).map { back ->
            val d = date.minusDays(back.toLong())
            val m = DayWindow.of(d, zone).from
            d to (if (back == 0) readiness.score else Recovery.readiness(nights.lastOrNull { it.day == m }, nights, sleepNeed = sleepNeed).score)
        }

        // Charge: start two days early so the starting guess has washed out through two nights of sleep.
        val simFrom = midnight - 48 * 3600
        val simTo = minOf(day.to + 1, now / 60 * 60 + 60)
        val stages = sessions.filter { it.end > simFrom && it.start < simTo }
            .flatMap { s -> dao.sleepStagesOnce(s.start).map { Triple(it.start, it.end, it.stage) } }
        val stress = dao.stressOnce(simFrom, simTo).map { it.timestamp to it.value }
        val steps = dao.activityOnce(simFrom, simTo).associate { it.timestamp / 60 * 60 to it.steps }

        // Personal stress threshold from calm readings: awake, still and at resting heart rate.
        val calmFrom = DayWindow.of(date.minusDays(Recovery.STRESS_BASELINE_DAYS.toLong() - 1), zone).from
        val calmSteps = dao.activityOnce(calmFrom, to).associate { it.timestamp / 60 * 60 to it.steps }
        val minuteHr = hr.toMap()
        val calm = dao.stressOnce(calmFrom, to).map { it.timestamp to it.value }.filter { (t, _) ->
            val minute = t / 60 * 60
            val bpm = minuteHr[minute]
            !asleep(t) && (calmSteps[minute] ?: 0) < CALM_STEPS && bpm != null &&
                Recovery.hrr(bpm, restingHr, maxHr) < Recovery.ACTIVITY_HRR
        }
        val stressThreshold = Recovery.stressThreshold(calm) { Instant.ofEpochSecond(it).atZone(zone).toLocalDate().toEpochDay() }
        val minutes = Recovery.minutes(simFrom, simTo, hr.filter { it.first in simFrom until simTo }, steps, stress, stages)
        val hrvScore = readiness.contributors.firstOrNull { it.factor == Recovery.Factor.HrvBalance }?.score
        val params = Recovery.ChargeParams(restingHr, maxHr, tonight?.restingHr, hrvScore, female, stressThreshold.value)
        val charge = Recovery.charge(minutes, params, day.from..day.to, start = 50f, mainSleepEnd = tonight?.end)

        val chronic = if (loads.size >= 7) Recovery.ewmaLoads(loads).second else null
        return RecoveryResult(
            date, readiness, charge, trend, todayLoad, chronic, restingHr, maxHr, age != null, stressThreshold, tonight,
            Recovery.breathingDisturbances(tonight, nights), Recovery.acwr(loads), sleepNeed,
        )
    }

    private fun night(
        morning: Long,
        s: SleepSession,
        hr: List<Pair<Long, Int>>,
        hrv: List<org.openstrap.app.data.db.HrvSample>,
        skin: List<org.openstrap.app.data.db.SkinTemperature>,
        breath: List<org.openstrap.app.data.db.BreathRate>,
        spo2: List<org.openstrap.app.data.db.SleepSpo2Sample>,
        osa: org.openstrap.app.data.db.OsaResultRow?,
    ): Recovery.Night {
        val inSleep = { t: Long -> t in s.start..s.end }
        val sleepHr = hr.filter { inSleep(it.first) && it.second in Recovery.HR_MIN_BPM..Recovery.HR_MAX_BPM }
        val sleepBpm = sleepHr.map { it.second.toFloat() }
        val rmssd = hrv.filter { inSleep(it.timestamp) && it.milliseconds in Recovery.HRV_MIN_MS..Recovery.HRV_MAX_MS }.map { it.milliseconds.toFloat() }
        val asleep = asleepMinutes(s)
        val skinValues = skin.filter { inSleep(it.timestamp) }.map { it.celsius }
        val spo2Values = spo2.filter { inSleep(it.timestamp) }.map { it.percent.toFloat() }
        return Recovery.Night(
            day = morning,
            start = s.start,
            end = s.end,
            asleepMinutes = asleep,
            awakeMinutes = s.awakeMinutes,
            deepMinutes = s.deepMinutes,
            remMinutes = s.remMinutes,
            strapScore = s.score,
            hrv = rmssd.mean(),
            restingHr = Recovery.lowestAverage(sleepHr, 30, Recovery.RHR_WINDOW_MIN_MINUTES)?.second,
            rhrMeanSleep = sleepBpm.mean(),
            rhrP10 = Recovery.percentile(sleepBpm, 0.10f),
            hrvSamples = rmssd.size,
            hrMinutes = sleepHr.size,
            nadirTime = Recovery.lowestAverage(sleepHr, 10)?.first,
            // A handful of readings doesn't describe a night; the strap logs one a minute when worn.
            skinTemperature = skinValues.takeIf { it.size >= 30 }?.mean(),
            breathRate = breath.filter { inSleep(it.timestamp) }.map { it.perMinute.toFloat() }.mean(),
            spo2 = spo2Values.takeIf { it.size >= MIN_SPO2_READINGS }?.mean(),
            // Events per hour need a real night to divide by.
            odi = osa?.takeIf { asleep >= MIN_ODI_SLEEP_MINUTES }?.let { it.eventCount * 60f / asleep },
        )
    }

    private fun asleepMinutes(s: SleepSession): Int = listOfNotNull(s.deepMinutes, s.lightMinutes, s.remMinutes).takeIf { it.isNotEmpty() }?.sum()
        ?: (((s.end - s.start) / 60).toInt() - (s.awakeMinutes ?: 0))

    private fun morningOf(s: SleepSession, zone: ZoneId): Long =
        if (s.day > 0) s.day else Instant.ofEpochSecond(s.end).atZone(zone).toLocalDate().atStartOfDay(zone).toEpochSecond()

    companion object {
        /** A day needs this many minutes of heart rate before its training load counts. */
        const val MIN_WORN_MINUTES = 8 * 60
        /** A minute with fewer steps than this counts as still for the calm stress level. */
        const val CALM_STEPS = 10
        /** Sleep SpO2 readings needed before a night's mean counts. */
        const val MIN_SPO2_READINGS = 5
        const val MIN_ODI_SLEEP_MINUTES = 120
    }
}

private fun List<Float>.mean(): Float? = if (isEmpty()) null else average().toFloat()
private fun List<Float>.median(): Float? = if (isEmpty()) null else sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
