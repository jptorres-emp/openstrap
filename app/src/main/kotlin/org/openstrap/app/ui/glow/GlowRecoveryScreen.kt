package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.History
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Narrative
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.theme.MetricColors
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Recovery tab in the glow language: "can I push today, and why". Two cards glow, the score and Charge;
 * everything that explains them sits in quiet dot panels underneath, and each body signal opens its detail.
 */
@Composable
fun GlowRecoveryScreen(
    state: DayUiState,
    recovery: RecoveryResult?,
    strapReadiness: StrapReadinessRow?,
    history: DailyHistory,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    picker: DayPicker,
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
) {
    val list = rememberLazyListState()
    val signals = rememberSignals(state, history)
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        GlowScreenList(contentPadding, list) {
            full { DateHeader(state.date, battery, onPrev, onNext, charging, picker) }
            val r = recovery?.takeIf { it.date == state.date }
            if (r == null) {
                full { DotPanel { Text("Calculating…", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium) } }
                return@GlowScreenList
            }
            val ready = r.readiness
            // Wide: the scores and where the charge went on the left; why, and the body signals, on the right.
            left { RecoveryCard(r) }
            if (ready.strainAlert) right { StrainPanel(ready) }
            left { ChargeCard(r.charge, state.dayStart..state.dayEnd) }
            if (r.charge.latest != null) left { ChargeFlowPanel(r.charge, r.stressThreshold) }
            if (ready.contributors.isNotEmpty()) right { ContributorsPanel(ready) }
            right { SignalsPanel(signals, onOpenMetric) }
            full { HowItWorksPanel(strapReadiness, ready.score) }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

private val HOURS = listOf("00", "06", "12", "18", "24")

/** The score card: the number, its sentence, and the last seven days underneath. Coloured by how the day looks. */
@Composable
private fun RecoveryCard(r: RecoveryResult) {
    val ready = r.readiness
    val score = ready.score
    val before = r.readinessTrend.dropLast(1).lastOrNull()?.second
    val style = GlowStyle.Bloom.copy(
        color = overviewColor(score, ready.strainAlert),
        coreShape = CoreShape.Radial, coreX = 0.5f, coreY = 0.8f, coreWidth = 0.95f, coreHeight = 0.75f,
        coreBlur = 30.dp, toneWidth = 16.dp, toneShift = -10f, glowStrength = 0.6f, bevel = 0.6f,
        motion = GlowMotion.Breath(5f), depth = 6.dp,
    )
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader(
                "Recovery",
                trailing = when {
                    ready.calibrating -> "Learning ${ready.baselineNights}/${Recovery.CALIBRATED_NIGHTS} nights"
                    score != null && before != null -> if (score == before) "Steady vs yesterday" else "${if (score > before) "+" else "−"}${kotlin.math.abs(score - before)} vs yesterday"
                    else -> null
                },
            )
            if (score == null) {
                Spacer(Modifier.height(GlowLayout.TitleGap))
                Text(
                    "Recovery is scored each morning from the night's sleep. Wear the strap to bed.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            GlowHeroNumber("$score", "/ 100", listOf(Narrative.readinessHeadline(score, ready.strainAlert)))
            Spacer(Modifier.height(12.dp))
            Text(Narrative.readinessBody(ready), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            if (r.readinessTrend.any { it.second != null }) {
                GlowChartGap()
                GlowTrend(
                    r.readinessTrend.map { (d, v) -> GlowDay(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), v?.toFloat()) },
                    height = GlowLayout.ChartHeight,
                )
            }
        }
    }
}

@Composable
private fun StrainPanel(ready: Recovery.Readiness) {
    DotPanel {
        DotHeader("Your body is under strain", warning = true)
        Text(
            ready.alertSignals.joinToString(", ").replaceFirstChar { it.uppercase() } + ". " +
                "Changes like these together often come with illness, heavy training, alcohol or a late night. " +
                "OpenStrap isn't a medical device; see a doctor if you feel unwell.",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Charge: the battery now, what filled and drained it, and the day's curve. */
@Composable
private fun ChargeCard(c: Recovery.ChargeDay, day: LongRange) {
    val now = c.latest
    val style = GlowStyle.Ember.copy(color = MetricColors.chargeAt(now), motion = GlowMotion.Breath(4f))
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader("Charge", trailing = c.atWake?.let { "${it.toInt()} at waking" })
            val hint = when {
                !c.anchored -> "Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from."
                now == null -> "Charge appears once the strap has a few hours of heart rate."
                else -> null
            }
            if (hint != null || now == null) {
                Spacer(Modifier.height(GlowLayout.TitleGap))
                Text(hint.orEmpty(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val drained = (c.drainedExercise + c.drainedStress + c.drainedLiving).toInt()
            GlowHeroNumber("${now.toInt()}", "/ 100", listOf(Narrative.chargeHeadline(now.toInt()), "${signed(c.recharged, +1)} · ${signed(drained.toFloat(), -1)}"))
            GlowChartGap()
            val values = remember(c.points, day) { bucket(c.points, day, 15 * 60L) }
            GlowLineChart(values, height = GlowLayout.ChartHeight, labels = HOURS, showAverage = false, yRange = 0f..100f) { i, v ->
                "${v.toInt()} · ${clock(day.first + i * 15 * 60L)}"
            }
        }
    }
}

/** Averages charge samples into evenly spaced buckets across [range]; empty buckets are gaps. */
private fun bucket(points: List<Recovery.ChargePoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.t - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.value; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

private fun clock(epoch: Long) = java.time.Instant.ofEpochSecond(epoch).atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun ChargeFlowPanel(c: Recovery.ChargeDay, threshold: Recovery.StressThreshold) {
    DotPanel {
        DotHeader("Where your charge went")
        val total = maxOf(c.recharged, c.drainedExercise + c.drainedStress + c.drainedLiving, 1f)
        FlowRow("Sleep and calm rest", signed(c.recharged, +1), c.recharged / total, MetricColors.good)
        FlowRow("Movement and exercise", signed(c.drainedExercise, -1), c.drainedExercise / total, MetricColors.steps)
        FlowRow("Stress", signed(c.drainedStress, -1), c.drainedStress / total, MetricColors.stress)
        FlowRow("Being awake", signed(c.drainedLiving, -1), c.drainedLiving / total, MetricColors.sleepLight)
        if (c.highest != null && c.lowest != null) Note("Highest ${c.highest.toInt()} · lowest ${c.lowest.toInt()} this day")
        Note(Narrative.stressThreshold(threshold))
    }
}

/** A charge amount with its sign, and no sign at all for zero ("−0" reads oddly). */
private fun signed(v: Float, sign: Int): String {
    val n = v.toInt()
    return if (n == 0) "0" else "${if (sign > 0) "+" else "−"}$n"
}

@Composable
private fun FlowRow(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
        GlowBar(fraction, color)
    }
}

/**
 * The quiet panels' progress bar: a faint solid track, the first [fraction] of it a glowing line in [color], and a
 * dot where it ends, like the knob on the sleep score's arc.
 */
@Composable
internal fun GlowBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val r = 5.dp.toPx()
        // Inset by the dot's halo, so a full or empty bar keeps its dot inside the panel.
        val x0 = r
        val x1 = size.width - r
        drawLine(Color.White.copy(alpha = 0.10f), Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) return@Canvas
        val end = Offset(x0 + (x1 - x0) * f, y)
        drawLine(color.copy(alpha = 0.22f), Offset(x0, y), end, 6.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(x0, y), end, 2.2.dp.toPx(), StrokeCap.Round)
        drawCircle(color.copy(alpha = 0.30f), r, end)
        drawCircle(color, 3.dp.toPx(), end)
        drawCircle(Color.White, 1.2.dp.toPx(), end)
    }
}

@Composable
internal fun Note(text: String) = Text(text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)

/** The score's contributors, grouped by what they're about. */
@Composable
private fun ContributorsPanel(ready: Recovery.Readiness) {
    val groups = listOf(
        "Body" to setOf(Recovery.Factor.HrvBalance, Recovery.Factor.RestingHr, Recovery.Factor.Temperature, Recovery.Factor.Breathing, Recovery.Factor.BloodOxygen),
        "Sleep" to setOf(Recovery.Factor.Sleep, Recovery.Factor.SleepBalance, Recovery.Factor.RecoveryIndex, Recovery.Factor.BreathingDisturbances),
        "Load" to setOf(Recovery.Factor.PreviousDayLoad, Recovery.Factor.LoadBalance),
    )
    DotPanel {
        DotHeader("What drove it")
        groups.forEachIndexed { gi, (name, factors) ->
            val rows = ready.contributors.filter { it.factor in factors }.sortedByDescending { it.factor.weight }
            if (rows.isEmpty()) return@forEachIndexed
            if (gi > 0) DotDivider()
            Text(name.uppercase(), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            rows.forEach { c ->
                val rating = Narrative.ratingFor(c.score)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.factor.label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(rating.label, color = rating.color, style = MaterialTheme.typography.titleSmall)
                    }
                    Note(c.detail)
                    GlowBar(c.score / 100f, rating.color)
                }
            }
        }
        if (ready.capped) Note("Capped: one body signal is far from your normal, so good sleep can't fully make up for it.")
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** A body signal for the grid: today's value, its last 7 days, and how it compares with your normal. */
private class BodySignal(
    val metric: Metric,
    val label: String,
    val accent: Color,
    val today: Float?,
    val week: List<Float?>,
    val normal: History.Normal?,
    /** Words for above and below your normal: Higher/Lower, or Faster/Slower for heart rate. */
    val up: String = "Higher",
    val down: String = "Lower",
    /** Show the value as a change from your normal (skin temperature), not as a level. */
    val asDeviation: Boolean = false,
)

/** Loads the last 30 days of each body signal, enough for its normal and a week's sparkline. */
@Composable
private fun rememberSignals(state: DayUiState, history: DailyHistory): List<BodySignal> {
    val end = state.date
    val from = end.minusDays(History.NORMAL_DAYS - 1L)
    @Composable fun daily(m: Metric) = remember(m, end) { history(m.daily, from, end) }
    val rhr by daily(Metric.RestingHr).collectAsState(emptyList())
    val hrv by daily(Metric.Hrv).collectAsState(emptyList())
    val skin by daily(Metric.SkinTemperature).collectAsState(emptyList())
    val breath by daily(Metric.Breathing).collectAsState(emptyList())
    val spo2 by daily(Metric.Spo2).collectAsState(emptyList())
    val stress by daily(Metric.Stress).collectAsState(emptyList())
    val week = (6 downTo 0).map { end.minusDays(it.toLong()) }
    fun weekOf(stats: List<org.openstrap.app.data.DayStat>) = week.map { d -> stats.firstOrNull { it.date == d }?.value }
    fun normalOf(stats: List<org.openstrap.app.data.DayStat>) = History.normal(stats, end)
    return listOf(
        BodySignal(Metric.RestingHr, "Resting HR", MetricColors.heart, state.restingHeartRate?.toFloat(), weekOf(rhr), normalOf(rhr), up = "Faster", down = "Slower"),
        BodySignal(Metric.Hrv, "HRV", MetricColors.hrv, state.hrv.avg()?.toFloat(), weekOf(hrv), normalOf(hrv)),
        BodySignal(
            Metric.SkinTemperature, "Skin temp", MetricColors.temperature,
            state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(), weekOf(skin), normalOf(skin), asDeviation = true,
        ),
        BodySignal(Metric.Breathing, "Breathing", MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), weekOf(breath), normalOf(breath)),
        BodySignal(Metric.Spo2, "Blood oxygen", MetricColors.spo2, state.spo2.avg()?.toFloat(), weekOf(spo2), normalOf(spo2)),
        BodySignal(Metric.Stress, "Stress", MetricColors.stress, state.stress.avg()?.toFloat(), weekOf(stress), normalOf(stress)),
    )
}

/** Body signals as a two-column grid, Zepp-style: value, a word against your normal, and the week as a sparkline. */
@Composable
private fun SignalsPanel(signals: List<BodySignal>, onOpen: (Metric) -> Unit) {
    DotPanel {
        DotHeader("Body signals", trailing = "vs. your normal")
        TileGrid(signals) { s, m -> SignalTile(s, m) { onOpen(s.metric) } }
        if (signals.all { it.normal == null }) Note("Your normal builds over the first week of wear; until then each signal shows its week.")
    }
}

@Composable
private fun SignalTile(s: BodySignal, modifier: Modifier, onClick: () -> Unit) {
    val m = s.metric
    val value = s.today?.let { v ->
        if (s.asDeviation && s.normal != null) {
            val d = v - s.normal.mean
            "${if (d >= 0) "+" else "−"}${m.format(kotlin.math.abs(d))}"
        } else m.format(v)
    }
    val word = when (History.status(s.today, s.normal)) {
        1 -> s.up
        -1 -> s.down
        0 -> "Normal"
        else -> null
    }
    GlowTile(s.label, s.accent, onClick, modifier) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (value != null && m.unit.isNotEmpty()) {
                Text(m.unit, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
        if (word != null) StatusPill(word, if (word == "Normal") TrendGood else TrendBad, Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Sparkline(s.week, s.accent, Modifier.padding(top = 10.dp).fillMaxWidth().height(22.dp))
    }
}

/** Seven days as dots joined by a faint line; missing days are gaps. */
@Composable
internal fun Sparkline(values: List<Float?>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val lo = known.min()
        val hi = known.max().let { if (it - lo < 0.001f) lo + 1f else it }
        val r = 2.dp.toPx()
        fun at(i: Int, v: Float) = Offset(
            r + i * (size.width - 2 * r) / (values.size - 1).coerceAtLeast(1),
            size.height - r - (v - lo) / (hi - lo) * (size.height - 2 * r),
        )
        values.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val next = values.getOrNull(i + 1)
            if (next != null) drawLine(color.copy(alpha = 0.45f), at(i, v), at(i + 1, next), 1.2.dp.toPx(), StrokeCap.Round)
        }
        values.forEachIndexed { i, v -> if (v != null) drawCircle(if (i == values.lastIndex) color else color.copy(alpha = 0.7f), r, at(i, v)) }
    }
}

/* ------------------------------------------------------------------------------------------------ */

@Composable
internal fun Fact(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

/** How the score works, folded away; the strap's own score sits inside as a comparison. */
@Composable
private fun HowItWorksPanel(strap: StrapReadinessRow?, ours: Int?) {
    var open by rememberSaveable { mutableStateOf(false) }
    DotPanel(onClick = { open = !open }) {
        DotHeader("How it works", expanded = open)
        if (!open) return@DotPanel
        Text(
            "Each morning OpenStrap compares your night with what's normal for you, not with other people: HRV, resting heart " +
                "rate, skin temperature, breathing and blood oxygen, last night's sleep and the past few nights, and how hard " +
                "the previous days were. The score is a weighted mix; one signal far from normal caps it.",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Charge is a running battery: sleep and calm rest fill it, movement, stress and simply being awake drain it.",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
        )
        val strapScore = strap?.score
        if (strapScore != null) {
            DotDivider()
            Row(verticalAlignment = Alignment.Bottom) {
                Fact("The strap's own score", "$strapScore")
                Spacer(Modifier.weight(1f))
                if (ours != null) Fact("Ours", "$ours")
            }
            Note(
                "The strap's score doesn't count sleep length, so after a short night it reads higher than ours. " +
                    "OpenStrap keeps both each day to tune its own score once there are a couple of weeks to compare.",
            )
        }
        Note("OpenStrap isn't a medical device.")
    }
}
