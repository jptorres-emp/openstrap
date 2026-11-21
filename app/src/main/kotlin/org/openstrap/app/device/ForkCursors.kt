package org.openstrap.app.device

import org.openstrap.protocol.history.HistoryDataType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The fork's first sync: a type never synced goes [FIRST_SYNC_DAYS] back instead of Zepp's 2 days
 * ([ZeppCursors.default]), so the days between unboxing the strap and pairing it here come too: the strap sends what it
 * still holds (sleep, HRV, stress, the extended minutes' steps, workouts...). Per-minute activity, which it keeps about
 * 43 hours, and max HR, which follows activity's cursor, keep Zepp's start. A stored cursor reads as Zepp reads it.
 */
internal object ForkCursors {
    /** How far back a never synced type starts. */
    const val FIRST_SYNC_DAYS = 30L

    /** The types whose first sync keeps Zepp's 2 days. */
    private val ZEPP_START = setOf(HistoryDataType.ACTIVITY, HistoryDataType.DAILY_MAX_EXERCISE_HR)

    /** [type]'s cursor under [key] as [ZeppCursors.read] reads it, or the fork's first start when there is none. */
    suspend fun read(type: HistoryDataType, cursors: SyncCursors, key: String, now: Long, zone: ZoneId): Long =
        cursors.get(key)?.let { ZeppCursors.onRead(it, now) }
            ?: if (type in ZEPP_START) ZeppCursors.default(now, zone) else firstStart(now, zone)

    /** [FIRST_SYNC_DAYS] back at the half day: Zepp's default, taken from that many days less 2 before [now]. */
    fun firstStart(now: Long, zone: ZoneId): Long {
        val earlier = ZonedDateTime.ofInstant(Instant.ofEpochSecond(now), zone).minusDays(FIRST_SYNC_DAYS - ZEPP_DAYS)
        return ZeppCursors.default(earlier.toEpochSecond(), zone)
    }

    /** Zepp's own first start, in days back ([ZeppCursors.default]). */
    private const val ZEPP_DAYS = 2L
}
