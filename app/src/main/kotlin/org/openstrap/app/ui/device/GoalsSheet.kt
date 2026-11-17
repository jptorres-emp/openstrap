package org.openstrap.app.ui.device

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.NoteVariant
import org.openstrap.app.ui.glow.HaloStepper
import org.openstrap.app.ui.glow.StepperSize
import org.openstrap.app.ui.glow.StepperNumber
import androidx.compose.foundation.layout.fillMaxSize
import org.openstrap.app.ui.glow.CardFill
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.settings.CalorieGoal
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.presentation.GoalCopy
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.glow.ButtonPair
import org.openstrap.app.ui.glow.HaloButton
import org.openstrap.app.ui.glow.HaloButtonVariant
import org.openstrap.app.ui.glow.HaloButtonSize
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.HaloRow
import org.openstrap.app.ui.glow.HaloSheet
import org.openstrap.app.ui.glow.HaloIconButton
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/**
 * Steps, calories, sleep and active hours to aim for each day, and workouts a week, in a sheet over the You page (board
 * HaloGoalsSheet, spec 3.4, calories spec 4.6): one panel of stepper rows, each a minus, the goal in Funnel Display Light
 * and a plus, with a tap on the number to type it. Every goal ring and "goal met" uses these, and the calorie goal goes
 * to the strap. [suggestedCalories] is the calorie goal your last four weeks suggest ([CalorieGoal.suggested]), null
 * while it loads or under a week of full days. Cancel, or pulling the sheet down, leaves them as they were.
 */
@Composable
fun GoalsSheet(goals: Goals, onSave: (Goals) -> Unit, onDismiss: () -> Unit, suggestedCalories: Int? = null) {
    var steps by rememberSaveable { mutableIntStateOf(goals.steps) }
    var calories by rememberSaveable { mutableIntStateOf(goals.calories) }
    var sleep by rememberSaveable { mutableIntStateOf(goals.sleepMinutes) }
    var active by rememberSaveable { mutableIntStateOf(goals.activeHours) }
    var workouts by rememberSaveable { mutableIntStateOf(goals.workoutsPerWeek) }
    val hourUnit = stringResource(R.string.you_unit_h)

    HaloSheet(
        onDismiss, stringResource(R.string.you_goals_title),
        footer = {
            ButtonPair(
                stringResource(R.string.you_action_cancel),
                onDismiss,
                stringResource(R.string.you_action_save),
                {
                        onSave(
                            goals.copy(
                                steps = steps, sleepMinutes = sleep, activeHours = active, calories = calories, workoutsPerWeek = workouts,
                            ),
            )
                        },
            )
        },
    ) {
        HaloCard(fill = CardFill.Rows) {
            GoalRow(
                stringResource(R.string.you_goals_steps_label), Units.count(steps.toLong()), null, Goals.STEPS, steps,
                { steps = it }, { (steps + it * STEPS_STEP).coerceIn(Goals.STEPS) }, { t -> digits(t) }, { it.toString() },
                divider = false,
            )
            GoalRow(
                stringResource(R.string.you_goals_calories_label), Units.count(calories.toLong()),
                stringResource(R.string.glow_goals_unit_kcal), Goals.CALORIES, calories, { calories = it },
                { CalorieGoal.step(calories, it * Goals.CALORIES_STEP) }, { t -> CalorieGoal.parse(t) }, { it.toString() },
                lowerLabel = R.string.you_goals_calories_lower, raiseLabel = R.string.you_goals_calories_raise,
                typeLabel = R.string.you_goals_calories_type,
            ) { Suggestion(suggestedCalories, calories) { calories = it } }
            GoalRow(
                stringResource(R.string.you_goals_sleep_label), sleepText(sleep, hourUnit), null, Goals.SLEEP_MINUTES, sleep,
                { sleep = it }, { (sleep + it * SLEEP_STEP).coerceIn(Goals.SLEEP_MINUTES) }, ::parseSleep, ::sleepEdit,
                rangeNote = Goals.SLEEP_MINUTES.first / MINUTES_PER_HOUR..Goals.SLEEP_MINUTES.last / MINUTES_PER_HOUR,
            )
            GoalRow(
                stringResource(R.string.you_goals_active_label), "$active", stringResource(R.string.you_goals_active_unit),
                Goals.ACTIVE_HOURS, active, { active = it }, { (active + it).coerceIn(Goals.ACTIVE_HOURS) },
                { t -> digits(t) }, { it.toString() },
            ) { HaloNote(stringResource(R.string.you_goals_active_note), variant = NoteVariant.Footnote) }
            GoalRow(
                stringResource(R.string.you_goals_workouts_label), "$workouts", stringResource(R.string.glow_goals_per_week),
                Goals.WORKOUTS_PER_WEEK, workouts, { workouts = it }, { (workouts + it).coerceIn(Goals.WORKOUTS_PER_WEEK) },
                { t -> digits(t) }, { it.toString() },
                lowerLabel = R.string.you_goals_workouts_fewer, raiseLabel = R.string.you_goals_workouts_more,
            ) { HaloNote(stringResource(R.string.you_goals_workouts_note), variant = NoteVariant.Footnote) }
        }
    }
}

/** A typed whole number: digits only, commas and spaces ignored, null when there are none. */
private fun digits(text: String): Int? = text.filter { it.isDigit() }.toIntOrNull()

/** Sleep as "7 h 30", or "8 h" on the hour. */
private fun sleepText(minutes: Int, hourUnit: String): String =
    if (minutes % MINUTES_PER_HOUR == 0) {
        "${minutes / MINUTES_PER_HOUR} $hourUnit"
    } else {
        "${minutes / MINUTES_PER_HOUR} $hourUnit ${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
    }

/** What typing a sleep goal starts with: "7:30", or "8" on the hour. */
private fun sleepEdit(minutes: Int): String = if (minutes % MINUTES_PER_HOUR == 0) {
    "${minutes / MINUTES_PER_HOUR}"
} else {
    "${minutes / MINUTES_PER_HOUR}:${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
}

/** A typed sleep goal, "7:30" or "8" or "7.5", as minutes; null when it isn't one. */
private fun parseSleep(text: String): Int? {
    val t = text.trim()
    return when {
        ':' in t -> t.split(':').let { (h, m) ->
            h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.takeIf { it < MINUTES_PER_HOUR }?.let { hh * MINUTES_PER_HOUR + it } }
        }
        '.' in t || ',' in t -> t.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * MINUTES_PER_HOUR).toInt() }
        else -> t.toIntOrNull()?.let { it * MINUTES_PER_HOUR }
    }
}

/** The calorie goal's suggestion under its row (calories spec 4.6): what it is, with Use while it differs. */
@Composable
private fun Suggestion(suggested: Int?, value: Int, use: (Int) -> Unit) {
    if (suggested == null) return
    val count = Units.count(suggested.toLong())
    if (suggested == value) {
        HaloNote(stringResource(R.string.you_goals_suggested_matched, count), variant = NoteVariant.Footnote)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
            Box(Modifier.weight(1f)) { HaloNote(stringResource(R.string.you_goals_suggested, count), variant = NoteVariant.Footnote) }
            HaloButton(stringResource(R.string.you_goals_use), { use(suggested) }, size = HaloButtonSize.Small)
        }
    }
}

/**
 * One goal as a row of the goals panel (board HaloGoalsSheet): its [label], then a [StepperSize.Row] [HaloStepper] with
 * the goal and its [unit]. [step] moves it by one step down or
 * up (-1, 1) already held in [range]. A tap on the number opens it for typing ([edit] is what it starts with, [parse]
 * reads it): Enter or leaving the field keeps a value in range, anything else keeps the goal as it was and says the
 * range. [extra] sits under it, a note or the calorie suggestion.
 */
@Composable
private fun GoalRow(
    label: String,
    display: String,
    unit: String?,
    range: IntRange,
    value: Int,
    onChange: (Int) -> Unit,
    step: (Int) -> Int,
    parse: (String) -> Int?,
    edit: (Int) -> String,
    divider: Boolean = true,
    rangeNote: IntRange = range,
    lowerLabel: Int = R.string.glow_goals_lower,
    raiseLabel: Int = R.string.glow_goals_raise,
    typeLabel: Int = R.string.glow_goals_type,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // The field reports "not focused" as soon as it's placed; only losing the focus it had commits.
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        if (!typing) return
        typing = false
        focused = false
        val typed = parse(text)?.takeIf { it in range }
        refused = typed == null
        if (typed != null) onChange(typed)
    }
    val typeText = stringResource(typeLabel, label)
    HaloRow(
        label,
        trail = {
            HaloStepper(
                stringResource(lowerLabel, label), stringResource(raiseLabel, label),
                { typing = false; refused = false; onChange(step(-1)) }, { typing = false; refused = false; onChange(step(1)) },
                size = StepperSize.Row,
            ) {
                Box(
                    Modifier.fillMaxSize().clip(Corner.Chip)
                        .let {
                            if (typing) it
                            else it.clickable(onClickLabel = typeText, role = Role.Button) {
                                text = edit(value)
                                refused = false
                                typing = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (typing) {
                        BasicTextField(
                            text, { t -> text = t.filter { it.isDigit() || it == ':' || it == '.' || it == ',' }.take(MAX_TYPED) },
                            Modifier.fillMaxWidth().focusRequester(focus)
                                .onFocusChanged { if (it.isFocused) focused = true else if (focused) commit() },
                            textStyle = StepperSize.Row.number.copy(color = Color.White, textAlign = TextAlign.Center), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commit() }),
                            cursorBrush = SolidColor(Color.White),
                        )
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    } else StepperNumber(display, unit, size = StepperSize.Row)
                }
            }
        },
        below = if (refused || extra != null) ({
            Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                if (refused) HaloNote(GoalCopy.outOfRange(rangeNote), color = MetricColors.warn)
                extra?.invoke(this)
            }
        }) else null,
        divider = divider,
    )
}

/** Minutes in an hour, for the sleep goal. */
private const val MINUTES_PER_HOUR = 60

/** The steps goal moves by this many at a tap. */
private const val STEPS_STEP = 500

/** The sleep goal moves by this many minutes at a tap. */
private const val SLEEP_STEP = 15

/** The most characters typed into a goal. */
private const val MAX_TYPED = 6
