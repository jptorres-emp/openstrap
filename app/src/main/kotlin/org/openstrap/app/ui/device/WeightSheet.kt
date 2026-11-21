package org.openstrap.app.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import org.openstrap.app.R
import org.openstrap.app.data.WeightLog
import org.openstrap.app.data.db.WeightEntry
import org.openstrap.app.data.settings.Units
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.ui.glow.GlowLineChart
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNumberField
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.theme.MetricColors
import java.time.LocalDate
import kotlin.math.abs

/** A weight as the log shows it, one decimal, in kg or in lb by the profile ([imperial]). */
fun weightText(kg: Float, imperial: Boolean, strings: Strings = Strings.app): String =
    if (imperial) strings.format(R.string.fork_weight_lb, Units.kgToLb(kg)) else strings.format(R.string.fork_weight_kg, kg)

/** The You row's line for the weight log: the last weight and the 7-day average, or a nudge before the first. */
fun weightLine(summary: WeightLog.Summary?, imperial: Boolean, strings: Strings = Strings.app): String = when {
    summary == null -> strings.get(R.string.fork_weight_line_none)
    summary.week != null -> strings.format(
        R.string.fork_weight_line, weightText(summary.latest.kg, imperial, strings), weightText(summary.week, imperial, strings),
    )
    else -> weightText(summary.latest.kg, imperial, strings)
}

/** How the 7-day average compares with the 7 days before, or null without both. A change under 0.05 is the same. */
fun weightChange(summary: WeightLog.Summary?, imperial: Boolean, strings: Strings = Strings.app): String? {
    val change = summary?.change ?: return null
    val text = weightText(abs(change), imperial, strings)
    return when {
        abs(change) < SAME_KG -> strings.get(R.string.fork_weight_same)
        change < 0 -> strings.format(R.string.fork_weight_down, text)
        else -> strings.format(R.string.fork_weight_up, text)
    }
}

/** Under this, a change of the 7-day average reads as the same: it rounds to 0.0. */
private const val SAME_KG = 0.05f

/**
 * The weight log in a sheet, from the You page's Weight row: today's weight to type (filled in when there is one),
 * the 7-day average and how it moved, and each week's average as a line once two weeks have one. Save keeps today's
 * weight and makes it the profile's ([onSave] gets kilograms); Cancel, or pulling the sheet down, changes nothing.
 */
@Composable
fun WeightSheet(
    entries: List<WeightEntry>,
    today: LocalDate,
    imperial: Boolean,
    onSave: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val todays = remember(entries, today) { WeightLog.today(entries, today) }
    var text by rememberSaveable(todays) {
        mutableStateOf(todays?.kg?.let { "%.1f".format(java.util.Locale.ROOT, if (imperial) Units.kgToLb(it) else it) }.orEmpty())
    }
    val kg = WeightLog.parse(text, imperial)
    val summary = remember(entries, today) { WeightLog.summary(entries, today) }
    val weeks = remember(entries, today) { WeightLog.weeks(entries, today) }
    PageSheet(onDismiss, stringResource(R.string.fork_weight_title), note = stringResource(R.string.fork_weight_note)) {
        PageNumberField(
            text, { text = it }, stringResource(R.string.fork_weight_field),
            stringResource(if (imperial) R.string.fork_weight_unit_lb else R.string.fork_weight_unit_kg),
            error = text.isNotBlank() && kg == null, decimal = true,
        )
        summary?.week?.let { week ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.fork_weight_average, weightText(week, imperial)), color = Color.White, style = GlowType.body)
                weightChange(summary, imperial)?.let {
                    Text(it, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary)
                }
            }
        }
        val shown = weeks.dropWhile { it.kg == null }
        if (shown.count { it.kg != null } >= 2) {
            val values = shown.map { w -> w.kg?.let { if (imperial) Units.kgToLb(it) else it } }
            val described = stringResource(
                R.string.fork_weight_chart, shown.size,
                weightText(shown.first { it.kg != null }.kg!!, imperial), weightText(shown.last { it.kg != null }.kg!!, imperial),
            )
            GlowLineChart(
                values, Modifier.semantics { contentDescription = described }, height = 110.dp,
                labels = listOf(DayWords.dayMonth(shown.first().end), DayWords.dayMonth(shown.last().end)),
                showAverage = false, color = MetricColors.steps, format = { "%.1f".format(java.util.Locale.getDefault(), it) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageButton(stringResource(R.string.glow_cancel), onDismiss, Modifier.weight(1f))
            PageButton(stringResource(R.string.glow_save), { kg?.let(onSave) }, Modifier.weight(1f), enabled = kg != null, primary = true)
        }
    }
}
