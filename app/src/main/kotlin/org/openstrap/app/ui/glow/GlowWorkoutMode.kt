package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.Units
import androidx.compose.ui.res.stringResource
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.WorkoutColors
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import org.openstrap.app.ui.components.PinnedFade
import org.openstrap.app.ui.components.pinnedFade
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.WorkoutSuggestions
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.ZONE_COLORS
import androidx.compose.ui.semantics.onClick
import org.openstrap.app.ui.workout.SummaryCopy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.openstrap.app.ui.theme.ControlSurface
import org.openstrap.app.data.HeartZones
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.runtime.ReadOnlyComposable

/* ---------------------------------------------- Shared ----------------------------------------------- */

/** A paused workout, or one with no reading yet, is a cool grey rather than a zone, the notification's grey. */
internal val PausedColor = Color(WorkoutColors.PAUSED_GREY)

/** The Control surface (spec 2.2): round buttons, chips, pills, tiles and fields on the workout pages. */
internal val Raised = ControlSurface

/** Body in Medium, for pills, chips and short live lines. */
internal val MediumBody = GlowType.body.copy(fontWeight = FontWeight.Medium)

/** The colour a workout takes: its live zone's in your [zones], or [PausedColor] with no reading. */
internal fun zoneColor(bpm: Int?, maxHr: Int, zones: HeartZones): Color = WorkoutRecorder.liveZone(bpm, maxHr,
    zones)?.let { ZONE_COLORS[it] } ?: PausedColor

/** The colour the whole app takes while [r] records. */
fun workoutColor(r: WorkoutState.Recording, liveHeartRate: Int?, connected: Boolean, zones: HeartZones): Color =
    if (r.paused) PausedColor else zoneColor(liveHeartRate.takeIf { connected }, r.maxHeartRate, zones)

/** "12:34", or "1:02:03" past the hour, in the phone's digits. Text drawn in dots passes [Units.DOT_LOCALE]. */
internal fun workoutClock(seconds: Long,
    locale: java.util.Locale = java.util.Locale.getDefault()): String = Units.workoutClock(seconds, locale)

/** [workoutClock] for the dot font, whose digits are ASCII only. */
internal fun dotClock(seconds: Long): String = Units.workoutClock(seconds, Units.DOT_LOCALE)

/** A live zone's heart rates in your zones: "under 115 bpm" for zone 1 (anything slower counts), else "134 to 152 bpm". */
// From the root, so it reads again when the zones are moved on the Heart rate zones page.
@Composable
@ReadOnlyComposable
internal fun zoneRange(zone: Int, maxHr: Int): String {
    val zones = LocalHeartZones.current
    if (zone == 0) return stringResource(R.string.glow_workout_zone_under, zones.startBpm(1, maxHr))
    val range = Ranges.of("${zones.startBpm(zone, maxHr)}", "${zones.endBpm(zone, maxHr) ?: maxHr}")
    return stringResource(R.string.glow_workout_zone_range, range)
}

/** Active time of the workout, ticking once a second while it records. */
@Composable
internal fun activeSeconds(r: WorkoutState.Recording): Long = activeSecondsState(r).value

/**
 * [activeSeconds] as state, for a live screen to hand on as `{ active.value }`: only what shows the time reads it, so
 * the screen itself isn't composed again each second.
 */
@Composable
internal fun activeSecondsState(r: WorkoutState.Recording): androidx.compose.runtime.State<Long> = produceState(WorkoutRecorder.activeSeconds(r.workout, System.currentTimeMillis() / 1000), r.workout) {
    while (true) {
        value = WorkoutRecorder.activeSeconds(r.workout, System.currentTimeMillis() / 1000)
        if (r.paused) break
        delay(1000 - System.currentTimeMillis() % 1000)
    }
}


/* ----------------------------------------- Activity tab entry ---------------------------------------- */

/** Where a suggestion sits: a quiet panel, or on Today an unlit strip ([strip]). */
@Composable
private fun OfferBox(strip: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (!strip) HaloCard(content = content)
    else HaloCard {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S12), content = content)
    }
}

/** "Looks like you worked out": a stretch nobody started, offered for saving. [strip] draws it as a Today strip. */
@Composable
internal fun SuggestionPanel(
    s: org.openstrap.app.data.WorkoutSuggestions.Suggestion, onSave: () -> Unit, onDismiss: () -> Unit, strip: Boolean = false,
) {
    OfferBox(strip) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Heartbeat, null, tint = ZONE_COLORS[s.zone], modifier = Modifier.padding(top = Space.S2).size(20.dp))
            Spacer(Modifier.width(Space.S8))
            Column {
                Text(stringResource(R.string.glow_workout_suggestion_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
                val day = dayName(Instant.ofEpochSecond(s.start).atZone(ZoneId.systemDefault()).toLocalDate())
                val times = Ranges.times(s.start, s.end)
                HaloNote(stringResource(R.string.glow_workout_suggestion_text, day, times, s.zone + 1, s.averageHeartRate))
            }
        }
        ButtonPair(
            stringResource(R.string.glow_workout_suggestion_dismiss), onDismiss, stringResource(R.string.glow_workout_suggestion_save),
            onSave,
            size = HaloButtonSize.Card,
        )
    }
}

/**
 * Adds a workout nobody started, after the fact, as a sheet with Cancel and Save (user decision 2026-11-15, over the
 * board HaloAddWorkout's page): the day's heart rate with two handles to drag to where it began and ended (first set
 * on the day's likeliest stretch), the range and its length above it, then the sport and the day in one panel. The
 * sport opens the picker page and comes back to the sheet. Built from the strap's stored minutes.
 */
@Composable
fun GlowAddWorkoutScreen(
    date: LocalDate,
    minuteHr: List<Pair<Long, Int>>?,
    maxHr: Int,
    contentPadding: PaddingValues,
    onSave: (from: Long, to: Long, sportType: Int) -> Unit,
    onBack: () -> Unit,
    recentSports: List<Int> = emptyList(),
    sides: Map<Int, Boolean> = emptyMap(),
) {
    val heartZones = LocalHeartZones.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val series = minuteHr.orEmpty()
    val first = series.firstOrNull()?.first ?: 0L
    val last = (series.lastOrNull()?.first ?: 0L) + 60
    val likely = remember(series, heartZones) { WorkoutSuggestions.find(series, 60, maxHr, emptyList(), heartZones).firstOrNull() }
    var from by rememberSaveable(series.size) { mutableStateOf(likely?.start ?: (last - 1800).coerceAtLeast(first)) }
    var to by rememberSaveable(series.size) { mutableStateOf(likely?.end ?: last) }
    var sport by rememberSaveable { mutableStateOf(recentSports.firstOrNull() ?: Workouts.EVERYDAY.first()) }
    if (picking) {
        // The picker is a page of its own over the app; the sheet is back once a sport is picked.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SportPicker(sport, recentSports, contentPadding, onPick = { sport = pickedType(it, sides); picking = false },
                onBack = { picking = false })
        }
        return
    }
    val ready = minuteHr != null && series.size >= 2
    HaloSheet(
        onBack, stringResource(R.string.glow_workout_add_title),
        footer = {
            ButtonPair(
                stringResource(R.string.glow_cancel),
                onBack,
                stringResource(R.string.glow_save),
                { onSave(from, to, sport) },
                rightEnabled = ready,
            )
        },
    ) {
        if (minuteHr == null) {
            HaloNote(stringResource(R.string.glow_workout_loading))
        } else if (series.size < 2) {
            HaloNote(stringResource(R.string.glow_workout_add_no_heart))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                Text(stringResource(R.string.glow_workout_add_when), color = GlowType.noteColor, style = GlowType.secondary,
                    modifier = Modifier.padding(horizontal = Space.S4))
                Column(Modifier.padding(horizontal = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    Text(Ranges.times(from, to), color = Color.White, style = GlowType.figure, maxLines = 1)
                    val inside = series.filter { it.first in from until to }.map { it.second }
                    Text(
                        if (inside.isEmpty()) stringResource(R.string.glow_workout_add_range_length, ((to - from) / 60).toInt())
                        else stringResource(R.string.glow_workout_add_range_note, ((to - from) / 60).toInt(), inside.average().toInt()),
                        color = GlowType.noteColor, style = GlowType.secondary,
                    )
                }
            }
            RangeChart(series, first, last, from, to, maxHr) { f, t -> from = f; to = t }
            Row(Modifier.fillMaxWidth()) {
                HaloNote(clock(first))
                Spacer(Modifier.weight(1f))
                HaloNote(clock(last))
            }
            Text(stringResource(R.string.glow_workout_add_hint), color = GlowType.footnoteColor, style = GlowType.footnote)
            HaloCard(fill = CardFill.Rows) {
                NavRow(stringResource(R.string.glow_workout_add_sport), Workouts.sportName(sport),
                    onClick = { picking = true }, divider = false)
                NavRow(stringResource(R.string.glow_workout_add_day), dayName(date))
            }
        }
    }
}

/**
 * The sport type to save for a tile picked away from Ready, where there's no switch: a sport with two kinds takes the
 * side you last chose on Ready ([sides]), else its default (a pool for Swim). Any other type is itself.
 */
internal fun pickedType(type: Int, sides: Map<Int, Boolean>): Int =
    Workouts.sidesOf(type)?.let { s -> s.type(Workouts.indoorSide(type, sides) ?: false) } ?: type

/** Room either side of a range chart, so its handles sit whole inside it. */
private val HandleInset = 14.dp

/** A day of minute heart rate in its zones' colours, with two handles marking a range; outside it dims. */
@Composable
private fun RangeChart(series: List<Pair<Long, Int>>, first: Long, last: Long, from: Long, to: Long, maxHr: Int,
    onChange: (Long, Long) -> Unit) {
    val heartZones = LocalHeartZones.current
    val lo = (series.minOfOrNull { it.second } ?: 60) - 5f
    val hi = (series.maxOfOrNull { it.second } ?: 180) + 5f
    // The drag reads the range as it stands when it starts and keeps its own copy while it moves, so nothing is
    // written back during composition (audit K6).
    val range by rememberUpdatedState(from to to)
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).pointerInput(series, first, last) {
            var dragging = 0
            var f = 0L
            var t = 0L
            val inset = HandleInset.toPx()
            fun timeAt(x: Float) = (first + ((x - inset) / (size.width - 2 * inset) * (last - first)).toLong()).coerceIn(first, last)
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    f = range.first
                    t = range.second
                    val at = timeAt(o.x)
                    dragging = if (kotlin.math.abs(at - f) <= kotlin.math.abs(at - t)) 0 else 1
                },
            ) { c, _ ->
                val at = timeAt(c.position.x) / 60 * 60
                if (dragging == 0) f = at.coerceAtMost(t - 60) else t = at.coerceAtLeast(f + 60)
                change(f, t)
            }
        },
    ) {
        val f = from
        val t = to
        val inset = HandleInset.toPx()
        fun x(time: Long) = inset + (time - first).toFloat() / (last - first) * (size.width - 2 * inset)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        drawZoneLine(series.size, 2.dp.toPx(), at = { i -> series[i].let { (time, bpm) -> Offset(x(time), y(bpm.toFloat())) } }) { i ->
            val (time, bpm) = series[i]
            // More than five minutes without a reading breaks the line.
            if (series[i + 1].first - time > 300) null
            else ZONE_COLORS[WorkoutRecorder.liveZoneOf(bpm, maxHr, heartZones)].copy(alpha = if (time in f until t) 1f else 0.25f)
        }
        listOf(f, t).forEach { time ->
            val hx = x(time)
            drawLine(Color.White, Offset(hx, 0f), Offset(hx, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(hx, size.height / 2))
        }
    }
}

/* ---------------------------------------------- Summary ---------------------------------------------- */

/** Done right after the end, and Delete workout behind a question. */
@Composable
internal fun ColumnScope.SummaryActions(justEnded: Boolean, onDone: () -> Unit, onDelete: () -> Unit) {
    if (justEnded) {
        HaloButton(stringResource(R.string.glow_done), onDone, Modifier.fillMaxWidth(), variant = HaloButtonVariant.Primary)
    }
    DeleteWorkout(stringResource(R.string.glow_workout_delete_phone), onDelete, Modifier.align(Alignment.CenterHorizontally), wide = true)
}

/**
 * Delete workout (boards HaloSummaryRun, HaloPastWorkout): a destructive button with a trash icon, asking first; [what]
 * says what deleting it does. [wide] fills the width at 56 (a screen that ends on it); otherwise it is a small pill
 * centred under what it deletes.
 */
@Composable
internal fun DeleteWorkout(what: String, onDelete: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val tap = { confirmDelete = true }
    if (wide) {
        HaloButton(
            stringResource(R.string.glow_workout_delete), tap, modifier.fillMaxWidth(),
            variant = HaloButtonVariant.Destructive, icon = AppIcons.Trash,
        )
    } else {
        Box(modifier.fillMaxWidth().padding(top = Space.S4), contentAlignment = Alignment.Center) {
            HaloButton(
                stringResource(R.string.glow_workout_delete), tap,
                variant = HaloButtonVariant.Destructive, size = HaloButtonSize.Card, icon = AppIcons.Trash,
            )
        }
    }
    if (confirmDelete) {
        HaloConfirmSheet(
            stringResource(R.string.glow_workout_delete_confirm), what, stringResource(R.string.glow_delete),
            { confirmDelete = false; onDelete() }, { confirmDelete = false },
            destructive = true,
        )
    }
}

/**
 * A phone workout's trim (the user's choice, 2026-11-04): "Trim" on its heart rate card opens the trimmer in a sheet
 * ([TrimSheet]), and what was done to its times reads under the card's chart with Undo ([TrimNote]). [onTrim] saves
 * new times, [onUndo] puts back the recorded ones. Whether the sheet is open outlives a rotation.
 */
@Composable
internal fun rememberWorkoutTrim(summary: PhoneSummary, onTrim: (from: Long, to: Long) -> Unit, onUndo: () -> Unit): WorkoutTrim {
    val open = rememberSaveable { mutableStateOf(false) }
    return WorkoutTrim(summary, open, onTrim, onUndo)
}

/** What [rememberWorkoutTrim] gives the page: the sheet's open state, and what Save and Undo do. */
@androidx.compose.runtime.Stable
internal class WorkoutTrim(
    val summary: PhoneSummary,
    private val shown: androidx.compose.runtime.MutableState<Boolean>,
    val save: (from: Long, to: Long) -> Unit,
    val undo: () -> Unit,
) {
    val isOpen: Boolean get() = shown.value
    val open: () -> Unit = { shown.value = true }
    val close: () -> Unit = { shown.value = false }
}

/** The trim sheet while [trim] is open: Save saves the new times and closes it. */
@Composable
internal fun TrimSheet(trim: WorkoutTrim) {
    if (!trim.isOpen) return
    TrimSheet(
        trim.summary,
        onSave = { from, to ->
            trim.close()
            trim.save(from, to)
        },
        onDismiss = trim.close,
    )
}

/**
 * What the app did to the workout's times, with Undo as a button: an early end it proposed (in a car, or when you'd
 * stopped), or a trim. Nothing while they're as recorded. [onCard] puts Undo on a card's glass.
 */
@Composable
internal fun TrimNote(summary: PhoneSummary, onUndo: () -> Unit, modifier: Modifier = Modifier, onCard: Boolean = true) {
    val text = trimNote(summary) ?: return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically) {
        HaloNote(text, Modifier.weight(1f))
        HaloButton(
            stringResource(R.string.glow_workout_undo), onUndo, size = HaloButtonSize.Small, icon = AppIcons.ArrowCounterClockwise,
            variant = if (onCard) HaloButtonVariant.Glass else HaloButtonVariant.Secondary,
        )
    }
}

/** The words of [TrimNote], null while the workout's times are as recorded. */
internal fun trimNote(summary: PhoneSummary, strings: Strings = Strings.app): String? {
    val a = summary.analysis
    val untouched = a.shownStart == a.workout.start
    val recorded = a.recordedEnd ?: return if (untouched) null else strings.format(R.string.glow_workout_trim_start, clock(a.shownStart))
    val cut = recorded - a.end
    val left = if (cut < 60) {
        strings.format(R.string.glow_workout_trim_seconds, cut)
    } else {
        strings.format(R.string.glow_workout_trim_minutes, cut / 60)
    }
    return when {
        summary.endReason == AppWorkout.END_CAR && untouched -> strings.format(R.string.glow_workout_trim_car, clock(a.end), left)
        summary.endReason == AppWorkout.END_AUTO && untouched -> strings.format(R.string.glow_workout_trim_auto, clock(a.end), left)
        else -> strings.format(R.string.glow_workout_trim_range, Ranges.times(a.shownStart, a.end))
    }
}

/**
 * The trimmer in a sheet, as the sleep page opens a night's times: the times it was recorded as the note, then
 * [WorkoutTrimBar]. Saving changes only what is shown, and nothing recorded is deleted.
 */
@Composable
internal fun TrimSheet(summary: PhoneSummary, onSave: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    val note = SummaryCopy.trimRecorded(clock(summary.recordedStart), clock(summary.recordedEnd))
    HaloSheet(onDismiss, stringResource(R.string.glow_workout_trim_title), note = note) {
        WorkoutTrimBar(summary, onSave, onDismiss)
    }
}

/**
 * Moves the start and end on the whole recording, as WHOOP allows: the times and length above the bar, then drag
 * either handle along the heart rate in its zones' colours, and what falls outside dims. Cancel and Save under it.
 */
@Composable
internal fun WorkoutTrimBar(summary: PhoneSummary, onSave: (Long, Long) -> Unit, onCancel: () -> Unit) {
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
        Text(SummaryCopy.trimHeader(clock(from), clock(to), to - from), color = Color.White, style = GlowType.body)
        // Recorded is all it can reach for now: the core and the reach are one, so the bar only trims.
        TrimBar(
            points, core = first..last, reach = first..last, from = from, to = to,
            colorOf = { v -> WorkoutRecorder.liveZone(v.toInt(), a.maxHeartRate, heartZones)?.let { ZONE_COLORS[it] } },
            onChange = { f, t -> from = f; to = t },
        )
        Text(SummaryCopy.TRIM_HINT, color = Ink.Secondary, style = GlowType.secondary)
        ButtonPair(
            stringResource(R.string.glow_cancel),
            onCancel,
            stringResource(R.string.glow_save),
            { onSave(from, to) },
        )
    }
}

/**
 * A screen over the app that comes and goes like every other screen ([org.openstrap.app.ui.components.PushContent]):
 * in from the right a quarter of the width with a fade, out the same way, kept on screen until it has gone. [cut]
 * drops it at once instead, for when what replaces it (a workout starting) shouldn't watch it leave.
 */
@Composable
fun <T : Any> PushOverlay(value: T?, cut: Boolean, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != null) last = value }
    if (cut) return
    val move = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(
        org.openstrap.app.ui.components.Motion.SWIFT, easing = org.openstrap.app.ui.components.Motion.easing,
    )
    androidx.compose.animation.AnimatedVisibility(
        value != null,
        enter = androidx.compose.animation.slideInHorizontally(move) { it / 4 } +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(org.openstrap.app.ui.components.Motion.SWIFT)),
        exit = androidx.compose.animation.slideOutHorizontally(move) { it / 4 } +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(org.openstrap.app.ui.components.Motion.QUICK)),
    ) { (value ?: last)?.let { content(it) } }
}
