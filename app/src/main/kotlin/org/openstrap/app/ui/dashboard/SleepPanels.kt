package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One cell of the sleep vitals grid. */
private class SleepVital(
    val metric: Metric,
    val label: String,
    val value: String,
    val note: String?,
    val accent: Color,
)

/**
 * Lowest and average heart rate, HRV, blood oxygen, breathing and skin temperature during last night's sleep.
 * HRV, blood oxygen, breathing and skin temperature are compared with your previous nights once there are
 * a week of them.
 */
@Composable
internal fun SleepVitalsPanel(state: DayUiState, onOpenMetric: ((Metric) -> Unit)?) {
    val session = state.sleep ?: return
    val tonight = state.nights.lastOrNull()?.takeIf { it.date == state.date }
    val previous = state.nights.dropLast(1)
    fun normal(pick: (SleepNight) -> Float?): History.Normal? =
        History.normal(previous.mapNotNull { n -> pick(n)?.let { DayStat(n.date, it, it, it, 1) } }, state.date.minusDays(1), SLEEP_NIGHTS - 1)
    fun word(value: Float?, normal: History.Normal?, learned: Int): String? = when (History.status(value, normal)) {
        null -> if (value == null) null else "Learning $learned/${History.MIN_NORMAL_DAYS} nights"
        1 -> "Higher than usual"
        -1 -> "Lower than usual"
        else -> "Usual"
    }
    fun learned(pick: (SleepNight) -> Float?) = previous.count { pick(it) != null }.coerceAtMost(History.MIN_NORMAL_DAYS)

    val hr = state.sleepHeartRate.filter { it.x in session.start..session.end }
    val lowest = hr.minByOrNull { it.y }
    val average = session.averageHeartRate ?: hr.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.roundToInt()
    val skinNormal = normal { it.skinTemperature }

    val vitals = listOfNotNull(
        lowest?.let { SleepVital(Metric.HeartRate, "Lowest heart rate", "${it.y.roundToInt()} bpm", "at ${clockOf(it.x)}", MetricColors.heart) },
        average?.let { SleepVital(Metric.HeartRate, "Average heart rate", "$it bpm", "while asleep", MetricColors.heart) },
        tonight?.hrv?.let { v ->
            SleepVital(Metric.Hrv, "HRV", "${v.roundToInt()} ms", word(v, normal { it.hrv }, learned { it.hrv }), MetricColors.hrv)
        },
        tonight?.spo2?.let { v ->
            SleepVital(Metric.Spo2, "Blood oxygen", "${v.roundToInt()} %", word(v, normal { it.spo2 }, learned { it.spo2 }), MetricColors.spo2)
        },
        tonight?.breathing?.let { v ->
            SleepVital(Metric.Breathing, "Breathing", "%.1f br/min".format(v), word(v, normal { it.breathing }, learned { it.breathing }), MetricColors.sleepLight)
        },
        tonight?.skinTemperature?.let { v ->
            // Skin temperature means little on its own; once there's a normal, show how far off it is.
            val value = skinNormal?.let { "%+.1f °C".format(v - it.mean).replace("-", "−") } ?: "%.1f °C".format(v)
            SleepVital(
                Metric.SkinTemperature, "Skin temperature", value,
                word(v, skinNormal, learned { it.skinTemperature }), MetricColors.temperature,
            )
        },
    )
    if (vitals.isEmpty()) return

    Panel {
        PanelHeader("Sleep vitals", PhosphorIcons.Heartbeat, MetricColors.heart, trailing = "During sleep")
        vitals.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { v -> VitalCell(v, Modifier.weight(1f), onOpenMetric) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VitalCell(v: SleepVital, modifier: Modifier, onOpenMetric: ((Metric) -> Unit)?) {
    Column(
        modifier.let { m -> onOpenMetric?.let { open -> m.clickable { open(v.metric) } } ?: m },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(v.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v.value, style = MaterialTheme.typography.titleLarge)
        // Only a reading outside your usual range takes the accent colour.
        val off = v.note?.endsWith("than usual") == true
        v.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (off) v.accent else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * The last seven nights: when each sleep started and ended on a shared clock, with time asleep over each bar,
 * then the week's averages and how regular the timing was (Zepp's bands, from five nights).
 */
@Composable
internal fun SleepWeekPanel(state: DayUiState) {
    val zone = ZoneId.systemDefault()
    val week = state.nights.takeLast(SleepHistory.REGULARITY_NIGHTS)
    val slept = week.filter { it.session != null }
    Panel {
        PanelHeader("This week", PhosphorIcons.CalendarBlank, MetricColors.sleepDeep, trailing = "Last 7 nights")
        if (slept.isEmpty()) {
            EmptyHint("No sleep recorded in the last 7 nights.")
            return@Panel
        }
        val regularity = SleepHistory.regularity(
            state.nights.mapNotNull { n -> n.session?.let { n.date to it } }.toMap(), state.date, zone,
        )
        SleepWindows(week, regularity, zone)

        val asleep = slept.mapNotNull { it.asleepSeconds }
        val scores = slept.mapNotNull { it.session?.score }
        val goal = LocalGoals.current.sleepSeconds
        val goalNights = asleep.count { it >= goal }
        Row(Modifier.fillMaxWidth()) {
            WeekFact("Average asleep", formatDuration(asleep.average().toLong()), Modifier.weight(1f))
            WeekFact("Average score", scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—", Modifier.weight(1f))
            WeekFact("Reached ${formatDuration(goal)}", "$goalNights of ${slept.size}", Modifier.weight(1f))
        }

        if (regularity == null) {
            Text(
                "Sleep regularity shows once ${SleepHistory.MIN_REGULARITY_NIGHTS} of the last 7 nights have sleep (${slept.size} so far).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Regularity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${regularity.score}%", style = MaterialTheme.typography.titleLarge)
                    Text(regularity.band.label, style = MaterialTheme.typography.bodySmall, color = bandColor(regularity.band))
                }
                WeekFact("Avg. fall-asleep", minutesClock(regularity.bedtime), Modifier.weight(1f))
                WeekFact("Avg. wake-up", minutesClock(regularity.wake), Modifier.weight(1f))
            }
            Text(
                "Regularity is how much your fall-asleep and wake-up times moved over the last ${regularity.nights} nights: " +
                    "100% means the same times every night, 50% about an hour either way.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekFact(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun bandColor(band: SleepHistory.Band) = when (band) {
    SleepHistory.Band.VeryRegular, SleepHistory.Band.Regular -> MetricColors.sleepRem
    SleepHistory.Band.FairlyRegular -> MetricColors.sleepLight
    SleepHistory.Band.Irregular -> MetricColors.sleepAwake
}

/**
 * One column per night on a clock running down the chart (evening at the top, morning at the bottom): the bar
 * runs from falling asleep to waking, time asleep sits above it, and dashed lines mark the average times.
 */
@Composable
private fun SleepWindows(week: List<SleepNight>, regularity: SleepHistory.Regularity?, zone: ZoneId) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val axis = TextStyle(color = muted, fontSize = 11.sp)
    val today = axis.copy(color = onSurface)
    val value = TextStyle(color = onSurface, fontSize = 11.sp, fontFamily = org.openstrap.app.ui.theme.FunnelDisplay)
    val bar = MetricColors.sleepDeep
    val goal = LocalGoals.current.sleepSeconds

    // Minutes since the noon before each night, so a night never wraps.
    fun sinceNoon(epoch: Long, wake: Boolean): Int {
        val t = Instant.ofEpochSecond(epoch).atZone(zone).toLocalTime()
        val m = t.hour * 60 + t.minute
        return if (wake) m + 12 * 60 else Math.floorMod(m - 12 * 60, 24 * 60)
    }
    val spans = week.map { n -> n.session?.let { sinceNoon(it.start, false) to sinceNoon(it.end, true).coerceAtLeast(sinceNoon(it.start, false) + 1) } }
    val known = spans.filterNotNull()
    val top = ((known.minOfOrNull { it.first } ?: (10 * 60)) / 60 - 1).coerceAtLeast(0) * 60
    val bottom = ((known.maxOfOrNull { it.second } ?: (20 * 60)) / 60 + 1).coerceAtMost(36) * 60

    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 40.dp.toPx()
        val labelH = 18.dp.toPx()
        val valueH = 18.dp.toPx()
        val chartTop = valueH
        val chartBottom = size.height - labelH
        val slot = (size.width - left) / week.size
        fun yOf(m: Int) = chartTop + (chartBottom - chartTop) * (m - top).toFloat() / (bottom - top).coerceAtLeast(1)
        fun clockLabel(m: Int) = "%02d:00".format(Math.floorMod(m / 60 + 12, 24))

        // Hour marks: top, bottom and every three hours between.
        var m = top
        while (m <= bottom) {
            val y = yOf(m)
            drawLine(muted.copy(alpha = 0.15f), Offset(left, y), Offset(size.width, y), 1.dp.toPx())
            val t = measurer.measure(clockLabel(m), axis)
            drawText(t, topLeft = Offset(0f, y - t.size.height / 2))
            m += 3 * 60
        }
        regularity?.let { r ->
            val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
            listOf(r.bedtime - 12 * 60, r.wake + 12 * 60).forEach { avg ->
                val y = yOf(avg)
                drawLine(onSurface.copy(alpha = 0.5f), Offset(left, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
        }
        val width = slot * 0.42f
        week.forEachIndexed { i, n ->
            val cx = left + slot * i + slot / 2
            val name = n.date.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).take(2)
            val label = measurer.measure(name, if (i == week.lastIndex) today else axis)
            drawText(label, topLeft = Offset(cx - label.size.width / 2, chartBottom + 4.dp.toPx()))
            val span = spans[i]
            if (span == null) {
                drawCircle(muted.copy(alpha = 0.35f), 2.5.dp.toPx(), Offset(cx, (chartTop + chartBottom) / 2))
                return@forEachIndexed
            }
            val y0 = yOf(span.first)
            val y1 = yOf(span.second)
            val met = (n.asleepSeconds ?: 0) >= goal
            drawRoundRect(
                if (i == week.lastIndex) bar else bar.copy(alpha = 0.7f),
                Offset(cx - width / 2, y0), Size(width, (y1 - y0).coerceAtLeast(width)), CornerRadius(width / 2),
            )
            val asleep = n.asleepSeconds ?: 0
            val text = measurer.measure(
                "%d:%02d".format(asleep / 3600, asleep % 3600 / 60),
                if (met) value.copy(color = MetricColors.sleepRem) else value,
            )
            drawText(text, topLeft = Offset(cx - text.size.width / 2, y0 - text.size.height - 2.dp.toPx()))
        }
    }
}

private fun minutesClock(minutes: Int): String = SleepHistory.clockMinutes(minutes).let { "%02d:%02d".format(it / 60, it % 60) }

private fun clockOf(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) }
