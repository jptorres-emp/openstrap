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
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/** The Healthspan card's lavender, used for the glow. */
private val Lavender = Color(0xFFB69CFF)

/** The same recipe as the Heart rate and Activity cards, in lavender. */
internal val HealthspanGlow = GlowStyle.chart(Lavender)

/** The estimate until it's computed from the strap's data (docs/algorithms/healthspan.md); the card and its screen read the same one. */
internal object HealthspanSample {
    const val AGE = 35
    const val LOW = 30
    const val HIGH = 34
    const val PACE = "Steady"
    const val LEVER = "strength training"

    /** One habit's effect: [years] under 0 takes years off, over 0 adds them. */
    class Term(val name: String, val years: Float, val effect: String, val detail: String)

    val terms = listOf(
        Term("Strength training", -1.22f, "takes off about 1 year", "40 min a week · most people your age do none"),
        Term("Fitness (VO2max)", -0.87f, "takes off about 1 year", "46 ml/kg/min · typical at 35: 40"),
        Term("Steps", -0.52f, "takes off about ½ year", "10,000 a day · typical: 7,800"),
        Term("Sleep regularity", -0.29f, "takes off a few months", "87 · typical: 81"),
        Term("Resting heart rate", -0.21f, "takes off a few months", "55 bpm asleep · typical: about 59"),
        Term("Time asleep", 0f, "no change", "7 h 30 m · counts only below 6 h 30 m"),
    )
}

/** "3 years younger", "1 year older" or "Right on your age" for an estimate against [age]. */
internal fun healthspanVerdict(age: Int, low: Int, high: Int): String {
    val years = age - (low + high) / 2
    return when {
        years > 0 -> "$years ${if (years == 1) "year" else "years"} younger"
        years < 0 -> "${-years} ${if (-years == 1) "year" else "years"} older"
        else -> "Right on your age"
    }
}

/**
 * Healthspan: how old your habits look, as a range of ages (docs/algorithms/healthspan.md). [low]..[high] is the
 * weekly estimate, [age] the person's own age.
 */
@Composable
fun HealthspanCard(age: Int, low: Int, high: Int, pace: String, lever: String, onClick: (() -> Unit)? = null) {
    val verdict = healthspanVerdict(age, low, high)
    GlowCard(HealthspanGlow, Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            GlowHeader("Healthspan", trailing = "Weekly")
            GlowHeroNumber("$low-$high", "", side = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Pace", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(pace, color = Color.White, style = GlowType.number.copy(fontWeight = FontWeight.SemiBold))
                }
            })
            Text(
                verdict, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
            GlowChartGap()
            AgeScale(age, low, high)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Helping most: $lever", color = Color.White, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(PhosphorIcons.CaretRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * A line of ages, seven years either side of yours. The estimate is drawn like the Recovery arc: a dotted track and a
 * soft halo under a solid white line. A dot marks the middle of the range, and a hairline your age (always centred).
 */
@Composable
internal fun AgeScale(age: Int, low: Int, high: Int) {
    val from = age - 7
    val to = age + 7
    Column {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val pad = 8.dp.toPx()
            fun x(a: Float) = pad + (a - from) / (to - from) * (size.width - 2 * pad)
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
            val mid = Offset((start.x + end.x) / 2, y)
            drawCircle(MetricColors.good.copy(alpha = 0.3f), 9.dp.toPx(), mid)
            drawCircle(MetricColors.good, 5.dp.toPx(), mid)
        }
        Box(Modifier.fillMaxWidth()) {
            val label = Color.White.copy(alpha = 0.7f)
            Text("$from", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            Text("you're $age", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text("$to", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
