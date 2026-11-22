@file:Suppress("MagicNumber", "UnstableCollections", "ComposeUnstableCollections")

package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayStyle
import java.util.Locale
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.data.SleepNight
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.presentation.SLEEP_NAMES
import org.openstrap.app.presentation.StageSpan
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.glow.draw.stageColor
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors

/*
 * The Sleep detail's pictures with no card (rules, Record and Vital kinds): the night's stages as four slim rows, the
 * stage shares as one segmented bar with its legend (board HaloSleep, Last night), and the week's bed and wake bars on
 * the detail chart frame. Private copies of what the Sleep tab and GlowSleepScreen draw on cards.
 */

/** The stage rows' track (board HaloSleep: white 0.06). */
private val StageTrack = Ink.Faint

/**
 * A night's stages as rows Awake to Deep (board HaloSleep, the hypnogram): a name 40 wide, an 8 dp track and the
 * stage's blocks in its own colour on it, with the start, middle and end times under the rows. [kept], the user's own
 * times for a sleep they moved, dims what the strap saw outside them.
 */
@Composable
internal fun DetailStages(spans: List<StageSpan>, modifier: Modifier = Modifier, kept: LongRange? = null) {
    if (spans.isEmpty()) return
    val start = spans.minOf { it.start }
    val end = spans.maxOf { it.end }.coerceAtLeast(start + 1)
    // A run of one stage is one bar as long as the run.
    val joined = remember(spans) { NightStages.join(spans) }
    val rows = DayViewModel.SLEEP_STAGE_ROWS
    val order = rows.entries.sortedBy { it.value }.map { it.key }
    // The names' column grows with the font, so "Awake" is never cut at a large size.
    val nameWidth = 40.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        order.forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Text(SLEEP_NAMES[stage].orEmpty(), Modifier.width(nameWidth), color = DetailAxisInk, style = GlowType.caption, maxLines = 1)
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    drawRoundRect(StageTrack, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                    joined.filter { it.stage == stage || (stage == SleepStage.LIGHT && it.stage !in rows) }.forEach { sp ->
                        val x0 = size.width * (sp.start - start) / (end - start)
                        val x1 = size.width * (sp.end - start) / (end - start)
                        val inside = kept == null || (sp.end > kept.first && sp.start < kept.last)
                        drawRoundRect(
                            stageColor(stage).copy(alpha = if (inside) 1f else 0.35f), Offset(x0, 0f),
                            Size((x1 - x0).coerceAtLeast(3.dp.toPx()), size.height), CornerRadius(4.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = nameWidth + Space.S8), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(start, start + (end - start) / 2, end).forEach { t ->
                Text(clock(t), color = DetailAxisInk, style = GlowType.caption)
            }
        }
    }
}

/**
 * Each stage's share of the night as one segmented 8 dp bar, 2 dp between the parts, then a two column legend of the stage
 * and its time (board HaloSleep, Last night).
 */
@Composable
internal fun DetailStageShares(b: Summaries.SleepBreakdown, modifier: Modifier = Modifier) {
    val order = listOf(SleepStage.DEEP, SleepStage.REM, SleepStage.LIGHT, SleepStage.AWAKE)
    val total = (b.asleepSeconds + b.awakeSeconds).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val gap = 2.dp.toPx()
            val parts = order.filter { (b.byStage[it] ?: 0L) > 0L }
            val room = size.width - gap * (parts.size - 1).coerceAtLeast(0)
            var x = 0f
            parts.forEach { st ->
                val w = room * (b.byStage[st] ?: 0L) / total
                drawRoundRect(stageColor(st), Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                x += w + gap
            }
        }
        order.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { st ->
                    Row(
                        Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S8),
                    ) {
                        Box(Modifier.size(8.dp).background(stageColor(st), CircleShape))
                        Text(
                            SLEEP_NAMES[st].orEmpty() + " " + Units.duration(b.byStage[st] ?: 0L),
                            color = Ink.Strong, style = GlowType.secondary, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The week's bed and wake bars (rules, Vital kind; board HaloDetailHrv's columns): a capsule per night on a clock running
 * down the chart, evening at the top, from falling asleep to waking, the [selected] night white and the rest the Sleep
 * colour at 0.55. Your usual bedtime and wake are bands in white 0.07, the day letters under 11 px #6E7480. A tap on a night
 * picks it ([onNight]).
 */
@Composable
internal fun DetailSleepWindows(
    week: List<SleepNight>,
    usual: SleepHistory.UsualTimes?,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    selected: LocalDate? = null,
    onNight: ((LocalDate) -> Unit)? = null,
    height: Dp = DetailChartHeight,
) {
    val m = rememberTextMeasurer()
    // Minutes since the noon before each night, so a night never wraps.
    fun sinceNoon(epoch: Long, wake: Boolean): Int {
        val t = Instant.ofEpochSecond(epoch).atZone(zone).toLocalTime()
        val min = t.hour * 60 + t.minute
        return if (wake) min + 12 * 60 else Math.floorMod(min - 12 * 60, 24 * 60)
    }
    val spans = remember(week, zone) {
        week.map { n ->
            n.session?.let { sinceNoon(it.start, false) to sinceNoon(it.end, true).coerceAtLeast(sinceNoon(it.start, false) + 1) }
        }
    }
    val known = spans.filterNotNull()
    val usualBed = usual?.let { it.bedtime - 12 * 60 }
    val usualWake = usual?.let { it.wake + 12 * 60 }
    val top = (listOfNotNull(known.minOfOrNull { it.first }, usualBed).minOrNull() ?: (10 * 60)) - 30
    val bottom = (listOfNotNull(known.maxOfOrNull { it.second }, usualWake).maxOrNull() ?: (20 * 60)) + 30
    val tap = if (onNight == null || week.isEmpty()) Modifier else Modifier.pointerInput(week, onNight) {
        detectTapGestures { o -> onNight(week[(o.x / (size.width.toFloat() / week.size)).toInt().coerceIn(0, week.lastIndex)].date) }
    }
    Canvas(modifier.fillMaxWidth().height(height).then(tap)) {
        val gutter = 30.dp.toPx()
        val plotRight = size.width - gutter
        val chartBottom = size.height - 20.dp.toPx()
        val slot = plotRight / week.size.coerceAtLeast(1)
        fun yOf(min: Int) = chartBottom * (min - top).toFloat() / (bottom - top).coerceAtLeast(1)
        // Each usual time: a band across the plot and its clock in the gutter.
        listOf(usualBed to usual?.bedtime, usualWake to usual?.wake).forEach { (at, clockMin) ->
            if (at == null || clockMin == null) return@forEach
            val band = 14.dp.toPx()
            drawRect(Ink.Faint, Offset(0f, yOf(at) - band / 2), Size(plotRight, band))
            val label = m.measure(org.openstrap.app.presentation.TimeOfDay.minutes(clockMin), GlowType.caption.copy(color = DetailAxisInk))
            drawText(label, topLeft = Offset(size.width - label.size.width, yOf(at) - label.size.height / 2f))
        }
        val width = (slot * 0.55f).coerceAtMost(30.dp.toPx())
        week.forEachIndexed { i, n ->
            val cx = slot * i + slot / 2
            val on = if (selected != null) n.date == selected else i == week.lastIndex
            val name = n.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault())
            val label = m.measure(name, GlowType.caption.copy(color = if (on) Color.White else DetailAxisInk))
            drawText(label, topLeft = Offset(cx - label.size.width / 2, chartBottom + 6.dp.toPx()))
            val span = spans[i] ?: return@forEachIndexed
            val y0 = yOf(span.first)
            val y1 = yOf(span.second)
            drawRoundRect(
                if (on) Color.White else MetricColors.sleepDeep.copy(alpha = 0.55f), Offset(cx - width / 2, y0),
                Size(width, (y1 - y0).coerceAtLeast(width)), CornerRadius(minOf(9.dp.toPx(), width / 2)),
            )
        }
    }
}
