package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.glow.HaloBigNumber
import org.openstrap.app.ui.glow.BigNumber
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.PausedGrey
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.DotMatrixText
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.dotClock
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * A paused workout (workout-rules.md, Colour; board HaloLivePaused): a grey hero, "Paused for" and the pause counting up
 * in the one dotted number, then a solid card with the heart rate now and how far it has dropped since you stopped
 * ([recent], the live readings, hold the one at the pause), then the workout's [figures] so far (value, label). A
 * [detail] adds a line to the hero: where a round workout stopped.
 */
@Composable
internal fun ColumnScope.LivePaused(r: WorkoutState.Recording, liveHeartRate: Int?, recent: List<Pair<Long, Int>>,
    figures: List<Pair<String?, String>>, detail: String? = null) {
    val at = r.workout.pausedAt ?: return
    val now = nowTicking()
    val tint = heroTint(PausedGrey)
    LiveHero(PausedGrey, liveAngle(r)) {
        HeroTitle(stringResource(R.string.workout_live_paused_for), null, tint)
        HaloBigNumber(dotClock((now - at).coerceAtLeast(0)), BigNumber.Live, unitColor = tint)
        HeroLine(stringResource(R.string.workout_live_paused_note), tint)
        detail?.let { HeroLine(it, tint) }
    }
    val before = Workouts.heartRateAt(recent, at) ?: recent.lastOrNull { it.first <= at }?.second
    val drop = if (before != null && liveHeartRate != null) before - liveHeartRate else null
    if (liveHeartRate != null) LiveCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_live_heart_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                modifier = Modifier.weight(1f))
            Text(stringResource(R.string.workout_live_paused_since), color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Icon(AppIcons.Heart, null, tint = GlowType.noteColor, modifier = Modifier.size(IconSize.GlyphS))
                Text("$liveHeartRate", color = Color.White, style = GlowType.figureInline)
                Text(stringResource(R.string.workout_live_unit_bpm), color = GlowType.noteColor, style = GlowType.secondary)
            }
            if (drop != null && drop > 0) {
                Text(stringResource(R.string.workout_live_paused_down, drop), color = GlowType.noteColor, style = GlowType.secondary)
            }
        }
    }
    // The readings stop with the pause, so a peak is the highest while moving.
    val peak = stringResource(R.string.workout_live_figure_peak_bpm)
    val peakMoving = stringResource(R.string.workout_live_figure_peak_moving)
    LiveFigures(figures.map { (v, label) -> v to if (label == peak) peakMoving else label })
}

/**
 * A row of plain live figures (value over label), no card (workout-rules.md, section 2; board HaloLiveRun): a pure
 * rename of [org.openstrap.app.ui.glow.HaloFigures] with [org.openstrap.app.ui.glow.FiguresVariant.Live], kept so the
 * nine live screens read as before.
 */
@androidx.compose.runtime.NonRestartableComposable
@Composable
internal fun LiveFigures(figures: List<Pair<String?, String>>, modifier: Modifier = Modifier) =
    org.openstrap.app.ui.glow.HaloFigures(figures, modifier, org.openstrap.app.ui.glow.FiguresVariant.Live)

/**
 * The label of the live Active kcal figure: its name, or why there is none. Kcal needs weight, height and age, so
 * without a figure the label says what it waits for.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun activeKcalLabel(hasValue: Boolean): String =
    stringResource(if (hasValue) R.string.workout_live_figure_active_kcal else R.string.workout_live_figure_kcal_needs_profile)
