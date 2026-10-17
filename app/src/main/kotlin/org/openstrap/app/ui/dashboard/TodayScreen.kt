package org.openstrap.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.openstrap.app.device.SyncState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.BarChart
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.HeroPanel
import org.openstrap.app.ui.components.Hypnogram
import org.openstrap.app.ui.components.KeyStatRow
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.ScoreChip
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.MuseoModerno
import org.openstrap.app.ui.theme.PhosphorIcons

val SLEEP_COLORS = mapOf(
    SleepStage.AWAKE to MetricColors.sleepAwake,
    SleepStage.REM to MetricColors.sleepRem,
    SleepStage.LIGHT to MetricColors.sleepLight,
    SleepStage.DEEP to MetricColors.sleepDeep,
)

val SLEEP_NAMES = mapOf(
    SleepStage.AWAKE to "Awake",
    SleepStage.REM to "REM",
    SleepStage.LIGHT to "Light",
    SleepStage.DEEP to "Deep",
)

/** Shared scrolling list with consistent gutters for all dashboard tabs. */
@Composable
fun ScreenList(
    contentPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    /** Space between the screen edge and the cards. */
    gutter: Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
    // The status bar gets an opaque strip so cards don't scroll underneath the clock.
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        state = state,
        contentPadding = PaddingValues(
            start = gutter, end = gutter,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Opens "Your strap" from the header chip on every dashboard tab; provided by the app root. */
val LocalOpenStrap = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** The red full stop after the OpenStrap wordmark. */
private val WordmarkDot = Color(0xFFFF4D5E)

/**
 * Top bar: OpenStrap wordmark and strap battery chip, then ‹ TODAY › centered on its own row. Tapping the
 * day opens a calendar of the days that have data.
 */
/** A small ring that fills as each data type syncs, with a label. */
@Composable
private fun SyncingBadge(sync: SyncState.Running) {
    val ring = Modifier.size(12.dp)
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = sync.step }, verticalAlignment = Alignment.CenterVertically) {
        val progress = sync.progress
        if (progress != null) {
            val shown by animateFloatAsState(progress, label = "sync progress")
            CircularProgressIndicator(progress = { shown }, ring, color = color, strokeWidth = 1.5.dp, trackColor = color.copy(alpha = 0.2f))
        } else {
            CircularProgressIndicator(ring, color = color, strokeWidth = 1.5.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text("Syncing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DateHeader(
    date: LocalDate,
    battery: Int?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    charging: Boolean = false,
    picker: DayPicker = DayPicker(),
    /** A sync in progress, shown beside the strap status. */
    sync: SyncState.Running? = null,
) {
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    if (calendarOpen) DayCalendarSheet(date, picker.available, picker.onPick, onDismiss = { calendarOpen = false })
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("OpenStrap")
                    withStyle(SpanStyle(color = WordmarkDot)) { append(".") }
                },
                Modifier.weight(1f),
                fontFamily = MuseoModerno,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (sync != null) {
                SyncingBadge(sync)
                Spacer(Modifier.width(10.dp))
            }
            // WHOOP's strap chip: always there, and it opens "Your strap".
            val openStrap = LocalOpenStrap.current
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .let { if (openStrap != null) it.clickable(onClickLabel = "Open your strap", onClick = openStrap) else it }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (battery != null) {
                    Text(
                        if (charging) "⚡$battery%" else "$battery%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (battery <= 15 || charging) MetricColors.battery(battery) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        PhosphorIcons.Watch, if (battery != null) "Strap connected" else "Strap not connected", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (battery != null) Box(Modifier.size(6.dp).background(MetricColors.battery(battery), CircleShape))
                }
            }
        }
        // Pull to sync opens here, under the wordmark, and pushes the day and everything below it down.
        org.openstrap.app.ui.components.PullSyncReveal()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(PhosphorIcons.CaretLeft, "Previous day") }
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Pick a day") { calendarOpen = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    when (date) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(PhosphorIcons.CaretDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, enabled = date < LocalDate.now()) { Icon(PhosphorIcons.CaretRight, "Next day") }
        }
    }
}

@Composable
fun TodayScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    battery: Int?,
    charging: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
    picker: DayPicker = DayPicker(),
    onOpenSleep: () -> Unit = {},
    /** Open a metric's detail on top of Today. */
    onOpenMetric: (Metric) -> Unit = {},
    recovery: RecoveryResult? = null,
    onOpenRecovery: () -> Unit = {},
    sync: SyncState.Running? = null,
    workouts: List<org.openstrap.app.data.WorkoutAnalysis> = emptyList(),
    onOpenWorkout: (Long) -> Unit = {},
) = ScreenList(contentPadding) {
    val rec = recovery?.takeIf { it.date == state.date }
    val chargeLeads = chargeLeads(state, rec)
    item { DateHeader(state.date, battery, onPrev, onNext, charging, picker, sync) }

    // Oura's score row: the day at a glance, each chip opening its tab.
    item {
        // Equal cells, so the gaps stay even however many chips there are.
        Row(Modifier.fillMaxWidth()) {
            val goals = LocalGoals.current
            val cell = Modifier.weight(1f)
            val sleepValue = state.sleep?.score?.toString() ?: state.asleepSeconds?.let { "%d:%02d".format(it / 3600, (it % 3600) / 60) }
            // Whichever score isn't the hero sits first in the row.
            if (chargeLeads) ScoreChip(rec?.readiness?.score?.toString(), "Recovery", PhosphorIcons.Plant, MetricColors.readiness, cell, rec?.readiness?.score?.let { it / 100f }, onClick = onOpenRecovery)
            else ScoreChip(rec?.charge?.latest?.toInt()?.toString(), "Charge", PhosphorIcons.Lightning, MetricColors.chargeAt(rec?.charge?.latest), cell, rec?.charge?.latest?.let { it.toFloat() / 100f }, onClick = onOpenRecovery)
            // The sleep ring is the score, or time asleep against your goal when there's no score.
            val sleepProgress = state.sleep?.score?.let { it / 100f } ?: state.asleepSeconds?.let { it / goals.sleepSeconds.toFloat() }
            ScoreChip(sleepValue, "Sleep", PhosphorIcons.Moon, MetricColors.sleepDeep, cell, sleepProgress, onClick = onOpenSleep)
            ScoreChip(compactCount(state.steps), "Steps", PhosphorIcons.Footprints, MetricColors.steps, cell, state.steps.toFloat() / goals.steps)
            ScoreChip(state.stress.avg()?.toString(), "Stress", PhosphorIcons.Brain, MetricColors.stress, cell, state.stress.avg()?.let { it / 100f }, onClick = { onOpenMetric(Metric.Stress) })
        }
    }

    // Hero: Readiness in the morning (the day's verdict), Charge once you've been up a few hours (the
    // live battery). Before the first scored night it says when readiness will arrive.
    item {
        if (chargeLeads && rec != null) {
            ChargeHero(rec, onClick = onOpenRecovery)
        } else if (rec?.readiness?.score != null) {
            ReadinessHero(rec, onClick = onOpenRecovery)
        } else {
            HeroPanel(
                label = "Recovery", value = "—", progress = null, accent = MetricColors.readiness,
                headline = "Sleep with your strap tonight",
                body = "Your first Recovery score arrives in the morning. It compares each night with your own " +
                    "normal, so it gets sharper over the first two weeks.",
                onClick = onOpenRecovery,
            )
        }
    }

    if (rec != null) item {
        ChargeCard(rec, state.dayStart..state.dayEnd, state.sleep?.let { it.start..it.end }, onClick = onOpenRecovery, showValues = !chargeLeads)
    }

    item {
        Panel(onClick = { onOpenMetric(Metric.HeartRate) }) {
            PanelHeader("Heart rate", PhosphorIcons.Heart, MetricColors.heart, trailing = if (liveHeartRate != null) "● Live" else null, chevron = true)
            val now = liveHeartRate ?: state.latestHeartRate
            if (now != null) BigValue("$now", "bpm", caption = if (liveHeartRate != null) "Right now" else "Latest")
            if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data yet. Sync your strap.")
            else LineChart(
                state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 140.dp,
                band = state.sleep?.let { it.start..it.end }, unit = "bpm",
            )
        }
    }

    item {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Key statistics", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text("vs. previous 7 days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    item {
        val b = state.baselines
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rhr = state.restingHeartRate?.toFloat()
            KeyStatRow("Resting HR", PhosphorIcons.Heart, MetricColors.heart, rhr?.let { "%.0f".format(it) }, "bpm",
                b.restingHr?.let { "%.0f".format(it) }, Narrative.direction(rhr, b.restingHr, 1.5f), higherIsBetter = false, onClick = { onOpenMetric(Metric.RestingHr) })
            val hrv = state.hrv.avg()?.toFloat()
            KeyStatRow("HRV", PhosphorIcons.Heartbeat, MetricColors.hrv, hrv?.let { "%.0f".format(it) }, "ms",
                b.hrv?.let { "%.0f".format(it) }, Narrative.direction(hrv, b.hrv, 3f), higherIsBetter = true, onClick = { onOpenMetric(Metric.Hrv) })
            val spo2 = state.spo2.avg()?.toFloat()
            KeyStatRow("Blood oxygen", PhosphorIcons.Drop, MetricColors.spo2, spo2?.let { "%.0f".format(it) }, "%",
                b.spo2?.let { "%.0f".format(it) }, Narrative.direction(spo2, b.spo2, 1f), higherIsBetter = true, onClick = { onOpenMetric(Metric.Spo2) })
            val skin = state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
            KeyStatRow("Skin temp", PhosphorIcons.ThermometerSimple, MetricColors.temperature, skin?.let { "%.1f".format(it) }, "°C",
                b.skinTemperature?.let { "%.1f".format(it) }, Narrative.direction(skin, b.skinTemperature, 0.3f), higherIsBetter = null, onClick = { onOpenMetric(Metric.SkinTemperature) })
            val stress = state.stress.avg()?.toFloat()
            KeyStatRow("Stress", PhosphorIcons.Brain, MetricColors.stress, stress?.let { "%.0f".format(it) }, "",
                b.stress?.let { "%.0f".format(it) }, Narrative.direction(stress, b.stress, 4f), higherIsBetter = false, onClick = { onOpenMetric(Metric.Stress) })
            val breath = state.breathRate.avg()?.toFloat()
            if (breath != null || b.breathRate != null) {
                KeyStatRow("Breathing", PhosphorIcons.Wind, MetricColors.sleepLight, breath?.let { "%.0f".format(it) }, "/min",
                    b.breathRate?.let { "%.0f".format(it) }, Narrative.direction(breath, b.breathRate, 1f), higherIsBetter = null, onClick = { onOpenMetric(Metric.Breathing) })
            }
        }
    }

    item {
        val goals = LocalGoals.current
        Panel {
            PanelHeader("Activity", PhosphorIcons.Footprints, MetricColors.steps, trailing = "Goal %,d".format(goals.steps))
            Row(verticalAlignment = Alignment.Bottom) {
                BigValue("%,d".format(state.steps), "steps", Modifier.weight(1f))
                state.pai?.let {
                    BigValue("${Math.round(it.total)}", "", caption = "PAI")
                    Spacer(Modifier.width(18.dp))
                }
                BigValue("${state.activeHours}", "of ${goals.activeHours} h", caption = "Active hours")
            }
            BarChart(state.hourlySteps, MetricColors.steps) { hour, steps -> "%02d:00–%02d:00 · %,d steps".format(hour, (hour + 1) % 24, Math.round(steps)) }
        }
    }

    val dayWorkouts = workouts.filter { it.workout.start in state.dayStart..state.dayEnd }.sortedBy { it.workout.start }
    if (dayWorkouts.isNotEmpty()) item { org.openstrap.app.ui.workout.WorkoutsPanel(dayWorkouts, onOpenWorkout) }

    if (state.sleep?.score == null) item {
        Panel(onClick = onOpenSleep) {
            PanelHeader("Sleep", PhosphorIcons.Moon, MetricColors.sleepDeep, chevron = true)
            val asleep = state.asleepSeconds
            if (asleep == null) EmptyHint("No sleep recorded for this night. Wear the strap to bed and it'll show up here.")
            else {
                BigValue(formatDuration(asleep), "", caption = "Time asleep")
                if (state.sleepStages.isNotEmpty()) {
                    Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 110.dp, stageNames = SLEEP_NAMES)
                }
            }
        }
    }

    if (state.naps.isNotEmpty()) item { NapsPanel(state.naps, onClick = onOpenSleep) }
}

/** How long after waking Readiness stays the headline before Charge takes over. */
private const val READINESS_HOURS_AFTER_WAKING = 3

/**
 * Whether Charge is the Today hero: only for today, once Charge has a value, and once you've been awake
 * [READINESS_HOURS_AFTER_WAKING] hours (or from noon when no sleep was recorded). Past days lead with
 * their Readiness, the day's verdict.
 */
internal fun chargeLeads(state: DayUiState, rec: RecoveryResult?): Boolean {
    if (state.date != LocalDate.now() || rec?.charge?.latest == null) return false
    val now = java.time.Instant.now().epochSecond
    val wake = state.sleep?.end
    return if (wake != null) now - wake >= READINESS_HOURS_AFTER_WAKING * 3600L
    else java.time.LocalTime.now().hour >= 12
}

fun List<ChartPoint>.avg(): Int? = if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()

/** 950 -> "950", 8_420 -> "8.4k", 12_000 -> "12k". */
fun compactCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 10_000 -> "%.1fk".format(n / 1000f).replace(".0k", "k")
    else -> "${n / 1000}k"
}
