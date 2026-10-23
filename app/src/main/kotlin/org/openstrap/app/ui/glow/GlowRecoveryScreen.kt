package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Healthspan
import org.openstrap.app.data.History
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.RecoveryCard
import org.openstrap.app.ui.dashboard.RecoveryFacts
import org.openstrap.app.ui.dashboard.TabCopy
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.dashboard.recoveryCards
import org.openstrap.app.ui.theme.MetricColors

/*
 * The Recovery tab (spec 5.1): "can I push today, and why". The score glows, what drove it as a list under it, then
 * Charge as the second glow with where it went, the body signals and Habit Age ([recoveryCards]). How the score is
 * worked out, the strap's own score and why it might be off belong in the Recovery info sheet (step 6), not here.
 */

/** Charge as a whole number the way every screen shows it, cut rather than rounded, so they all agree. */
fun chargeShown(value: Float): Int = value.toInt()

@Composable
fun GlowRecoveryScreen(
    state: DayUiState,
    recovery: RecoveryResult?,
    history: DailyHistory,
    /** The tab's header, or a back header when the screen is opened from a card on another tab. */
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
    /** The weekly Habit Age estimate, or why it's withheld; null leaves the card out. */
    healthspan: Healthspan.Outcome? = null,
    onOpenHealthspan: () -> Unit = {},
    /** Opens the profile, for Habit Age's reminder to add a birth year or sex. */
    onOpenProfile: () -> Unit = {},
    /** Scroll to the Charge card once (the All metrics Charge row); [onChargeShown] when it's there. */
    showCharge: Boolean = false,
    onChargeShown: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val signals = rememberSignals(state, history)
    val r = recovery?.takeIf { it.date == state.date }
    val learning = r?.readiness?.calibrating == true
    val waiting = if (r != null && !r.charge.anchored) "After your first night" else "No reading yet"
    val slots = recoveryCards(RecoveryFacts(habitAge = healthspan != null, learning = learning, hasRecovery = r != null))
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        GlowScreenList(contentPadding, list, focus = showCharge, onFocused = onChargeShown) {
            full { header() }
            tabCards(slots, focus = RecoveryCard.Charge) { card ->
                when (card) {
                    RecoveryCard.Hero -> RecoveryHero(r)
                    RecoveryCard.Drivers -> DriversList(r?.readiness)
                    RecoveryCard.Charge -> ChargeGlow(r?.charge, state.dayStart..state.dayEnd)
                    RecoveryCard.ChargeToday -> ChargeTodayList(r?.charge)
                    RecoveryCard.BodySignals -> SignalsList(signals, waiting, onOpenMetric)
                    RecoveryCard.HabitAge -> HabitAgeEdgeCard(healthspan, onOpenHealthspan, onOpenProfile)
                }
            }
        }
    }
}

/* ----------------------------------------------- Hero ----------------------------------------------- */

/** The score sits in a dark pool, light all round it (spec 2.5): the core behind the number, low on the left. */
private fun recoveryHeroStyle(color: Color) = GlowStyle.Bloom.copy(
    color = color,
    coreShape = CoreShape.Radial, coreX = 0.3f, coreY = 0.72f, coreWidth = 0.75f, coreHeight = 0.6f,
    coreBlur = 30.dp, toneWidth = 16.dp, toneShift = -10f, glowStrength = 0.6f, bevel = 0.9f,
)

/**
 * The score: what it means for the day and what held it back, then the number and its word. Compared with yesterday
 * in the title, and the calibration caveat under the sentence while it learns. As high as its content.
 */
@Composable
private fun RecoveryHero(r: RecoveryResult?) {
    val ready = r?.readiness
    val score = ready?.score
    val yesterday = r?.readinessTrend?.dropLast(1)?.lastOrNull()?.second
    val alert = ready?.strainAlert == true
    // TODO(step 6): tapping opens the Recovery detail, and an (i) opens the info sheet with the method and the strap's own score.
    GlowCard(recoveryHeroStyle(overviewColor(score, alert)), Modifier.fillMaxWidth().gridSize(CardSize.Wide)) {
        Column {
            GlowHeader("Recovery", trailing = score?.let { TabCopy.vsYesterday(it, yesterday) })
            HeroSentence(
                when {
                    ready == null -> TabCopy.RECOVERY_WAITING
                    score != null -> TabCopy.recovery(score, alert, ready.contributors)
                    ready.phase == Recovery.Phase.Withheld -> TodayCopy.noScoreLine(TodayCopy.NoScore.Learning(ready.baselineNights, Recovery.SCORED_NIGHTS))
                    ready.withheld -> TodayCopy.noScoreLine(TodayCopy.NoScore.NoHeartData)
                    else -> "Recovery is scored each morning from the night's sleep."
                },
            )
            // Before the first score the sentence already says how many nights are in, so the caveat waits for a score.
            if (score != null) {
                ready.let { TabCopy.learning(it.phase, it.baselineNights) }?.let { CaveatLine(it, overviewColor(score, alert)) }
                Spacer(Modifier.height(22.dp))
                HeroFigure(score.toString(), TodayCopy.recoveryWord(score, alert), big = true)
            }
        }
    }
}

/** What drove the score: the three that weigh most, each with its bar and word. */
@Composable
private fun DriversList(ready: Recovery.Readiness?) {
    val all = ready?.contributors.orEmpty()
    val top = TabCopy.topDrivers(all)
    // TODO(step 6): "All" opens the Recovery detail with every contributor; until then the note only counts them.
    ListPanel(title = "What drove it", note = if (all.size > top.size) "Top ${top.size} of ${all.size}" else null) {
        if (top.isEmpty()) ListRow("Waiting for a night", MetricColors.readinessDeep, null, "What drove Recovery shows here each morning", dash = false, divider = false)
        top.forEachIndexed { i, c ->
            BarRow(
                TabCopy.factorName(c.factor), MetricColors.readinessDeep, c.score.toString(), TabCopy.contributorWord(c.score),
                c.score / 100f, offNormal = TabCopy.contributorOff(c.score), divider = i > 0,
            )
        }
    }
}

/* ---------------------------------------------- Charge ---------------------------------------------- */

private val HOURS = listOf("00", "06", "12", "18", "24")

/** Charge, the second glow: lit to its level from below, the number now, and the day's curve. */
@Composable
private fun ChargeGlow(c: Recovery.ChargeDay?, day: LongRange) {
    val now = c?.latest?.let(::chargeShown)
    val atWake = c?.atWake?.toInt()
    // TODO(step 6): tapping opens the Charge detail.
    GlowCard(GlowStyle.Ember.copy(lightLevel = (now ?: 0) / 100f, second = true), Modifier.fillMaxWidth().gridSize(CardSize.Wide)) {
        Column {
            GlowHeader("Charge", trailing = atWake?.let { if (c.strapOffOvernight) "$it at start" else "$it at waking" })
            EdgeValue(now?.toString(), null, TabCopy.chargeLine(now, atWake, c?.anchored == true), Color.White.copy(alpha = 0.8f))
            if (c?.strapOffOvernight == true) Note(org.openstrap.app.ui.dashboard.STRAP_OFF_OVERNIGHT)
            if (c != null && now != null && c.points.size >= 2) {
                Spacer(Modifier.height(16.dp))
                val values = remember(c.points, day) { bucket(c.points, day, 15 * 60L) }
                GlowLineChart(values, height = 72.dp, labels = HOURS, showAverage = false, yRange = 0f..100f) { i, v ->
                    "${v.toInt()} at ${clockAt(day.first + i * 15 * 60L)}"
                }
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

/** Where today's Charge came from and went: sleep filled it, the day drained it. */
@Composable
private fun ChargeTodayList(c: Recovery.ChargeDay?) {
    ListPanel(title = "Charge today") {
        val ready = c != null && c.anchored && c.latest != null
        TabCopy.chargeRows(c?.recharged ?: 0f, c?.drainedExercise ?: 0f, c?.drainedStress ?: 0f, c?.drainedLiving ?: 0f).forEachIndexed { i, row ->
            ListRow(
                row.name, MetricColors.chargeDeep, row.value.takeIf { ready },
                if (ready) row.status else "After your first night", divider = i > 0,
            )
        }
    }
}

/* ------------------------------------------- Body signals ------------------------------------------- */

/** A body signal for the list: today's value and how it compares with your normal. */
private class BodySignal(
    val metric: Metric,
    val label: String,
    val accent: Color,
    val today: Float?,
    val normal: History.Normal?,
    /** Show the value as a change from your normal (skin temperature), not as a level. */
    val asDeviation: Boolean = false,
    val unit: String = "",
)

/** Loads the last 30 days of each body signal, enough for its normal. */
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
    fun normalOf(stats: List<org.openstrap.app.data.DayStat>) = History.normal(stats, end)
    return listOf(
        BodySignal(Metric.RestingHr, "Resting heart rate", MetricColors.heart, state.restingHeartRate?.toFloat(), normalOf(rhr)),
        BodySignal(Metric.Hrv, "HRV", MetricColors.hrv, state.hrv.avg()?.toFloat(), normalOf(hrv)),
        BodySignal(
            Metric.SkinTemperature, "Skin temperature", MetricColors.temperature,
            state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(), normalOf(skin), asDeviation = true,
        ),
        BodySignal(Metric.Breathing, "Breathing rate", MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), normalOf(breath)),
        BodySignal(Metric.Spo2, "Blood oxygen", MetricColors.spo2, state.spo2.avg()?.toFloat(), normalOf(spo2), unit = "%"),
        BodySignal(Metric.Stress, "Stress", MetricColors.stress, state.stress.avg()?.toFloat(), normalOf(stress)),
    )
}

/**
 * Body signals against your normal, each opening its metric. Skin temperature shows as its change from your normal,
 * since the level depends on fit and room. [waiting] is what a row with no reading says.
 */
@Composable
private fun SignalsList(signals: List<BodySignal>, waiting: String, onOpen: (Metric) -> Unit) {
    ListPanel(title = "Body signals", note = "Against your normal") {
        signals.forEachIndexed { i, s ->
            val direction = History.status(s.today, s.normal)
            val value = s.today?.let { v ->
                if (s.asDeviation && s.normal != null) {
                    val d = v - s.normal.mean
                    (if (d >= 0) "+" else "-") + s.metric.format(kotlin.math.abs(d))
                } else s.metric.format(v) + s.unit
            }
            ListRow(
                s.label, s.accent, value,
                if (s.today == null) waiting else TodayCopy.normalWord(direction),
                offNormal = direction != null && direction != 0, onClick = { onOpen(s.metric) }, divider = i > 0,
            )
        }
    }
}

/* --------------------------------------------- Shared ------------------------------------------------ */

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

@Composable
internal fun Fact(label: String, value: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
