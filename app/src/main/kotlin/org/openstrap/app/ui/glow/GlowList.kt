package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Corner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.presentation.Metric
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink

/*
 * The quiet tier of the glow language: a grouped list for small facts (body signals, time to recover, calories) and
 * for lists of things (workouts, contributors, settings). No glow at all, so the cards above it keep the light.
 */

/**
 * A grouped list: a [HaloCard] of rows with an optional title row ([title] left, an optional [note] right), usually
 * [MetricRow]s. With [onNote] the note is a way on ("All 9"), with a caret.
 */
@Composable
fun ListPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    note: String? = null,
    onNote: (() -> Unit)? = null,
    /** A sync marker next to the title ([StateMarker]), "Up to 09:40". */
    marker: String? = null,
    /** A neutral tag next to the title when there's no [marker] ([TitleMark]), "Partial day". */
    tag: TitleTag? = null,
    content: @Composable ColumnScope.() -> Unit,
) = HaloCard(
    modifier,
    fill = CardFill.Rows,
    title = if (title != null || note != null) {
        { HaloTitle(title.orEmpty(), note = note, onNote = onNote, marker = marker, tag = tag) }
    } else null,
    content = content,
)

/** A row icon that isn't a metric's (a workout's sport): white at 72% (spec 2.8). */
internal val SportIconTint = Ink.Secondary

/* -------------------------------------------- Row icons --------------------------------------------- */

/** A metric's row icon (spec 2.5, Grouped list), drawn in its ink. Hugeicons names in the comments. */
internal fun Metric.rowIcon(): ImageVector = when (this) {
    Metric.HeartRate, Metric.RestingHr -> AppIcons.Heart // Favourite
    Metric.Hrv -> AppIcons.Pulse01
    Metric.Spo2 -> AppIcons.Drop // Droplet
    Metric.Stress -> AppIcons.Brain02
    Metric.SkinTemperature -> AppIcons.ThermometerSimple // Thermometer
    Metric.Breathing -> AppIcons.Lungs
    Metric.Steps -> AppIcons.Footprints // FootPrints
}

/**
 * A score's row icon. Recovery, which the spec's table leaves out, takes its tab's icon; Active minutes a timer;
 * Heart points the heart, in its ink.
 */
internal fun Score.rowIcon(): ImageVector = when (this) {
    Score.Recovery -> AppIcons.Plant
    Score.Energy -> AppIcons.BatteryCharging // BatteryCharging01
    Score.Sleep, Score.Regularity, Score.Naps -> AppIcons.Moon // Moon02
    Score.HeartPoints -> AppIcons.Heart // Favourite
    Score.ActiveMinutes -> AppIcons.Timer
    Score.Load -> AppIcons.Barbell // Dumbbell01
    Score.Vo2Max -> AppIcons.FastWind
    Score.Recover -> AppIcons.Hourglass
    Score.Calories -> AppIcons.Fire
    Score.Distance -> AppIcons.Route01
}

/** The row icon for what a row opens, when it's a metric's; null for anything else. */
internal fun Detail.rowIcon(): ImageVector? = when (this) {
    is Detail.OfMetric -> metric.rowIcon()
    is Detail.OfScore -> score.rowIcon()
    Detail.Recovery -> Score.Recovery.rowIcon()
    Detail.Sleep -> Score.Sleep.rowIcon()
    // Habit Age and the workouts aren't in the spec's table: an age in years, and a runner apart from Load's barbell.
    Detail.Healthspan -> AppIcons.CalendarBlank
    Detail.AllWorkouts -> AppIcons.PersonSimpleRun
    else -> null
}

/**
 * The empty value: a short dim bar drawn by the UI, never a dash character. 14 by 3 in a row or a figure, 28 by 6 in a
 * card's number's place ([onTile], section 5 row 12).
 */
@Composable
fun WaitingDash(modifier: Modifier = Modifier, onTile: Boolean = false) {
    val described = stringResource(R.string.glow_no_value_yet)
    Box(
        modifier
            .let { if (onTile) it.size(BigNumberSpec.DashWidth, BigNumberSpec.DashHeight) else it.size(14.dp, 3.dp) }
            .background(Ink.Disabled, Corner.Pill)
            .semantics { contentDescription = described },
    )
}
