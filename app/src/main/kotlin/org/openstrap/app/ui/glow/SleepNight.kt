package org.openstrap.app.ui.glow

import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepSession
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.presentation.StageSpan
import org.openstrap.app.ui.dashboard.DayUiState

/*
 * One night as every sleep picture and sentence reads it. The stage rows are the only source for the time asleep, the
 * time awake, the number of wake-ups and the graph, so the legend, "What shaped the score", the sentence and the drawn
 * ticks always agree. The strap's own summary figures (its awake minutes and wake count) are only used for a night with
 * no stage rows. Stage rows are kept to the time between falling asleep and waking and never overlap, so the totals
 * can't add up to more than the night is long.
 */

/** The rules that turn a night's stage rows into the one set of figures. */
internal object NightStages {
    /** An awake stretch shorter than this isn't a wake-up: the strap's own floor for one. */
    const val MIN_WAKE_SECONDS = 60L

    /**
     * [spans] kept to [start] until [end], in time order, with none overlapping another: where two rows cover the same
     * time the one that starts first keeps it, and the later one keeps only what's after. Rows left empty go.
     */
    fun clean(spans: List<StageSpan>, start: Long, end: Long): List<StageSpan> {
        if (end <= start) return emptyList()
        var cursor = start
        return spans.sortedWith(compareBy({ it.start }, { it.end })).mapNotNull { sp ->
            val from = maxOf(sp.start, cursor)
            val to = minOf(sp.end, end)
            if (to <= from) null else StageSpan(from, to, sp.stage).also { cursor = to }
        }
    }

    /** [spans] in time order with runs of the same stage joined into one, so a long stretch is one span. */
    fun join(spans: List<StageSpan>): List<StageSpan> = spans.sortedBy { it.start }.fold(emptyList()) { acc, sp ->
        val last = acc.lastOrNull()
        if (last != null && last.stage == sp.stage && sp.start <= last.end) acc.dropLast(1) + last.copy(end = maxOf(last.end, sp.end))
        else acc + sp
    }

    /** [stages] as hypnogram stages with the runs of one row joined, the times as fractions of the night. */
    fun joinStages(stages: List<GlowStage>): List<GlowStage> = stages.sortedBy { it.from }.fold(emptyList()) { acc, st ->
        val last = acc.lastOrNull { it.row == st.row }
        // A run joins the one before it in its own row when nothing else came between them.
        if (last != null && acc.last() === last && st.from <= last.to + JOIN_GAP) acc.dropLast(1) + last.copy(to = maxOf(last.to, st.to))
        else acc + st
    }

    /** The most a hypnogram leaves between two runs of one stage and still draws them as one: a rounding hair. */
    private const val JOIN_GAP = 1e-4f

    /** The figures of [spans], which are clean ([clean]). */
    fun breakdown(spans: List<StageSpan>): Summaries.SleepBreakdown {
        val byStage = spans.groupBy { it.stage }.mapValues { (_, v) -> v.sumOf { it.end - it.start } }
        val awake = byStage[SleepStage.AWAKE] ?: 0L
        return Summaries.SleepBreakdown(byStage.values.sum() - awake, awake, byStage)
    }

    /** How many times [spans] show the sleeper awake: each joined awake stretch of at least [MIN_WAKE_SECONDS]. */
    fun wakes(spans: List<StageSpan>): Int =
        join(spans).count { it.stage == SleepStage.AWAKE && it.end - it.start >= MIN_WAKE_SECONDS }
}

/**
 * [session] as shown with its [spans] and the one set of figures. [session] is the strap's, its totals and wake count
 * replaced by those of the stage rows when there are some, so what reads them (the headline, the factors) agrees with
 * the legend and the graph.
 */
internal class ShownNight(
    val session: SleepSession,
    val spans: List<StageSpan>,
    /** The stage totals, null with no stage rows. */
    val breakdown: Summaries.SleepBreakdown?,
    val asleep: Long,
    val awake: Long,
)

/** The night [session] with its stage [spans] as every sleep picture and sentence reads it; null without a session. */
internal fun shownNight(session: SleepSession?, spans: List<StageSpan>): ShownNight? {
    session ?: return null
    val length = (session.end - session.start).coerceAtLeast(0L)
    val clean = NightStages.clean(spans, session.start, session.end)
    if (clean.isEmpty()) {
        // No stage rows: the strap's own figures, held to the length of the night.
        val asleep = SleepHistory.asleepSeconds(session).coerceIn(0L, length)
        val awake = ((session.awakeMinutes ?: 0) * 60L).coerceIn(0L, length - asleep)
        return ShownNight(session, emptyList(), null, asleep, awake)
    }
    val b = NightStages.breakdown(clean)
    fun minutes(stage: Int) = ((b.byStage[stage] ?: 0L) / 60).toInt()
    val shown = session.copy(
        deepMinutes = minutes(SleepStage.DEEP), lightMinutes = minutes(SleepStage.LIGHT), remMinutes = minutes(SleepStage.REM),
        awakeMinutes = minutes(SleepStage.AWAKE), wakeCount = NightStages.wakes(clean),
    )
    return ShownNight(shown, clean, b, b.asleepSeconds, b.awakeSeconds)
}

/** This day's main sleep as every sleep picture reads it ([shownNight]). */
internal fun DayUiState.shownNight(): ShownNight? = shownNight(sleep, sleepStages)
