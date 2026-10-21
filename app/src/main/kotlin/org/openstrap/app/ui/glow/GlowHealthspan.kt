package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.openstrap.app.data.Healthspan
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/** The Healthspan card's orchid, used for the glow: warm enough to stay clear of Sleep's blue. */
private val Orchid = Color(0xFFD86BEA)

/** The same recipe as the Heart rate and Activity cards, in orchid. */
internal val HealthspanGlow = GlowStyle.chart(Orchid)

/** "3 years younger", "1 year older" or "Right on your age" for an [estimate] against [age], in whole years. */
internal fun healthspanVerdict(age: Int, estimate: Float): String {
    val years = age - estimate.roundToInt()
    return when {
        years > 0 -> "$years ${if (years == 1) "year" else "years"} younger"
        years < 0 -> "${-years} ${if (-years == 1) "year" else "years"} older"
        else -> "Right on your age"
    }
}

/** "Steady" at 1x, "Slower" below, "Faster" above; "Learning" until there's enough history. */
internal fun paceLabel(pace: Float?): String = when {
    pace == null -> "Learning"
    pace < 1f -> "Slower"
    pace > 1f -> "Faster"
    else -> "Steady"
}

/** "1x", "0.5x", "-1x". */
internal fun paceText(pace: Float): String = if (pace % 1f == 0f) "${pace.toInt()}x" else "${pace}x"

/** "takes off about 1 year", "adds about ½ year", "takes off a few months" or "no change". */
internal fun effectText(years: Float): String {
    val y = abs(years)
    val verb = if (years < 0) "takes off" else "adds"
    return when {
        y < 0.05f -> "no change"
        y < 0.375f -> "$verb a few months"
        y < 0.75f -> "$verb about ½ year"
        else -> y.roundToInt().let { "$verb about $it ${if (it == 1) "year" else "years"}" }
    }
}

/** The line under a habit: your value, the typical one it's scored against, and a target where there is one. */
internal fun termDetail(t: Healthspan.Term, age: Int): String {
    val v = t.value
    val ref = t.reference
    if (v == null) return if (t.habit == Healthspan.Habit.Vo2max) "No VO2max from workouts yet · counted as typical" else "Not enough data yet · counted as typical"
    return when (t.habit) {
        Healthspan.Habit.Strength -> {
            val m = v.roundToInt()
            (if (m == 0) "None recorded" else "$m min a week") + " · most people your age do none" +
                (if (m < Healthspan.STRENGTH_TARGET_MINUTES) " · aim for ${Healthspan.STRENGTH_TARGET_MINUTES}" else "")
        }
        Healthspan.Habit.Vo2max -> "${v.roundToInt()} ml/kg/min · typical at $age: ${ref?.roundToInt()}"
        Healthspan.Habit.Steps -> "${"%,d".format(v.roundToInt())} a day · typical: ${"%,d".format(((ref ?: 0f) / 100).roundToInt() * 100)}" +
            (if (v < 8000f) " · aim for 8,000–10,000" else "")
        Healthspan.Habit.Regularity -> "${v.roundToInt()} · typical: ${ref?.roundToInt()}"
        Healthspan.Habit.RestingHr -> "${v.roundToInt()} bpm asleep · typical: about ${ref?.roundToInt()}"
        Healthspan.Habit.TimeAsleep -> v.roundToInt().let { m -> "${m / 60} h ${m % 60} m · counts only below 6 h 30 m" }
    }
}

/** "Helping most: strength training", "Holding back most: steps", or a line for when every habit is typical. */
internal fun leverText(r: Healthspan.Result): String {
    val lever = r.lever ?: return "Every habit is close to typical"
    return (if (lever.years < 0) "Helping most: " else "Holding back most: ") + lever.habit.label.replaceFirstChar { it.lowercase() }
}

/** "Sunday 22 Nov" for local midnight [day] (epoch seconds), with no-break spaces so it never wraps inside the date. */
internal fun sundayText(day: Long): String = java.time.Instant.ofEpochSecond(day).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM")).replace(' ', '\u00A0')

/** What to add to the profile: "Add your birth year and sex in your profile to start your Healthspan." */
internal fun profileReminder(missing: List<String>): String =
    "Add your ${missing.joinToString(" and ")} in your profile to start your Healthspan."

/** The starting state's line under your age: when a first estimate is due, or what it's still waiting for. */
internal fun learningText(o: Healthspan.Outcome): String = when {
    // The date's line is kept whole, so "First estimate:" never ends up apart from its date.
    o.firstEstimate != null -> "Starts at your age.\nFirst\u00A0estimate:\u00A0${sundayText(o.firstEstimate)}."
    o.withheld == Healthspan.Withheld.Learning -> "Starts at your age. Wear the strap for 30 days for a first estimate."
    else -> "Starts at your age. ${o.withheld?.reason.orEmpty()}"
}

/**
 * Healthspan: how old your habits look, as a range of ages (docs/algorithms/healthspan.md), from [state]. Before there's
 * an estimate it starts at your own age; with no birth year or sex it asks for them, and a tap opens the profile
 * ([onOpenProfile]) instead of the Healthspan screen. Nothing shows until it has been worked out.
 */
@Composable
fun HealthspanCard(state: Healthspan.Outcome?, onClick: (() -> Unit)? = null, onOpenProfile: (() -> Unit)? = null) {
    state ?: return
    val r = state.result
    val withheld = state.withheld
    val needsProfile = r == null && state.missing.isNotEmpty() && withheld?.profile == true
    GlowCard(HealthspanGlow, Modifier.fillMaxWidth(), onClick = if (needsProfile) onOpenProfile else onClick) {
        Column {
            GlowHeader("Healthspan", trailing = if (r?.early == true) "Early" else "Weekly")
            when {
                r != null -> {
                    GlowHeroNumber("${r.low}-${r.high}", "", side = {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Pace", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            Text(paceLabel(r.pace), color = Color.White, style = GlowType.number.copy(fontWeight = FontWeight.SemiBold))
                        }
                    })
                    CardText(healthspanVerdict(r.age, r.estimate))
                    GlowChartGap()
                    AgeScale(r.age, r.low, r.high, r.estimate)
                    CardLink(leverText(r))
                }
                needsProfile -> {
                    CardText(profileReminder(state.missing), bright = true)
                    CardLink("Add to profile")
                }
                withheld?.learning == true && state.age != null -> {
                    GlowHeroNumber("${state.age}", "your age")
                    CardText(learningText(state))
                    CardLink("How it works")
                }
                else -> {
                    CardText(withheld?.reason.orEmpty(), bright = true)
                    CardLink("How it works")
                }
            }
        }
    }
}

/** A line of the card's text under the number; [bright] when it's the card's only text. */
@Composable
private fun CardText(text: String, bright: Boolean = false) = Text(
    text, color = Color.White.copy(alpha = if (bright) 0.85f else 0.75f),
    style = if (bright) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(top = 10.dp),
)

/** The card's last row: a line of text and a chevron. */
@Composable
private fun CardLink(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(PhosphorIcons.CaretRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

/**
 * A line of ages, seven years either side of yours. The estimate is drawn like the Recovery arc: a dotted track and a
 * soft halo under a solid white line. A dot marks the [estimate], and a hairline your age (always centred). An early
 * estimate's range can run past the ends; it's drawn to them.
 */
@Composable
internal fun AgeScale(age: Int, low: Int, high: Int, estimate: Float) {
    val from = age - 7
    val to = age + 7
    Column {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val pad = 8.dp.toPx()
            fun x(a: Float) = pad + (a.coerceIn(from.toFloat(), to.toFloat()) - from) / (to - from) * (size.width - 2 * pad)
            val y = size.height / 2
            val start = Offset(x(low.toFloat()), y)
            val end = Offset(x(high.toFloat()), y)
            drawLine(
                Color.White.copy(alpha = 0.22f), Offset(x(from.toFloat()), y), Offset(x(to.toFloat()), y), 2.dp.toPx(),
                StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0f, 7.dp.toPx())),
            )
            drawLine(Color.White.copy(alpha = 0.1f), start, end, 8.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, start, end, 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.8f), Offset(x(age.toFloat()), y - 11.dp.toPx()), Offset(x(age.toFloat()), y + 11.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            val mid = Offset(x(estimate), y)
            // Green when the estimate is at or under your age, amber when it's over.
            val dot = if (estimate.roundToInt() <= age) MetricColors.good else MetricColors.warn
            drawCircle(dot.copy(alpha = 0.3f), 9.dp.toPx(), mid)
            drawCircle(dot, 5.dp.toPx(), mid)
        }
        Box(Modifier.fillMaxWidth()) {
            val label = Color.White.copy(alpha = 0.7f)
            Text("$from", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            Text("you're $age", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text("$to", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
