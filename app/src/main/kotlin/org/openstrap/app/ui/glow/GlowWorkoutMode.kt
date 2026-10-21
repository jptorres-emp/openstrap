package org.openstrap.app.ui.glow

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import org.openstrap.app.ui.theme.PhosphorIcons
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
private fun activeSeconds(r: WorkoutState.Recording): Long = produceState(WorkoutRecorder.activeSeconds(r.workout, System.currentTimeMillis() / 1000), r.workout) {
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
private fun WorkoutPage(glow: Color, contentPadding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val width = constraints.maxWidth.toFloat()
        Box(
            Modifier.fillMaxWidth().height(420.dp).background(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Color.Transparent), center = Offset(width / 2f, 0f), radius = width * 0.9f),
            ),
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(contentPadding).padding(start = GlowLayout.Gutter, end = GlowLayout.Gutter, top = 8.dp, bottom = 16.dp)
                .heightIn(min = maxHeight - 80.dp),
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
            Icon(PhosphorIcons.CaretDown, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/** The five zones side by side, the current one lit, with a white marker where [bpm] sits. */
@Composable
private fun ZoneBar(bpm: Int?, maxHr: Int, ring: Color, modifier: Modifier = Modifier, barHeight: Dp = 8.dp) {
    val zone = WorkoutRecorder.liveZone(bpm, maxHr)
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val gap = 4.dp.toPx()
        val h = barHeight.toPx()
        val w = (size.width - gap * (Workouts.ZONES - 1)) / Workouts.ZONES
        val top = (size.height - h) / 2
        for (z in 0 until Workouts.ZONES) {
            drawRoundRect(ZONE_COLORS[z].copy(alpha = if (z == zone) 1f else 0.3f), Offset(z * (w + gap), top), Size(w, h), CornerRadius(h / 2))
        }
        if (bpm != null) {
            val r = size.height / 2
            val x = (WorkoutRecorder.zonePosition(bpm, maxHr) * size.width).coerceIn(r, size.width - r)
            drawCircle(ring, radius = r + 3.dp.toPx(), center = Offset(x, size.height / 2))
            drawCircle(Color.White, radius = r, center = Offset(x, size.height / 2))
        }
    }
}

/* ----------------------------------------- Activity tab entry ---------------------------------------- */

/** A sport someone has done, for the start card: how often and when last. */
data class SportUse(val sportType: Int, val count: Int, val lastStart: Long?)

/** The 3 most-used sports across both kinds of workout, else three sensible defaults. */
fun topSports(workouts: List<WorkoutAnalysis>, n: Int = 3): List<SportUse> {
    val used = workouts.groupBy { it.workout.sportType }
        .map { (type, ws) -> SportUse(type, ws.size, ws.maxOf { it.workout.start }) }
        .sortedWith(compareByDescending<SportUse> { it.count }.thenByDescending { it.lastStart })
        .take(n)
    return used + Workouts.DEFAULT_SPORTS.filter { d -> used.none { it.sportType == d } }.take(n - used.size).map { SportUse(it, 0, null) }
}

/** "Last: Mon" within the week, "Last: today", else the date; "Not done yet" when never done. */
private fun lastDone(epoch: Long?): String {
    epoch ?: return "Not done yet"
    val date = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, LocalDate.now())
    return "Last: " + when {
        days == 0L -> "today"
        days < 7 -> date.format(DateTimeFormatter.ofPattern("EEE"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

/**
 * "Start a workout": the three most-used sports and every sport, as the app's own stat tiles two by two. Each tile
 * says when the sport was last done; the sport's icon and name sit underneath.
 */
@Composable
internal fun StartWorkoutPanel(sports: List<SportUse>, onStart: (Int) -> Unit, onAllSports: () -> Unit) {
    DotPanel {
        DotHeader("Start a workout")
        val tiles: List<SportUse?> = sports + null
        TileGrid(tiles) { s, modifier ->
            if (s == null) {
                GlowTile("${Workouts.sports.size} sports", Color.White.copy(alpha = 0.5f), onAllSports, modifier) {
                    SportTileName(PhosphorIcons.SlidersHorizontal, "All sports")
                }
            } else {
                GlowTile(lastDone(s.lastStart), MetricColors.heart, { onStart(s.sportType) }, modifier) {
                    SportTileName(SportIcons.of(s.sportType), Workouts.sportName(s.sportType))
                }
            }
        }
    }
}

@Composable
private fun SportTileName(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Looks like you worked out": a stretch nobody started, offered for saving. */
@Composable
internal fun SuggestionPanel(s: org.openstrap.app.data.WorkoutSuggestions.Suggestion, onSave: () -> Unit, onDismiss: () -> Unit) {
    DotPanel {
        Row(verticalAlignment = Alignment.Top) {
            Icon(PhosphorIcons.Heartbeat, null, tint = ZONE_COLORS[s.zone], modifier = Modifier.padding(top = 2.dp).size(20.dp))
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
            IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back", tint = Color.White) }
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
    onStart: (sportType: Int, askToEnd: Boolean, target: WorkoutTarget?) -> Unit,
    onBack: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(pickSport) }
    var askToEnd by rememberSaveable { mutableStateOf(true) }
    var targetCode by rememberSaveable { mutableStateOf<String?>(null) }
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
            ) { Icon(PhosphorIcons.X, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
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
        WorkoutButton("Start", accent, Color.Black, { onStart(sportType, askToEnd, target) }, icon = PhosphorIcons.PlayFill)
    }
}

@Composable
private fun SportPicker(selected: Int, contentPadding: PaddingValues, onPick: (Int) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query) { Workouts.sports.filter { it.second.contains(query.trim(), ignoreCase = true) } }
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
        shown.forEach { (type, name) ->
            item(key = type) {
                Row(
                    Modifier.fillMaxWidth().glass(GlassRowShape, { onPick(type) }).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(SportIcons.of(type), null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(name, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (type == selected) Canvas(Modifier.size(8.dp)) { drawCircle(Color.White) }
                }
            }
        }
    }
}

/* -------------------------------------------- Live screen -------------------------------------------- */

/**
 * The workout as it records, in the current zone's colour. The card beats at the live heart rate with the last ten
 * minutes behind the number; the zone, its range and the time spent in it sit under a zone bar. Back tucks it away;
 * ending takes a one-second hold on the square so a pocket can't end it.
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
) {
    BackHandler(onBack = onMinimize)
    val connected = connection is ConnectionState.Connected
    val bpm = liveHeartRate.takeIf { connected && !r.paused }
    val zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate)
    val color = workoutColor(r, liveHeartRate, connected)
    val active = activeSeconds(r)
    WorkoutPage(color, contentPadding) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize) { Icon(PhosphorIcons.CaretDown, "Minimize", tint = Color.White) }
            Pill(Workouts.sportName(r.workout.sportType), color)
            Spacer(Modifier.weight(1f))
            Text(
                when { r.paused -> "Paused"; connected -> "Strap"; else -> "Reconnecting" },
                color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(6.dp)) { drawCircle(if (connected) TrendGood else TrendBad) }
        }
        GlowCard(
            GlowStyle.chart(color).copy(motion = if (bpm != null) GlowMotion.Heartbeat(bpm) else GlowMotion.Still),
            Modifier.fillMaxWidth(),
        ) {
            Column {
                GlowHeader("Heart rate", trailing = bpm?.let { "${it * 100 / r.maxHeartRate}% of max" })
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    val now = recent.lastOrNull()?.first ?: (System.currentTimeMillis() / 1000)
                    val values = remember(recent) { bucketRecent(recent, now) }
                    if (values.any { it != null }) {
                        GlowLineChart(values, Modifier.align(Alignment.BottomCenter).alpha(0.45f), height = 120.dp, showAverage = false)
                    }
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        DotMatrixText(bpm?.toString() ?: "--", dot = 9.dp, gap = 3.dp)
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
        // Three equal columns, each centred, so the row sits centred under the card; labels share the top line.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            LiveStat("Moving time", Modifier.weight(1f)) {
                // Plain digits: the heart rate above is the one dot-matrix number on the screen.
                Text(workoutClock(active), color = Color.White, style = GlowType.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum"))
            }
            LiveStat("Avg", Modifier.weight(1f)) { LiveValue(stats?.average?.toString() ?: "—") }
            LiveStat("Max", Modifier.weight(1f)) { LiveValue(stats?.max?.toString() ?: "—") }
        }
        if (!connected && !r.paused) {
            Note("The strap isn't connected. Recording carries on, and the minutes missed are filled from its own history after it syncs.")
        }
        r.endPrompt?.let { at -> StoppedPrompt(r, at, onEndAtPrompt, onKeepGoing) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Lap goes here in a later step; the space keeps Pause centred.
            Spacer(Modifier.size(64.dp))
            Spacer(Modifier.weight(1f))
            GlowButton(color, 88.dp, onClick = if (r.paused) onResume else onPause) {
                Icon(if (r.paused) PhosphorIcons.PlayFill else PhosphorIcons.PauseFill, if (r.paused) "Resume" else "Pause", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.weight(1f))
            HoldToEnd(onEnd)
        }
        Text(
            "Hold the square to end", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun LiveStat(label: String, modifier: Modifier, value: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        value()
    }
}

/** Avg and Max in the same face and size as the moving time beside them. */
@Composable
private fun LiveValue(text: String) =
    Text(text, color = Color.White, style = GlowType.headline.copy(fontSize = 34.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum"))

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
private fun GlowButton(color: Color, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val style = remember(color, size) {
        GlowStyle.chart(color).copy(
            radius = size / 2, coreShape = CoreShape.Radial, coreX = 0.5f, coreY = 0.5f, coreWidth = 0.66f, coreHeight = 0.66f,
            coreBlur = size / 7, toneWidth = size / 10, glowSize = size / 5, glowStrength = 0.9f, motion = GlowMotion.Still,
        )
    }
    GlowCard(style, modifier.size(size), onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
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
        GlowButton(
            EndRed, 64.dp,
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
            Icon(PhosphorIcons.StopFill, "Hold to end the workout", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Canvas(Modifier.size(64.dp).padding(2.dp)) {
            if (progress.value > 0f) drawArc(Color.White, -90f, 360f * progress.value, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
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
            Icon(if (r.paused) PhosphorIcons.PlayFill else PhosphorIcons.PauseFill, if (r.paused) "Resume" else "Pause", tint = Color.Black, modifier = Modifier.size(18.dp))
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
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var trimming by rememberSaveable { mutableStateOf(false) }
    val a = summary?.analysis
    WorkoutPage(Color.Transparent, contentPadding) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!justEnded) IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back", tint = Color.White) }
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
                DotMatrixText(workoutClock(active), dot = 6.dp, gap = 2.dp)
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
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.weight(1f))
        if (justEnded) WorkoutButton("Done", Color.White, Color.Black, onBack)
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
        DotHeader("Trim", trailing = "${clock(from)} – ${clock(to)}")
        val known = points.filterNotNull()
        val lo = (known.minOrNull() ?: 60f) - 5f
        val hi = (known.maxOrNull() ?: 180f) + 5f
        Canvas(
            Modifier.fillMaxWidth().height(96.dp).pointerInput(summary) {
                var dragging = 0
                val inset = HandleInset.toPx()
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
            // Inset, so a handle at either end is whole and easy to grab.
            val inset = HandleInset.toPx()
            val w = size.width - 2 * inset
            val dx = w / (points.size - 1).coerceAtLeast(1)
            fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height
            fun x(t: Long) = inset + (t - first).toFloat() / (last - first) * w
            for (i in 0 until points.size - 1) {
                val v0 = points[i] ?: continue
                val v1 = points[i + 1] ?: continue
                val t = first + i * step
                val inside = t in from until to
                val color = ZONE_COLORS[WorkoutRecorder.liveZone(v0.toInt(), a.maxHeartRate)!!].copy(alpha = if (inside) 1f else 0.25f)
                drawLine(color, Offset(inset + i * dx, y(v0)), Offset(inset + (i + 1) * dx, y(v1)), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            listOf(from, to).forEach { t ->
                val hx = x(t)
                drawLine(Color.White, Offset(hx, 0f), Offset(hx, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(hx, size.height / 2))
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
