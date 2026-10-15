package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.openstrap.app.ui.components.StatRing
import org.openstrap.app.ui.components.VitalPill
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
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
fun ScreenList(contentPadding: PaddingValues, content: LazyListScope.() -> Unit) {
    // The status bar gets an opaque strip so cards don't scroll underneath the clock.
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** WHOOP-style top bar: ‹ TODAY › centered, strap battery chip on the right. */
@Composable
fun DateHeader(date: LocalDate, battery: Int?, onPrev: () -> Unit, onNext: () -> Unit, charging: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(56.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
        Row(
            Modifier.width(56.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
) = ScreenList(contentPadding) {
    item { DateHeader(state.date, battery, onPrev, onNext, charging) }

    item {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatRing("Steps", compactCount(state.steps), state.steps / STEP_GOAL.toFloat(), MetricColors.steps)
                StatRing(
                    "Sleep",
                    state.asleepSeconds?.let { "%dh%02d".format(it / 3600, (it % 3600) / 60) } ?: "—",
                    (state.asleepSeconds ?: 0L) / SLEEP_GOAL_SECONDS.toFloat(),
                    MetricColors.sleepDeep,
                )
                StatRing("Active hrs", "${state.activeHours}", state.activeHours / ACTIVE_HOURS_GOAL.toFloat(), MetricColors.stress)
            }
        }
    }

    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mod = Modifier.weight(1f)
            VitalPill((liveHeartRate ?: state.latestHeartRate)?.toString(), "bpm", Icons.Default.Favorite, MetricColors.heart, mod)
            VitalPill(state.restingHeartRate?.toString(), "rest", Icons.Default.MonitorHeart, MetricColors.heart, mod)
            VitalPill(state.hrv.avg()?.toString(), "ms", Icons.Default.MonitorHeart, MetricColors.hrv, mod)
            VitalPill(state.spo2.avg()?.let { "$it" }, "% SpO₂", Icons.Default.Air, MetricColors.spo2, mod)
            VitalPill(state.stress.avg()?.toString(), "stress", Icons.Default.Psychology, MetricColors.stress, mod)
        }
    }

    item {
        Panel {
            PanelHeader("Heart rate", Icons.Default.Favorite, MetricColors.heart, trailing = if (liveHeartRate != null) "● Live" else null)
            if (state.heartRate.isEmpty()) EmptyHint("No heart-rate data yet. Sync your strap.")
            else LineChart(
                state.heartRate, MetricColors.heart, state.dayStart..state.dayEnd, height = 150.dp,
                band = state.sleep?.let { it.start..it.end },
            )
        }
    }

    item {
        Panel {
            PanelHeader("Sleep", Icons.Default.Bedtime, MetricColors.sleepDeep)
            val asleep = state.asleepSeconds
            if (asleep == null) EmptyHint("No sleep recorded for this night.")
            else {
                BigValue(formatDuration(asleep), "", caption = "Time asleep")
                if (state.sleepStages.isNotEmpty()) {
                    Hypnogram(state.sleepStages, SLEEP_COLORS, DayViewModel.SLEEP_STAGE_ROWS, height = 110.dp)
                }
            }
        }
    }

    item {
        Panel {
            PanelHeader("Steps", Icons.AutoMirrored.Filled.DirectionsWalk, MetricColors.steps, trailing = "Goal %,d".format(STEP_GOAL))
            BigValue("%,d".format(state.steps), "steps")
            BarChart(state.hourlySteps, MetricColors.steps)
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
