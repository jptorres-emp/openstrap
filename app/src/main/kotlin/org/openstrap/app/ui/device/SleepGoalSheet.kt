package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.alarms.Wheel
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloSheet
import java.util.concurrent.TimeUnit.MINUTES
import org.openstrap.app.ui.theme.Ink

/**
 * The sleep goal on its own, in a sheet over the You page (board HaloSleepGoalSheet): the hours and the minutes on two
 * wheels with their units beside them, then the sleep need the goal is measured against ([needMinutes], from the profile
 * when it holds one, [fromProfile], else from your age when the profile has one, [ageKnown], else a default), a note on
 * what uses the goal, and Cancel and Save. It saves the same goal the goals sheet does. Cancel, or pulling the sheet
 * down, leaves it as it was. Minutes step by 5, or by 1 when the goal isn't on a 5.
 */
@Composable
fun SleepGoalSheet(
    goal: Int,
    needMinutes: Int,
    fromProfile: Boolean,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    ageKnown: Boolean = false,
) {
    var hours by rememberSaveable { mutableIntStateOf(goal / MINUTES_PER_HOUR) }
    var minutes by rememberSaveable { mutableIntStateOf(goal % MINUTES_PER_HOUR) }
    val minuteStep = remember { if (goal % MINUTE_STEP == 0) MINUTE_STEP else 1 }
    HaloSheet(
        onDismiss, stringResource(R.string.you_sleep_goal_title),
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel), onDismiss, stringResource(R.string.you_action_save),
                { onSave((hours * MINUTES_PER_HOUR + minutes).coerceIn(Goals.SLEEP_MINUTES)) },
            )
        },
    ) {
        Box(modifier.fillMaxWidth().padding(vertical = Space.S4), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(64.dp).clip(GlowLayout.TileShape).background(Greys.Control))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Wheel(
                    (Goals.SLEEP_MINUTES.first / MINUTES_PER_HOUR..Goals.SLEEP_MINUTES.last / MINUTES_PER_HOUR).toList(), hours,
                    { hours = it }, { it.toString() }, Modifier.weight(1f),
                )
                Unit(stringResource(R.string.you_unit_h))
                Wheel(
                    (0 until MINUTES_PER_HOUR step minuteStep).toList(), minutes,
                    { minutes = it }, { it.toString() }, Modifier.weight(1f),
                )
                Unit(stringResource(R.string.you_unit_min))
            }
        }
        HaloCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Text(
                    stringResource(R.string.you_sleep_goal_need, Units.duration(MINUTES.toSeconds(needMinutes.toLong()))),
                    color = NeedInk, style = GlowType.secondary, softWrap = false,
                )
                // The need stays on one line, and the source after it takes what's left, wrapping when it's long.
                HaloNote(modifier = Modifier.weight(1f), textAlign = TextAlign.End, text = stringResource(
                        when {
                            fromProfile -> R.string.you_sleep_goal_need_profile
                            ageKnown -> R.string.you_sleep_goal_need_age
                            else -> R.string.you_sleep_goal_need_default
                        },
                    ))
            }
        }
        Text(stringResource(R.string.you_sleep_goal_note), color = GlowType.noteColor, style = GlowType.footnote)
    }
}

/** A wheel's unit, 36 wide beside it (board: 15, #9F9F9F). */
@Composable
private fun Unit(text: String) = Text(text, Modifier.width(36.dp), color = GlowType.noteColor, style = GlowType.body)

private val NeedInk = Ink.Strong

private const val MINUTES_PER_HOUR = 60
private const val MINUTE_STEP = 5
