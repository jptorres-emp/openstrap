package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.openstrap.app.data.CheckIn
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.ui.components.formatDuration
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.TodayCard
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.TodayEdits
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons

/*
 * The pieces Today is built from (spec 4.2): the hero's parts, strips, edge cards and lists. Each card has one home
 * on the screen and opens its detail. The words come from [TodayCopy].
 */

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
internal fun clockAt(epoch: Long): String = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(CLOCK)

/** The smallest height a card of [size] takes: its grid height, so long text grows the card instead of clipping. */
internal fun Modifier.gridSize(size: CardSize): Modifier = GlowGrid.height(size)?.let { heightIn(min = it) } ?: this

/* ----------------------------------------------- Hero ----------------------------------------------- */

/** The hero's sentence, the answer the card leads with. */
@Composable
internal fun HeroSentence(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Color.White, style = GlowType.sentence, modifier = modifier.padding(top = 14.dp))

/** A number on the hero: dot-matrix digits, or the dim dash while there's none, with a [label] under it. */
@Composable
internal fun HeroFigure(
    value: String?, label: String, big: Boolean = false, horizontal: Alignment.Horizontal = Alignment.Start, dot: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = horizontal, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (value != null) DotMatrixText(value, dot = if (big) GlowLayout.HeroDot else dot, gap = if (big) GlowLayout.HeroGap else dot * 0.37f)
        else WaitingDash(Modifier.padding(vertical = 8.dp))
        Text(label, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
    }
}

/** The white pill that starts something, like the ready screen's Start. */
@Composable
internal fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(48.dp).clip(shape).background(Color.White).pressClickable(onClick, shape).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon) {
            Icon(AppIcons.PlayFill, null, tint = Color.Black, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.Black, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** A quieter glass pill beside [HeroButton]. */
@Composable
internal fun HeroSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(48.dp).glass(RoundedCornerShape(50), onClick).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/* ---------------------------------------------- Strips ---------------------------------------------- */

private val StripPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/** A full-width strip: a state or an action, [GlowGrid.StripHeight] high unless its words need more. */
@Composable
internal fun TodayStrip(
    color: Color, kind: EdgeKind, modifier: Modifier = Modifier, status: EdgeStatus = EdgeStatus.Usual,
    onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) = EdgeCard(
    color, modifier.fillMaxWidth().gridSize(CardSize.Strip), status = status, kind = kind, onClick = onClick,
    contentPadding = StripPadding, content = { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, content = content) },
)

/** A strip's single line: a [title] and, on the right, its [detail]. */
@Composable
internal fun StripLine(title: String, detail: String?, chevron: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.label, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(AppIcons.CaretRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

/** Last night, collapsed to one line once the morning is over (spec 4.2). */
@Composable
internal fun LastNightStrip(state: DayUiState, onClick: () -> Unit, modifier: Modifier = Modifier) =
    TodayStrip(MetricColors.sleepDeep, EdgeKind.Sleep, modifier, onClick = onClick) {
        StripLine("Last night", TodayCopy.lastNight(state.asleepSeconds, state.sleep?.score) ?: "No sleep recorded")
    }

/** The morning check-in: how you feel, 1 to 5. It never changes the score; it's how the score gets checked. */
@Composable
internal fun CheckInStrip(feeling: Int?, onCheckIn: (Int?) -> Unit, modifier: Modifier = Modifier) =
    TodayStrip(MetricColors.readiness, EdgeKind.Earlier, modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("How do you feel?", color = Color.White, style = GlowType.label, modifier = Modifier.weight(1f))
            if (feeling != null) Text(CheckIn.label(feeling), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (CheckIn.MIN..CheckIn.MAX).forEach { v ->
                val selected = feeling == v
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier.weight(1f)
                        .let { if (selected) it.background(Color.White.copy(alpha = 0.85f), shape) else it }
                        .glass(shape, { onCheckIn(if (selected) null else v) })
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("$v", color = if (selected) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

/** What the strap warned about, in the words of its notification. Codes as in HealthAlert.Type. */
internal fun alertText(a: HealthAlertRow): String = when (a.type) {
    1 -> "High heart rate, ${a.value} bpm"
    2 -> "Low heart rate, ${a.value} bpm"
    3 -> "High stress, ${a.value}"
    4 -> "Low blood oxygen, ${a.value}%"
    5 -> "Irregular heart rhythm"
    else -> "Alert from the strap"
}

private fun alertMetric(a: HealthAlertRow) = when (a.type) { 3 -> Metric.Stress; 4 -> Metric.Spo2; else -> Metric.HeartRate }

/** "From your strap": the newest of the day's alerts, opening its metric, with how many more there were. */
@Composable
internal fun AlertsStrip(alerts: List<HealthAlertRow>, onOpenMetric: (Metric) -> Unit, modifier: Modifier = Modifier) {
    val newest = alerts.maxByOrNull { it.timestamp } ?: return
    TodayStrip(MetricColors.heart, EdgeKind.Value, modifier, status = EdgeStatus.Above, onClick = { onOpenMetric(alertMetric(newest)) }) {
        StripLine("From your strap", if (alerts.size > 1) "${alerts.size - 1} more today" else clockAt(newest.timestamp))
        Text(
            alertText(newest) + if (alerts.size > 1) " at ${clockAt(newest.timestamp)}." else ".",
            color = Color.White.copy(alpha = 0.85f), style = GlowType.secondary,
        )
        Text(
            org.openstrap.app.data.BodySignalsCopy.DISCLAIMER, color = Color.White.copy(alpha = 0.5f), style = GlowType.secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ------------------------------------------- Edge cards --------------------------------------------- */

/** An edge card's title row: [title] left, [note] right. */
@Composable
internal fun EdgeTitle(title: String, note: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.label, modifier = Modifier.weight(1f))
        if (note != null) Text(note, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, maxLines = 1)
    }
}

/** An edge card's number and the line under it; a dim dash and [status] while there's no number. */
@Composable
internal fun EdgeValue(
    value: String?, unit: String?, status: String?, statusColor: Color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
    /** Whether a missing [value] shows the waiting dash; false where the [status] says all there is. */
    waiting: Boolean = true,
) {
    if (value != null || waiting) Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
        if (value != null) DotMatrixText(value, dot = 3.4.dp, gap = 1.2.dp) else WaitingDash(Modifier.padding(vertical = 8.dp))
        if (unit != null && value != null) Text(unit, color = Color.White.copy(alpha = 0.7f), style = GlowType.secondary, modifier = Modifier.padding(start = 8.dp))
    }
    if (status != null) Text(status, color = statusColor, style = GlowType.secondary, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A length of time as dot-matrix digits: "7:40". */
internal fun clockDuration(seconds: Long): String = "%d:%02d".format(seconds / 3600, (seconds % 3600) / 60)

/** Last night in full, on waking: time asleep, the score and the stages, lit from the top like a night sky. */
@Composable
internal fun LastNightCard(state: DayUiState, firstNights: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = state.sleep
    EdgeCard(MetricColors.sleepDeep, modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Sleep, onClick = onClick) {
        EdgeTitle("Last night", s?.let { "${clockAt(it.start)}–${clockAt(it.end)}" })
        val asleep = state.asleepSeconds
        val headline = s?.let { n -> asleep?.let { org.openstrap.app.ui.dashboard.Narrative.sleepHeadline(n, it) } }
        EdgeValue(
            asleep?.let(::clockDuration), "asleep",
            when {
                asleep == null -> if (firstNights) "After your first night" else "No sleep recorded"
                s?.score != null -> listOfNotNull("Score ${s.score}", headline?.lowercase()).joinToString(", ")
                else -> headline
            },
        )
        if (state.sleepStages.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            GlowSleepStages(state.sleepStages, 44.dp, compact = true)
        }
    }
}

/**
 * Heart rate today: the latest reading and the day so far. Lit from the right while the reading is live. The live
 * switch lives in the Heart rate detail, not here.
 */
@Composable
internal fun HeartCard(state: DayUiState, live: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = live ?: state.latestHeartRate
    EdgeCard(
        MetricColors.heart, modifier.fillMaxWidth().gridSize(CardSize.Wide),
        kind = if (live != null) EdgeKind.Live else EdgeKind.Value, onClick = onClick,
    ) {
        EdgeTitle("Heart rate", state.restingHeartRate?.let { "Resting $it" })
        val at = state.heartRate.lastOrNull()?.x
        EdgeValue(
            now?.toString(), "bpm",
            when {
                live != null -> "Live"
                now != null && at != null -> "At ${clockAt(at)}"
                else -> "Waiting for the strap"
            },
        )
        if (state.heartRate.size >= 2) {
            val values = remember(state.heartRate, state.dayStart) { bucket(state.heartRate, state.dayStart..state.dayEnd, 10 * 60) }
            Spacer(Modifier.height(10.dp))
            GlowLineChart(values, height = 40.dp, showAverage = false, color = MetricColors.heart)
        }
    }
}

/** Steps toward the day's goal, with the bar inside the card. */
@Composable
internal fun ActivityCard(state: DayUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val goal = org.openstrap.app.ui.dashboard.LocalGoals.current.steps
    // TODO(step 4): the ghost line of your usual steps by this time (spec 2.7) needs hourly steps from past days.
    EdgeCard(
        MetricColors.steps, modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Progress,
        progress = if (goal > 0) state.steps / goal.toFloat() else null, onClick = onClick,
    ) {
        EdgeTitle("Activity", "Goal %,d".format(goal))
        EdgeValue(
            "%,d".format(state.steps), "steps",
            if (state.steps >= goal) "Goal reached" else "${state.steps * 100 / goal.coerceAtLeast(1)}% of your goal",
        )
    }
}

/**
 * This morning's Recovery, later in the day: about earlier, so lit from the left. The same as the waking hero shows:
 * OpenStrap's score, the strap's estimate labelled as such, or why there's none yet.
 */
@Composable
internal fun RecoveryEarlierCard(shown: TodayCopy.RecoveryShown, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EdgeCard(MetricColors.readinessDeep, modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Earlier, onClick = onClick) {
        EdgeTitle("Recovery", "This morning")
        EdgeValue(shown.value?.toString(), shown.word, shown.line)
    }
}

/**
 * Charge as a wide card: the second glow before and after a workout, lit to its level, or an edge card filling toward
 * full in the evening. [status] says what's happening to it.
 */
@Composable
internal fun ChargeWideCard(now: Int?, status: String, secondGlow: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val m = modifier.fillMaxWidth().gridSize(CardSize.Wide)
    if (secondGlow) {
        GlowCard(GlowStyle.Ember.copy(lightLevel = (now ?: 0) / 100f, second = true), m, onClick = onClick) {
            Column {
                GlowHeader("Charge")
                EdgeValue(now?.toString(), null, status, Color.White.copy(alpha = 0.8f))
            }
        }
    } else EdgeCard(MetricColors.chargeDeep, m, kind = EdgeKind.Progress, progress = now?.let { it / 100f }, onClick = onClick) {
        EdgeTitle("Charge", null)
        EdgeValue(now?.toString(), null, status)
    }
}

/** The workout just done, after it: how long it was, the average heart rate and the zone it mostly sat in. Lit from the left, about earlier. */
@Composable
internal fun WorkoutCard(w: org.openstrap.app.data.WorkoutAnalysis, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EdgeCard(MetricColors.heart, modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Earlier, onClick = onClick) {
        EdgeTitle(Workouts.sportName(w.workout.sportType), "${clockAt(w.shownStart)}–${clockAt(w.end)}")
        val mostly = w.zoneSeconds.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
        EdgeValue(
            workoutClock((w.end - w.shownStart).coerceAtLeast(0)), "long",
            listOfNotNull(w.workout.averageHeartRate?.let { "Average $it bpm" }, mostly?.let { "mostly zone ${it + 1}" })
                .joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { null },
        )
    }
}

/**
 * Your week (spec 4.2): last Monday to Sunday against the week before, every day of this week. Steps a day, time
 * asleep a night and the workouts done. A summary of three metrics borrows none of their colours: a neutral edge
 * from the bottom, soft, like any value at its usual.
 */
@Composable
internal fun WeekCard(
    state: DayUiState, workouts: List<org.openstrap.app.data.WorkoutAnalysis>, history: DailyHistory?, modifier: Modifier = Modifier,
) {
    val week = TodayCopy.lastWeek(state.date)
    val start = week.start
    val end = week.endInclusive
    val steps by remember(end, history) {
        history?.invoke(Metric.Steps.daily, start.minusDays(7), end) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(emptyList())
    fun stepsAvg(from: java.time.LocalDate, to: java.time.LocalDate) =
        steps.filter { it.date in from..to }.map { it.value.toDouble() }.takeIf { it.isNotEmpty() }?.average()
    val stepsNow = stepsAvg(start, end)
    // A night belongs to the morning it ended, so the week's nights end Tuesday through the Monday after.
    val asleep = state.nights.filter { it.date in start.plusDays(1)..end.plusDays(1) }.mapNotNull { it.asleepSeconds }
    val zone = ZoneId.systemDefault()
    val done = workouts.count { Instant.ofEpochSecond(it.workout.start).atZone(zone).toLocalDate() in start..end }
    val range = DateTimeFormatter.ofPattern("d MMM")
    // TODO(step 6): tapping opens the week story; there is none yet, so the card doesn't open anything.
    EdgeCard(EdgeNeutral, modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Value) {
        EdgeTitle("Your week", "${start.format(range)}–${end.format(range)}")
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            HeroFigure(stepsNow?.let { "%,d".format(it.roundToInt()) }, "Steps a day")
            HeroFigure(asleep.takeIf { it.isNotEmpty() }?.average()?.let { clockDuration(it.toLong()) }, "Asleep a night")
            HeroFigure("$done", "Workouts")
        }
        val change = TodayCopy.stepsChange(stepsNow, stepsAvg(start.minusDays(7), start.minusDays(1)))
        Text(
            change?.let { "$it on the week before." } ?: TodayCopy.WEEK_FIRST,
            color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/* ---------------------------------------------- Lists ----------------------------------------------- */

/** A row of a body list: a value against your normal. */
internal class BodyRow(
    val name: String, val color: Color, val value: Float?, val baseline: Float?, val tolerance: Float, val unit: String, val metric: Metric,
)

/** Body values against your normal, each opening its metric. [waiting] is what an empty row says. */
@Composable
internal fun BodyList(title: String, rows: List<BodyRow>, waiting: String, onOpenMetric: (Metric) -> Unit, modifier: Modifier = Modifier) {
    ListPanel(modifier, title = title, note = "Against your normal") {
        rows.forEachIndexed { i, r ->
            val direction = org.openstrap.app.ui.dashboard.Narrative.direction(r.value, r.baseline, r.tolerance)
            ListRow(
                r.name, r.color, r.value?.let { "${it.roundToInt()}${r.unit}" },
                if (r.value == null) waiting else TodayCopy.normalWord(direction),
                offNormal = direction != null && direction != 0, onClick = { onOpenMetric(r.metric) }, divider = i > 0,
            )
        }
    }
}

/** Load today and time to recover, before or after a workout. */
@Composable
internal fun LoadList(title: String, load: Float?, loadLine: String, recover: Pair<String?, String>?, modifier: Modifier = Modifier) {
    ListPanel(modifier, title = title) {
        ListRow("Load today", MetricColors.load, load?.roundToInt()?.toString(), loadLine, divider = false)
        // Recovered is the whole answer: no number, no dash.
        if (recover != null) ListRow("Time to recover", MetricColors.readinessDeep, recover.first, recover.second, dash = recover.first != null)
    }
}

/* ------------------------------------------- Edit Today --------------------------------------------- */

/** The last thing on Today: a way to change what it shows. */
@Composable
internal fun EditTodayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.glass(CircleShape, onClick, scale = 0.97f).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.SlidersHorizontal, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit Today", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Which cards Today shows, and up to two pinned under the hero. Saved as a whole on Done. */
@Composable
internal fun EditTodayDialog(edits: TodayEdits, onSave: (TodayEdits) -> Unit, onDismiss: () -> Unit) {
    var e by remember { mutableStateOf(edits) }
    org.openstrap.app.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Today") },
        text = {
            Column {
                Text(
                    "Pin up to two cards under the top card, or hide the ones you don't need.",
                    color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TodayCard.entries.filter { it.editable }.forEach { card ->
                    val shown = card !in e.hidden
                    Row(Modifier.fillMaxWidth().clickable { e = e.toggleHidden(card) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(shown, { e = e.toggleHidden(card) })
                        Text(card.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (card.pinnable) {
                            val pinned = card in e.pinned
                            TextButton(onClick = { e = e.togglePin(card) }, enabled = e.canPin(card)) {
                                Text(if (pinned) "Pinned" else "Pin", color = if (pinned) Color.White else Color.White.copy(alpha = if (e.canPin(card)) 0.7f else 0.3f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(e) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
