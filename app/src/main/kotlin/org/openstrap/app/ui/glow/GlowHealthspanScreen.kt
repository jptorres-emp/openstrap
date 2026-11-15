package org.openstrap.app.ui.glow

import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.openstrap.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import org.openstrap.app.presentation.Range
import org.openstrap.app.presentation.TabCopy
import org.openstrap.app.ui.dashboard.InfoCopy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import org.openstrap.app.data.Healthspan
import org.openstrap.app.ui.dashboard.ScreenList
import kotlin.math.abs
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/** The scale on the Healthspan screen's bars: a habit this many years either side of the middle fills its half. */
private const val BAR_FULL_YEARS = 1.5f

/** "Updated Sunday, next in 5 days", from the Sunday the estimate was made; a week old or more says only that it's weekly. */
@Composable
@ReadOnlyComposable
private fun updatedText(asOf: Long): String {
    val made = java.time.Instant.ofEpochSecond(asOf).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val next = 7 - java.time.temporal.ChronoUnit.DAYS.between(made, java.time.LocalDate.now()).toInt().coerceIn(0, 7)
    return when (next) {
        7 -> stringResource(R.string.glow_healthspan_updated_today)
        1 -> stringResource(R.string.glow_healthspan_updated_tomorrow)
        0 -> stringResource(R.string.glow_healthspan_updated_weekly)
        else -> pluralStringResource(R.plurals.glow_healthspan_updated_next, next, next)
    }
}

/**
 * Habit Age's detail (board HaloDetailHabitAge, kind Estimate): the years in Doto with "vs 34 actual" under the status, the
 * pace on a solid scale from -1x to 3x, then each habit as a row with its effect on the same scale. There is no day strip
 * and no range, since it is worked out weekly. Before an estimate, the headline's state word says why it waits.
 */
@Composable
fun GlowHealthspanScreen(state: Healthspan.Outcome?, contentPadding: PaddingValues, onBack: () -> Unit) {
    val r = state?.result
    var info by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val color = MetricColors.habitAge
    val withheld = state?.withheld
    val needsProfile = r == null && state?.missing?.isNotEmpty() == true && withheld?.profile == true
    val waiting = when {
        r != null -> null
        needsProfile -> profileReminder(state.missing)
        state?.firstEstimate != null -> TabCopy.firstEstimate(
            java.time.Instant.ofEpochSecond(state.firstEstimate).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        )
        else -> withheld?.reason
    }
    val shown = r?.estimate?.let { Math.round(it) } ?: state?.age
    val line = r?.let { TabCopy.habitAgeLine(it.estimate, state.lastWeek, it.low, it.high) }
    val years = stringResource(R.string.glow_healthspan_years)
    val headline = when {
        shown != null -> DetailHeadline(
            shown.toString(),
            "$years, " + (r?.let { updatedText(it.asOf) } ?: TabCopy.habitAgeForNow(waiting)),
            line?.text, unit = years,
        )
        else -> DetailHeadline.none(
            stringResource(R.string.glow_healthspan_title), waiting ?: stringResource(R.string.glow_healthspan_updated_weekly),
        )
    }
    DetailTemplate(
        stringResource(R.string.glow_healthspan_title), color, emptyList(), onInfo = { info = true }, onBack = onBack,
        contentPadding = contentPadding, headline = headline, strip = emptyList(), onDay = {},
        ranges = listOf(Range.Day), range = Range.Day, onRange = {},
        heroBelow = r?.let { { RangeLine(TabCopy.ageRange(it.low, it.high)) } },
    ) {
        if (r != null) {
            item { PacePanel(r.pace) }
            item { TermsPanel(r) }
        }
    }
    if (info) {
        val here = r?.let { InfoCopy.habitAgeBar(it.age - Math.round(it.estimate)) }
        InfoSheet(InfoCopy.HABIT_AGE, color, here, here?.let { InfoCopy.HERE }, onDismiss = { info = false })
    }
}

/** "Likely 29 to 33" under the headline. */
@Composable
private fun RangeLine(text: String) =
    Text(text, color = Color.White.copy(alpha = GlowType.MOMENT_ALPHA), style = GlowType.secondary)

/**
 * Where the last 30 days sit against the 6-month habits: "Pace of aging" with its pace as the note, the scale from -1x to
 * 3x with a marker at the pace, and a sentence in the Detail sentence type saying what it means.
 */
@Composable
private fun PacePanel(pace: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailHeading(
            stringResource(R.string.glow_healthspan_pace_title),
            note = pace?.let { stringResource(R.string.glow_healthspan_pace_note, paceLabel(it), paceText(it)) } ?: paceLabel(null),
        )
        if (pace != null) PaceScale(marker = (pace + 1f) / 4f, color = MetricColors.habitAge, Modifier.padding(horizontal = 4.dp))
        Text(
            stringResource(
                when {
                    pace == null -> R.string.glow_healthspan_pace_needs
                    pace < 1f -> R.string.glow_healthspan_pace_beat
                    pace > 1f -> R.string.glow_healthspan_pace_behind
                    else -> R.string.glow_healthspan_pace_match
                },
            ),
            color = Color.White, style = GlowType.detailSentence, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** A solid track from -1x to 3x with the marker at the pace and the ends named under it. */
@Composable
private fun PaceScale(marker: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val y = size.height / 2
            val track = Size(size.width, 6.dp.toPx())
            drawRoundRect(DetailGridInk, Offset(0f, y - 3.dp.toPx()), track, CornerRadius(3.dp.toPx()))
            val x = 8.dp.toPx() + (size.width - 16.dp.toPx()) * marker.coerceIn(0f, 1f)
            drawRoundRect(color, Offset(0f, y - 3.dp.toPx()), Size(x, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawCircle(Color.Black, 8.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 8.dp.toPx(), Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
        }
        ScaleLabels(
            stringResource(R.string.glow_healthspan_scale_slower), stringResource(R.string.glow_healthspan_scale_steady),
            stringResource(R.string.glow_healthspan_scale_faster),
        )
    }
}

/**
 * Each habit against its typical value (board HaloDetailHabitAge, "What moves it"): one plain panel, a row a habit with its
 * tile, what it takes off or adds on the right and the numbers behind it, and its bar on the one scale under it. No row
 * opens anything.
 */
@Composable
private fun TermsPanel(r: Healthspan.Result) {
    DetailPanel(title = stringResource(R.string.glow_healthspan_terms_title), note = stringResource(R.string.glow_healthspan_terms_note)) {
        r.terms.forEachIndexed { i, t ->
            val (icon, ink) = habitIcon(t.habit)
            Column(
                Modifier.fillMaxWidth().topHairline(i > 0, DetailRowLine).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(GlowLayout.IconShape).background(ink.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(icon, null, tint = ink, modifier = Modifier.size(17.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RowName(t.habit.label)
                        Text(termDetail(t, r.age), color = GlowType.noteColor, style = GlowType.noteSmall)
                    }
                    Text(
                        effectText(t.years), Modifier.widthIn(max = 96.dp),
                        color = effectInk(t.years),
                        style = GlowType.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
                EffectBar(t.years, Modifier.padding(start = 44.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.glow_healthspan_scale_younger), color = DetailAxisInk, style = GlowType.caption)
            Text(stringResource(R.string.glow_healthspan_scale_older), color = DetailAxisInk, style = GlowType.caption)
        }
    }
    if (r.fitnessCapped) Text(
        stringResource(R.string.glow_healthspan_fitness_capped),
        color = GlowType.footnoteColor, style = GlowType.footnote, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** A habit's metric icon and ink, as its metric's rows draw them (spec 2.5): Load's barbell, the heart, steps, sleep. */
private fun habitIcon(h: Healthspan.Habit): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> = when (h) {
    Healthspan.Habit.Strength -> AppIcons.Barbell to MetricColors.load
    Healthspan.Habit.Vo2max -> AppIcons.FastWind to MetricColors.heart
    Healthspan.Habit.Steps -> AppIcons.Footprints to MetricColors.steps
    Healthspan.Habit.Regularity, Healthspan.Habit.TimeAsleep -> AppIcons.Moon to MetricColors.sleepDeep
    Healthspan.Habit.RestingHr -> AppIcons.Heart to MetricColors.heart
}

/** A scale's ends and middle in Caption, the middle in white. */
@Composable
private fun ScaleLabels(start: String, middle: String, end: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        ScaleLabel(start, 0f)
        ScaleLabel(middle, at = 0.5f, color = Color.White)
        ScaleLabel(end, 1f)
    }
}

/** A scale's label in Caption, centred on [at] (0 to 1) across the scale and kept inside it. */
@Composable
internal fun BoxScope.ScaleLabel(text: String, at: Float, color: Color = Color.White.copy(alpha = GlowType.CAPTION_ALPHA)) {
    // Centred on its point by aligning a share of the way across, as BiasAlignment does.
    Text(text, color = color, style = GlowType.caption, modifier = Modifier.align(BiasAlignment((at * 2f - 1f).coerceIn(-1f, 1f), 0f)))
}

/** A habit's effect in words: grey with none, the Habit Age tint when it takes years off, white when it adds. */
private fun effectInk(years: Float): Color = when {
    abs(years) < NO_EFFECT -> GlowType.noteColor
    years < 0 -> statusTint(MetricColors.habitAge)
    else -> Color.White.copy(alpha = GlowType.ROW_VALUE_ALPHA)
}

/** Under this many years a habit has no effect to show. */
private const val NO_EFFECT = 0.05f

/**
 * One habit's effect as a bar out from the middle: left takes years off, right adds them. A solid 4 dp track, a mark at the
 * middle and the bar in Habit Age's own ink either way; a habit with no effect shows only the track and the mark.
 */
@Composable
private fun EffectBar(years: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val mid = size.width / 2
        val track = Size(size.width, 4.dp.toPx())
        drawRoundRect(DetailGridInk, Offset(0f, 3.dp.toPx()), track, CornerRadius(2.dp.toPx()))
        if (abs(years) >= NO_EFFECT) {
            val end = mid + (years / BAR_FULL_YEARS).coerceIn(-1f, 1f) * mid
            drawRoundRect(
                MetricColors.habitAge, Offset(minOf(mid, end), 1.dp.toPx()), Size(abs(end - mid), 8.dp.toPx()),
                CornerRadius(4.dp.toPx()),
            )
        }
        val mark = Size(2.dp.toPx(), 10.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(mid - 1.dp.toPx(), 0f), mark, CornerRadius(1.dp.toPx()))
    }
}
