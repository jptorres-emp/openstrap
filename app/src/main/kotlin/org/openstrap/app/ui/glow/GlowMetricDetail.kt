package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.DayStat
import org.openstrap.app.data.History
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.ABOUT
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.RELATED
import org.openstrap.app.ui.dashboard.Range
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.dashboard.colorOf
import org.openstrap.app.ui.theme.PhosphorIcons
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/* ------------------------------------------- Shared pieces ------------------------------------------ */

/** Back arrow, the screen's title in the glow title face, and the day or subtitle it shows. */
@Composable
internal fun GlowDetailHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = GlowType.title.copy(fontSize = GlowType.title.fontSize * 1.25f))
            if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun dayName(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
}

/** A row of choices on a dotted pill; the chosen one is a soft white pill. */
@Composable
internal fun GlowSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .pressClickable({ onSelect(i) }, RoundedCornerShape(50), scale = 0.97f)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color.White else Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/* ------------------------------------------- Metric detail ------------------------------------------ */

private val HOURS = listOf("00", "06", "12", "18", "24")

/**
 * One metric in the glow language. A single card glows in the metric's colour: the number and chart for the
 * chosen range (the day, or a week, month or year of daily values against your normal). Your normal, the range's
 * lowest and highest, what the metric means and related metrics sit in dot panels underneath.
 */
@Composable
fun GlowMetricDetailScreen(
    metric: Metric,
    state: DayUiState,
    history: DailyHistory,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    onLiveToggle: (Boolean) -> Unit,
    onOpenMetric: (Metric) -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var rangeIndex by rememberSaveable(metric) { mutableIntStateOf(0) }
    val range = Range.entries[rangeIndex]
    val end = state.date
    val from = end.minusDays(maxOf(range.days, History.NORMAL_DAYS) - 1L)
    val stats by remember(metric, end, from) { history(metric.daily, from, end) }.collectAsState(null)
    val normal = stats?.let { History.normal(it, end) }
    val list = rememberLazyListState()

    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        ScreenList(contentPadding, list, gutter = GlowLayout.Gutter) {
            item { GlowDetailHeader(metric.title, dayName(state.date), onBack) }
            item { GlowSegments(Range.entries.map { it.label }, rangeIndex, { rangeIndex = it }) }
            if (range == Range.Day) {
                item {
                    DayCard(metric, state, normal, if (metric == Metric.HeartRate && state.date == LocalDate.now() && canUseLive) liveEnabled else null, liveHeartRate, onLiveToggle)
                }
            } else {
                item { HistoryCard(metric, range, stats, normal, end) }
                val summary = stats?.let { History.summary(it, end.minusDays(range.days - 1L), end) }
                if (summary != null) item { LowHighPanel(metric, range, summary) }
            }
            item { NormalPanel(metric, normal, stats?.count { it.date in end.minusDays(History.NORMAL_DAYS - 1L)..end } ?: 0) }
            item { AboutPanel(metric) }
            val related = RELATED[metric].orEmpty()
            if (related.isNotEmpty()) item { RelatedPanel(related, onOpenMetric) }
        }
    }
}

/** Averages timed points into evenly spaced buckets across [range]; empty buckets are gaps. */
private fun evenly(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.x - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** "Usually 65" and where the value sits against your normal, as hero facts. */
private fun normalFacts(metric: Metric, value: Float?, normal: History.Normal?): List<String> = listOfNotNull(
    normal?.let { "Usually ${metric.format(it.mean)}" },
    when (History.status(value, normal)) {
        1 -> "Above your normal"
        -1 -> "Below your normal"
        0 -> "Within your normal"
        else -> null
    },
)

/** The selected day: today's number, how it compares, and the day's own chart. */
@Composable
private fun DayCard(metric: Metric, state: DayUiState, normal: History.Normal?, liveEnabled: Boolean?, live: Int?, onLiveToggle: (Boolean) -> Unit) {
    val color = colorOf(metric)
    val day = state.dayStart..state.dayEnd
    // The day's series (and its window) for metrics drawn as a line.
    val (series, window) = when (metric) {
        Metric.HeartRate -> state.heartRate to day
        Metric.Hrv -> state.hrv to day
        Metric.Spo2 -> state.spo2 to day
        Metric.Stress -> state.stress to day
        Metric.SkinTemperature -> state.skinTemperature to day
        Metric.Breathing -> state.breathRate to (state.sleep?.let { it.start..it.end } ?: day)
        else -> emptyList<ChartPoint>() to day
    }
    val value: Float? = when (metric) {
        Metric.HeartRate -> (live ?: state.latestHeartRate)?.toFloat()
        Metric.RestingHr -> state.restingHeartRate?.toFloat()
        Metric.Steps -> state.steps.toFloat()
        else -> series.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
    }
    val style = GlowStyle.chart(color).copy(motion = if (metric == Metric.HeartRate) GlowMotion.Heartbeat(value?.toInt() ?: 62) else GlowMotion.Breath(5f))
    GlowCard(style, Modifier.fillMaxWidth()) {
        Column {
            GlowHeader(
                when (metric) {
                    Metric.HeartRate -> if (live != null) "Now" else "Latest"
                    Metric.RestingHr -> "Resting"
                    Metric.Steps -> "Total"
                    Metric.Breathing -> "Average during sleep"
                    else -> "Day average"
                },
                trailingContent = liveEnabled?.let { on ->
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Live", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(8.dp))
                            GlowSwitch(on, onLiveToggle, accent = color)
                        }
                    }
                },
            )
            val facts = when (metric) {
                // Heart rate's usual range is a daily average, not a reading; the day's spread says more here.
                Metric.HeartRate -> series.takeIf { it.isNotEmpty() }?.let { s ->
                    listOf("Low ${s.minOf { it.y }.toInt()} · high ${s.maxOf { it.y }.toInt()}", "Average ${s.map { it.y }.average().toInt()}")
                }.orEmpty()
                else -> normalFacts(metric, value, normal)
            }
            if (value == null) {
                Spacer(Modifier.height(GlowLayout.TitleGap))
                Text(
                    if (metric == Metric.Breathing) "Breathing rate is measured during sleep." else "No ${metric.title.lowercase()} for this day yet.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            GlowHeroNumber(metric.format(value).replace(",", ""), metric.unit.ifEmpty { "/ 100" }, facts)
            when (metric) {
                Metric.Steps -> {
                    GlowChartGap()
                    GlowDotBars(state.hourlySteps, height = GlowLayout.ChartHeight, labels = HOURS, accent = Color.White) { h, v ->
                        "%02d:00–%02d:00 · %,d steps".format(h, (h + 1) % 24, Math.round(v))
                    }
                }
                Metric.RestingHr -> if (state.restingTrend.any { it.value != null }) {
                    GlowChartGap()
                    GlowTrend(state.restingTrend.map { GlowDay(it.label, it.value) }, height = GlowLayout.ChartHeight)
                }
                else -> if (series.isNotEmpty()) {
                    // Readings come every minute for heart rate, sparser for the rest.
                    val bucket = if (metric == Metric.HeartRate) 5 * 60L else 15 * 60L
                    val values = remember(series, window) { evenly(series, window, bucket) }
                    val labels = if (window == day) HOURS else listOf(clock(window.first), clock(window.last))
                    GlowChartGap()
                    GlowLineChart(values, height = GlowLayout.ChartHeight, labels = labels, format = metric::format) { i, v ->
                        "${metric.format(v)} ${metric.unit} · ${clock(window.first + i * bucket)}".replace("  ", " ")
                    }
                }
            }
        }
    }
}

/** A week, month or year of daily values (weekly for a year), your normal as a band. */
@Composable
private fun HistoryCard(metric: Metric, range: Range, stats: List<DayStat>?, normal: History.Normal?, end: LocalDate) {
    val color = colorOf(metric)
    val start = end.minusDays(range.days - 1L)
    GlowCard(GlowStyle.chart(color).copy(motion = GlowMotion.Breath(6f)), Modifier.fillMaxWidth()) {
        Column {
            GlowHeader(
                if (metric == Metric.Steps) "Steps a day" else "Daily average",
                trailing = when (range) { Range.Week -> "Last 7 days"; Range.Month -> "Last 30 days"; else -> "Last 12 months" },
            )
            val summary = stats?.let { History.summary(it, start, end) }
            if (summary == null) {
                Spacer(Modifier.height(GlowLayout.TitleGap))
                Text(
                    if (stats == null) "Loading…" else "No ${metric.title.lowercase()} in this range yet.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }
            val facts = buildList {
                if (metric == Metric.Steps) add("%,d in total".format(Math.round(summary.total)))
                add("${summary.days} days with data")
                // Over a year, a month's average sitting inside last month's normal says nothing; status is for weeks and months.
                if (range != Range.Year) normalFacts(metric, summary.average, normal).lastOrNull()?.let(::add)
            }
            GlowHeroNumber(metric.format(summary.average).replace(",", ""), metric.unit.ifEmpty { "/ 100" }, facts)

            val (values, labels, dates) = series(range, stats, start, end)
            GlowChartGap()
            GlowLineChart(
                values, height = GlowLayout.ChartHeight, labels = labels, showAverage = false,
                band = normal?.let { it.low..it.high }, format = metric::format,
            ) { i, v ->
                val d = dates[i]
                val label = if (range == Range.Year) "Week of ${d.format(DateTimeFormatter.ofPattern("d MMM"))}" else d.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                "${metric.format(v)} ${metric.unit} · $label".replace("  ", " ")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (normal != null) "Your normal" else "Your normal shows after ${History.MIN_NORMAL_DAYS} days of data",
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Evenly spaced values for the chart: one a day, or one a week over a year; gaps are null. Also axis labels and each point's date. */
private fun series(range: Range, stats: List<DayStat>, start: LocalDate, end: LocalDate): Triple<List<Float?>, List<String>, List<LocalDate>> {
    val inRange = stats.filter { it.date in start..end }
    val dates: List<LocalDate>
    val byDate: Map<LocalDate, Float>
    if (range == Range.Year) {
        val first = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        dates = generateSequence(first) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
        byDate = History.weekly(inRange).associate { it.date to it.value }
    } else {
        dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        byDate = inRange.associate { it.date to it.value }
    }
    val labels = when (range) {
        Range.Week -> dates.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
        Range.Month -> listOf(0f, 1 / 3f, 2 / 3f, 1f).map { dates[((dates.size - 1) * it).toInt()].format(DateTimeFormatter.ofPattern("d MMM")) }
        else -> listOf(0f, 1 / 3f, 2 / 3f, 1f).map { dates[((dates.size - 1) * it).toInt()].format(DateTimeFormatter.ofPattern("MMM")) }
    }
    return Triple(dates.map { byDate[it] }, labels, dates)
}

@Composable
private fun LowHighPanel(metric: Metric, range: Range, s: History.Summary) {
    val day = DateTimeFormatter.ofPattern("EEE d MMM")
    DotPanel {
        Row(Modifier.fillMaxWidth()) {
            listOf("Lowest" to s.lowest, "Highest" to s.highest).forEach { (label, stat) ->
                Column(Modifier.weight(1f)) {
                    Fact(if (range == Range.Year) "$label day" else label, "${metric.format(stat.value)} ${metric.unit}".trim())
                    Note(stat.date.format(day))
                }
            }
        }
    }
}

/** Your normal: the usual range over the last 30 days, or how long until there is one. */
@Composable
private fun NormalPanel(metric: Metric, normal: History.Normal?, days: Int) {
    DotPanel {
        DotHeader("Your normal", trailing = "Last ${History.NORMAL_DAYS} days")
        if (normal == null) {
            Note("Your normal shows after ${History.MIN_NORMAL_DAYS} days of data ($days so far). It's the range your daily values usually fall in.")
            GlowBar(days / History.MIN_NORMAL_DAYS.toFloat(), colorOf(metric))
            return@DotPanel
        }
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) { Fact("Usual", "${metric.format(normal.mean)} ${metric.unit}".trim()) }
            Box(Modifier.weight(1f)) { Fact("Range", "${metric.format(normal.low)}–${metric.format(normal.high)}") }
        }
        Note("From ${normal.days} days. Most of your days fall inside this range; outside it is worth a look, not a worry.")
    }
}

@Composable
private fun AboutPanel(metric: Metric) {
    val (what, moves) = ABOUT.getValue(metric)
    DotPanel {
        DotHeader("About ${metric.title.lowercase()}")
        Text(what, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        Note(moves)
    }
}

@Composable
private fun RelatedPanel(related: List<Metric>, onOpen: (Metric) -> Unit) {
    DotPanel {
        DotHeader("Related")
        related.forEach { m ->
            Row(
                Modifier.fillMaxWidth().glass(GlassRowShape, { onOpen(m) }).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.padding(end = 10.dp).size(7.dp)) { drawCircle(colorOf(m)) }
                Text(m.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
