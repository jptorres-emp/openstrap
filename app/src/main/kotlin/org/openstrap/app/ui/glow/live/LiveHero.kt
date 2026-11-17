package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.glow.CardTone
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.glow.BigNumber
import org.openstrap.app.ui.glow.BigNumberText
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.workout.PausedGrey
import org.openstrap.app.ui.workout.familyColor
import org.openstrap.app.ui.workout.familyLightAngle

/*
 * The parts a Halo live screen is made of (workout-rules.md, Body order; boards HaloLiveRun and its family): one lit
 * hero in the sport's colour holding the screen's one dotted number, the sport's own solid card, and the plain figures
 * row. Written once so every family's screen reads as the same one.
 */

/** The sport's colour on a live screen, or the paused grey (workout-rules.md, Tokens). */
internal fun liveColor(r: WorkoutState.Recording): Color =
    if (r.paused) PausedGrey else familyColor(Workouts.family(r.workout.sportType))

/** Where the sport's light comes from on a live screen (workout-rules.md, Tokens). */
internal fun liveAngle(r: WorkoutState.Recording): Float = familyLightAngle(Workouts.family(r.workout.sportType))

/** The notes on a hero are the colour's tint, as the boards' #EEFBD0 on lime. */
private const val TINT_TOWARD_WHITE = 0.82f

internal fun heroTint(color: Color): Color = lerp(color, Color.White, TINT_TOWARD_WHITE)

/** The grey of a side button's label, a plan line and a card's own words (workout-rules.md, Frame: #C9CED6). */
internal val LiveLabelInk = Ink.Strong


/**
 * The lit hero: a solid squircle lit from one edge in [color], nothing outside it (board HaloLiveRun). [content] is
 * the title row, the big number and the one line, 14 apart.
 */
@Composable
internal fun LiveHero(color: Color, angle: Float, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HaloCard(modifier.fillMaxWidth(), tone = CardTone.lit(heroStyle(color, angle))) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S12), content = content)
    }
}

/** The hero's title row: 16 semibold at the left, one small note in the tint at the right. */
@Composable
internal fun HeroTitle(title: String, note: String?, tint: Color, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = GlowType.heroTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
        else if (note != null) Text(note, color = tint, style = GlowType.secondary, maxLines = 1)
    }
}

/** The hero's one 13 px line in the tint: what the strap will do, or what is next. */
@Composable
internal fun HeroLine(text: String, tint: Color) =
    Text(text, color = tint, style = GlowType.secondary)

/** The sport's own card on a live screen: a grey [org.openstrap.app.ui.glow.HaloCard] (kept as a name: 20 call sites). */
@androidx.compose.runtime.NonRestartableComposable
@Composable
internal fun LiveCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    org.openstrap.app.ui.glow.HaloCard(modifier, content = content)

/**
 * The heart rate as a row (board HaloLiveHiitWork): the heart in its zone's colour, the bpm in Display Light 20 and
 * [unit] after it, the zone's name at the right. Put it in a [LiveCard].
 */
@Composable
internal fun HeartLine(bpm: Int?, maxHr: Int, paused: Boolean, modifier: Modifier = Modifier, unit: String? = null) {
    val heartZones = org.openstrap.app.ui.dashboard.LocalHeartZones.current
    val zone = org.openstrap.app.data.WorkoutRecorder.liveZone(bpm, maxHr, heartZones)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (bpm == null) {
            Text(org.openstrap.app.presentation.WorkoutCopy.noHeartRate(paused), color = GlowType.noteColor, style = GlowType.secondary)
            return@Row
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            androidx.compose.material3.Icon(
                org.openstrap.app.ui.theme.AppIcons.Heartbeat, null,
                    tint = zone?.let { org.openstrap.app.ui.workout.ZONE_COLORS[it] } ?: Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text("$bpm", color = Color.White, style = GlowType.figureInline)
            Text(
                unit ?: androidx.compose.ui.res.stringResource(org.openstrap.app.R.string.workout_live_unit_bpm),
                color = GlowType.noteColor, style = GlowType.secondary, maxLines = 1,
            )
        }
        zone?.let { Text(org.openstrap.app.presentation.WorkoutCopy.zoneTitle(it).first, color = GlowType.noteColor,
            style = GlowType.secondary, maxLines = 1) }
    }
}
