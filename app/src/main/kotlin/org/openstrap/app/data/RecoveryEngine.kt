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
    /** Sleep Regularity Index over the last 7 day-pairs, or the midpoint fallback. */
    val regularity: SleepRegularity.Result? = null,
    /** The sleep-health count for the night that ended on [date]. */
    val sleepHealth: SleepRegularity.Health? = null,
    /** Fitness (CTL), fatigue (ATL) and form at the start of [date], from worn days' loads. */
    val loadTrend: Recovery.LoadTrend? = null,
    /** Strain 0-21 from [load], and where [load] sits among the last 90 days (0-100). */
    val strain: Float = 0f,
    val strainPercentile: Int? = null,
    /** True when max HR is the user's own tested value. */
    val maxHrTested: Boolean = false,
    /** Steps, MVPA and distance for [date]. */
    val activity: Activity.Day? = null,
    /** Energy expenditure for [date]; null without weight, height and age. */
    val energy: EnergyExpenditure.Day? = null,
    /** Non-exercise VO2max (Jurca 2005), recomputed weekly; or why it's withheld. */
    val vo2max: Vo2max.Estimate? = null,
    val vo2maxWithheld: Vo2max.Withheld? = null,
)

/** Loads what [Recovery] needs from the database and runs it. All work happens off the main thread by the caller. */
class RecoveryEngine(private val dao: HealthDao) {

    suspend fun compute(date: LocalDate, profile: UserProfile, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis() / 1000): RecoveryResult {
        val day = DayWindow.of(date, zone)
        val from = DayWindow.of(date.minusDays(Recovery.BASELINE_NIGHTS.toLong() + 1), zone).from
        val to = day.to

        // Load history (CTL, Strain's "vs your usual", max HR) reaches further back than the nightly baselines.
        val loadFrom = DayWindow.of(date.minusDays(LOAD_DAYS.toLong()), zone).from
        val hrAll = minuteHeartRate(loadFrom, to)
        val hr = hrAll.filter { it.first >= from }
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

        // Daytime resting HR: the strap's daily values when it has them, else the nightly lowest plus an offset.
        val restingHr = Recovery.daytimeRestingHr(
            strapResting.associate { it.day to it.bpm.toFloat() },
            nights.mapNotNull { n -> n.restingHr?.let { n.day to it } }.toMap(),
            day.from,
        ) ?: 60f
        val age = profile.age()
        fun dayOf(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        val maxHr = maxHeartRate(profile, hrAll, pai?.maxHeartRate, loadFrom, to, zone)
        val female = profile.sex == UserProfile.Sex.Female

        // Daily training load for the 28 days before the selected one, skipping days the strap was mostly off.
        val sleepRanges = sessions.map { it.start..it.end }
        val asleep = { t: Long -> sleepRanges.any { t in it } }
        val hrByDay = hrAll.groupBy { dayOf(it.first) }
        val loadDays = (LOAD_DAYS downTo 1).mapNotNull { back ->
            val d = date.minusDays(back.toLong())
            val w = DayWindow.of(d, zone)
            val series = hrByDay[d].orEmpty()
            if (series.size < MIN_WORN_MINUTES) null else w.from to Recovery.dailyLoad(series, restingHr, maxHr, female, asleep)
        }
        val loads = loadDays.map { it.second }

        // A likely cause for a body-signal alert: a hard training day before the morning.
        val hardDays = loadDays.indices.filter { k ->
            k >= 7 && Recovery.hardDay(loadDays[k].second, Recovery.ewmaLoads(loads.take(k)).second)
        }.map { loadDays[it].first }
        val causes = { morning: Long ->
            val before = morning - 86_400 + 3600
            listOfNotNull("a hard training day".takeIf { hardDays.any { d -> d in before - 7200..before + 7200 } })
        }
        val todayLoad = Recovery.dailyLoad(hr.filter { it.first in day.from..day.to }, restingHr, maxHr, female, asleep)

        val midnight = day.from
        val tonight = nights.lastOrNull { it.day == midnight }
        val sleepNeed = Recovery.sleepNeed(age, profile.sleepNeedMinutes)
        val readiness = Recovery.readiness(tonight, nights, loads, causes, sleepNeed)
        val trend = (6 downTo 0).map { back ->
            val d = date.minusDays(back.toLong())
            val m = DayWindow.of(d, zone).from
            d to (if (back == 0) readiness.score else Recovery.readiness(nights.lastOrNull { it.day == m }, nights, loadsBefore(loadDays, m), causes, sleepNeed).score)
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

        // Activity: MVPA from cadence, then heart rate, over the 7 days ending on the selected one.
        val weekFrom = DayWindow.of(date.minusDays(6), zone).from
        val workouts = dao.workoutsOnce(weekFrom, to)
        val minuteSteps = dao.activityOnce(weekFrom, to).filter { it.steps in 0..250 }.associate { it.timestamp / 60 * 60 to it.steps }
        val weekHr = hr.filter { it.first >= weekFrom }.toMap()
        val activityDays = (6 downTo 0).map { back ->
            val w = DayWindow.of(date.minusDays(back.toLong()), zone)
            // Minutes with any data; no row is missing, never zero.
            val times = (minuteSteps.keys.filter { it in w.from..w.to } + weekHr.keys.filter { it in w.from..w.to }).toSortedSet()
            times.map { t ->
                Activity.Minute(
                    t, minuteSteps[t] ?: 0, weekHr[t]?.let { Recovery.hrr(it, restingHr, maxHr) }, asleep(t),
                    workouts.any { t in it.start..it.end },
                )
            }
        }
        val weekMvpa = activityDays.map { Activity.mvpa(it) }
        val todayMinutes = activityDays.last()
        val activity = Activity.Day(
            steps = todayMinutes.sumOf { it.steps },
            mvpa = weekMvpa.last(),
            weekEquivalent = weekMvpa.sumOf { it.equivalent },
            distanceMeters = Activity.distanceMeters(todayMinutes, profile.heightCm),
            stepGoal = Activity.defaultStepGoal(age),
        )

        // VO2max: recomputed weekly, as of the latest Monday, from the nightly resting HR.
        val monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val (vo2, vo2Withheld) = Vo2max.estimate(
            profile, profile.age(monday), nights.mapNotNull { n -> n.validRestingHr?.let { n.day to it } }.toMap(),
            DayWindow.of(monday, zone).from,
        )

        // Energy: every minute of the day with data (a step record, heart rate, or sleep); no row is missing, never zero.
        val todaySleep = sessions.flatMap { s -> (maxOf(s.start, day.from) / 60 * 60 until minOf(s.end, day.to + 1) step 60).toList() }.toSet()
        val energyTimes = (minuteSteps.keys.filter { it in day.from..day.to } + weekHr.keys.filter { it in day.from..day.to } + todaySleep).toSortedSet()
        val energy = EnergyExpenditure.day(
            energyTimes.map { t ->
                val w = workouts.firstOrNull { t in it.start until it.end }
                EnergyExpenditure.Minute(
                    t, minuteSteps[t], weekHr[t]?.let { Recovery.hrr(it, restingHr, maxHr) }, t in todaySleep || asleep(t),
                    w?.calories?.let { kcal -> kcal / ((w.end - w.start) / 60f).coerceAtLeast(1f) },
                )
            },
            profile, age, vo2?.value?.toFloat(),
        )

        val loadTrend = if (loads.size >= 7) Recovery.fitnessFatigue(loads).last() else null
        val chronic = loadTrend?.ctl

        // Sleep regularity: 8 noon-to-noon grid days ending at noon today, so 7 day-pairs.
        val gridStart = date.minusDays(SleepRegularity.SRI_DAYS.toLong() + 1).atTime(SleepRegularity.DAY_START_HOUR, 0).atZone(zone).toEpochSecond()
        val gridEnd = date.atTime(SleepRegularity.DAY_START_HOUR, 0).atZone(zone).toEpochSecond()
        val gridStages = sessions.filter { it.end > gridStart && it.start < gridEnd }
            .flatMap { s -> dao.sleepStagesOnce(s.start).map { Triple(it.start, it.end, it.stage) } }
        val wornMinutes = hr.mapTo(HashSet()) { it.first / 60 * 60 }
        val grids = (0..SleepRegularity.SRI_DAYS).map { k ->
            val noon = date.minusDays(SleepRegularity.SRI_DAYS.toLong() + 1 - k).atTime(SleepRegularity.DAY_START_HOUR, 0).atZone(zone).toEpochSecond()
            SleepRegularity.grid(noon, gridStages.filter { it.second > noon && it.first < noon + 86_400 }) { it in wornMinutes }
        }
        // Midpoints as minutes after local noon, so a night never wraps round midnight.
        fun minutesAfterNoon(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalTime().toSecondOfDay().let { ((it / 60 - 720) % 1440 + 1440) % 1440 }
        val midpoints = mains.filterKeys { it > midnight - 7 * 86_400 + 3600 && it <= midnight }.values.map { minutesAfterNoon((it.start + it.end) / 2).toFloat() }
        val regularity = SleepRegularity.result(grids, midpoints)
        val sleepHealth = tonight?.let { n ->
            val main = mains[midnight]
            val stagesTonight = main?.let { dao.sleepStagesOnce(it.start).filter { st -> st.stage != org.openstrap.app.data.db.SleepStage.AWAKE } }.orEmpty()
            val period = stagesTonight.takeIf { it.isNotEmpty() }?.let { ((it.maxOf { st -> st.end } - it.minOf { st -> st.start }) / 60).toInt() }
            val mid = Instant.ofEpochSecond((n.start + n.end) / 2).atZone(zone).toLocalTime().toSecondOfDay() / 60
            SleepRegularity.health(n.asleepMinutes, n.wasoMinutes, period, regularity, age, mid)
        }
        return RecoveryResult(
            date, readiness, charge, trend, todayLoad, chronic, restingHr, maxHr, age != null, stressThreshold, tonight,
            Recovery.breathingDisturbances(tonight, nights), Recovery.acwr(loads), sleepNeed, regularity, sleepHealth,
            loadTrend, Recovery.strain(todayLoad), Recovery.loadPercentile(todayLoad, loads), profile.maxHeartRate != null && maxHr == profile.maxHeartRate.toFloat(),
            activity, energy, vo2, vo2Withheld,
        )
    }

    /**
     * Max HR as of [date] by the same rule [compute] uses, for screens that don't run the whole model (workout zones):
     * the tested value, else Tanaka or the strap's PAI value, raised by observed peaks over [LOAD_DAYS].
     */
    suspend fun maxHeartRate(profile: UserProfile, date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Float {
        val to = DayWindow.of(date, zone).to
        val loadFrom = DayWindow.of(date.minusDays(LOAD_DAYS.toLong()), zone).from
        // Same PAI window as compute: the latest record within the nightly baseline span.
        val paiFrom = DayWindow.of(date.minusDays(Recovery.BASELINE_NIGHTS.toLong() + 1), zone).from
        val strapMax = dao.paiOnce(paiFrom, to).lastOrNull()?.maxHeartRate
        // A tested value wins outright, so it doesn't need 90 days of heart rate read back.
        val tested = profile.maxHeartRate
        if (tested != null) Recovery.maxHeartRate(profile.age(), strapMax, tested = tested).let { if (it == tested.toFloat()) return it }
        return maxHeartRate(profile, minuteHeartRate(loadFrom, to), strapMax, loadFrom, to, zone)
    }

    /** Live heart rate is stored several times a minute; the model works in minutes. */
    private suspend fun minuteHeartRate(from: Long, to: Long): List<Pair<Long, Int>> =
        Summaries.bucketAverage(Summaries.heartRateSeries(dao.heartRateOnce(from, to), dao.activityOnce(from, to)), 60)

    private suspend fun maxHeartRate(profile: UserProfile, hrAll: List<Pair<Long, Int>>, strapMax: Int?, from: Long, to: Long, zone: ZoneId): Float {
        // Each day's highest real heart rate (filtered per-minute HR, and the strap's daily exercise maximum when the
        // minutes back it up); two separate days have to agree before it raises max HR, so one artefact can't.
        fun dayOf(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        val minuteMax = hrAll.groupBy { dayOf(it.first) }.mapValues { (_, m) -> Recovery.filteredMax(m) }
        val strapDaily = dao.dailyMaxHrOnce(from, to).map { dayOf(it.day) to it.bpm }
        val observedPeak = Recovery.observedPeak(Recovery.dailyMaxima(minuteMax, strapDaily))
        return Recovery.maxHeartRate(profile.age(), strapMax, observedPeak, profile.maxHeartRate)
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
        /**
         * The daily loads a morning's readiness saw: those of [loadDays] (day start to load, oldest first) before
         * [morning], so a past day in the trend gets the "Previous day's load" it had then.
         */
        fun loadsBefore(loadDays: List<Pair<Long, Float>>, morning: Long): List<Float> = loadDays.filter { it.first < morning }.map { it.second }

        /** Days of load history: CTL's 42-day constant and Strain's 90-day "vs your usual". */
        const val LOAD_DAYS = 90
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
