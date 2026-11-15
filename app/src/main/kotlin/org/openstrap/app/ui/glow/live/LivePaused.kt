package org.openstrap.app.ui.glow.live

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
import org.openstrap.app.ui.theme.MetricColors
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
 * ([recent], the live readings, hold the one at the pause), then the workout's [figures] so far (value, label).
 */
@Composable
internal fun ColumnScope.LivePaused(r: WorkoutState.Recording, liveHeartRate: Int?, recent: List<Pair<Long, Int>>,
    figures: List<Pair<String?, String>>) {
    val at = r.workout.pausedAt ?: return
    val now = nowTicking()
    val tint = heroTint(PausedGrey)
    LiveHero(PausedGrey, liveAngle(r)) {
        HeroTitle(stringResource(R.string.workout_live_paused_for), null, tint)
        HeroNumber(dotClock((now - at).coerceAtLeast(0)), null, tint)
        HeroLine(stringResource(R.string.workout_live_paused_note), tint)
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
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.Heartbeat, null, tint = MetricColors.heart, modifier = Modifier.size(16.dp))
                Text("$liveHeartRate", color = Color.White, style = GlowType.figureInline)
                Text(stringResource(R.string.workout_live_unit_bpm), color = GlowType.noteColor, style = GlowType.secondary)
            }
            if (drop != null && drop > 0) {
                Text(stringResource(R.string.workout_live_paused_down, drop), color = GlowType.noteColor, style = GlowType.secondary)
            }
        }
    }
    LiveFigures(figures)
}

/**
 * A row of plain live figures (value over label), no card: each column after the first has a rule on its left and 16 of
 * room after it (workout-rules.md, section 2; board HaloLiveRun). A missing value is the waiting dash.
 */
@Composable
internal fun LiveFigures(figures: List<Pair<String?, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp).height(IntrinsicSize.Min)) {
        figures.forEachIndexed { i, (value, label) ->
            if (i > 0) StatRule()
            LiveStat(label, Modifier.weight(1f).padding(start = if (i > 0) 16.dp else 0.dp)) { LiveValue(value) }
        }
    }
}
