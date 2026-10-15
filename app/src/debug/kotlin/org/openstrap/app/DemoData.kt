package org.openstrap.app

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
import kotlin.math.sin
import kotlin.random.Random

/**
 * Debug builds only: fills an empty database (an emulator, never a real strap's) with eight days of
 * plausible data (three weeks, so readiness has a settled baseline) so screens can be checked without wearing the strap for a week.
 */
object DemoData {
    suspend fun fill(dao: HealthDao) {
        val zone = ZoneId.systemDefault()
        val rnd = Random(7)
        for (back in 20 downTo 0) {
            val date = LocalDate.now().minusDays(back.toLong())
            val midnight = date.atStartOfDay(zone).toEpochSecond()
            // Night: 23:20 the evening before until about 07:05.
            val sleepStart = midnight - 40 * 60
            val sleepEnd = midnight + 7 * 3600 + 5 * 60 + rnd.nextInt(-20, 20) * 60
            val stages = buildList {
                var t = sleepStart
                val cycle = listOf(SleepStage.LIGHT to 25, SleepStage.DEEP to 35, SleepStage.LIGHT to 20, SleepStage.REM to 18, SleepStage.AWAKE to 3)
                while (t < sleepEnd) for ((stage, minutes) in cycle) {
                    if (t >= sleepEnd) break
                    val end = minOf(sleepEnd, t + (minutes + rnd.nextInt(-5, 6)) * 60L)
                    add(SleepStage(t, end, sleepStart, stage))
                    t = end
                }
            }
            fun mins(s: Int) = (stages.filter { it.stage == s }.sumOf { it.end - it.start } / 60).toInt()
            val day = midnight
            dao.insertSleepSessions(listOf(SleepSession(
                sleepStart, sleepEnd, day, nap = false, score = 72 + rnd.nextInt(0, 18), averageHeartRate = 56 + rnd.nextInt(0, 5),
                deepMinutes = mins(SleepStage.DEEP), lightMinutes = mins(SleepStage.LIGHT), remMinutes = mins(SleepStage.REM),
                awakeMinutes = mins(SleepStage.AWAKE), wakeCount = stages.count { it.stage == SleepStage.AWAKE },
            )))
            dao.insertSleepStages(stages)
            dao.insertRestingHr(listOf(RestingHeartRate(midnight, 56 + rnd.nextInt(0, 6))))
            dao.insertBreathRate((0 until 14).map { BreathRate(sleepStart + it * 1800L, 13 + rnd.nextInt(0, 3)) })
            dao.insertHrv((0 until 8).map { HrvSample(sleepStart + it * 3600L, (if (back % 2 == 0) 40 else 48) + rnd.nextInt(0, 12)) })

            val endOfData = if (back == 0) System.currentTimeMillis() / 1000 else midnight + 24 * 3600
            val minutes = (midnight until endOfData step 60L).toList()
            // Every other evening a 50-minute run at ~150 bpm; the night after it shows a little in HRV.
            val workout = back % 2 == 1
            val runStart = midnight + 18 * 3600
            dao.insertHeartRate(minutes.map {
                val hour = (it - midnight) / 3600.0
                val asleep = it < sleepEnd
                val base = if (asleep) 52.0 + 4 * sin((it - sleepStart) / 3600.0) else 66 + 10 * sin((hour - 9) / 24 * 2 * Math.PI)
                val run = workout && it in runStart until runStart + 50 * 60
                HeartRateSample(it, (if (run) 148.0 + rnd.nextInt(-6, 7) else base + rnd.nextInt(-3, 4)).toInt().coerceAtLeast(45))
            })
            // Stress: calm mornings and evenings, busier working hours.
            dao.insertStress(minutes.filter { it >= sleepEnd }.filterIndexed { i, _ -> i % 5 == 0 }.map {
                val hour = ((it - midnight) / 3600).toInt()
                StressSample(it, (if (hour in 9..16) 38 + rnd.nextInt(0, 35) else 15 + rnd.nextInt(0, 20)))
            })
            dao.insertSkinTemperature(((sleepStart until sleepEnd step 60L) + minutes.filterIndexed { i, _ -> i % 10 == 0 }).map {
                SkinTemperature(it, (if (it < sleepEnd) 34.4f else 33.2f) + 0.3f * rnd.nextFloat())
            })
            dao.insertSpo2(listOf(Spo2Sample(midnight + 3 * 3600, 96 + rnd.nextInt(0, 4))))
            dao.insertPai(listOf(PaiSample(endOfData - 60, rnd.nextFloat() * 20, 40f + back, 58, 192)))
        }
    }
}
