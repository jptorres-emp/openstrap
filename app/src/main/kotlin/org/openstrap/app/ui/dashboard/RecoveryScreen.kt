package org.openstrap.app.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.ui.components.BigValue
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.ChartSegment
import org.openstrap.app.ui.components.LegendDot
import org.openstrap.app.ui.components.ContributorRow
import org.openstrap.app.ui.components.EmptyHint
import org.openstrap.app.ui.components.HeroPanel
import org.openstrap.app.ui.components.LineChart
import org.openstrap.app.ui.components.Panel
import org.openstrap.app.ui.components.PanelHeader
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.components.TrendChart
import org.openstrap.app.ui.components.TrendPoint
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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

/** Charge as the Today hero once the morning is over: the battery now and where it went. */
@Composable
fun ChargeHero(r: RecoveryResult, onClick: (() -> Unit)? = null) {
    val now = r.charge.latest ?: return
    HeroPanel(
        label = "Charge",
        value = "${now.toInt()}",
        progress = now / 100f,
        accent = MetricColors.charge,
        headline = Narrative.chargeHeadline(now.toInt()),
        body = Narrative.chargeBody(r.charge),
        onClick = onClick,
    )
}

/** Readiness hero for Today and the detail screen. */
@Composable
fun ReadinessHero(r: RecoveryResult, onClick: (() -> Unit)? = null) {
    val score = r.readiness.score ?: return
    HeroPanel(
        label = if (r.readiness.calibrating) "Readiness · learning" else "Readiness",
        value = "$score",
        progress = score / 100f,
        accent = if (r.readiness.strainAlert) MetricColors.warn else MetricColors.readiness,
        headline = Narrative.readinessHeadline(score, r.readiness.strainAlert),
        body = Narrative.readinessBody(r.readiness),
        onClick = onClick,
    )
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
        PanelHeader("Charge", PhosphorIcons.BatteryCharging, MetricColors.charge, trailing = c.atWake?.let { "Woke at ${it.toInt()}" }, chevron = onClick != null)
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
            c.points.map { ChartPoint(it.t, it.value) }, MetricColors.charge, dayRange, height = 130.dp,
            showAverage = false, band = sleepBand, yRange = 0f..100f, segments = chargeSegments(c.points),
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
fun RecoveryScreen(r: RecoveryResult?, date: LocalDate, dayRange: LongRange, sleepBand: LongRange?, contentPadding: PaddingValues, onBack: () -> Unit, onEditProfile: () -> Unit) {
    BackHandler(onBack = onBack)
    ScreenList(contentPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(PhosphorIcons.ArrowLeft, "Back") }
                Text("Recovery", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                SectionLabel(
                    when (date) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        if (r == null || r.date != date) {
            item { Panel { EmptyHint("Calculating…") } }
            return@ScreenList
        }
        val ready = r.readiness
        if (ready.score != null) item { ReadinessHero(r) }
        else item {
            Panel {
                PanelHeader("Readiness", PhosphorIcons.Plant, MetricColors.readiness)
                EmptyHint("Readiness is scored each morning from the night's sleep. Wear the strap to bed.")
            }
        }

        if (ready.strainAlert) item {
            Panel(highlight = MetricColors.warn) {
                PanelHeader("Your body is under strain", PhosphorIcons.Warning, MetricColors.warn)
                Text(
                    ready.alertSignals.joinToString(", ").replaceFirstChar { it.uppercase() } + ". " +
                        "Changes like these together often come with illness, heavy training, alcohol or a late night. " +
                        "OpenStrap isn't a medical device; see a doctor if you feel unwell.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (ready.contributors.isNotEmpty()) item {
            Panel {
                PanelHeader("Contributors", PhosphorIcons.Plant, MetricColors.readiness, trailing = if (ready.calibrating) "Learning ${ready.baselineNights}/${Recovery.CALIBRATED_NIGHTS} nights" else null)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (c in ready.contributors.sortedByDescending { it.factor.weight }) {
                        val rating = Narrative.ratingFor(c.score)
                        ContributorRow(c.factor.label, rating.label, c.score / 100f, rating.color, c.detail)
                    }
                }
                if (ready.capped) {
                    Text(
                        "Capped: one body signal is far from your normal, so good sleep can't fully make up for it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ready.calibrating) {
                    Text(
                        "Readiness compares each night with your own history. It gets sharper over the first two weeks.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (r.readinessTrend.any { it.second != null }) item {
            Panel {
                PanelHeader("Readiness", PhosphorIcons.ChartLine, MetricColors.readiness, trailing = "Last 7 days")
                TrendChart(
                    r.readinessTrend.map { (d, v) -> TrendPoint(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), v?.toFloat()) },
                    MetricColors.readiness,
                )
            }
        }

        item { ChargeCard(r, dayRange, sleepBand) }
        item { ChargeBreakdown(r.charge) }
        item { LoadPanel(r, onEditProfile) }
        item { HowItWorks() }
    }
}

@Composable
private fun ChargeBreakdown(c: Recovery.ChargeDay) {
    if (c.latest == null) return
    Panel {
        PanelHeader("Where your charge went", PhosphorIcons.BatteryCharging, MetricColors.charge)
        val total = maxOf(c.recharged, c.drainedExercise + c.drainedStress + c.drainedLiving, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow("Recharged by sleep and calm rest", "+${c.recharged.toInt()}", c.recharged / total, MetricColors.good)
            FlowRow("Movement and exercise", "−${c.drainedExercise.toInt()}", c.drainedExercise / total, MetricColors.steps)
            FlowRow("Stress", "−${c.drainedStress.toInt()}", c.drainedStress / total, MetricColors.stress)
            FlowRow("Being awake", "−${c.drainedLiving.toInt()}", c.drainedLiving / total, MetricColors.sleepLight)
        }
        if (c.highest != null && c.lowest != null) {
            Text(
                "Highest ${c.highest.toInt()} · lowest ${c.lowest.toInt()} this day",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowRow(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(5.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun LoadPanel(r: RecoveryResult, onEditProfile: () -> Unit) {
    Panel {
        PanelHeader("Training load", PhosphorIcons.Barbell, MetricColors.steps, trailing = "TRIMP")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BigValue("${r.load.toInt()}", "", caption = "Today")
            BigValue(r.chronicLoad?.toInt()?.toString() ?: "—", "", caption = "Usual day")
            BigValue("${r.maxHr.toInt()}", "bpm", caption = "Max HR")
        }
        Text(
            "Load weights each minute by how hard your heart works between resting (${r.restingHr.toInt()} bpm) and maximum. " +
                "An hour of moderate exercise is about 60–80.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!r.maxHrFromAge) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Max HR is estimated. Add your age for accurate effort numbers.",
                    style = MaterialTheme.typography.bodySmall, color = MetricColors.warn, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditProfile) { Text("Add age") }
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    Panel {
        PanelHeader("How it works", PhosphorIcons.Info)
        ExplainerSection(
            "Readiness: how recovered you are",
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
