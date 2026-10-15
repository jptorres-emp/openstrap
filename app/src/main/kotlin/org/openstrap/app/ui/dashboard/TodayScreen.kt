package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.BarChart
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Hypnogram
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.HeroPanel
import org.openstrap.app.ui.components.KeyStatRow
import org.openstrap.app.ui.components.ScoreChip
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.MuseoModerno
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val SLEEP_COLORS = mapOf(
    SleepStage.AWAKE to MetricColors.sleepAwake,
    SleepStage.REM to MetricColors.sleepRem,
    SleepStage.LIGHT to MetricColors.sleepLight,
    SleepStage.DEEP to MetricColors.sleepDeep,
)

/** Shared scrolling list with consistent gutters for all dashboard tabs. */
@Composable
fun ScreenList(
    contentPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    index: SectionIndex? = null,
    content: LazyListScope.() -> Unit,
) {
    // The status bar gets an opaque strip so cards don't scroll underneath the clock.
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        state = state,
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { index?.reset(); content() },
    )
}

/** The red full stop after the OpenStrap wordmark. */
private val WordmarkDot = Color(0xFFFF4D5E)

/** Top bar: OpenStrap wordmark and strap battery chip, then ‹ TODAY › centered on its own row. */
@Composable
fun DateHeader(date: LocalDate, battery: Int?, onPrev: () -> Unit, onNext: () -> Unit, charging: Boolean = false) {
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
            if (battery != null) {
                Text(
                    if (charging) "⚡$battery%" else "$battery%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (battery <= 15 || charging) MetricColors.battery(battery) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(Icons.Default.Watch, "Strap connected", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(6.dp).background(MetricColors.battery(battery), CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous day") }
            SectionLabel(
                when (date) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                },
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onNext, enabled = date < LocalDate.now()) { Icon(Icons.Default.ChevronRight, "Next day") }
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
    onOpenSleep: () -> Unit = {},
    /** Jump to a metric's chart on the Heart or Sleep tab. */
    onFocus: (FocusTarget) -> Unit = {},
    recovery: RecoveryResult? = null,
    onOpenRecovery: () -> Unit = {},
) = ScreenList(contentPadding) {
    val rec = recovery?.takeIf { it.date == state.date }
    item { DateHeader(state.date, battery, onPrev, onNext, charging) }

    // Oura's score row: the day at a glance, each chip opening its tab.
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val sleepValue = state.sleep?.score?.toString() ?: state.asleepSeconds?.let { "%d:%02d".format(it / 3600, (it % 3600) / 60) }
            ScoreChip(rec?.readiness?.score?.toString(), "Readiness", Icons.Default.Spa, MetricColors.readiness, onClick = onOpenRecovery)
            ScoreChip(rec?.charge?.latest?.toInt()?.toString(), "Charge", Icons.Default.BatteryChargingFull, MetricColors.charge, onClick = onOpenRecovery)
            ScoreChip(sleepValue, "Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep, onClick = onOpenSleep)
            ScoreChip(compactCount(state.steps), "Steps", Icons.AutoMirrored.Filled.DirectionsWalk, MetricColors.steps)
            ScoreChip(state.stress.avg()?.toString(), "Stress", Icons.Default.Psychology, MetricColors.stress, onClick = { onFocus(FocusTarget.Stress) })
        }
    }

    // Hero: readiness is the day's headline. Before the first scored night it says when it'll arrive;
    // steps live in the Activity card below.
    item {
        if (rec?.readiness?.score != null) {
            ReadinessHero(rec, onClick = onOpenRecovery)
        } else {
            HeroPanel(
                label = "Readiness", value = "—", progress = null, accent = MetricColors.readiness,
                headline = "Sleep with your strap tonight",
                body = "Your first Readiness score arrives in the morning. It compares each night with your own " +
                    "normal, so it gets sharper over the first two weeks.",
                onClick = onOpenRecovery,
            )
        }
    }

    if (rec != null) item {
        ChargeCard(rec, state.dayStart..state.dayEnd, state.sleep?.let { it.start..it.end }, onClick = onOpenRecovery)
    }

    item {
        Panel(onClick = { onFocus(FocusTarget.HeartRate) }) {
            PanelHeader("Heart rate", Icons.Default.Favorite, MetricColors.heart, trailing = if (liveHeartRate != null) "● Live" else null)
            val now = liveHeartRate ?: state.latestHeartRate
            if (now != null) BigValue("$now", "bpm", caption = if (liveHeartRate != null) "Right now" else "Latest")
            if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data yet. Sync your strap.")
            else LineChart(
                state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 140.dp,
                band = state.sleep?.let { it.start..it.end },
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
            KeyStatRow("Resting HR", Icons.Default.Favorite, MetricColors.heart, rhr?.let { "%.0f".format(it) }, "bpm",
                b.restingHr?.let { "%.0f".format(it) }, Narrative.direction(rhr, b.restingHr, 1.5f), higherIsBetter = false, onClick = { onFocus(FocusTarget.RestingHr) })
            val hrv = state.hrv.avg()?.toFloat()
            KeyStatRow("HRV", Icons.Default.MonitorHeart, MetricColors.hrv, hrv?.let { "%.0f".format(it) }, "ms",
                b.hrv?.let { "%.0f".format(it) }, Narrative.direction(hrv, b.hrv, 3f), higherIsBetter = true, onClick = { onFocus(FocusTarget.Hrv) })
            val spo2 = state.spo2.avg()?.toFloat()
            KeyStatRow("Blood oxygen", Icons.Default.Air, MetricColors.spo2, spo2?.let { "%.0f".format(it) }, "%",
                b.spo2?.let { "%.0f".format(it) }, Narrative.direction(spo2, b.spo2, 1f), higherIsBetter = true, onClick = { onFocus(FocusTarget.Spo2) })
            val skin = state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
            KeyStatRow("Skin temp", Icons.Default.Thermostat, MetricColors.temperature, skin?.let { "%.1f".format(it) }, "°C",
                b.skinTemperature?.let { "%.1f".format(it) }, Narrative.direction(skin, b.skinTemperature, 0.3f), higherIsBetter = null, onClick = { onFocus(FocusTarget.SkinTemperature) })
            val stress = state.stress.avg()?.toFloat()
            KeyStatRow("Stress", Icons.Default.Psychology, MetricColors.stress, stress?.let { "%.0f".format(it) }, "",
                b.stress?.let { "%.0f".format(it) }, Narrative.direction(stress, b.stress, 4f), higherIsBetter = false, onClick = { onFocus(FocusTarget.Stress) })
            val breath = state.breathRate.avg()?.toFloat()
            if (breath != null || b.breathRate != null) {
                KeyStatRow("Breathing", Icons.Default.Air, MetricColors.sleepLight, breath?.let { "%.0f".format(it) }, "/min",
                    b.breathRate?.let { "%.0f".format(it) }, Narrative.direction(breath, b.breathRate, 1f), higherIsBetter = null, onClick = { onFocus(FocusTarget.Breathing) })
            }
        }
    }

    item {
        Panel {
            PanelHeader("Activity", Icons.AutoMirrored.Filled.DirectionsWalk, MetricColors.steps, trailing = "Goal %,d".format(STEP_GOAL))
            Row(verticalAlignment = Alignment.Bottom) {
                BigValue("%,d".format(state.steps), "steps", Modifier.weight(1f))
                state.pai?.let {
                    BigValue("${Math.round(it.total)}", "", caption = "PAI")
                    Spacer(Modifier.width(18.dp))
                }
                BigValue("${state.activeHours}", "of $ACTIVE_HOURS_GOAL h", caption = "Active hours")
            }
            BarChart(state.hourlySteps, MetricColors.steps)
        }
    }

    if (state.sleep?.score == null) item {
        Panel(onClick = onOpenSleep) {
            PanelHeader("Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep)
            val asleep = state.asleepSeconds
            if (asleep == null) EmptyHint("No sleep recorded for this night. Wear the strap to bed and it'll show up here.")
            else {
                BigValue(formatDuration(asleep), "", caption = "Time asleep")
                if (state.sleepStages.isNotEmpty()) {
                    Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 110.dp)
                }
            }
        }
    }
}

fun List<ChartPoint>.avg(): Int? = if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()

/** 950 -> "950", 8_420 -> "8.4k", 12_000 -> "12k". */
fun compactCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 10_000 -> "%.1fk".format(n / 1000f).replace(".0k", "k")
    else -> "${n / 1000}k"
}
