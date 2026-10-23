package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import org.openstrap.app.ui.theme.AppIcons

/** The Healthspan card's orchid, used for the glow: warm enough to stay clear of Sleep's blue. */
private val Orchid = MetricColors.habitAge

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

/** What to add to the profile: "Add your birth year and sex in your profile to see your Habit Age." */
internal fun profileReminder(missing: List<String>): String =
    "Add your ${missing.joinToString(" and ")} in your profile to see your Habit Age."

/** The starting state's line under your age: when a first estimate is due, or what it's still waiting for. */
internal fun learningText(o: Healthspan.Outcome): String = when {
    // The date's line is kept whole, so "First estimate:" never ends up apart from its date.
    o.firstEstimate != null -> "Starts at your age.\nFirst\u00A0estimate:\u00A0${sundayText(o.firstEstimate)}."
    o.withheld == Healthspan.Withheld.Learning -> "Starts at your age. Wear the strap for 30 days for a first estimate."
    else -> "Starts at your age. ${o.withheld?.reason.orEmpty()}"
}

/** The local date of [epoch] (a local midnight). */
private fun dateOf(epoch: Long) = java.time.Instant.ofEpochSecond(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

/**
 * Habit Age, one design wherever it shows (Recovery's foot, Today when there's a new estimate): an edge card lit from
 * the left, since it's about the weeks behind you. The title with "Updated Sun 18 Oct", the age in dots, one status
 * line against last week (the range until there's a last week, orchid only when the number moved) and the age
 * scale. Before the first estimate it says when that is, with no number; with no birth year or sex it says what's
 * needed and opens the profile ([onOpenProfile]) instead of the Healthspan screen. Nothing shows until it has been
 * worked out.
 */
@Composable
fun HabitAgeEdgeCard(state: Healthspan.Outcome?, onClick: () -> Unit, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    state ?: return
    val r = state.result
    val withheld = state.withheld
    val needsProfile = r == null && state.missing.isNotEmpty() && withheld?.profile == true
    EdgeCard(
        // Without an estimate it's one line, so it's only as high as that line.
        Orchid, modifier.fillMaxWidth().let { if (r != null) it.gridSize(CardSize.Wide) else it }, kind = EdgeKind.Earlier,
        onClick = if (needsProfile) onOpenProfile else onClick,
    ) {
        EdgeTitle("Habit Age", r?.let { org.openstrap.app.ui.dashboard.TabCopy.habitAgeUpdated(dateOf(it.asOf)) })
        when {
            r != null -> {
                val line = org.openstrap.app.ui.dashboard.TabCopy.habitAgeLine(r.estimate, state.lastWeek, r.low, r.high)
                EdgeValue(r.estimate.roundToInt().toString(), "years", line.text, if (line.moved) Orchid else Color.White.copy(alpha = GlowType.SECONDARY_ALPHA))
                Spacer(Modifier.height(10.dp))
                AgeScale(r.age, r.low, r.high, r.estimate)
            }
            needsProfile -> EdgeValue(null, null, profileReminder(state.missing), waiting = false)
            state.firstEstimate != null -> EdgeValue(null, null, org.openstrap.app.ui.dashboard.TabCopy.firstEstimate(dateOf(state.firstEstimate)), waiting = false)
            else -> EdgeValue(null, null, withheld?.reason, waiting = false)
        }
    }
}

/**
 * The age scale (chart kit): a line of ages, seven years either side of yours, the estimate's range as an orchid band
 * with a white dot at the [estimate], and a hairline at your [age]. Labelled with the range under the band and you at
 * the hairline. An early estimate's range can run past the ends; it's drawn to them.
 */
@Composable
internal fun AgeScale(age: Int, low: Int, high: Int, estimate: Float) {
    val from = age - 7
    val to = age + 7
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val pad = 8.dp
        fun f(a: Float) = (a.coerceIn(from.toFloat(), to.toFloat()) - from) / (to - from)
        fun x(a: Float) = pad + (w - pad * 2) * f(a)
        val label = Color.White.copy(alpha = 0.6f)
        Column {
            // Your age over its hairline, the range under its band, as in the kit, so the two never overlap.
            Box(Modifier.fillMaxWidth().height(16.dp)) { ScaleLabel(org.openstrap.app.ui.dashboard.TabCopy.yourAge(age), x(age.toFloat()), w, label) }
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val y = size.height / 2
                fun px(a: Float) = x(a).toPx()
                drawLine(Color.White.copy(alpha = 0.15f), Offset(px(from.toFloat()), y), Offset(px(to.toFloat()), y), 6.dp.toPx(), StrokeCap.Round)
                drawLine(Orchid.copy(alpha = 0.5f), Offset(px(low.toFloat()), y), Offset(px(high.toFloat()), y), 12.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(px(age.toFloat()), 0f), Offset(px(age.toFloat()), size.height), 1.dp.toPx())
                drawCircle(Color.White, 7.dp.toPx(), Offset(px(estimate), y))
            }
            Box(Modifier.fillMaxWidth().height(16.dp)) { ScaleLabel(org.openstrap.app.ui.dashboard.TabCopy.ageRange(low, high), x((low + high) / 2f), w, label) }
        }
    }
}

/** A label centred on [at] across a scale [width] wide, kept inside it. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScaleLabel(text: String, at: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, color: Color) {
    // Centred on its point by aligning a share of the way across, as BiasAlignment does.
    val bias = ((at / width) * 2f - 1f).coerceIn(-1f, 1f)
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(androidx.compose.ui.BiasAlignment(bias, 0f)))
}
