package org.openstrap.app.data

import org.openstrap.app.data.db.HealthDao
import org.openstrap.app.data.db.Workout
import org.openstrap.app.data.db.WorkoutHeartRate
import org.openstrap.app.data.settings.UserProfile
import java.time.Instant
import java.time.ZoneId

/**
 * The fork's fix (jptorres-emp/openstrap) for a workout started in Zepp's app and mirrored on the strap (summary
 * subtype 6, codemap 05 §8): the strap sums it up over its first minutes only, while it records the heart rate to the
 * end (seen on a 53-minute session summed up as 3 min 19 s, load 24). Such a workout then ends where its track does,
 * less the recovery tail, and takes its heart rate figures and its load from the track ([wholeTrack]). The sync stores
 * it so as each one comes in, and once for those stored before ([REPAIR_KEY]).
 */
object ForkMirrored {
    /** Summary `Type.subtype` for a workout started in a phone app (Zepp's) that the strap mirrored. */
    const val SUBTYPE_MIRRORED = 6

    /** How far its heart rate must run past its summary before the summary is set aside. */
    const val EXTRA_S = 10 * 60L

    /** The sync's key for the one repair of the mirrored workouts stored before this fix. */
    const val REPAIR_KEY = "repair_mirrored_v1"

    /** Heart rate samples a track needs before it stands for the workout. */
    private const val MIN_SAMPLES = 60
    private const val LOWEST_BPM = 30
    private const val HIGHEST_BPM = 230

    /**
     * [w] taken from its whole heart rate track [hr] when it's a mirrored workout whose summary stops short: its end where
     * the track's does less [Workouts.RECOVERY_TAIL_S], its heart rate figures from the track, its load the EPOC peak on
     * the strap's scale ([WorkoutEffect.epoc]) and its aerobic training effect at the activity class Zepp's app uses
     * without history. Null when the summary covers the track, or for any other workout.
     */
    fun wholeTrack(w: Workout, hr: List<WorkoutHeartRate>, restingHr: Float, maxHr: Float): Workout? {
        val last = hr.maxOfOrNull { it.timestamp }
        val end = last?.let { it - Workouts.RECOVERY_TAIL_S }
        val inside = if (end == null) emptyList() else hr.filter { it.timestamp in w.start..end && it.bpm in LOWEST_BPM..HIGHEST_BPM }
        val epochs = if (end == null) emptyList() else WorkoutEffect.epochs(inside.map { it.timestamp to it.bpm }, w.start, end)
        val stopsShort = w.subtype == SUBTYPE_MIRRORED && end != null && end - w.end >= EXTRA_S
        val usable = stopsShort && inside.size >= MIN_SAMPLES && epochs.isNotEmpty()
        if (!usable || end == null) return null
        val epoc = WorkoutEffect.epoc(epochs, restingHr, maxHr)
        val bpm = inside.map { it.bpm }
        return w.copy(
            end = end, exerciseSeconds = (end - w.start).toInt(),
            averageHeartRate = Math.round(bpm.average()).toInt(), maxHeartRate = bpm.max(), minHeartRate = bpm.min(),
            trainingLoad = Math.round(epoc),
            aerobicTrainingEffect = WorkoutEffect.aerobicTe(epoc, epochs.size, WorkoutEffect.activityClass(0f)),
            anaerobicTrainingEffect = null, calories = null,
        )
    }

    /**
     * Each of [workouts] that's a mirrored one cut short, taken from its whole track and stored again. One that fails
     * is logged and left as it was; a cancelled sync still stops here ([catching]).
     */
    suspend fun repair(dao: HealthDao, workouts: List<Workout>, profile: UserProfile, zone: ZoneId, log: (String) -> Unit) {
        for (w in workouts.filter { it.subtype == SUBTYPE_MIRRORED }) {
            catching {
                val date = Instant.ofEpochSecond(w.start).atZone(zone).toLocalDate()
                val maxHr = RecoveryEngine(dao).maxHeartRate(profile, date, zone)
                val fixed = wholeTrack(w, dao.workoutHeartRateOnce(w.start), WorkoutEffectEngine(dao).restingHr(w.start, zone), maxHr)
                if (fixed != null) {
                    dao.insertWorkouts(listOf(fixed))
                    log("history workout ${w.start}: mirrored, whole track ${fixed.exerciseSeconds} s, load ${fixed.trainingLoad}")
                }
            }.onFailure { log("history workout ${w.start}: whole track failed: $it") }
        }
    }
}
