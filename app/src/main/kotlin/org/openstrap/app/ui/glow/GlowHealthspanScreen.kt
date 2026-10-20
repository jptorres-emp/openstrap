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
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors

/** The scale on the Healthspan screen's bars: a habit this many years either side of the middle fills its half. */
private const val BAR_FULL_YEARS = 1.5f

/**
 * The screen behind the Healthspan card: the estimate, the pace it's moving at, and what moves it. Numbers are the
 * [HealthspanSample] until the estimate is computed.
 */
@Composable
fun GlowHealthspanScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val s = HealthspanSample
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { GlowDetailHeader("Healthspan", "Updated Sunday · next in 5 days", onBack) }
        item {
            GlowCard(HealthspanGlow.copy(motion = GlowMotion.Breath(5f)), Modifier.fillMaxWidth()) {
                Column {
                    GlowHeader("Your habits look like", trailing = "Weekly")
                    GlowHeroNumber("${s.LOW}-${s.HIGH}", "years", listOf(healthspanVerdict(s.AGE, s.LOW, s.HIGH), "You're ${s.AGE}"))
                    GlowChartGap()
                    AgeScale(s.AGE, s.LOW, s.HIGH)
                }
            }
        }
        item { PacePanel() }
        item { TermsPanel() }
        item { HowItWorksPanel() }
    }
}

/** Where the last 30 days sit against the 6-month habits: 1x is in step with the calendar. */
@Composable
private fun PacePanel() {
    DotPanel {
        DotHeader("Pace of aging", trailing = "1x · ${HealthspanSample.PACE}")
        PaceScale(marker = 0.5f)
        Note("Your last 30 days match your 6-month habits, so the estimate is moving in step with the calendar.")
    }
}

/** A track from -1x to 3x with a dot at the pace, in the quiet panels' bar style (see [GlowBar]). */
@Composable
private fun PaceScale(marker: Float) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val y = size.height / 2
            val r = 5.dp.toPx()
            val x0 = r
            val x1 = size.width - r
            drawLine(Color.White.copy(alpha = 0.10f), Offset(x0, y), Offset(x1, y), 2.dp.toPx(), StrokeCap.Round)
            glowDot(Offset(x0 + (x1 - x0) * marker, y), MetricColors.good)
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
private fun TermsPanel() {
    DotPanel {
        DotHeader("What moves it")
        Note("Each habit against the typical person your age, over 6 months")
        // One scale for every bar below: a habit at either end takes off, or adds, BAR_FULL_YEARS or more.
        Box(Modifier.fillMaxWidth()) {
            val label = Color.White.copy(alpha = 0.6f)
            Text("−1½ y younger", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterStart))
            Text("0", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.Center))
            Text("older +1½ y", color = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterEnd))
        }
        HealthspanSample.terms.forEach { t ->
            DotDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(t.effect, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            EffectBar(t.years)
            Note(t.detail)
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
        if (years == 0f) return@Canvas
        val color = if (years < 0) MetricColors.good else MetricColors.warn
        val end = Offset(mid + (years / BAR_FULL_YEARS).coerceIn(-1f, 1f) * (mid - x0), y)
        drawLine(color.copy(alpha = 0.22f), Offset(mid, y), end, 6.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(mid, y), end, 2.2.dp.toPx(), StrokeCap.Round)
        glowDot(end, color)
    }
}

/** How the estimate is worked out, folded away; the full method is docs/algorithms/healthspan.md. */
@Composable
private fun HowItWorksPanel() {
    var open by rememberSaveable { mutableStateOf(false) }
    DotPanel(onClick = { open = !open }) {
        DotHeader("How it works", expanded = open)
        if (!open) return@DotPanel
        Text(
            "Each habit is scored against the typical person your age and sex: resting heart rate, steps, sleep regularity, " +
                "time asleep, strength training and fitness. Better than typical takes years off, worse adds them. " +
                "The total is shrunk, because the habits overlap, and capped at 5 years either way.",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "It looks back 6 months (3 for fitness). Pace compares the last 30 days with those 6 months. " +
                "It updates once a week, and it's withheld until there's enough data.",
            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
        )
        Note("An estimate for personal insight, not a medical measurement. Shown as a range because each habit's effect is uncertain.")
    }
}
