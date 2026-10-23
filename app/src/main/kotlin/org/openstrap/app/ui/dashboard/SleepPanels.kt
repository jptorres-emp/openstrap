package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import org.openstrap.app.data.SleepRegularity
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.theme.MetricColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A row of the Sleep tab's night vitals: the [value] in dot-matrix digits (null while there's none), what it says
 * against your previous nights ([status]) and which way it's off ([direction], null with no normal yet).
 */
internal class NightVital(
    val metric: Metric,
    val label: String,
    val color: Color,
    val value: String?,
    val status: String,
    val direction: Int? = null,
)

/**
 * The six night vitals the Sleep tab lists (spec 5.2), always all six, in order: lowest heart rate, HRV, breathing
 * rate, blood oxygen, skin temperature (as its change from your normal once there is one) and average heart rate.
 * HRV, breathing, blood oxygen and skin temperature are read against your previous nights once there are a week of them.
 */
internal fun nightVitals(state: DayUiState): List<NightVital> {
    val session = state.sleep
    val tonight = state.nights.lastOrNull()?.takeIf { it.date == state.date && session != null }
    val previous = state.nights.dropLast(1)
    fun normal(pick: (SleepNight) -> Float?): History.Normal? =
        History.normal(previous.mapNotNull { n -> pick(n)?.let { DayStat(n.date, it, it, it, 1) } }, state.date.minusDays(1), SLEEP_NIGHTS - 1)
    val missing = if (session == null) "No sleep recorded" else "No reading last night"
    fun against(metric: Metric, label: String, color: Color, pick: (SleepNight) -> Float?, show: (Float, History.Normal?) -> String): NightVital {
        val v = tonight?.let(pick) ?: return NightVital(metric, label, color, null, missing)
        val n = normal(pick)
        val direction = History.status(v, n)
        val status = if (n == null) {
            "Learning, ${previous.count { pick(it) != null }.coerceAtMost(History.MIN_NORMAL_DAYS)} of ${History.MIN_NORMAL_DAYS} nights"
        } else TodayCopy.normalWord(direction)
        return NightVital(metric, label, color, show(v, n), status, direction.takeIf { n != null })
    }
    val hr = session?.let { s -> state.sleepHeartRate.filter { it.x in s.start..s.end } }.orEmpty()
    val lowest = hr.minByOrNull { it.y }
    val average = session?.averageHeartRate ?: hr.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.roundToInt()
    return listOf(
        NightVital(
            Metric.HeartRate, "Lowest heart rate", MetricColors.heart, lowest?.y?.roundToInt()?.toString(),
            lowest?.let { "At ${clockOf(it.x)}" } ?: missing,
        ),
        against(Metric.Hrv, "HRV", MetricColors.hrv, { it.hrv }) { v, _ -> "${v.roundToInt()}" },
        against(Metric.Breathing, "Breathing rate", MetricColors.sleepLight, { it.breathing }) { v, _ -> "%.1f".format(v) },
        against(Metric.Spo2, "Blood oxygen", MetricColors.spo2, { it.spo2 }) { v, _ -> "${v.roundToInt()}%" },
        // Skin temperature means little on its own; once there's a normal, show how far off it is.
        against(Metric.SkinTemperature, "Skin temperature", MetricColors.temperature, { it.skinTemperature }) { v, n ->
            n?.let { "%+.1f".format(v - it.mean) } ?: "%.1f".format(v)
        },
        NightVital(Metric.HeartRate, "Average heart rate", MetricColors.heart, average?.toString(), if (average != null) "While asleep" else missing),
    )
}

/**
 * The last seven nights: when each sleep started and ended on a shared clock, with time asleep over each bar,
 * then the week's averages and how regular it was. Regularity is the Sleep Regularity Index from RecoveryResult
 * (SleepRegularity.Result.sri), banded at the UK Biobank quintile cuts; null [regularity] means it is still loading.
 */
internal class SleepWeek(
    val nights: List<SleepNight>,
    val slept: Int,
    val usual: SleepHistory.UsualTimes?,
    val regularity: SleepRegularity.Result?,
    val averageAsleep: String,
    val averageScore: String,
    val goalNights: Int,
) {
    /** The SRI shown as "Regularity", or null when withheld or loading. */
    val sri: Float? get() = regularity?.sri
    val band: SleepRegularity.Band? get() = regularity?.band
    val learningNote: String? get() = regularity?.takeIf { it.sri == null }?.let {
        "Not enough nights yet. Regularity needs ${SleepRegularity.MIN_VALID_PAIRS / 60} h of wear compared day to day " +
            "(${it.validPairs / 60} h so far), about 5 days worn around the clock."
    }
    val regularityNote: String? get() = regularity?.takeIf { it.sri != null }?.let {
        "How alike your sleep and wake were, minute by minute, from one day to the next over the last 7 days " +
            "(${it.validPairs / 60} h compared). 100 is the same every day; half of UK Biobank adults scored 74–86."
    }
}

/** The last seven nights' facts, or null when none of them has sleep. [regularity] comes from the selected day's recovery. */
internal fun sleepWeek(
    state: DayUiState, goalSeconds: Long, regularity: SleepRegularity.Result?, zone: ZoneId = ZoneId.systemDefault(),
): SleepWeek? {
    val week = state.nights.takeLast(SleepHistory.WEEK_NIGHTS)
    val slept = week.filter { it.session != null }
    if (slept.isEmpty()) return null
    val asleep = slept.mapNotNull { it.asleepSeconds }
    val scores = slept.mapNotNull { it.session?.score }
    return SleepWeek(
        nights = week,
        slept = slept.size,
        usual = SleepHistory.usualTimes(state.nights.mapNotNull { n -> n.session?.let { n.date to it } }.toMap(), state.date, zone),
        regularity = regularity,
        averageAsleep = formatDuration(asleep.average().toLong()),
        averageScore = scores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toString() ?: "—",
        goalNights = asleep.count { it >= goalSeconds },
    )
}

internal fun bandColor(band: SleepRegularity.Band) = when (band) {
    SleepRegularity.Band.VeryRegular, SleepRegularity.Band.Regular -> MetricColors.sleepRem
    SleepRegularity.Band.Fair -> MetricColors.sleepLight
    SleepRegularity.Band.Irregular -> MetricColors.sleepAwake
}

/**
 * One column per night on a clock running down the chart (evening at the top, morning at the bottom): the bar
 * runs from falling asleep to waking, time asleep sits above it, and dashed lines mark the average times.
 */
@Composable
internal fun SleepWindows(week: List<SleepNight>, usual: SleepHistory.UsualTimes?, zone: ZoneId) {
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
        usual?.let { r ->
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

internal fun minutesClock(minutes: Int): String = SleepHistory.clockMinutes(minutes).let { "%02d:%02d".format(it / 60, it % 60) }

private fun clockOf(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).let { "%02d:%02d".format(it.hour, it.minute) }
