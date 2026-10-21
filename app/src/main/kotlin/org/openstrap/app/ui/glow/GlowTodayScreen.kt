package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import org.openstrap.app.ui.theme.PhosphorIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.openstrap.app.ui.components.pressClickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.openstrap.app.ui.dashboard.DailyHistory
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.Narrative
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.dashboard.chargeLeads
import org.openstrap.app.ui.theme.MetricColors

/**
 * Today in the glow language: the home screen answers "how am I, what changed, what should I do".
 * Each thing appears once. The verdict leads; in the morning last night comes next, later in the day
 * your heart and movement do. Key statistics list only what moved away from your normal. Only a few
 * cards glow; the rest is black with dots, so the glow keeps meaning "look here".
 */
@Composable
fun GlowTodayScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    battery: Int?,
    charging: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    picker: DayPicker = DayPicker(),
    onOpenSleep: () -> Unit = {},
    onOpenMetric: (Metric) -> Unit = {},
    recovery: RecoveryResult? = null,
    /** Opens the Recovery tab at the Recovery or the Charge section. */
    onOpenRecovery: (RecoverySection) -> Unit = {},
    workouts: List<org.openstrap.app.data.WorkoutAnalysis> = emptyList(),
    onOpenWorkout: (Long) -> Unit = {},
    /** Whether live heart rate is on; null hides the switch. */
    liveEnabled: Boolean? = null,
    onLiveToggle: (Boolean) -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenHealthspan: () -> Unit = {},
    /** Opens the profile, for the Healthspan card's reminder to add a birth year or sex. */
    onOpenProfile: () -> Unit = {},
    /** The weekly Healthspan estimate, or why it's withheld; null hides the card. */
    healthspan: org.openstrap.app.data.Healthspan.Outcome? = null,
    /** Per-day history for the Monday recap; null leaves the recap out. */
    history: DailyHistory? = null,
    /** The statistics What changed shows, in order. */
    tiles: List<Metric> = TODAY_TILES,
    onTilesChange: (List<Metric>) -> Unit = {},
    /** The strap's own Readiness for the day, shown as "Strap's estimate" while OpenStrap's score is withheld. */
    strapReadiness: StrapReadinessRow? = null,
    /** The morning check-in (1-5), asked on Today before noon; see [CheckInPanel]. */
    checkIn: Int? = null,
    onCheckIn: (Int?) -> Unit = {},
    /** A stretch nobody started, offered for saving; null hides the card. */
    suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion? = null,
    onSaveSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    /** Opens the ready screen with your usual sport; null hides the row. */
    onStartWorkout: (() -> Unit)? = null,
    /** An effort under way nobody started ([org.openstrap.app.data.AutoStart]); null hides the card. */
    offer: org.openstrap.app.data.AutoStart.Offer? = null,
    onRecordOffer: () -> Unit = {},
    onDeclineOffer: () -> Unit = {},
    /** A workout recording now: the Start workout pill becomes a way back to it ([WorkoutShortcut]). */
    running: org.openstrap.app.device.WorkoutState.Recording? = null,
    onOpenRunning: () -> Unit = {},
    onResumeRunning: () -> Unit = {},
) {
    // The glow holds still while the list scrolls, so scrolling stays as smooth as on other screens.
    val list = rememberLazyListState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    if (choosing) TilesDialog(tiles, onSave = { onTilesChange(it); choosing = false }, onDismiss = { choosing = false })
    val extras = TodayExtras(
        liveEnabled, onLiveToggle, onOpenActivity, onOpenHealthspan, onOpenProfile, healthspan, history, tiles, strapReadiness, checkIn, onCheckIn,
        suggestion, onSaveSuggestion, onDismissSuggestion, onStartWorkout, offer, onRecordOffer, onDeclineOffer,
        running, onOpenRunning, onResumeRunning,
    ) { choosing = true }
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        TodayList(state, liveHeartRate, battery, charging, onPrev, onNext, contentPadding, picker, onOpenSleep, onOpenMetric, recovery, onOpenRecovery, workouts, onOpenWorkout, list, extras)
    }
}

/** Today's newer controls, kept together so the list's signature stays readable. */
private class TodayExtras(
    val liveEnabled: Boolean?,
    val onLiveToggle: (Boolean) -> Unit,
    val onOpenActivity: () -> Unit,
    val onOpenHealthspan: () -> Unit,
    val onOpenProfile: () -> Unit,
    val healthspan: org.openstrap.app.data.Healthspan.Outcome?,
    val history: DailyHistory?,
    val tiles: List<Metric>,
    val strapReadiness: StrapReadinessRow?,
    val checkIn: Int?,
    val onCheckIn: (Int?) -> Unit,
    val suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion?,
    val onSaveSuggestion: () -> Unit,
    val onDismissSuggestion: () -> Unit,
    val onStartWorkout: (() -> Unit)?,
    val offer: org.openstrap.app.data.AutoStart.Offer?,
    val onRecordOffer: () -> Unit,
    val onDeclineOffer: () -> Unit,
    val running: org.openstrap.app.device.WorkoutState.Recording?,
    val onOpenRunning: () -> Unit,
    val onResumeRunning: () -> Unit,
    val onChooseTiles: () -> Unit,
)

@Composable
private fun TodayList(
    state: DayUiState,
    liveHeartRate: Int?,
    battery: Int?,
    charging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    picker: DayPicker,
    onOpenSleep: () -> Unit,
    onOpenMetric: (Metric) -> Unit,
    recovery: RecoveryResult?,
    onOpenRecovery: (RecoverySection) -> Unit,
    workouts: List<org.openstrap.app.data.WorkoutAnalysis>,
    onOpenWorkout: (Long) -> Unit,
    list: LazyListState,
    extras: TodayExtras,
) = GlowScreenList(contentPadding, list) {
    val rec = recovery?.takeIf { it.date == state.date }
    full { DateHeader(state.date, battery, onPrev, onNext, charging, picker) }

    // On a wide screen the scores, sleep and movement run down the left; what changed and your heart down the right.

    // Glow: the day's three scores at a glance, the leading one as the hero. The only place they appear.
    left { OverviewCard(state, rec, extras.strapReadiness?.takeIf { it.day == state.dayStart }, chargeLeads(state, rec), onOpenRecovery, onOpenSleep) }

    // Mornings only (today, before noon): how you feel, under the score it's there to check.
    val morning = state.date == LocalDate.now() && LocalTime.now().hour < 12
    if (morning) left { CheckInPanel(extras.checkIn, extras.onCheckIn) }

    // Quiet but first: anything the strap warned about this day.
    if (state.alerts.isNotEmpty()) right { AlertsPanel(state.alerts, onOpenMetric) }

    // Mondays: the week just gone against the one before.
    if (extras.history != null && isRecapDay(state)) right { WeeklyRecapPanel(state, recovery, workouts, extras.history) }

    // Dark grey glow: last night, naps included. Right after waking it's what you want to know, so it leads.
    val hasSleep = state.asleepSeconds != null || state.naps.isNotEmpty()
    if (hasSleep && morning) left { SleepPanel(state, onOpenSleep) }

    // Quiet: what the readiness score means, then only the signals that moved away from your normal.
    right { WhatChanged(state, rec?.readiness?.let { Narrative.readinessBody(it) }, extras.tiles, onOpenMetric, extras.onChooseTiles) }

    // Glow: the weekly healthspan estimate (docs/algorithms/healthspan.md); absent until it's worked out.
    if (extras.healthspan != null) right { HealthspanCard(extras.healthspan, extras.onOpenHealthspan, extras.onOpenProfile) }

    // Glow: heart rate, beating at the latest reading.
    right { HeartGlow(state, liveHeartRate, extras.liveEnabled, extras.onLiveToggle) { onOpenMetric(Metric.HeartRate) } }

    // Glow: activity, moving with the day's steps.
    left { ActivityGlow(state, extras.onOpenActivity) }
    val dayWorkouts = workouts.filter { it.workout.start in state.dayStart..state.dayEnd }.sortedBy { it.workout.start }
    val isToday = state.date == LocalDate.now()
    if (extras.offer != null && isToday) left { OfferPanel(extras.offer, extras.onRecordOffer, extras.onDeclineOffer) }
    if (extras.suggestion != null && isToday) left { SuggestionPanel(extras.suggestion, extras.onSaveSuggestion, extras.onDismissSuggestion) }
    // Today's workouts with a way to start one; a past day lists only what was done.
    val start = extras.onStartWorkout.takeIf { isToday }
    if (dayWorkouts.isNotEmpty() || start != null) left { TodayWorkouts(dayWorkouts, onOpenWorkout, start, extras.running?.takeIf { isToday }, liveHeartRate, extras) }

    if (hasSleep && !morning) left { SleepPanel(state, onOpenSleep) }
}

/**
 * The selected day's workouts, in the same glass rows as the Activity screen's list, under a way to start one today:
 * a white pill like the ready screen's Start, so it reads as an action rather than another past workout.
 */
@Composable
private fun TodayWorkouts(
    workouts: List<org.openstrap.app.data.WorkoutAnalysis>, onOpen: (Long) -> Unit, onStart: (() -> Unit)?,
    running: org.openstrap.app.device.WorkoutState.Recording?, liveHeartRate: Int?, extras: TodayExtras,
) {
    DotPanel {
        DotHeader("Workouts")
        // While one records there's no starting another: the pill leads back to it instead.
        if (running != null) WorkoutShortcut(running, liveHeartRate, extras.onOpenRunning, extras.onResumeRunning)
        else if (onStart != null) Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White)
                .pressClickable(onStart, RoundedCornerShape(50)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(org.openstrap.app.ui.theme.PhosphorIcons.PlayFill, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Start workout", color = Color.Black, style = MaterialTheme.typography.titleSmall)
        }
        workouts.forEach { WorkoutLine(it, showDate = false) { onOpen(it.workout.start) } }
    }
}

/* ------------------------------------------------------------------------------------------------ */

private val HOURS = listOf("00", "06", "12", "18", "24")
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(CLOCK)

/** Averages timed points into evenly spaced buckets across [range]; empty buckets are null (gaps). */
internal fun bucket(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.x - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/**
 * The top card: the leading score as one big gauge with its number and a one-line verdict
 * inside, and the other two scores as pills underneath that open their tabs.
 */
@Composable
private fun OverviewCard(
    state: DayUiState, rec: RecoveryResult?, strap: StrapReadinessRow?, chargeLeads: Boolean, onOpen: (RecoverySection) -> Unit, onOpenSleep: () -> Unit,
) {
    val c = rec?.charge
    val chargeNow = c?.latest?.toInt()
    val readinessNow = rec?.readiness?.score
    val readinessBefore = rec?.readinessTrend?.dropLast(1)?.lastOrNull()?.second
    val sleepNow = state.sleep?.score
    val strain = rec?.readiness?.strainAlert == true
    // Teal, gold and the Sleep card's blue, all at the same saturation so no dot outshines the others.
    val charge = Score("Charge", chargeNow, MetricColors.chargeDeep, chargeNow.minus(c?.atWake?.toInt()), "since waking")
    // Without OpenStrap's own score, the strap's stands in as its estimate (as on the Recovery tab), with why ours is missing.
    val estimate = rec?.readiness?.takeIf { readinessNow == null && it.showStrapEstimate }?.let { strap?.score }
    val recovery = Score(
        "Recovery", readinessNow ?: estimate, MetricColors.readinessDeep, readinessNow.minus(readinessBefore), "vs yesterday",
        note = rec?.readiness?.let { readinessState(it, estimate != null, short = true) },
        noteLong = rec?.readiness?.let { readinessState(it, estimate != null, short = false) },
        estimate = estimate != null,
    )
    val sleep = Score("Sleep", sleepNow, SleepGlow.color, sleepNow.minus(state.previousSleepScore), "vs night before")
    val (lead, second) = if (chargeLeads) charge to recovery else recovery to charge
    // The card opens the leading score's section, the bar under it the other one's.
    val (leadSection, secondSection) = if (chargeLeads) RecoverySection.Charge to RecoverySection.Recovery else RecoverySection.Recovery to RecoverySection.Charge
    val verdict = when {
        strain -> org.openstrap.app.data.BodySignalsCopy.SHORT
        lead.value == null || lead.estimate -> null
        chargeLeads -> Narrative.chargeHeadline(lead.value)
        else -> Narrative.readinessHeadline(lead.value, alert = false)
    }

    // The glow sits low and central, under the gauge.
    val style = GlowStyle.Bloom.copy(
        color = overviewColor(lead.value.takeUnless { lead.estimate }, strain),
        coreShape = CoreShape.Radial, coreX = 0.5f, coreY = 0.6f, coreWidth = 0.95f, coreHeight = 0.75f,
        coreBlur = 30.dp, toneWidth = 16.dp, toneShift = -10f, glowStrength = 0.6f, bevel = 0.6f,
        motion = GlowMotion.Breath(5f), depth = 6.dp,
    )
    GlowCard(style, Modifier.fillMaxWidth(), onClick = { onOpen(leadSection) }) {
        Column {
            GlowHeader(lead.label, trailingContent = { Change(lead, withNote = true) })
            GlowArcGauge(
                (lead.value ?: 0) / 100f, lead.color,
                Modifier.padding(top = GlowLayout.TitleGap, start = 12.dp, end = 12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DotMatrixText(lead.value?.toString() ?: "-", dot = 5.5.dp, gap = 2.dp)
                    if (verdict != null) Text(
                        verdict, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // Why there's no score yet, or that it's still learning: small, under the verdict.
                    if (lead.noteLong != null) Text(
                        lead.noteLong, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (verdict != null) 4.dp else 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ScoreBar(second, { onOpen(secondSection) }, Modifier.weight(1f))
                ScoreBar(sleep, onOpenSleep, Modifier.weight(1f))
            }
        }
    }
}

private fun Int?.minus(other: Int?): Int? = if (this == null || other == null) null else this - other

/**
 * A score on the overview, with how far it moved and against what. [note] (under a bar) and [noteLong] (under the
 * gauge) are one-line states: still learning, why there's no score, or that [value] is the strap's [estimate].
 */
private class Score(
    val label: String, val value: Int?, val color: Color, val change: Int?, val against: String,
    val note: String? = null, val noteLong: String? = null, val estimate: Boolean = false,
)

/**
 * The Readiness state in a few words, worded as on the Recovery tab; null once it's settled. "Learning" counts the
 * nights toward a score while withheld, then toward a settled baseline while scored. [short] fits half the card
 * at phone width on one line, so there the strap's estimate says only that; the gauge has room for why.
 */
private fun readinessState(r: Recovery.Readiness, estimate: Boolean, short: Boolean): String? {
    val state = when {
        r.score != null -> if (r.phase == Recovery.Phase.Learning) "Learning ${r.baselineNights}/${Recovery.SETTLED_NIGHTS} nights" else null
        !r.withheld -> "No sleep yet"
        r.phase == Recovery.Phase.Withheld -> "Learning ${r.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
        else -> "Needs HRV data"
    }
    return when {
        !estimate -> state
        short || state == null -> "Strap's estimate"
        // Kept under ~30 characters so the gauge line never wraps at phone width.
        r.phase == Recovery.Phase.Withheld -> "Strap's estimate, ${r.baselineNights}/${Recovery.SCORED_NIGHTS} nights"
        else -> "Strap's estimate, no HRV yet"
    }
}

/** How a score moved: a dot arrow (green up, amber down) and the amount; three level dots when it didn't. */
@Composable
private fun Change(score: Score, withNote: Boolean) {
    val d = score.change ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (d != 0) DotArrow(d > 0, if (d > 0) TrendGood else TrendBad)
        else DotLevel(Color.White.copy(alpha = 0.6f))
        // No change has no amount: the level dots say it, so it never wraps in the narrow column.
        val text = listOfNotNull(if (d == 0) null else "${kotlin.math.abs(d)}", if (withNote) score.against else null).joinToString(" ")
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * One of the other two scores under the gauge, tappable: the label with a chevron and the value with its change on one line, and
 * below them a thin bar filled to the score in its colour. No box: the bar carries the colour and the fill.
 */
@Composable
private fun ScoreBar(score: Score, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.pressClickable(onClick, GlassRowShape, scale = 0.97f).padding(horizontal = 4.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The chevron right after the label marks the half as something to open, as on the cards' headers.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    score.label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(2.dp))
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
            }
            Text("${score.value ?: "-"}", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
            if (score.change != null) {
                Spacer(Modifier.width(6.dp))
                Change(score, withNote = false)
            }
        }
        Spacer(Modifier.height(6.dp))
        // The strap's estimate draws fainter, so it doesn't read as OpenStrap's own score.
        GlowBar((score.value ?: 0) / 100f, if (score.estimate) score.color.copy(alpha = 0.5f) else score.color)
        if (score.note != null) Text(
            score.note, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The card's colour tells how the day looks before you read a number: teal when the leading score is
 * good, amber when it's middling or there are signs of strain, coral when it's low. The rings sit on
 * the dark core, so they stay readable whatever the colour.
 */
internal fun overviewColor(score: Int?, strain: Boolean) = when {
    score == null -> Color(0xFF6E7280)
    strain -> Color(0xFFF2A33A)
    score >= 70 -> MetricColors.hrv
    score >= 50 -> Color(0xFFF2A33A)
    else -> Color(0xFFF0605A)
}

@Composable
private fun HeartGlow(state: DayUiState, live: Int?, liveEnabled: Boolean?, onLiveToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val now = live ?: state.latestHeartRate
    val style = GlowStyle.chart(MetricColors.heart).copy(motion = GlowMotion.Heartbeat(now ?: 62))
    GlowCard(style, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            // A live reading needs no label (the beating glow says it). An older one says "Latest" only when
            // there's no Live switch: an off switch already says it, and "Latest" beside "Live" reads oddly.
            GlowHeader(
                "Heart rate",
                trailing = if (live == null && liveEnabled == null) "Latest" else null,
                // Live readings every few seconds while the app is open; off saves the strap's battery.
                trailingContent = liveEnabled?.let { on ->
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Live", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            GlowSwitch(on, onLiveToggle)
                        }
                    }
                },
            )
            if (now != null) GlowHeroNumber("$now", "bpm", listOfNotNull(state.restingHeartRate?.let { "Resting $it" }))
            GlowChartGap()
            if (state.heartRate.isEmpty()) {
                Text("No heart-rate data yet. Sync your strap.", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
            } else {
                val values = remember(state.heartRate, state.dayStart) { bucket(state.heartRate, state.dayStart..state.dayEnd, 5 * 60) }
                GlowLineChart(values, height = GlowLayout.ChartHeight, labels = HOURS) { i, v -> "${v.toInt()} bpm · ${clock(state.dayStart + i * 5 * 60L)}" }
            }
        }
    }
}

/** One key statistic against its 7-day baseline. */
private class Signal(
    val label: String, val accent: Color, val value: Float?, val baseline: Float?, val unit: String,
    val decimals: Int, val tolerance: Float, val higherIsBetter: Boolean?, val target: Metric,
) {
    val direction get() = Narrative.direction(value, baseline, tolerance)
    fun fmt(v: Float?) = v?.let { "%.${decimals}f".format(it) }
}

/**
 * Key statistics against your normal: each value with its 7-day
 * baseline, and an arrow on the ones that moved. A tile shows as soon as there is a value or a baseline.
 */
@Composable
private fun WhatChanged(state: DayUiState, advice: String?, tiles: List<Metric>, onOpenMetric: (Metric) -> Unit, onChooseTiles: () -> Unit) {
    val b = state.baselines
    val signals = listOfNotNull(
        Signal("Resting HR", MetricColors.heart, state.restingHeartRate?.toFloat(), b.restingHr, "bpm", 0, 1.5f, false, Metric.RestingHr),
        Signal("HRV", MetricColors.hrv, state.hrv.avg()?.toFloat(), b.hrv, "ms", 0, 3f, true, Metric.Hrv),
        Signal("Blood oxygen", MetricColors.spo2, state.spo2.avg()?.toFloat(), b.spo2, "%", 0, 1f, true, Metric.Spo2),
        Signal(
            "Skin temp", MetricColors.temperature, state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat(),
            b.skinTemperature, "°C", 1, 0.3f, null, Metric.SkinTemperature,
        ),
        Signal("Stress", MetricColors.stress, state.stress.avg()?.toFloat(), b.stress, "", 0, 4f, false, Metric.Stress),
        Signal("Breathing", MetricColors.sleepLight, state.breathRate.avg()?.toFloat(), b.breathRate, "/min", 0, 1f, null, Metric.Breathing),
    ).filter { (it.value != null || it.baseline != null) && it.target in tiles }
        .sortedBy { tiles.indexOf(it.target) }
    if (signals.isEmpty() && advice.isNullOrEmpty()) return

    DotPanel {
        // No leading dot: on this panel it would be plain grey, a legend for nothing. Editing the tiles is a
        // proper button where you'd look for it, so the 7-day note moves under the title.
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("What changed", color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
                EditPill(onChooseTiles)
            }
            Text("vs. your last 7 days", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        if (!advice.isNullOrEmpty()) Text(advice, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        // Two columns, so six statistics take three rows instead of a screenful.
        TileGrid(signals) { sg, m ->
            DotStatTile(sg.label, sg.accent, sg.fmt(sg.value), sg.unit, sg.fmt(sg.baseline), sg.direction, sg.higherIsBetter, m) { onOpenMetric(sg.target) }
        }
        if (signals.isNotEmpty() && signals.all { it.baseline == null }) {
            Text("Your normal builds over the first week of wear.", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A small glass pill with the sliders icon: opens the chooser for which statistics show. */
@Composable
private fun EditPill(onClick: () -> Unit) {
    Row(
        Modifier
            .glass(CircleShape, onClick, scale = 0.97f)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PhosphorIcons.SlidersHorizontal, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Edit", color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityGlow(state: DayUiState, onClick: () -> Unit) {
    val goals = LocalGoals.current
    val progress = state.steps.toFloat() / goals.steps
    val style = GlowStyle.chart(MetricColors.steps).copy(motion = GlowMotion.Stride(energy = progress.coerceIn(0f, 1f)))
    GlowCard(style, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            GlowHeader("Activity", chevron = true)
            // How far along the day's goal is; the hourly chart below shows when you moved.
            val goal = if (state.steps >= goals.steps) "Goal reached" else "%d%% of %,d".format(state.steps * 100 / goals.steps, goals.steps)
            GlowHeroNumber(
                "${state.steps}", "steps",
                listOfNotNull(goal, "Active ${state.activeHours} of ${goals.activeHours} h", state.pai?.let { "PAI ${it.total.roundToInt()}" }),
            )
            GlowChartGap()
            GlowDotBars(state.hourlySteps, height = GlowLayout.ChartHeight, labels = HOURS, accent = MetricColors.steps) { h, v ->
                "%02d:00–%02d:00 · %,d steps".format(h, (h + 1) % 24, Math.round(v))
            }
        }
    }
}

@Composable
private fun SleepPanel(state: DayUiState, onClick: () -> Unit) {
    val session = state.sleep
    GlowCard(SleepGlow, Modifier.fillMaxWidth(), onClick = onClick) {
    Column {
        GlowHeader("Sleep", trailing = session?.let { "${clock(it.start)} – ${clock(it.end)}" }, chevron = true)
        state.asleepSeconds?.let { s ->
            GlowHeroNumber(
                "%d:%02d".format(s / 3600, (s % 3600) / 60), "asleep",
                session?.score?.let { listOf("Score $it", Narrative.sleepHeadline(it)) } ?: emptyList(),
            )
        }
        if (state.sleepStages.isNotEmpty()) {
            GlowChartGap()
            GlowSleepStages(state.sleepStages, GlowLayout.ChartHeight)
        }
        if (state.naps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            DotDivider()
            Spacer(Modifier.height(14.dp))
            val total = state.naps.sumOf { it.end - it.start }
            Text(
                "${if (state.naps.size == 1) "1 nap" else "${state.naps.size} naps"} · ${formatDuration(total)} · " +
                    state.naps.joinToString(", ") { clock(it.start) },
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    }
}
