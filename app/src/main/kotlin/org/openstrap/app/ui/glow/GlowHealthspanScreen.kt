package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import org.openstrap.app.data.Healthspan
import org.openstrap.app.ui.dashboard.ScreenList
import kotlin.math.abs
import org.openstrap.app.ui.theme.MetricColors

/** The scale on the Healthspan screen's bars: a habit this many years either side of the middle fills its half. */
private const val BAR_FULL_YEARS = 1.5f

/** "Updated Sunday, next in 5 days", from the Sunday the estimate was made. */
private fun updatedText(asOf: Long): String {
    val made = java.time.Instant.ofEpochSecond(asOf).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val next = 7 - java.time.temporal.ChronoUnit.DAYS.between(made, java.time.LocalDate.now()).toInt().coerceIn(0, 7)
    return when (next) {
        7 -> "Updated today, next on Sunday"
        1 -> "Updated Sunday, next tomorrow"
        else -> "Updated Sunday, next in $next days"
    }
}

/**
 * The page behind the Habit Age card (spec 5.4): the same edge card at the top, its (i) holding the method, then the
 * pace it's moving at and what moves it; or, before an estimate, only the card saying why it waits.
 */
@Composable
fun GlowHealthspanScreen(state: Healthspan.Outcome?, contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val r = state?.result
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader("Habit Age", onBack, subtitle = r?.let { updatedText(it.asOf) } ?: "Updated weekly, on Sunday") }
        // The page is what the card opens, so here it opens nothing: no caret.
        item { HabitAgeEdgeCard(state, onClick = null, onOpenProfile = null) }
        if (r != null) {
            item { PacePanel(r.pace) }
            item { TermsPanel(r) }
        }
    }
}

/** Where the last 30 days sit against the 6-month habits: 1x is in step with the calendar. */
@Composable
private fun PacePanel(pace: Float?) {
    QuietPanel {
        PanelTitle("Pace of aging", trailing = pace?.let { "${paceLabel(it)}, ${paceText(it)}" } ?: paceLabel(null))
        if (pace != null) PaceScale(marker = (pace + 1f) / 4f, color = MetricColors.habitAge)
        Note(
            when {
                pace == null -> "Needs 30 days with enough data, and 3 months before them to tell a real change from day-to-day noise."
                pace < 1f -> "Your last 30 days beat your 6-month habits: kept up, the estimate will come down."
                pace > 1f -> "Your last 30 days fall behind your 6-month habits: kept up, the estimate will go up."
                else -> "Your last 30 days match your 6-month habits, so the estimate is moving in step with the calendar."
            },
        )
    }
}

/** A track from -1x to 3x with a dot at the pace, in the quiet panels' bar style (see [GlowBar]). */
@Composable
private fun PaceScale(marker: Float, color: Color) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val y = size.height / 2
            val r = 5.dp.toPx()
            val x0 = r
            val x1 = size.width - r
            drawLine(Color.White.copy(alpha = 0.10f), Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
            glowDot(Offset(x0 + (x1 - x0) * marker.coerceIn(0f, 1f), y), color)
        }
        Box(Modifier.fillMaxWidth()) {
            val label = Color.White.copy(alpha = 0.6f)
            Text("−1x slower", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            Text("1x", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text("3x faster", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** [GlowBar]'s dot: a halo, the solid dot, and a white pip. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.glowDot(at: Offset, color: Color) {
    drawCircle(color.copy(alpha = 0.30f), 5.dp.toPx(), at)
    drawCircle(color, 3.dp.toPx(), at)
    drawCircle(Color.White, 1.2.dp.toPx(), at)
}

/** Each habit against the typical person your age: what it takes off or adds, and the numbers behind it. */
@Composable
private fun TermsPanel(r: Healthspan.Result) {
    QuietPanel {
        PanelTitle("What moves it")
        Note("Each habit against the typical person your age, over 6 months")
        // One scale for every bar below: a habit at either end takes off, or adds, BAR_FULL_YEARS or more.
        Box(Modifier.fillMaxWidth()) {
            val label = Color.White.copy(alpha = 0.6f)
            Text("−1½ y younger", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            Text("0", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text("older +1½ y", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
        r.terms.forEach { t ->
            PanelDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t.habit.label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(effectText(t.years), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            EffectBar(t.years)
            Note(termDetail(t, r.age))
        }
        if (r.fitnessCapped) {
            PanelDivider()
            Note("Fitness terms capped: resting heart rate, steps and VO2 max together count for at most 3 years either way.")
        }
    }
}

/**
 * One habit's effect as a bar out from the middle: left takes years off, right adds them. Drawn like [GlowBar], the quiet
 * panels' bar: a faint track, a glowing line and a dot at its end, green for years off and amber for years added. A habit
 * with no effect shows only the track and the middle mark.
 */
@Composable
private fun EffectBar(years: Float) {
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val y = size.height / 2
        val r = 5.dp.toPx()
        val x0 = r
        val x1 = size.width - r
        val mid = size.width / 2
        drawLine(Color.White.copy(alpha = 0.10f), Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(mid, y - 5.dp.toPx()), Offset(mid, y + 5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
        if (abs(years) < 0.05f) return@Canvas
        // Habit Age's own ink either way: the side of the centre says better or worse, never green or amber.
        val color = MetricColors.habitAge
        val end = Offset(mid + (years / BAR_FULL_YEARS).coerceIn(-1f, 1f) * (mid - x0), y)
        drawLine(color.copy(alpha = 0.22f), Offset(mid, y), end, 6.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(mid, y), end, 2.2.dp.toPx(), StrokeCap.Round)
        glowDot(end, color)
    }
}
