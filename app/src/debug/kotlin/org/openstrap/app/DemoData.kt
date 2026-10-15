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
 * plausible data so screens can be checked without wearing the strap for a week.
 */
object DemoData {
    suspend fun fill(dao: HealthDao) {
        val zone = ZoneId.systemDefault()
        val rnd = Random(7)
        for (back in 7 downTo 0) {
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
            dao.insertHrv((0 until 8).map { HrvSample(sleepStart + it * 3600L, 38 + rnd.nextInt(0, 18)) })

            val endOfData = if (back == 0) System.currentTimeMillis() / 1000 else midnight + 24 * 3600
            val minutes = (midnight until endOfData step 60L).toList()
            dao.insertHeartRate(minutes.filterIndexed { i, _ -> i % 5 == 0 }.map {
                val hour = (it - midnight) / 3600.0
                HeartRateSample(it, (62 + 14 * sin((hour - 9) / 24 * 2 * Math.PI) + rnd.nextInt(-4, 5)).toInt().coerceAtLeast(50))
            })
            dao.insertActivity(minutes.map {
                val hour = ((it - midnight) / 3600).toInt()
                val active = hour in 8..20 && rnd.nextInt(10) < 3
                ActivityMinute(it, if (active) rnd.nextInt(20, 110) else 0, if (active) 60 else 10, 0, null)
            })
            dao.insertStress(minutes.filterIndexed { i, _ -> i % 15 == 0 }.map { StressSample(it, 25 + rnd.nextInt(0, 40)) })
            dao.insertSkinTemperature(minutes.filterIndexed { i, _ -> i % 10 == 0 }.map { SkinTemperature(it, 33.2f + rnd.nextFloat()) })
            dao.insertSpo2(listOf(Spo2Sample(midnight + 3 * 3600, 96 + rnd.nextInt(0, 4))))
            dao.insertPai(listOf(PaiSample(endOfData - 60, rnd.nextFloat() * 20, 40f + back, 58, 192)))
        }
    }
}
