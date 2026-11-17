package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.openstrap.app.ui.theme.Ink
import sv.lib.squircleshape.SquircleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableLongStateOf
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.dashboard.LocalHeartZones
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.SummaryCopy
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.openstrap.app.data.db.SleepSession

/** The trim frame's sides, the handles. The strip is inset by as much so either side can reach its end. */
internal val TrimSide = 12.dp

/** The track's height: the panel the line sits on. */
private val TrackHeight = 110.dp

/** Above and below the line inside the panel. */
private val PlotInset = 14.dp


/** Under the track, room for the line that marks what was recorded or detected. */
private val MarkRoom = 10.dp

/** The trim bar's test tag, so a test can drag along it. */
internal const val TRIM_BAR_TAG = "trimBar"

/**
 * The rules of the one trim bar that workouts, sleep and naps share ([TrimBar]): where its handles can go and how what
 * lies under it is shaded.
 */
internal object Trim {
    /** The handles never come closer than this. */
    const val MIN_SECONDS = 60L

    /** A workout's handles keep no length but [MIN_SECONDS]. */
    val ANY_LENGTH = MIN_SECONDS..Long.MAX_VALUE

    /** How far a sleep's or nap's handles reach past what the strap detected, either side. */
    const val SLEEP_REACH_SECONDS = 2 * 3600L

    /** A sleep's or nap's handles move a whole minute at a time. */
    const val SLEEP_STEP_SECONDS = 60L

    /**
     * [time] on the nearest [step] (seconds). A moment in [keep] on that same step is kept to the second instead, so
     * a handle dragged back onto the strap's own time leaves it the strap's. A step of 1 leaves [time] as it is.
     */
    fun snap(time: Long, step: Long, keep: List<Long> = emptyList()): Long {
        if (step <= 1) return time
        val snapped = Math.floorDiv(time + step / 2, step) * step
        return keep.firstOrNull { Math.floorDiv(it, step) * step == snapped } ?: snapped
    }

    /**
     * Where the handles of a sleep or nap can go: what the strap [detected] with [SLEEP_REACH_SECONDS] either side,
     * widened to take in times the user gave it before ([shownStart], [shownEnd]). It stops at [now] and at the
     * [others] (the other sleep and naps as shown) before and after it, so a handle is never dragged into either.
     */
    fun sleepReach(
        detected: SleepSession, shownStart: Long, shownEnd: Long, now: Long, others: List<SleepSession> = emptyList(),
    ): LongRange {
        val before = others.filter { it.end <= shownStart }.maxOfOrNull { it.end } ?: Long.MIN_VALUE
        val after = others.filter { it.start >= shownEnd }.minOfOrNull { it.start } ?: Long.MAX_VALUE
        val first = maxOf(minOf(detected.start - SLEEP_REACH_SECONDS, shownStart), before)
        val last = minOf(maxOf(detected.end + SLEEP_REACH_SECONDS, shownEnd), now, after)
        return first..last.coerceAtLeast(first + MIN_SECONDS)
    }

    private const val LEFT_OUT_ALPHA = 0.3f
    private const val CONTEXT_ALPHA = 0.15f

    /** How a stretch of the bar is shaded. */
    enum class Shade(val alpha: Float) {
        /** Inside the kept range: as recorded. */
        Kept(1f),
        /** Recorded or detected, but left out. */
        LeftOut(LEFT_OUT_ALPHA),
        /** Outside what was recorded or detected and left out: the strap's heart rate, there for context. */
        Context(CONTEXT_ALPHA),
    }

    /** The shade of the stretch at [time]: [kept] when it lies inside the handles, else by whether it's in the [core]. */
    fun shade(kept: Boolean, time: Long, core: LongRange): Shade = when {
        kept -> Shade.Kept
        time in core -> Shade.LeftOut
        else -> Shade.Context
    }

    /**
     * The handles after a drag to [at] (a time on the bar), the start one when [start]: on the [step] ([snap], with
     * [keep]), a [length] from the other handle (its ends rounded onto the step the way that keeps the length inside
     * it), inside [reach], and at least [MIN_SECONDS] from the other. When the reach is too tight for the shortest
     * length, the reach wins and Save's check says why.
     */
    fun drag(
        at: Long, start: Boolean, from: Long, to: Long, reach: LongRange, step: Long, keep: List<Long>,
        length: LongRange = ANY_LENGTH,
    ): Pair<Long, Long> {
        val other = if (start) to else from
        val near = if (start) other - length.first else other + length.first
        val far = when {
            length.last == Long.MAX_VALUE -> if (start) Long.MIN_VALUE else Long.MAX_VALUE
            start -> other - length.last
            else -> other + length.last
        }
        val t = if (start) snap(at, step, keep).coerceAtLeast(ceil(far, step)).coerceAtMost(floor(near, step))
        else snap(at, step, keep).coerceAtMost(floor(far, step)).coerceAtLeast(ceil(near, step))
        val inReach = t.coerceIn(reach.first, reach.last)
        return if (start) inReach.coerceAtMost(to - MIN_SECONDS) to to else from to inReach.coerceAtLeast(from + MIN_SECONDS)
    }

    private fun floor(time: Long, step: Long) =
        if (step <= 1 || time == Long.MIN_VALUE || time == Long.MAX_VALUE) time else Math.floorDiv(time, step) * step

    private fun ceil(time: Long, step: Long) =
        if (step <= 1 || time == Long.MIN_VALUE || time == Long.MAX_VALUE) time else -Math.floorDiv(-time, step) * step
}

/**
 * A video trimmer, one for workouts, sleep and naps: the heart rate as thin solid bars ([points], evenly spaced across
 * [reach]) in [colorOf]'s colours, inside one rounded frame whose thicker sides are the handles (Snapchat's,
 * Polarsteps'). Drag either side in, or out as far as [reach]. Outside [from] to [to] the bars fade back. What was
 * recorded or detected is the [core]: when the reach goes past it, a line under the track marks it, and the heart rate
 * past it is dimmer still, there for context, until the handles take it in. A workout passes a reach equal to its
 * core, so it only trims. The handles move a [step] at a time, landing on a moment in [keep] to the second, and stop
 * a [length] apart, as they stop at the reach. The clocks of the reach's ends sit under it.
 */
// The readings and the moments to keep arrive whole with the workout or the sleep and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun TrimBar(
    points: List<Float?>,
    core: LongRange,
    reach: LongRange,
    from: Long,
    to: Long,
    colorOf: (Float) -> Color?,
    onChange: (from: Long, to: Long) -> Unit,
    modifier: Modifier = Modifier,
    step: Long = 1,
    keep: List<Long> = emptyList(),
    length: LongRange = Trim.ANY_LENGTH,
) {
    val first = reach.first
    val last = reach.last.coerceAtLeast(first + 1)
    // The drag reads the range as it stands when it starts and keeps its own copy while it moves, so nothing is
    // written back during composition (audit K6).
    val range by rememberUpdatedState(from to to)
    val change by rememberUpdatedState(onChange)
    val known = points.filterNotNull()
    val lo = (known.minOrNull() ?: 60f) - 10f
    val hi = (known.maxOrNull() ?: 180f) + 5f
    val marked = core != reach
    // The clocks sit as far under the bar as the workout's panel spaces its blocks.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Canvas(
            Modifier.fillMaxWidth().height(if (marked) TrackHeight + MarkRoom else TrackHeight).testTag(TRIM_BAR_TAG)
                .pointerInput(first, last, step, keep, length) {
                var start = true
                var f = 0L
                var t = 0L
                val inset = TrimSide.toPx()
                fun timeAt(x: Float) = (first + ((x - inset) / (size.width - 2 * inset) * (last - first)).toLong()).coerceIn(first, last)
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        f = range.first
                        t = range.second
                        val at = timeAt(o.x)
                        start = abs(at - f) <= abs(at - t)
                    },
                ) { c, _ ->
                    val (nf, nt) = Trim.drag(timeAt(c.position.x), start, f, t, first..last, step, keep, length)
                    f = nf
                    t = nt
                    change(f, t)
                }
            },
        ) {
            val inset = TrimSide.toPx()
            val w = size.width - 2 * inset
            val h = TrackHeight.toPx()
            fun x(time: Long) = inset + (time - first).toFloat() / (last - first) * w
            val fx = x(from)
            val tx = x(to)
            // The panel the line sits on (board HaloTrimSheet: #0A0B0D, a squircle of 20).
            drawOutline(Corner.Inner.createOutline(Size(size.width, h), layoutDirection, this), Greys.Card)
            // The heart rate as one rounded line in its zones' colours (board HaloTrimSheet): each stretch coloured by the
            // zone it sits in, as bright as the shade of where it lies. A stretch with no reading is left empty.
            val top = PlotInset.toPx()
            val plot = h - 2 * top
            fun y(v: Float) = top + (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f)) * plot
            if (points.size >= 2) for (i in 1 until points.size) {
                val v0 = points[i - 1]
                val v1 = points[i]
                val color = if (v0 != null && v1 != null) colorOf((v0 + v1) / 2f) else null
                if (color != null && v0 != null && v1 != null) {
                    val x0 = inset + (i - 1) / (points.size - 1f) * w
                    val x1 = inset + i / (points.size - 1f) * w
                    // Kept by where the segment sits on screen, as the frame is drawn, so a stretch is never half in.
                    val at = first + ((i - 0.5f) / (points.size - 1f) * (last - first)).toLong()
                    val shade = Trim.shade((x0 + x1) / 2 in fx..tx, at, core)
                    drawLine(
                        color.copy(alpha = color.alpha * shade.alpha), Offset(x0, y(v0)), Offset(x1, y(v1)),
                        strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
            // What lies outside the handles is dimmed once more.
            drawRect(Ink.Scrim, Offset(0f, 0f), Size(fx, h))
            drawRect(Ink.Scrim, Offset(tx, 0f), Size(size.width - tx, h))
            // The frame: a white outline round what is kept, a pill handle on each side with a short grip.
            drawRoundRect(
                Ink.Primary, Offset(fx, 1.dp.toPx()), Size(tx - fx, h - 2.dp.toPx()), CornerRadius(12.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
            val handleW = 14.dp.toPx()
            val handleH = 44.dp.toPx()
            val grip = 2.dp.toPx()
            listOf(fx, tx).forEach { gx ->
                drawRoundRect(Color.White, Offset(gx - handleW / 2, (h - handleH) / 2), Size(handleW, handleH), CornerRadius(handleW / 2))
                drawRoundRect(
                    Ink.Scrim, Offset(gx - grip / 2, h / 2 - 10.dp.toPx()), Size(grip, 20.dp.toPx()),
                    CornerRadius(grip / 2),
                )
            }
            if (marked) {
                val my = h + MarkRoom.toPx() / 2
                val mark = 2.dp.toPx()
                drawLine(
                    Ink.Disabled,
                    Offset(x(core.first.coerceIn(first, last)), my), Offset(x(core.last.coerceIn(first, last)), my),
                    strokeWidth = mark, cap = StrokeCap.Round,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            HaloNote(clock(first))
            Spacer(Modifier.weight(1f))
            HaloNote(clock(last))
        }
    }
}

/**
 * The trim sheet (board HaloTrimSheet) while [trim] is open: "Trim start and end" with when it was recorded, the times and
 * length kept in Funnel Display Light, the line with its handles and the clocks of what was recorded, a hint, then Cancel
 * and Save. Saving changes only what is shown, and nothing recorded is deleted. This replaces `TrimSheet(trim)` of
 * GlowWorkoutMode.kt for the summary and a past workout.
 */
@Composable
internal fun HaloTrimSheet(trim: WorkoutTrim) {
    if (!trim.isOpen) return
    val summary = trim.summary
    val note = SummaryCopy.trimRecorded(clock(summary.recordedStart), clock(summary.recordedEnd))
    HaloSheet(trim.close, stringResource(R.string.glow_workout_trim_title), note = note) {
        HaloTrimContent(summary, onSave = { from, to -> trim.close(); trim.save(from, to) }, onCancel = trim.close)
    }
}

/** What the sheet holds: the kept times, the [TrimBar] with its zone colours, the hint and the two buttons. */
@Composable
private fun HaloTrimContent(summary: PhoneSummary, onSave: (Long, Long) -> Unit, onCancel: () -> Unit) {
    val heartZones = LocalHeartZones.current
    val a = summary.analysis
    val first = summary.recordedStart
    val last = summary.recordedEnd.coerceAtLeast(first + 60)
    // The handles outlive a rotation, a fold and process death while the sheet stays open (audit K7). They start again
    // only when the workout or its shown range changes.
    var from by rememberSaveable(a.workout.start, a.shownStart, a.end) { mutableLongStateOf(a.shownStart) }
    var to by rememberSaveable(a.workout.start, a.shownStart, a.end) { mutableLongStateOf(a.end) }
    val step = 10L
    val points = remember(summary) {
        val n = ((last - first) / step + 1).toInt().coerceAtLeast(2)
        org.openstrap.app.data.Summaries.bucketMeans(summary.recorded, n, { ((it.timestamp - first) / step).toInt() }, { it.bpm.toFloat() })
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S16)) {
        Text(
            SummaryCopy.trimHeader(clock(from), clock(to), to - from), color = Color.White,
            style = GlowType.sentence,
        )
        // Recorded is all it can reach for now: the core and the reach are one, so the bar only trims.
        TrimBar(
            points, core = first..last, reach = first..last, from = from, to = to,
            colorOf = { v -> WorkoutRecorder.liveZone(v.toInt(), a.maxHeartRate, heartZones)?.let { ZONE_COLORS[it] } },
            onChange = { f, t -> from = f; to = t },
        )
        Text(SummaryCopy.TRIM_HINT, color = GlowType.noteColor, style = GlowType.secondary)
        ButtonPair(
            stringResource(R.string.glow_cancel),
            onCancel,
            stringResource(R.string.glow_save),
            { onSave(from, to) },
        )
    }
}
