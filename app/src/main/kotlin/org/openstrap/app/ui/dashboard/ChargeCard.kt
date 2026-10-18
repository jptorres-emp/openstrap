package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.ChartSegment
import org.openstrap.app.ui.components.LegendDot
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/** What each charge state looks like on the strip under the chart; rest and no-data stay blank. */
private val STATE_COLORS = mapOf(
    Recovery.State.Sleep to MetricColors.sleepDeep,
    Recovery.State.Relax to MetricColors.hrv,
    Recovery.State.Activity to MetricColors.steps.copy(alpha = 0.6f),
    Recovery.State.Exercise to MetricColors.steps,
    Recovery.State.ExerciseRecovery to MetricColors.steps.copy(alpha = 0.35f),
    Recovery.State.Stress to MetricColors.stress,
)

/**
 * Strip segments from the charge samples: each 15-minute block takes its most common state (so a strap
 * stress reading flickering around the threshold doesn't turn the strip into confetti), then equal
 * neighbouring blocks join up.
 */
private fun chargeSegments(points: List<Recovery.ChargePoint>): List<ChartSegment> {
    val block = 15 * 60L
    val blocks = points.groupBy { it.t / block * block }.toSortedMap()
        .mapValues { (_, v) -> v.groupingBy { it.state }.eachCount().maxBy { it.value }.key }
    val out = mutableListOf<ChartSegment>()
    for ((t, state) in blocks) {
        val color = STATE_COLORS[state] ?: continue
        val last = out.lastOrNull()
        if (last != null && last.color == color && last.to == t) out[out.size - 1] = last.copy(to = t + block)
        else out += ChartSegment(t, t + block, color)
    }
    return out
}

/** Today's battery: the value now, the day's curve and what filled and drained it. */
@Composable
fun ChargeCard(
    r: RecoveryResult,
    dayRange: LongRange,
    sleepBand: LongRange?,
    onClick: (() -> Unit)? = null,
    /** False when a Charge hero above already shows the number; the card then shows just the day's curve. */
    showValues: Boolean = true,
) {
    val c = r.charge
    Panel(onClick = onClick) {
        PanelHeader("Charge", PhosphorIcons.Lightning, MetricColors.chargeAt(c.latest), trailing = c.atWake?.let { "Woke at ${it.toInt()}" }, chevron = onClick != null)
        val now = c.latest
        if (!c.anchored) {
            EmptyHint("Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from.")
            return@Panel
        }
        if (now == null) {
            EmptyHint("Charge appears once the strap has a few hours of heart rate.")
            return@Panel
        }
        if (showValues) Row(verticalAlignment = Alignment.Bottom) {
            BigValue("${now.toInt()}", "/ 100", Modifier.weight(1f), caption = Narrative.chargeHeadline(now.toInt()))
            BigValue("+${c.recharged.toInt()}", "", caption = "Recharged")
            Spacer(Modifier.width(18.dp))
            BigValue("−${(c.drainedExercise + c.drainedStress + c.drainedLiving).toInt()}", "", caption = "Drained")
        }
        LineChart(
            c.points.map { ChartPoint(it.t, it.value) }, MetricColors.chargeAt(now), dayRange, height = 130.dp,
            showAverage = false, band = sleepBand, yRange = 0f..100f, segments = chargeSegments(c.points), unit = "/ 100",
            valueGradient = MetricColors.chargeScale.map { (f, col) -> f * 100f to col },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot("Sleep", STATE_COLORS.getValue(Recovery.State.Sleep))
            LegendDot("Calm", STATE_COLORS.getValue(Recovery.State.Relax))
            LegendDot("Active", STATE_COLORS.getValue(Recovery.State.Exercise))
            LegendDot("Stress", STATE_COLORS.getValue(Recovery.State.Stress))
        }
    }
}

@Composable
private fun HowItWorks() {
    Panel {
        PanelHeader("How it works", PhosphorIcons.Info)
        ExplainerSection(
            "Recovery: how ready your body is",
            "Each morning we compare your night with what's normal for you, not with other people:",
            listOf(
                "Heart rate variability. Higher than your usual means your body has bounced back.",
                "Resting heart rate. Lower than your usual is good. A jump can mean strain or a cold coming on.",
                "Sleep. How long you slept last night, and whether you've been short on sleep this week.",
                "Temperature and breathing. A clear change from your normal is an early warning sign.",
                "Training. Whether yesterday was much harder than you're used to.",
            ),
            "If one of these is far off, your score stays low even when the rest look fine.",
        )
        ExplainerSection(
            "Charge: your energy right now",
            "Think of it as a battery that runs all day:",
            listOf(
                "Sleep fills it. Deep sleep most of all, and a calm, steady night fills it faster.",
                "Being awake. Slowly uses it up, a little every hour.",
                "Exercise and stress. Use it faster, while quiet, relaxed moments slow the drain.",
            ),
        )
        Text(
            "Everything is worked out on this phone from your strap's data. It's a guide to help you plan your day, " +
                "not medical advice.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A short titled block for [HowItWorks]: a heading, a lead-in line, bullets and an optional closing line. */
@Composable
private fun ExplainerSection(title: String, intro: String, bullets: List<String>, outro: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (b in bullets) {
            Row {
                Box(Modifier.padding(top = 8.dp, end = 10.dp).size(5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                // "Lead. Rest of the sentence": the lead is bold so the list scans quickly.
                val cut = b.indexOf(". ")
                val text = if (cut < 0) androidx.compose.ui.text.AnnotatedString(b) else androidx.compose.ui.text.buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) {
                        append(b.substring(0, cut + 1))
                    }
                    append(b.substring(cut + 1))
                }
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (outro != null) Text(outro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
