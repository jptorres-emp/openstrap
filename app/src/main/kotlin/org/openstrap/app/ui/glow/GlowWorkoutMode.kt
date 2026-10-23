package org.openstrap.app.ui.glow

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.widthIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.WorkoutSuggestions
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.data.Route
import org.openstrap.app.data.Strength
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.data.db.AppWorkout
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.SportIcons
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZONE_NAMES
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* ---------------------------------------------- Shared ----------------------------------------------- */

/** A paused workout, or one with no reading yet, is a cool grey rather than a zone. */
internal val PausedColor = Color(0xFF9AA0B4)
private val Raised = Color(0xFF1C1C1E)

/** The colour a workout takes: its live zone's, or [PausedColor] with no reading. */
internal fun zoneColor(bpm: Int?, maxHr: Int): Color = WorkoutRecorder.liveZone(bpm, maxHr)?.let { ZONE_COLORS[it] } ?: PausedColor

/** The colour the whole app takes while [r] records. */
fun workoutColor(r: WorkoutState.Recording, liveHeartRate: Int?, connected: Boolean): Color =
    if (r.paused) PausedColor else zoneColor(liveHeartRate.takeIf { connected }, r.maxHeartRate)

/** "12:34", or "1:02:03" past the hour. */
internal fun workoutClock(seconds: Long): String =
    if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

private fun zoneRange(zone: Int, maxHr: Int) =
    if (zone == 0) "under ${60 * maxHr / 100} bpm"
    else "${(50 + zone * 10) * maxHr / 100}–${if (zone == Workouts.ZONES - 1) maxHr else (60 + zone * 10) * maxHr / 100 - 1} bpm"

/** Active time of the workout, ticking once a second while it records. */
@Composable
internal fun activeSeconds(r: WorkoutState.Recording): Long = produceState(WorkoutRecorder.activeSeconds(r.workout, System.currentTimeMillis() / 1000), r.workout) {
    while (true) {
        value = WorkoutRecorder.activeSeconds(r.workout, System.currentTimeMillis() / 1000)
        if (r.paused) break
        delay(1000 - System.currentTimeMillis() % 1000)
    }
}.value

/** A target's colour: the top zone it aims for, a calm blue for a time, white for none. */
internal fun targetColor(t: WorkoutTarget?): Color = when (t) {
    is WorkoutTarget.Zones -> ZONE_COLORS[t.high]
    is WorkoutTarget.Time -> ZONE_COLORS[0]
    null -> Color.White
}

internal fun targetLabel(t: WorkoutTarget?): String = when (t) {
    is WorkoutTarget.Zones -> t.label
    is WorkoutTarget.Time -> t.label
    null -> "None"
}

/** A full-width pill button. */
@Composable
internal fun WorkoutButton(
    label: String, background: Color, content: Color, onClick: () -> Unit,
    modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, height: Dp = 64.dp,
) {
    Row(
        modifier.fillMaxWidth().height(height).shadow(28.dp, RoundedCornerShape(50), ambientColor = background, spotColor = background)
            .clip(RoundedCornerShape(50)).background(background).pressClickable(onClick, RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = content, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * A workout screen: black, with the workout's colour glowing softly from the top, scrolling if it must but filling
 * the height so the controls sit at the bottom.
 */
@Composable
private fun WorkoutPage(glow: Color, contentPadding: PaddingValues, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val width = constraints.maxWidth.toFloat()
        Box(
            Modifier.fillMaxWidth().height(420.dp).background(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent), center = Offset(width / 2f, 0f), radius = width * 0.9f),
            ),
        )
        // The live screen fits the screen instead (scroll = false): its controls never move.
        Column(
            Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).statusBarsPadding()
                .padding(contentPadding).padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, top = 8.dp, bottom = 16.dp)
                .then(if (scroll) Modifier.heightIn(min = maxHeight - 80.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** A dark pill: a dot and a label, optionally tappable with a caret. */
@Composable
private fun Pill(label: String, dot: Color?, onClick: (() -> Unit)? = null, caret: Boolean = false) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(Raised)
            .let { if (onClick != null) it.pressClickable(onClick, RoundedCornerShape(50)) else it }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Canvas(Modifier.size(8.dp)) { drawCircle(dot) }
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
        if (caret) {
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.CaretDown, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** The five zones side by side, the current one lit, with a white marker where [bpm] sits. */
@Composable
private fun ZoneBar(bpm: Int?, maxHr: Int, ring: Color, modifier: Modifier = Modifier, barHeight: Dp = 4.dp) {
    val zone = WorkoutRecorder.liveZone(bpm, maxHr)
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val gap = 4.dp.toPx()
        val h = barHeight.toPx()
        val w = (size.width - gap * (Workouts.ZONES - 1)) / Workouts.ZONES
        val top = (size.height - h) / 2
        val halo = 9.dp.toPx()
        val x = bpm?.let { (WorkoutRecorder.zonePosition(it, maxHr) * size.width).coerceIn(halo, size.width - halo) }
        // Like the Recovery and Charge gauges: bright up to the marker, faint after it, so the marker stands clear.
        for (z in 0 until Workouts.ZONES) {
            val left = z * (w + gap)
            drawRoundRect(ZONE_COLORS[z].copy(alpha = 0.25f), Offset(left, top), Size(w, h), CornerRadius(h / 2))
            if (x != null && x > left) clipRect(right = x) {
                drawRoundRect(ZONE_COLORS[z], Offset(left, top), Size(w, h), CornerRadius(h / 2))
            }
        }
        if (bpm != null && x != null) {
            // The gauges' marker: the colour in a soft halo of itself.
            val dot = zone?.let { ZONE_COLORS[it] } ?: ring
            drawCircle(dot.copy(alpha = 0.3f), radius = halo, center = Offset(x, size.height / 2))
            drawCircle(dot, radius = 5.dp.toPx(), center = Offset(x, size.height / 2))
        }
    }
}

/* ----------------------------------------- Activity tab entry ---------------------------------------- */

/** A sport someone has done, for the start card: how often and when last. */
data class SportUse(val sportType: Int, val count: Int, val lastStart: Long?)

/** The 3 most-used sports across both kinds of workout, else three sensible defaults. */
fun topSports(workouts: List<WorkoutAnalysis>, n: Int = 3): List<SportUse> {
    // The strap's own "Activity" records aren't a sport to offer.
    val used = workouts.filter { it.workout.sportType != Workouts.GENERIC_ACTIVITY }.groupBy { it.workout.sportType }
        .map { (type, ws) -> SportUse(type, ws.size, ws.maxOf { it.workout.start }) }
        .sortedWith(compareByDescending<SportUse> { it.count }.thenByDescending { it.lastStart })
        .take(n)
    return used + Workouts.DEFAULT_SPORTS.filter { d -> used.none { it.sportType == d } }.take(n - used.size).map { SportUse(it, 0, null) }
}

@Composable
private fun SportBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit) {
    // Tight corners and room underneath, so the press area never clips a label that fills the width.
    Column(modifier.pressClickable(onClick, RoundedCornerShape(8.dp)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                // The sport chosen now: a ring, not a fill, so it doesn't read as a button apart.
                .then(if (selected) Modifier.border(BorderStroke(2.dp, Color.White), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        // Two lines, so "Strength training" isn't cut short.
        Text(
            name, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Where an offer or a suggestion sits: a dotted panel, or on Today a strip lit in heart red from [edge]'s side, so the
 * two never look alike when both show.
 */
@Composable
private fun OfferBox(edge: EdgeKind?, content: @Composable ColumnScope.() -> Unit) {
    if (edge == null) DotPanel(content = content)
    else EdgeCard(MetricColors.heart, Modifier.fillMaxWidth(), kind = edge) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** "Looks like you worked out": a stretch nobody started, offered for saving. [edge] draws it as a Today strip. */
@Composable
internal fun SuggestionPanel(
    s: org.openstrap.app.data.WorkoutSuggestions.Suggestion, onSave: () -> Unit, onDismiss: () -> Unit, edge: EdgeKind? = null,
) {
    OfferBox(edge) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(AppIcons.Heartbeat, null, tint = ZONE_COLORS[s.zone], modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Looks like you worked out", color = Color.White, style = MaterialTheme.typography.titleSmall)
                val day = dayName(Instant.ofEpochSecond(s.start).atZone(ZoneId.systemDefault()).toLocalDate())
                Note("$day ${clock(s.start)}–${clock(s.end)}, mostly zone ${s.zone + 1}, ${s.averageHeartRate} bpm on average. Nobody pressed Start.")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { WorkoutButton("Save as workout", Color.White, Color.Black, onSave, height = 48.dp) }
            Box(Modifier.weight(1f)) { WorkoutButton("Not a workout", Raised, Color.White, onDismiss, height = 48.dp) }
        }
    }
}

/**
 * "Looks like a workout": an effort still under way that nobody started, offered for recording from its start.
 * [edge] draws it as a Today strip.
 */
@Composable
internal fun OfferPanel(o: org.openstrap.app.data.AutoStart.Offer, onRecord: () -> Unit, onDecline: () -> Unit, edge: EdgeKind? = null) {
    OfferBox(edge) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(SportIcons.of(o.sportType), null, tint = MetricColors.heart, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Looks like a workout", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Note("Your heart rate has been up since ${clock(o.start)}, ${o.averageHeartRate} bpm on average. Record a ${Workouts.sportName(o.sportType).lowercase()} from ${clock(o.start)}?")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { WorkoutButton("Record", MetricColors.heart, Color.Black, onRecord, height = 48.dp) }
            Box(Modifier.weight(1f)) { WorkoutButton("Not now", Raised, Color.White, onDecline, height = 48.dp) }
        }
    }
}

/**
 * Adds a workout nobody started, after the fact: the day's heart rate with two handles to drag to where it began
 * and ended (first set on the day's likeliest stretch), then the sport. Built from the strap's stored minutes.
 */
@Composable
fun GlowAddWorkoutScreen(
    date: LocalDate,
    minuteHr: List<Pair<Long, Int>>?,
    maxHr: Int,
    contentPadding: PaddingValues,
    onSave: (from: Long, to: Long, sportType: Int) -> Unit,
    onBack: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val series = minuteHr.orEmpty()
    val first = series.firstOrNull()?.first ?: 0L
    val last = (series.lastOrNull()?.first ?: 0L) + 60
    val likely = remember(series) { WorkoutSuggestions.find(series, 60, maxHr, emptyList()).firstOrNull() }
    var from by rememberSaveable(series.size) { mutableStateOf(likely?.start ?: (last - 1800).coerceAtLeast(first)) }
    var to by rememberSaveable(series.size) { mutableStateOf(likely?.end ?: last) }
    if (picking) {
        SportPicker(-1, contentPadding, onPick = { onSave(from, to, it) }, onBack = { picking = false })
        return
    }
    BackHandler(onBack = onBack)
    WorkoutPage(Color.Transparent, contentPadding) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowLeft, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Add a workout", color = Color.White, style = GlowType.headline.copy(fontSize = GlowType.headline.fontSize * 1.1f))
                Text(dayName(date), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (minuteHr == null) {
            DotPanel { Note("Loading…") }
            return@WorkoutPage
        }
        if (series.size < 2) {
            DotPanel { Note("The strap hasn't sent heart rate for this day yet. Sync, then try again.") }
            return@WorkoutPage
        }
        DotPanel {
            DotHeader("When was it?", trailing = "${clock(from)} – ${clock(to)}")
            RangeChart(series, first, last, from, to, maxHr) { f, t -> from = f; to = t }
            Row(Modifier.fillMaxWidth()) {
                Note(clock(first))
                Spacer(Modifier.weight(1f))
                Note(clock(last))
            }
            Note("Drag the handles to where the workout began and ended. Its heart rate comes from the strap's minute records.")
        }
        Spacer(Modifier.weight(1f))
        WorkoutButton("Choose the sport", Color.White, Color.Black, { picking = true })
    }
}

/** Room either side of a range chart, so its handles sit whole inside it. */
private val HandleInset = 14.dp
/** The trim frame's sides, the handles; the strip is inset by as much so either side can reach its end. */
private val TrimSide = 12.dp

/** A day of minute heart rate in its zones' colours, with two handles marking a range; outside it dims. */
@Composable
private fun RangeChart(series: List<Pair<Long, Int>>, first: Long, last: Long, from: Long, to: Long, maxHr: Int, onChange: (Long, Long) -> Unit) {
    val lo = (series.minOfOrNull { it.second } ?: 60) - 5f
    val hi = (series.maxOfOrNull { it.second } ?: 180) + 5f
    var f by remember { mutableStateOf(from) }
    var t by remember { mutableStateOf(to) }
    f = from
    t = to
    Canvas(
        Modifier.fillMaxWidth().height(120.dp).pointerInput(series) {
            var dragging = 0
            val inset = HandleInset.toPx()
            fun timeAt(x: Float) = (first + ((x - inset) / (size.width - 2 * inset) * (last - first)).toLong()).coerceIn(first, last)
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    val at = timeAt(o.x)
                    dragging = if (kotlin.math.abs(at - f) <= kotlin.math.abs(at - t)) 0 else 1
                },
            ) { change, _ ->
                val at = timeAt(change.position.x) / 60 * 60
                if (dragging == 0) f = at.coerceAtMost(t - 60) else t = at.coerceAtLeast(f + 60)
                onChange(f, t)
            }
        },
    ) {
        val inset = HandleInset.toPx()
        fun x(time: Long) = inset + (time - first).toFloat() / (last - first) * (size.width - 2 * inset)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        series.zipWithNext().forEach { (a, b) ->
            if (b.first - a.first > 300) return@forEach
            val inside = a.first in f until t
            val color = ZONE_COLORS[WorkoutRecorder.liveZone(a.second, maxHr)!!].copy(alpha = if (inside) 1f else 0.25f)
            drawLine(color, Offset(x(a.first), y(a.second.toFloat())), Offset(x(b.first), y(b.second.toFloat())), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        listOf(f, t).forEach { time ->
            val hx = x(time)
            drawLine(Color.White, Offset(hx, 0f), Offset(hx, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(hx, size.height / 2))
        }
    }
}

/** The sport list on its own, for saving a stretch nobody started. */
@Composable
fun GlowSportPickerScreen(contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit) =
    SportPicker(-1, contentPadding, onPick, onBack)

/* -------------------------------------------- Ready screen ------------------------------------------- */

/**
 * One screen, not a wizard: close, the sport (tap to change, searchable), the strap's state with live heart rate,
 * and Start. Recording starts even when the strap is away; the strap's history fills in after it syncs.
 */
@Composable
fun GlowWorkoutReadyScreen(
    sportType: Int,
    pickSport: Boolean,
    connection: ConnectionState,
    liveHeartRate: Int?,
    contentPadding: PaddingValues,
    onSport: (Int) -> Unit,
    onStart: (sportType: Int, askToEnd: Boolean, target: WorkoutTarget?, useGps: Boolean, keepRoute: Boolean, rest: Strength.Rest?, setsTarget: Int?) -> Unit,
    onBack: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(pickSport) }
    var askToEnd by rememberSaveable { mutableStateOf(true) }
    var targetCode by rememberSaveable { mutableStateOf<String?>(null) }
    var useGps by rememberSaveable { mutableStateOf(false) }
    var keepRoute by rememberSaveable { mutableStateOf(false) }
    val outdoor = sportType in Workouts.OUTDOOR
    val strength = sportType in Strength.SPORTS
    var setsTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var restSeconds by rememberSaveable { mutableStateOf(Strength.Rest.DEFAULT.seconds) }
    var restBelow by rememberSaveable { mutableStateOf<Int?>(null) }
    val target = WorkoutTarget.parse(targetCode)
    if (picking) {
        SportPicker(sportType, contentPadding, onPick = { onSport(it); picking = false }, onBack = { if (pickSport) onBack() else picking = false })
        return
    }
    BackHandler(onBack = onBack)
    // Start takes the target's colour, previewing the workout mode; plain white with none.
    val accent = targetColor(target)
    WorkoutPage(accent, contentPadding) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Raised).pressClickable(onBack, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.X, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Box(Modifier.align(Alignment.Center)) { Pill(Workouts.sportName(sportType), null, onClick = { picking = true }, caret = true) }
        }
        Column(Modifier.padding(top = 10.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ready when you are", color = Color.White, style = GlowType.headline.copy(fontSize = GlowType.headline.fontSize * 1.25f))
            Text("Recording starts when you tap Start.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
        DotPanel {
            val connected = connection as? ConnectionState.Connected
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Strap", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Note(
                        if (connected == null) "Not connected"
                        else listOfNotNull("Connected", connected.batteryPercent?.let { "$it%" }, liveHeartRate?.let { "$it bpm live" } ?: "waiting for heart rate").joinToString(" · "),
                    )
                }
                Canvas(Modifier.size(10.dp)) { drawCircle(if (connected != null) TrendGood else TrendBad) }
            }
            Note(
                if (connected == null) "You can start anyway. OpenStrap records once the strap reconnects, and fills the time before from the strap's own history after it syncs."
                else "Heart rate is recorded every second, with the screen off too. If the strap drops out, the missing minutes are filled from its own history after the next sync.",
            )
        }
        // GPS is offered for outdoor sports only, off by default; the route is discarded unless kept.
        if (outdoor) DotPanel {
            SwitchRow("Use GPS", if (useGps) "Only during this workout, for distance, pace and the map. Map tiles come from OpenFreeMap, which sees roughly where the map is; its Route only button loads none." else if (sportType in Workouts.ON_FOOT) "Off · distance from the strap's steps (≈)" else "Off · no distance without it", useGps) { useGps = it }
            if (useGps) {
                DotDivider()
                SwitchRow(
                    "Keep route on this phone",
                    if (keepRoute) "Saved on the phone for the map and export" else "Off · only totals are kept, the route is discarded",
                    keepRoute,
                ) { keepRoute = it }
            }
        }
        // Strength counts sets and rest; the strap buzzes when rest is over, so the phone can stay in a pocket.
        if (strength) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipLabel("Sets")
            Chips(listOf("Open") + listOf(3, 4, 5).map { "$it sets" }, listOf(null, 3, 4, 5).indexOf(setsTarget), Color.White) { setsTarget = listOf(null, 3, 4, 5)[it] }
            ChipLabel("Rest · the strap buzzes when it's over")
            Chips(Strength.Rest.TIMES.map { "${it} s" }, Strength.Rest.TIMES.indexOf(restSeconds), Color.White) { restSeconds = Strength.Rest.TIMES[it] }
            ChipLabel("Or sooner, once heart rate drops below")
            Chips(Strength.Rest.LEVELS.map { it?.let { b -> "$b bpm" } ?: "Off" }, Strength.Rest.LEVELS.indexOf(restBelow), Color.White) { restBelow = Strength.Rest.LEVELS[it] }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Target · the strap buzzes if you drift out", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<WorkoutTarget?>(null) + WorkoutTarget.presets).forEach { t ->
                    val on = t?.code == targetCode
                    val c = targetColor(t)
                    Text(
                        targetLabel(t), color = if (on) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.height(44.dp).clip(RoundedCornerShape(16.dp)).background(if (on) c else Raised)
                            .pressClickable({ targetCode = t?.code }, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp).wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
        DotPanel(onClick = { askToEnd = !askToEnd }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask to end when I stop", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Note("After 8 min near resting heart rate and no steps, the strap buzzes and asks.")
                }
                Spacer(Modifier.width(12.dp))
                GlowSwitch(askToEnd, { askToEnd = it })
            }
        }
        Spacer(Modifier.weight(1f))
        WorkoutButton("Start", accent, Color.Black, {
            onStart(
                sportType, askToEnd, target, outdoor && useGps, outdoor && useGps && keepRoute,
                Strength.Rest(restSeconds, restBelow).takeIf { strength }, setsTarget.takeIf { strength },
            )
        }, icon = AppIcons.PlayFill)
    }
}

@Composable
private fun ChipLabel(text: String) =
    Text(text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))

/** A scrolling row of choices; the chosen one lit in [color]. */
@Composable
private fun Chips(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label, color = if (on) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.height(44.dp).clip(RoundedCornerShape(16.dp)).background(if (on) color else Raised)
                    .pressClickable({ onSelect(i) }, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressClickable({ onChange(!checked) }, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Note(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        GlowSwitch(checked, onChange)
    }
}

@Composable
private fun SportPicker(selected: Int, contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    // The sections, each with the sports matching the search; empty sections drop out.
    val shown = remember(query) {
        Workouts.SECTIONS.map { (title, types) -> title to types.filter { Workouts.sportName(it).contains(query.trim(), ignoreCase = true) } }
            .filter { it.second.isNotEmpty() }
    }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { GlowDetailHeader("Choose a sport", null, onBack) }
        item {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search") },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.4f), unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                ),
            )
        }
        if (shown.isEmpty()) item { DotPanel { Note("No sport matches \"$query\".") } }
        // Each section a grid of the same round buttons as Start a workout, four to a row.
        shown.forEach { (title, types) ->
            item(key = title) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    types.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            row.forEach { type ->
                                SportBubble(SportIcons.of(type), Workouts.sportName(type), Modifier.weight(1f), selected = type == selected) { onPick(type) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/* -------------------------------------------- Live screen -------------------------------------------- */

/**
 * The workout as it records, in the current zone's colour. The card beats at the live heart rate with the last ten
 * minutes behind the number; the zone, its range and the time spent in it sit under a zone bar. Back tucks it away;
 * ending takes a one-second hold on the square so a pocket can't end it. With [split] the heart card is on the other
 * half of a folded phone ([GlowWorkoutGlance]), so this half keeps the figures and the controls.
 */
@Composable
fun GlowWorkoutLiveScreen(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    stats: WorkoutRecorder.Stats?,
    zoneSeconds: IntArray,
    connection: ConnectionState,
    contentPadding: PaddingValues,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit,
    onMinimize: () -> Unit,
    onEndAtPrompt: () -> Unit,
    onKeepGoing: () -> Unit,
    route: List<Route.Point> = emptyList(),
    history: List<Pair<Long, Int>> = emptyList(),
    onStartSet: () -> Unit = {},
    onSetDone: () -> Unit = {},
    onExtendRest: () -> Unit = {},
    onLap: () -> Unit = {},
    split: Boolean = false,
) {
    BackHandler(onBack = onMinimize)
    val pager = androidx.compose.foundation.pager.rememberPagerState { 3 }
    // The map and splits pages take the heart card's height, so swiping never moves what's below.
    var cardHeight by remember { mutableStateOf(360.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var routeOnly by rememberSaveable { mutableStateOf(false) }
    val connected = connection is ConnectionState.Connected
    val color = workoutColor(r, liveHeartRate, connected)
    val active = activeSeconds(r)
    WorkoutPage(color, contentPadding, scroll = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize) { Icon(AppIcons.CaretDown, "Minimize", tint = Color.White) }
            Pill(Workouts.sportName(r.workout.sportType), color)
            Spacer(Modifier.weight(1f))
            Text(
                when { r.paused -> "Paused"; connected -> "Strap"; else -> "Reconnecting" },
                color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(6.dp)) { drawCircle(if (connected) TrendGood else TrendBad) }
        }
        val heartCard: @Composable () -> Unit = {
            WorkoutHeartCard(
                r, liveHeartRate, recent, zoneSeconds, connected, active,
                Modifier.fillMaxWidth().onSizeChanged { cardHeight = with(density) { it.height.toDp() } },
            )
        }
        // GPS workouts have three pages, as on the design: heart rate, the map, and the kilometre splits. They swipe
        // as well as tap; a drag that starts on the map pans the map. Split across a fold, the card has the other half.
        if (split) Unit
        else if (r.workout.useGps) {
            GlowSegments(listOf("Heart", "Map", "Splits"), pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
            androidx.compose.foundation.pager.HorizontalPager(
                pager, pageSpacing = 16.dp, verticalAlignment = Alignment.Top, beyondViewportPageCount = 2,
            ) { page ->
                Column(Modifier.fillMaxWidth()) {
                    when (page) {
                        1 -> LiveMapPage(route, history, r.maxHeartRate, routeOnly, Modifier.height(cardHeight)) { routeOnly = it }
                        2 -> SplitsPage(route, history, r.workout.pauseRanges, Modifier.height(cardHeight))
                        else -> heartCard()
                    }
                }
            }
        } else heartCard()
        if (r.strength) SetsPanel(r, color, onStartSet, onSetDone, onExtendRest)
        // Centred between the card and the buttons: the moving time large in the middle, two smaller figures either
        // side (Oura's avg and max around the live number), labels under the numbers and thin rules between (WHOOP).
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Bottom) {
            val distance = r.workout.distanceMeters
            // With a distance it and the pace (GPS) take the sides; from steps, the distance and the average.
            // "1.25" alone could be anything: the label carries the unit from a kilometre on.
            val unit = if (distance != null && distance >= 1000) " km" else ""
            when {
                distance == null -> LiveStat("Avg", Modifier.weight(1f)) { LiveValue(stats?.average?.toString() ?: "—") }
                else -> LiveStat((if (r.workout.distanceFromSteps) "Distance ≈" else "Distance") + unit, Modifier.weight(1f)) { LiveValue(km(distance)) }
            }
            StatRule()
            LiveStat("Moving time", Modifier.weight(1.5f)) {
                // Plain digits: the heart rate above is the one dot-matrix number on the screen.
                Text(workoutClock(active), color = Color.White, style = GlowType.headline.copy(fontSize = 46.sp, lineHeight = 50.sp, fontFeatureSettings = "tnum"))
            }
            StatRule()
            when {
                distance != null && !r.workout.distanceFromSteps -> LiveStat("Pace /km", Modifier.weight(1f)) { LiveValue(Route.formatPace(Route.currentPace(route))) }
                distance != null -> LiveStat("Avg", Modifier.weight(1f)) { LiveValue(stats?.average?.toString() ?: "—") }
                else -> LiveStat("Max", Modifier.weight(1f)) { LiveValue(stats?.max?.toString() ?: "—") }
            }
        }
        // Once Lap has been pressed: the lap under way and its own clock, so a press shows on screen too.
        r.workout.lapMarks.lastOrNull()?.let { mark ->
            val lap = (active - WorkoutRecorder.activeSeconds(r.workout, mark)).coerceAtLeast(0)
            Text(
                "Lap ${r.workout.lapMarks.size + 1} · ${workoutClock(lap)}",
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (!connected && !r.paused) {
            Note("The strap isn't connected. Recording carries on, and the minutes missed are filled from its own history after it syncs.")
        }
        r.endPrompt?.let { at -> StoppedPrompt(r, at, onEndAtPrompt, onKeepGoing) }
        Spacer(Modifier.weight(1f))
        // At most a thumb's reach wide, so on a wide screen (an unfolded or half-folded phone) Lap and End stay by Pause.
        Row(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Lap (not for strength, which has sets), keeping Pause centred either way.
            val haptics = LocalHapticFeedback.current
            if (!r.strength && !r.paused) FlatButton(Raised, 64.dp, onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onLap() }) {
                Icon(SportIcons.Lap, "Lap", tint = Color.White, modifier = Modifier.size(22.dp))
            } else Spacer(Modifier.size(64.dp))
            Spacer(Modifier.weight(1f))
            FlatButton(Color.White, 88.dp, onClick = if (r.paused) onResume else onPause) {
                Icon(if (r.paused) AppIcons.PlayFill else AppIcons.PauseFill, if (r.paused) "Resume" else "Pause", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.weight(1f))
            HoldToEnd(onEnd)
        }
        Text(
            "Hold the square to end", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // A little room under the controls, so they sit up off the screen's edge.
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The live heart card: the beating number over the last ten minutes, then the zone, its range, the zone bar and the
 * time in zone. With [fill] it takes all the height it's given and the number grows, for the standing half of a fold.
 */
@Composable
private fun WorkoutHeartCard(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    zoneSeconds: IntArray,
    connected: Boolean,
    active: Long,
    modifier: Modifier,
    fill: Boolean = false,
) {
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate)
    val color = workoutColor(r, liveHeartRate, connected)
    GlowCard(GlowStyle.chart(color).copy(motion = if (bpm != null) GlowMotion.Heartbeat(bpm) else GlowMotion.Still), modifier) {
        Column(if (fill) Modifier.fillMaxSize() else Modifier) {
            GlowHeader("Heart rate", trailing = bpm?.let { "${it * 100 / r.maxHeartRate}% of max" })
            Box(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier.height(200.dp))) {
                val now = recent.lastOrNull()?.first ?: (System.currentTimeMillis() / 1000)
                val values = remember(recent) { bucketRecent(recent, now) }
                if (values.any { it != null }) {
                    GlowLineChart(values, Modifier.align(Alignment.BottomCenter).alpha(0.45f), height = 120.dp, showAverage = false)
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotMatrixText(bpm?.toString() ?: "--", dot = if (fill) 12.dp else 9.dp, gap = if (fill) 4.dp else 3.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when { r.paused -> "paused"; bpm == null && !connected -> "waiting for the strap"; else -> "bpm · last 10 min behind" },
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(zone?.let { "Zone ${it + 1} · ${ZONE_NAMES[it]}" } ?: "No zone yet", color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
                zone?.let { Text(zoneRange(it, r.maxHeartRate), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(10.dp))
            ZoneBar(bpm, r.maxHeartRate, ring = Color.Black.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Row {
                zone?.let { Note("${workoutClock(zoneSeconds[it].toLong())} in zone") }
                Spacer(Modifier.weight(1f))
                when (val t = WorkoutTarget.parse(r.workout.target)) {
                    is WorkoutTarget.Zones -> Note("Target ${t.label.removePrefix("Zone ")} · strap buzzes if you drift")
                    is WorkoutTarget.Time -> Note("${workoutClock(active)} of ${t.label}")
                    null -> Unit
                }
            }
        }
    }
}

/**
 * The standing half of a foldable half-open during a workout (on a table, or stood up like a book): the live heart
 * card as big as the half allows, readable from a step away. The controls stay on the other half, under your hand.
 */
@Composable
fun GlowWorkoutGlance(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    recent: List<Pair<Long, Int>>,
    zoneSeconds: IntArray,
    connection: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val connected = connection is ConnectionState.Connected
    Box(modifier.background(Color.Black).statusBarsPadding().padding(horizontal = GlowLayout.Gutter, vertical = 12.dp)) {
        WorkoutHeartCard(r, liveHeartRate, recent, zoneSeconds, connected, activeSeconds(r), Modifier.fillMaxSize(), fill = true)
    }
}

/**
 * Strength: the sets as dots against the target, and the one control that matters now: "Set done" during a set;
 * during rest a countdown with "Start set N" and "+30 s"; otherwise "Start set N".
 */
@Composable
private fun SetsPanel(r: WorkoutState.Recording, color: Color, onStartSet: () -> Unit, onSetDone: () -> Unit, onExtend: () -> Unit) {
    val now by produceState(System.currentTimeMillis() / 1000) { while (true) { value = System.currentTimeMillis() / 1000; delay(1000 - System.currentTimeMillis() % 1000) } }
    val done = r.sets.count { it.end != null }
    val target = r.workout.setsTarget
    DotPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                DotHeader(
                    when {
                        r.inSet -> "Set ${r.sets.size} · ${workoutClock(now - r.sets.last().start)}"
                        r.resting -> "Resting after set $done · ${workoutClock((r.restUntil!! - now).coerceAtLeast(0))}"
                        done == 0 -> "Ready for set 1"
                        else -> "Rest over · set ${done + 1} next"
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(maxOf(target ?: 0, done + if (r.inSet) 1 else 0)) { i ->
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < done) color else if (i == done && r.inSet) color.copy(alpha = 0.5f) else Raised))
                }
            }
        }
        r.restUntil?.let { until ->
            val start = r.sets.lastOrNull()?.end ?: until
            GlowBar(((now - start).toFloat() / (until - start).coerceAtLeast(1)).coerceIn(0f, 1f), color)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (r.inSet) Box(Modifier.weight(1f)) { WorkoutButton("Set done", Color.White, Color.Black, onSetDone, height = 52.dp) }
            else Box(Modifier.weight(1f)) { WorkoutButton("Start set ${r.sets.size + 1}", Color.White, Color.Black, onStartSet, height = 52.dp) }
            if (r.resting) Box(Modifier.width(96.dp)) { WorkoutButton("+30 s", Raised, Color.White, onExtend, height = 52.dp) }
        }
        if (target != null && done >= target && !r.inSet) Note("All $target sets done. Hold the square to end, or keep going.")
    }
}

/** "5.21" kilometres, or "820 m" under one. */
internal fun km(meters: Float): String = if (meters < 1000) "${meters.toInt()} m" else String.format(java.util.Locale.ROOT, "%.2f", meters / 1000)

/** The live route in zone colours, the GPS accuracy, and "route only" (no tiles), with what loading tiles reveals. */
@Composable
private fun LiveMapPage(route: List<Route.Point>, history: List<Pair<Long, Int>>, maxHr: Int, routeOnly: Boolean, modifier: Modifier, onRouteOnly: (Boolean) -> Unit) {
    val stretches = remember(route.size, history.size) { Route.byZone(route, history, maxHr) }
    // The whole page is the map; what its tiles reveal is said on the ready screen, by the GPS switch.
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(48.dp)).background(Raised)) {
        // Where you are now, from the latest fix good enough to count, even while standing still.
        val here = route.lastOrNull { it.accuracy <= Route.MAX_ACCURACY_M }
        WorkoutMap(stretches, routeOnly, Modifier.fillMaxSize(), here)
        Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val acc = route.lastOrNull()?.accuracy
            Pill(if (acc == null || acc > 1000) "Finding GPS…" else "GPS ±${acc.toInt()} m", if (acc != null && acc <= Route.MAX_ACCURACY_M) TrendGood else TrendBad)
            Pill(if (routeOnly) "Route only" else "Map", null, onClick = { onRouteOnly(!routeOnly) })
        }
    }
}

/** Each kilometre's time, pace and average heart rate; the last one may be partial. */
@Composable
private fun SplitsPage(route: List<Route.Point>, history: List<Pair<Long, Int>>, pauses: List<LongRange>, modifier: Modifier) {
    val splits = remember(route.size) { Route.splits(route, pauses) }
    DotPanel(modifier) {
        DotHeader("Splits", trailing = "per km")
        if (splits.isEmpty()) Note("The first split shows once GPS has a fix and you've moved.")
        // A long run's splits scroll inside the panel; the screen itself doesn't.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var startT = route.firstOrNull()?.t ?: 0L
        splits.forEach { sp ->
            val hr = history.filter { it.first in startT..sp.endT }.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.toInt()
            startT = sp.endT
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (sp.meters >= 999) "Km ${sp.index + 1}" else "${km(sp.meters.toFloat())}", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(Route.formatPace(sp.pace), color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
                Text(hr?.let { "$it bpm" } ?: "—", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
            }
        }
        }
    }
}

@Composable
private fun LiveStat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
    }
}

/** The thin rule between the live figures. */
@Composable
private fun StatRule() = Box(Modifier.padding(vertical = 6.dp).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))

/** Avg and Max in the same face and size as the moving time beside them. */
@Composable
private fun LiveValue(text: String) =
    Text(text, color = Color.White, style = GlowType.headline.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"))

private const val BUCKET_S = 5L
private const val WINDOW_S = 600L

/** The last ten minutes in five-second averages, oldest first; gaps stay null and break the line. */
private fun bucketRecent(recent: List<Pair<Long, Int>>, now: Long): List<Float?> {
    val n = (WINDOW_S / BUCKET_S).toInt()
    val sums = FloatArray(n)
    val counts = IntArray(n)
    val from = now - WINDOW_S + 1
    recent.forEach { (t, bpm) ->
        val i = ((t - from) / BUCKET_S).toInt()
        if (i in 0 until n) { sums[i] += bpm.toFloat(); counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/**
 * A round button in the glow language: a small glow card lit from its rim in [color], its icon on the dark core.
 * [onClick] null leaves the touch handling to [modifier] (the end square's hold).
 */
@Composable
private fun FlatButton(container: Color, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    // Flat, like the app's Start and Done: the live screen's card already carries the glow.
    Box(
        modifier.size(size).clip(CircleShape).background(container)
            .then(if (onClick != null) Modifier.pressClickable(onClick, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "Looks like you've stopped": end where the wearer stopped, or keep going. The strap has buzzed already. */
@Composable
private fun StoppedPrompt(r: WorkoutState.Recording, at: Long, onEnd: () -> Unit, onKeep: () -> Unit) {
    DotPanel {
        DotHeader(if (r.inCar) "Looks like you're in a car" else "Looks like you've stopped")
        Note(
            if (r.inCar) "You last moved at ${clock(at)}. End the ${Workouts.sportName(r.workout.sportType).lowercase()} there, so the drive isn't counted?"
            else "Your heart rate has been back near resting since ${clock(at)}. End the ${Workouts.sportName(r.workout.sportType).lowercase()} at ${clock(at)}?",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { WorkoutButton("End at ${clock(at)}", Color.White, Color.Black, onEnd) }
            Box(Modifier.weight(1f)) { WorkoutButton("Keep going", Raised, Color.White, onKeep) }
        }
    }
}

/** The end square needs a one-second hold: a ring fills around it, and letting go early cancels it. */
@Composable
private fun HoldToEnd(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    Box(contentAlignment = Alignment.Center) {
        FlatButton(
            Raised, 64.dp,
            Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        val hold = launch {
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnd()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            hold.cancel()
                            progress.animateTo(0f, tween(200))
                        }
                    }
                })
            },
        ) {
            Icon(AppIcons.StopFill, "Hold to end the workout", tint = EndRed, modifier = Modifier.size(20.dp))
        }
        Canvas(Modifier.size(64.dp).padding(2.dp)) {
            if (progress.value > 0f) drawArc(EndRed, -90f, 360f * progress.value, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

private val EndRed = MetricColors.heart
private const val HOLD_MS = 1000

/* ------------------------------------- Rest of the app meanwhile ------------------------------------- */

/**
 * The workout tucked away: a bar above the tab bar ringed in the zone's colour, with a dot pulsing at the heart
 * rate, the sport and time, the zone, the bpm and Pause. Tap it to bring the live screen back.
 */
@Composable
fun WorkoutCapsule(
    r: WorkoutState.Recording,
    liveHeartRate: Int?,
    connected: Boolean,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate)
    val color = workoutColor(r, liveHeartRate, connected)
    val active = activeSeconds(r)
    val shape = RoundedCornerShape(50)
    Row(
        modifier.fillMaxWidth().height(64.dp).shadow(24.dp, shape, ambientColor = color, spotColor = color)
            .clip(shape).background(Raised).border(BorderStroke(1.5.dp, color), shape)
            .pressClickable(onClick, shape).padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(color, bpm, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${Workouts.sportName(r.workout.sportType)} · ${workoutClock(active)}", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                when {
                    r.endPrompt != null -> "Stopped? Tap to answer"
                    r.paused -> "Paused"
                    zone != null -> "Zone ${zone + 1} · ${ZONE_NAMES[zone]}"
                    connected -> "Waiting for heart rate"
                    else -> "Strap reconnecting"
                },
                color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
        if (bpm != null) {
            DotMatrixText("$bpm", dot = 3.dp, gap = 1.dp, color = color)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(color).pressClickable(if (r.paused) onResume else onPause, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (r.paused) AppIcons.PlayFill else AppIcons.PauseFill, if (r.paused) "Resume" else "Pause", tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

/** A dot in [color] that beats at [bpm]; still without one. */
@Composable
fun PulseDot(color: Color, bpm: Int?, size: Dp) {
    val beat = if (bpm != null) {
        val period = (60_000 / bpm).coerceIn(250, 2000)
        rememberInfiniteTransition(label = "beat").animateFloat(
            1f, 1f,
            infiniteRepeatable(keyframes { durationMillis = period; 1.5f at period * 15 / 100; 1f at period * 40 / 100 }, RepeatMode.Restart),
            label = "dot",
        ).value
    } else 1f
    Canvas(Modifier.size(size).scale(beat)) { drawCircle(color) }
}

/* ---------------------------------------------- Summary ---------------------------------------------- */

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * A workout recorded on the phone: "Walk saved" right after it ends, its sport when opened later. Time glows in the
 * calm readiness teal, each figure compared with your usual for similar workouts; the heart rate is drawn in its
 * zones' colours (minutes from the strap's history dashed), and time in each zone carries a "your usual" mark. What
 * the app did to the end says so, with undo, and the start and end can be trimmed.
 */
@Composable
fun GlowPhoneWorkoutScreen(
    summary: PhoneSummary?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTrim: (from: Long, to: Long) -> Unit,
    onUndoTrim: () -> Unit,
    justEnded: Boolean,
    onExportGpx: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var trimming by rememberSaveable { mutableStateOf(false) }
    val a = summary?.analysis
    WorkoutPage(Color.Transparent, contentPadding) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!justEnded) IconButton(onClick = onBack) { Icon(AppIcons.ArrowLeft, "Back", tint = Color.White) }
            Text(
                a?.let { if (justEnded) "${it.sportName} saved" else it.sportName } ?: "Workout",
                color = Color.White, style = GlowType.headline.copy(fontSize = GlowType.headline.fontSize * 1.1f),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            a?.let { Text("${clock(it.shownStart)} – ${clock(it.end)}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) }
        }
        if (summary == null || a == null) {
            DotPanel { Note("Loading…") }
            return@WorkoutPage
        }
        val w = a.workout
        val u = summary.usual
        val active = w.exerciseSeconds?.toLong() ?: a.durationSeconds
        GlowCard(GlowStyle.chart(MetricColors.readiness), Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    DotMatrixText(workoutClock(active), dot = 6.dp, gap = 2.dp)
                    summary.distanceMeters?.let { d ->
                        Spacer(Modifier.width(12.dp))
                        Text(
                            (if (summary.distanceFromSteps) "≈ " else "") + km(d) + if (d >= 1000) " km" else "",
                            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    CompareFact("Avg HR", w.averageHeartRate?.toString() ?: "—", delta(w.averageHeartRate, u?.averageHeartRate, " bpm"), Modifier.weight(1f))
                    CompareFact("Max HR", w.maxHeartRate?.toString() ?: "—", delta(w.maxHeartRate, u?.maxHeartRate, ""), Modifier.weight(1f))
                    CompareFact("Time", workoutClock(active), u?.let { minutesDelta(active - it.activeSeconds) }, Modifier.weight(1f))
                    CompareFact("Paused", formatShort((a.durationSeconds - active).coerceAtLeast(0)), null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (u != null) "Compared with your last ${if (u.count == 1) "" else "${u.count} "}${a.sportName.lowercase()}${if (u.count == 1) "" else "s"} of similar length"
                    else "Comparisons start with your next ${a.sportName.lowercase()} of similar length",
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (w.trainingLoad != null) DotPanel {
            DotHeader("Training effect", trailing = "Worked out by OpenStrap")
            EffectFacts(w, w.vo2max?.toFloat(), a.recoveryMinutes)
            Note(
                "From your heart rate, on the strap's 0–5 scale; EPOC (ml/kg) as the strap reports it, not daily load. " +
                    if (w.sportType == org.openstrap.app.data.WorkoutEffect.OUTDOOR_RUN) "VO₂ max needs 10 minutes of steady running with GPS."
                    else "VO₂ max is worked out for outdoor runs only.",
            )
        }
        summary.strapWorkout?.let { s ->
            DotPanel {
                DotHeader("From the strap", trailing = "Its own record")
                EffectFacts(s.workout, s.effect?.vo2max ?: s.workout.vo2max?.toFloat(), s.recoveryMinutes)
                Note("The strap recorded this workout too; training effect and recovery are its own figures.")
            }
        }
        if (summary.laps.size > 1) DotPanel {
            DotHeader("Laps", trailing = "${summary.laps.size}")
            summary.laps.forEach { lap ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lap ${lap.index + 1}", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(workoutClock(lap.end - lap.start), color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                    lap.meters?.let { Text(km(it.toFloat()) + if (it >= 1000) " km" else "", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp)) }
                    Text(lap.averageHeartRate?.let { "$it bpm" } ?: "—", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
                }
            }
        }
        if (summary.sets.isNotEmpty()) DotPanel {
            val done = summary.sets.filter { it.end != null }
            DotHeader("Sets", trailing = "${done.size} done")
            done.forEach { set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${set.index + 1}", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(workoutClock(set.end!! - set.start), color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                    Note(set.restEnd?.let { "rest ${workoutClock(it - set.end)}" } ?: "")
                }
            }
        }
        if (summary.route.size >= 2) {
            val stretches = remember(summary) { Route.byZone(summary.route, summary.history, a.maxHeartRate) }
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(28.dp)).background(Raised)) {
                WorkoutMap(stretches, routeOnly = false, Modifier.fillMaxSize())
                Text(
                    "Start and end hidden when you share", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).background(Color.Black, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        DotPanel {
            DotHeader("Heart rate", trailing = w.maxHeartRate?.let { "max $it" })
            if (a.heartRate.isEmpty()) {
                Note("No heart rate reached the phone. Once the strap syncs, its own history fills in what it recorded.")
            } else {
                ZoneLine(a)
                Note(
                    if (a.historyRanges.isEmpty()) "Coloured by zone."
                    else "Dashed: " + a.historyRanges.joinToString(", ") { "${clock(it.first)}–${clock(it.last + 1)}" } + " came from the strap's history.",
                )
            }
        }
        val zones = remember(a) { WorkoutRecorder.liveZoneSeconds(a.heartRate.filter { it.timestamp <= a.end }.map { it.timestamp to it.bpm }, a.maxHeartRate) }
        if (zones.sum() > 0) DotPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { DotHeader("Time in zones") }
                if (u != null) {
                    Box(Modifier.size(width = 2.dp, height = 12.dp).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Note("your usual")
                }
            }
            val total = zones.sum().coerceAtLeast(1)
            for (z in 0 until Workouts.ZONES) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Z${z + 1}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(28.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        GlowBar(zones[z].toFloat() / total, ZONE_COLORS[z], Modifier.fillMaxWidth())
                        u?.let { usual ->
                            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                                val x = usual.zoneShares[z].coerceIn(0f, 1f) * size.width
                                drawRect(Color.White, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                            }
                        }
                    }
                    Text(workoutClock(zones[z].toLong()), color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp).padding(start = 10.dp))
                }
            }
        }
        // What the app did, with undo: an early end it proposed, or a trim.
        a.recordedEnd?.let { recorded ->
            val cut = recorded - a.end
            val left = if (cut < 60) "$cut s" else "${cut / 60} min"
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (summary.endReason == AppWorkout.END_CAR && a.shownStart == w.start) "Ended at ${clock(a.end)}. $left of driving were left out."
                    else if (summary.endReason == AppWorkout.END_AUTO && a.shownStart == w.start) "Ended at ${clock(a.end)}, when you'd stopped. $left after that were left out."
                    else "Trimmed to ${clock(a.shownStart)}–${clock(a.end)}. The rest is kept.",
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                Text(
                    "Undo", color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressClickable(onUndoTrim, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } ?: if (a.shownStart != w.start) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Trimmed to start at ${clock(a.shownStart)}. The rest is kept.", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    "Undo", color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50)).pressClickable(onUndoTrim, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        } else Unit
        if (trimming) {
            TrimPanel(summary, onSave = { from, to -> onTrim(from, to); trimming = false }, onCancel = { trimming = false })
        } else {
            Row(
                Modifier.fillMaxWidth().glass(GlassRowShape, { trimming = true }).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Trim start and end", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(AppIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.weight(1f))
        val canExport = Route.privacyTrim(summary.route).isNotEmpty()
        if (justEnded || canExport) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canExport) Box(Modifier.weight(1f)) { WorkoutButton("Export GPX", Raised, Color.White, onExportGpx) }
            if (justEnded) Box(Modifier.weight(1f)) { WorkoutButton("Done", Color.White, Color.Black, onBack) }
        }
        Text(
            "Delete workout", color = TrendBad, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                .pressClickable({ confirmDelete = true }, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
    if (confirmDelete) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this workout?") },
            text = { Text("Its heart rate and times are removed from OpenStrap. The strap's own history is kept.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A figure with how it compares with your usual underneath ("−4 bpm"); nothing under it without a usual. */
@Composable
private fun CompareFact(label: String, value: String, delta: String?, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
        if (delta != null) Text(delta, color = MetricColors.readiness, style = MaterialTheme.typography.labelSmall)
    }
}

private fun delta(value: Int?, usual: Int?, unit: String): String? {
    if (value == null || usual == null) return null
    val d = value - usual
    return when { d > 0 -> "+$d$unit"; d < 0 -> "−${-d}$unit"; else -> "as usual" }
}

private fun minutesDelta(seconds: Long): String {
    val m = Math.round(seconds / 60.0)
    return when { m > 0 -> "+${m}m"; m < 0 -> "−${-m}m"; else -> "as usual" }
}

/**
 * Moves the start and end on the whole recording, as WHOOP allows: drag either handle along the heart rate; what
 * falls outside dims. Saving changes only what is shown; nothing recorded is deleted.
 */
@Composable
private fun TrimPanel(summary: PhoneSummary, onSave: (Long, Long) -> Unit, onCancel: () -> Unit) {
    val a = summary.analysis
    val first = summary.recordedStart
    val last = summary.recordedEnd.coerceAtLeast(first + 60)
    var from by remember(summary) { mutableStateOf(a.shownStart) }
    var to by remember(summary) { mutableStateOf(a.end) }
    val step = 10L
    val points = remember(summary) {
        val n = ((last - first) / step + 1).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        summary.recorded.forEach { s ->
            val i = ((s.timestamp - first) / step).toInt()
            if (i in 0 until n) { sums[i] += s.bpm.toFloat(); counts[i]++ }
        }
        List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }
    DotPanel {
        DotHeader("Trim", trailing = "${clock(from)} – ${clock(to)} · ${workoutClock(to - from)}")
        val known = points.filterNotNull()
        val lo = (known.minOrNull() ?: 60f) - 10f
        val hi = (known.maxOrNull() ?: 180f) + 5f
        // A video trimmer: the workout as columns of dots in their zone colours, inside one rounded frame whose
        // thicker sides are the handles (Snapchat's, Polarsteps'); what's left out fades back. Drag either side in.
        Canvas(
            Modifier.fillMaxWidth().height(64.dp).pointerInput(summary) {
                var dragging = 0
                val inset = TrimSide.toPx()
                fun timeAt(x: Float) = (first + ((x - inset) / (size.width - 2 * inset) * (last - first)).toLong()).coerceIn(first, last)
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val t = timeAt(o.x)
                        dragging = if (kotlin.math.abs(t - from) <= kotlin.math.abs(t - to)) 0 else 1
                    },
                ) { change, _ ->
                    val t = timeAt(change.position.x)
                    if (dragging == 0) from = t.coerceAtMost(to - 60) else to = t.coerceAtLeast(from + 60)
                }
            },
        ) {
            val inset = TrimSide.toPx()
            val w = size.width - 2 * inset
            val h = size.height
            val edge = 3.dp.toPx()
            fun x(t: Long) = inset + (t - first).toFloat() / (last - first) * w
            drawRoundRect(Raised, Offset(inset, 0f), Size(w, h), CornerRadius(8.dp.toPx()))
            // Dot columns, as tall as the heart rate in that stretch; unlit dots stay faintly visible, like DotMatrixText.
            val pitch = 5.dp.toPx()
            val dot = 1.7.dp.toPx()
            val pad = edge + 5.dp.toPx()
            val rows = ((h - 2 * pad) / pitch).toInt().coerceAtLeast(1)
            val cols = (w / pitch).toInt().coerceAtLeast(1)
            val fx = x(from)
            val tx = x(to)
            for (c in 0 until cols) {
                val cx = inset + (c + 0.5f) * (w / cols)
                val i = ((c + 0.5f) / cols * points.size).toInt().coerceIn(0, points.size - 1)
                val v = points[i]
                val lit = v?.let { (((it - lo) / (hi - lo)).coerceIn(0.1f, 1f) * rows).roundToInt() } ?: 0
                val zone = v?.let { WorkoutRecorder.liveZone(it.toInt(), a.maxHeartRate) }
                val inside = cx in fx..tx
                for (r in 0 until rows) {
                    val cy = h - pad - (r + 0.5f) * pitch
                    val color = if (r < lit && zone != null) ZONE_COLORS[zone] else Color.White.copy(alpha = 0.08f)
                    drawCircle(if (inside) color else color.copy(alpha = color.alpha * 0.3f), dot, Offset(cx, cy))
                }
            }
            // The frame: one rounded outline, its sides thick enough to grab, each with a short grip.
            val frame = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRoundRect(androidx.compose.ui.geometry.RoundRect(fx - inset, 0f, tx + inset, h, CornerRadius(10.dp.toPx())))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(fx, edge, tx, h - edge, CornerRadius(4.dp.toPx())))
            }
            drawPath(frame, Color.White)
            val grip = 2.dp.toPx()
            listOf(fx - inset / 2, tx + inset / 2).forEach { gx ->
                drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(gx - grip / 2, h / 2 - 8.dp.toPx()), Size(grip, 16.dp.toPx()), CornerRadius(grip / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Note(clock(first))
            Spacer(Modifier.weight(1f))
            Note(clock(last))
        }
        Note("Drag the handles to where the workout really started and ended. Nothing recorded is deleted.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { WorkoutButton("Cancel", Raised, Color.White, onCancel) }
            Box(Modifier.weight(1f)) { WorkoutButton("Save", Color.White, Color.Black, { onSave(from, to) }) }
        }
    }
}

private fun formatShort(seconds: Long) = if (seconds < 60) "${seconds}s" else org.openstrap.app.ui.components.formatDuration(seconds)

/** Heart rate in ten-second steps, each stretch in its zone's colour; minutes from the strap's history dashed. */
@Composable
private fun ZoneLine(a: WorkoutAnalysis) {
    val step = 10L
    val first = a.shownStart
    val points = remember(a) {
        val n = ((a.end - first) / step + 1).toInt().coerceAtLeast(2)
        val sums = FloatArray(n)
        val counts = IntArray(n)
        a.heartRate.forEach { s ->
            val i = ((s.timestamp - first) / step).toInt()
            if (i in 0 until n) { sums[i] += s.bpm.toFloat(); counts[i]++ }
        }
        List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
    }
    val known = points.filterNotNull()
    val lo = (known.minOrNull() ?: 60f) - 5f
    val hi = (known.maxOrNull() ?: 180f) + 5f
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val dx = size.width / (points.size - 1).coerceAtLeast(1)
        fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        for (i in 0 until points.size - 1) {
            val v0 = points[i] ?: continue
            val v1 = points[i + 1] ?: continue
            val t = first + i * step
            val zone = WorkoutRecorder.liveZone(v0.toInt(), a.maxHeartRate)!!
            drawLine(
                ZONE_COLORS[zone], Offset(i * dx, y(v0)), Offset((i + 1) * dx, y(v1)), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (a.historyRanges.any { t in it }) dash else null,
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Note(clock(first))
        Spacer(Modifier.weight(1f))
        Note(clock(a.end))
    }
}

/**
 * The live screen tucking away into its capsule above the tab bar, and back, in the app's screen motion: the screen
 * drops a quarter of its height and fades while the capsule rises a little and fades in, and the reverse. [capsule]
 * gets the modifier that places it; [capsuleBottom] is its distance from the bottom edge.
 */
@Composable
fun WorkoutMorph(
    minimized: Boolean,
    capsuleBottom: androidx.compose.ui.unit.Dp,
    full: @Composable () -> Unit,
    capsule: @Composable (Modifier) -> Unit,
) {
    val swift = org.openstrap.app.ui.components.Motion.SWIFT
    val move = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(swift, easing = org.openstrap.app.ui.components.Motion.easing)
    val fadeIn = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(swift))
    val fadeOut = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(org.openstrap.app.ui.components.Motion.QUICK))
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            !minimized, Modifier.fillMaxSize(),
            enter = androidx.compose.animation.slideInVertically(move) { it / 4 } + fadeIn,
            exit = androidx.compose.animation.slideOutVertically(move) { it / 4 } + fadeOut,
        ) { Box(Modifier.fillMaxSize().background(Color.Black)) { full() } }
        androidx.compose.animation.AnimatedVisibility(
            minimized, Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(move) { it / 2 } + fadeIn,
            exit = androidx.compose.animation.slideOutVertically(move) { it / 2 } + fadeOut,
        ) { capsule(Modifier.widthIn(max = 440.dp).padding(start = 16.dp, end = 16.dp, bottom = capsuleBottom)) }
    }
}

/**
 * Where Today's Start workout pill stands while a workout records, in the same white pill: running, the sport and its
 * clock with a live dot, opening the live screen; paused, "Resume" with a play icon, resuming at once.
 */
@Composable
fun WorkoutShortcut(r: WorkoutState.Recording, liveHeartRate: Int?, onOpen: () -> Unit, onResume: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val sport = Workouts.sportName(r.workout.sportType)
    val clock = workoutClock(activeSeconds(r))
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(shape).background(Color.White).pressClickable(if (r.paused) onResume else onOpen, shape),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (r.paused) Icon(AppIcons.PlayFill, null, tint = Color.Black, modifier = Modifier.size(16.dp))
        else PulseDot(Color.Black, liveHeartRate, 10.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (r.paused) "Resume ${sport.lowercase()} · $clock" else "$sport · $clock",
            color = Color.Black, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
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
