package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.theme.MetricColors
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * Sample cards for the Design lab: today's dashboard elements (heart line, steps bars, hypnogram,
 * weekly trend, recovery ring) redrawn in the glow language. Data is made up.
 */

/** A glow style tuned for charts: the dark core sits low and wide, behind the plot. */
fun GlowStyle.Companion.chart(color: Color) = Bloom.copy(
    color = color,
    coreY = 0.64f, coreWidth = 0.88f, coreHeight = 0.66f,
    coreBlur = 24.dp, toneWidth = 16.dp,
    // Sleep's quiet recipe: a near-same-hue band, a softer rim and a lighter bevel.
    toneShift = -10f, glowStrength = 0.6f, bevel = 0.6f,
)

/** Title on the left, a small note on the right. */
@Composable
private fun ChartHeader(title: String, note: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.title, modifier = Modifier.weight(1f))
        Text(note, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

/** A dot-matrix value with a small unit after it. */
@Composable
private fun Reading(value: String, unit: String, dot: Float = 4.5f) {
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom) {
        DotMatrixText(value, dot = dot.dp, gap = (dot * 0.4f).dp)
        Text(unit, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun HeartChartCard(modifier: Modifier = Modifier) {
    val hr = remember {
        val rnd = Random(3)
        List(144) { i ->
            val hour = i / 6f
            val asleep = hour < 6.5f
            val base = if (asleep) 52f else 66f + 8f * sin((hour - 9f) / 12f * PI.toFloat())
            val walk = if (hour in 17.5f..18.5f) 38f else 0f
            if (i in 60..63) null else base + walk + rnd.nextFloat() * 6f
        }
    }
    GlowCard(GlowStyle.chart(MetricColors.heart).copy(motion = GlowMotion.Heartbeat(62)), modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Column {
            ChartHeader("Heart rate", "Today")
            Reading("62", "bpm resting")
            Spacer(Modifier.padding(top = 18.dp))
            GlowLineChart(hr, height = 130.dp, labels = listOf("00", "06", "12", "18", "24"))
        }
    }
}

@Composable
fun StepsChartCard(modifier: Modifier = Modifier) {
    val steps = remember {
        val rnd = Random(5)
        List(24) { h ->
            when (h) {
                in 0..6 -> 0f
                8, 12, 18 -> 900f + rnd.nextFloat() * 500f
                in 19..23 -> if (h > 20) 0f else 200f + rnd.nextFloat() * 200f
                else -> 150f + rnd.nextFloat() * 450f
            }
        }
    }
    GlowCard(GlowStyle.chart(MetricColors.steps).copy(motion = GlowMotion.Stride(energy = 0.84f)), modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Column {
            ChartHeader("Steps", "Goal 10 000")
            Reading("8412", "steps")
            Spacer(Modifier.padding(top = 18.dp))
            GlowDotBars(steps, height = 100.dp, labels = listOf("00", "06", "12", "18", "24"), accent = MetricColors.steps)
        }
    }
}

@Composable
fun SleepChartCard(modifier: Modifier = Modifier) {
    // Rows: 0 Awake, 1 REM, 2 Light, 3 Deep.
    val stages = listOf(
        GlowStage(0f, 0.04f, 0), GlowStage(0.04f, 0.12f, 2), GlowStage(0.12f, 0.24f, 3), GlowStage(0.24f, 0.33f, 2),
        GlowStage(0.33f, 0.40f, 1), GlowStage(0.40f, 0.52f, 2), GlowStage(0.52f, 0.60f, 3), GlowStage(0.60f, 0.70f, 2),
        GlowStage(0.70f, 0.72f, 0), GlowStage(0.72f, 0.82f, 1), GlowStage(0.82f, 0.95f, 2), GlowStage(0.95f, 1f, 1),
    )
    val style = GlowStyle.Midnight.copy(
        color = MetricColors.sleepDeep,
        coreX = 0.5f, coreY = 0.6f, coreWidth = 1.1f, coreHeight = 0.9f,
        motion = GlowMotion.Breath(5f),
    )
    GlowCard(style, modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Column {
            ChartHeader("Sleep", "23:10 – 06:34")
            Reading("7:24", "asleep")
            Spacer(Modifier.padding(top = 18.dp))
            GlowHypnogram(stages, listOf("Awake", "REM", "Light", "Deep"), height = 130.dp, labels = listOf("23:10", "06:34"))
        }
    }
}

@Composable
fun HrvTrendCard(modifier: Modifier = Modifier) {
    val days = listOf(
        GlowDay("M", 44f), GlowDay("T", 51f), GlowDay("W", 47f), GlowDay("T", null),
        GlowDay("F", 39f), GlowDay("S", 46f), GlowDay("S", 53f),
    )
    val style = GlowStyle.Ember.copy(color = MetricColors.hrv, coreY = 0.0f, coreHeight = 1.3f)
    GlowCard(style, modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Column {
            ChartHeader("HRV", "Last 7 days")
            Reading("53", "ms today")
            Spacer(Modifier.padding(top = 14.dp))
            GlowTrend(days, height = 140.dp, average = 47f)
        }
    }
}

@Composable
fun RecoveryRingCard(modifier: Modifier = Modifier) {
    val style = GlowStyle.chart(MetricColors.readiness).copy(coreX = 0.3f, coreY = 0.55f, coreWidth = 0.6f, coreHeight = 0.9f, coreShape = CoreShape.Radial)
    GlowCard(style, modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowRing(0.82f, size = 140.dp) { DotMatrixText("82", dot = 5.dp, gap = 2.dp) }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recovery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                Text("Ready to train. HRV above your average, resting HR steady.", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
