package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.openstrap.app.ui.theme.PhosphorIcons

/** A sandbox for the glow design language: the three reference cards, then the app's metric colours. */
@Composable
fun GlowLabScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var bloomColor by remember { mutableStateOf(GlowStyle.Bloom.color) }
    var training by remember { mutableStateOf(false) }

    ScreenList(contentPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Text("Design lab", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
        }

        item { SectionLabel("Reference cards", Modifier.padding(start = 4.dp, top = 4.dp)) }
        item { LastTestCard(GlowStyle.Bloom.tinted(bloomColor)) }
        item { ColourRow(bloomColor) { bloomColor = it } }
        item { TrainingCard(training, onCheckedChange = { training = it }) }
        item { BreathCard() }

        item { SectionLabel("Charts in glow cards", Modifier.padding(start = 4.dp, top = 12.dp)) }
        item { RecoveryRingCard() }
        item { HeartChartCard() }
        item { StepsChartCard() }
        item { SleepChartCard() }
        item { HrvTrendCard() }

        item { SectionLabel("One colour per metric", Modifier.padding(start = 4.dp, top = 12.dp)) }
        val metrics = listOf(
            Tile("Heart rate", "62", "bpm", MetricColors.heart, GlowMotion.Heartbeat(62)),
            Tile("HRV", "48", "ms", MetricColors.hrv),
            Tile("Deep sleep", "1:42", "h", MetricColors.sleepDeep, GlowMotion.Breath(5f)),
            Tile("SpO₂", "97", "%", MetricColors.spo2, GlowMotion.Breath(6f)),
            Tile("Stress", "24", "", MetricColors.stress),
            Tile("Steps", "8412", "", MetricColors.steps, GlowMotion.Stride(energy = 0.84f)),
        )
        metrics.chunked(2).forEach { pair ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { MetricTile(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

@Composable
private fun CardLabel(text: String, size: Int = 17) =
    Text(text, color = Color.White, style = GlowType.title.copy(fontSize = (size + 2).sp))

@Composable
fun LastTestCard(style: GlowStyle = GlowStyle.Bloom, modifier: Modifier = Modifier) {
    GlowCard(style.copy(motion = GlowMotion.Breath(6f)), modifier.fillMaxWidth().height(340.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Last test", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(PhosphorIcons.Info, "About this test", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DotMatrixText("3.3", dot = 6.dp, gap = 2.5.dp)
            Spacer(Modifier.height(16.dp))
            Text("Liter", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
        DottedOrbits(
            points = listOf(Offset(0.3f, 0.12f), Offset(0.55f, 0.42f), Offset(0.24f, 0.72f), Offset(0.82f, 0.86f)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp),
        )
    }
}

@Composable
fun TrainingCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    GlowCard(GlowStyle.Midnight, modifier.fillMaxWidth().height(96.dp), contentPadding = PaddingValues(horizontal = 28.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Training", 22)
            Spacer(Modifier.weight(1f))
            GlowSwitch(checked, onCheckedChange)
        }
    }
}

@Composable
fun BreathCard(modifier: Modifier = Modifier) {
    GlowCard(GlowStyle.Ember.copy(motion = GlowMotion.Breath(6f)), modifier.fillMaxWidth().height(220.dp), contentPadding = PaddingValues(28.dp)) {
        Column {
            CardLabel("Breath Volume", 22)
            Row(Modifier.padding(top = 22.dp)) {
                DotMatrixText("5.5", dot = 5.dp, gap = 2.dp)
                Text("L", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        ArcTimeline(
            marks = listOf(
                ArcMark(0.40f, Color(0xFFD8EF5C)), ArcMark(0.58f, Color(0xFF9FDC5A)),
                ArcMark(0.65f, Color(0xFFE9A064)), ArcMark(0.92f, Color(0xFFE9A064)), ArcMark(1f, Color(0xFFA9DD9A)),
            ),
            arcs = listOf(0.40f to 0.58f, 0.58f to 1f),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp),
        )
    }
}

private data class Tile(val label: String, val value: String, val unit: String, val color: Color, val motion: GlowMotion = GlowMotion.Drift)

/** A compact tile: the Bloom look, tinted with one metric's colour and moving like the metric. */
@Composable
private fun MetricTile(tile: Tile, modifier: Modifier = Modifier) {
    val (label, value, unit, color, motion) = tile
    val style = GlowStyle.Bloom.copy(
        color = color, radius = 28.dp, motion = motion,
        coreY = 0.68f, coreWidth = 0.6f, coreHeight = 0.5f, coreBlur = 16.dp, toneWidth = 16.dp,
        glowSize = 22.dp,
    )
    GlowCard(style, modifier.height(150.dp), contentPadding = PaddingValues(18.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.Bottom) {
            DotMatrixText(value, dot = 3.5.dp, gap = 1.5.dp)
            if (unit.isNotEmpty()) Text(unit, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private val SWATCHES = listOf(
    Color(0xFFD65AA0), Color(0xFFF2582A), Color(0xFF2A44B0), Color(0xFF2FB889),
    Color(0xFF8F5CFF), Color(0xFFE8B23A), Color(0xFF2AA6D6), Color(0xFFE23B4E),
)

/** Pick the main colour; the card above re-derives its whole palette from it. */
@Composable
private fun ColourRow(selected: Color, onSelect: (Color) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SWATCHES.forEach { c ->
            Box(
                Modifier
                    .size(30.dp)
                    .border(2.dp, if (c == selected) Color.White else Color.Transparent, CircleShape)
                    .padding(4.dp)
                    .background(c, CircleShape)
                    .clickable { onSelect(c) },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 1400)
@Composable
private fun GlowLabPreview() = OpenStrapTheme { GlowLabScreen(PaddingValues(), onBack = {}) }
