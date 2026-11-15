package org.openstrap.app.ui.device

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
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.ButtonSize
import org.openstrap.app.ui.glow.topHairline
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.glow.RoundIconButton
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

    PageSheet(onDismiss, stringResource(R.string.you_goals_title)) {
        PageRows {
            GoalStepper(
                stringResource(R.string.you_goals_steps_label), Units.count(steps.toLong()), null, Goals.STEPS, steps,
                { steps = it }, { (steps + it * STEPS_STEP).coerceIn(Goals.STEPS) }, { t -> digits(t) }, { it.toString() },
                divider = false,
            )
            GoalStepper(
                stringResource(R.string.you_goals_calories_label), Units.count(calories.toLong()),
                stringResource(R.string.glow_goals_unit_kcal), Goals.CALORIES, calories, { calories = it },
                { CalorieGoal.step(calories, it * Goals.CALORIES_STEP) }, { t -> CalorieGoal.parse(t) }, { it.toString() },
                lowerLabel = R.string.you_goals_calories_lower, raiseLabel = R.string.you_goals_calories_raise,
                typeLabel = R.string.you_goals_calories_type,
            ) { suggestion(suggestedCalories, calories) { calories = it } }
            GoalStepper(
                stringResource(R.string.you_goals_sleep_label), sleepText(sleep, hourUnit), null, Goals.SLEEP_MINUTES, sleep,
                { sleep = it }, { (sleep + it * SLEEP_STEP).coerceIn(Goals.SLEEP_MINUTES) }, ::parseSleep, ::sleepEdit,
                rangeNote = Goals.SLEEP_MINUTES.first / 60..Goals.SLEEP_MINUTES.last / 60,
            )
            GoalStepper(
                stringResource(R.string.you_goals_active_label), "$active", stringResource(R.string.you_goals_active_unit),
                Goals.ACTIVE_HOURS, active, { active = it }, { (active + it).coerceIn(Goals.ACTIVE_HOURS) },
                { t -> digits(t) }, { it.toString() },
            ) { RowNote(stringResource(R.string.you_goals_active_note)) }
            GoalStepper(
                stringResource(R.string.you_goals_workouts_label), "$workouts", stringResource(R.string.glow_goals_per_week),
                Goals.WORKOUTS_PER_WEEK, workouts, { workouts = it }, { (workouts + it).coerceIn(Goals.WORKOUTS_PER_WEEK) },
                { t -> digits(t) }, { it.toString() },
                lowerLabel = R.string.you_goals_workouts_fewer, raiseLabel = R.string.you_goals_workouts_more,
            ) { RowNote(stringResource(R.string.you_goals_workouts_note)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(stringResource(R.string.you_action_cancel), onDismiss, Modifier.weight(1f))
            PageButton(
                stringResource(R.string.you_action_save),
                {
                    onSave(
                        goals.copy(
                            steps = steps, sleepMinutes = sleep, activeHours = active, calories = calories, workoutsPerWeek = workouts,
                        ),
                    )
                },
                Modifier.weight(1f), primary = true,
            )
        }
    }
}

/** A line of the goals panel's small print under a row (board HaloGoalsSheet: 12, #6E7480). */
@Composable
private fun RowNote(text: String) = Text(text, color = GlowType.footnoteColor, style = GlowType.footnote)

/** A typed whole number: digits only, commas and spaces ignored, null when there are none. */
private fun digits(text: String): Int? = text.filter { it.isDigit() }.toIntOrNull()

/** Sleep as "7 h 30", or "8 h" on the hour. */
private fun sleepText(minutes: Int, hourUnit: String): String =
    if (minutes % 60 == 0) "${minutes / 60} $hourUnit" else "${minutes / 60} $hourUnit ${(minutes % 60).toString().padStart(2, '0')}"

/** What typing a sleep goal starts with: "7:30", or "8" on the hour. */
private fun sleepEdit(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60}" else "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"

/** A typed sleep goal, "7:30" or "8" or "7.5", as minutes; null when it isn't one. */
private fun parseSleep(text: String): Int? {
    val t = text.trim()
    val minutes = when {
        ':' in t -> t.split(':').let { (h, m) ->
            h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.takeIf { it in 0..59 }?.let { hh * 60 + it } }
        }
        '.' in t || ',' in t -> t.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 60).toInt() }
        else -> t.toIntOrNull()?.let { it * 60 }
    }
    return minutes
}

/** The calorie goal's suggestion under its row (calories spec 4.6): what it is, with Use while it differs. */
@Composable
private fun suggestion(suggested: Int?, value: Int, use: (Int) -> Unit) {
    if (suggested == null) return
    val count = Units.count(suggested.toLong())
    if (suggested == value) {
        RowNote(stringResource(R.string.you_goals_suggested_matched, count))
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { RowNote(stringResource(R.string.you_goals_suggested, count)) }
            PageButton(stringResource(R.string.you_goals_use), { use(suggested) }, size = ButtonSize.Row)
        }
    }
}

/**
 * One goal as a row of the goals panel (board HaloGoalsSheet): its [label], a minus, the goal in Funnel Display Light 26
 * with its [unit], and a plus, 40 squares for the buttons and the number 104 wide. [step] moves it by one step down or
 * up (-1, 1) already held in [range]. A tap on the number opens it for typing ([edit] is what it starts with, [parse]
 * reads it): Enter or leaving the field keeps a value in range, anything else keeps the goal as it was and says the
 * range. [extra] sits under it, a note or the calorie suggestion.
 */
@Composable
private fun GoalStepper(
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
    extra: @Composable ColumnScope.() -> Unit = {},
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
    Column(
        Modifier.fillMaxWidth().topHairline(divider).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, Modifier.weight(1f), color = Color.White, style = GlowType.body, maxLines = 1)
            RoundIconButton(
                AppIcons.Minus, stringResource(lowerLabel, label), { typing = false; refused = false; onChange(step(-1)) },
                size = 40.dp, iconSize = 18.dp,
            )
            Box(
                Modifier.width(104.dp).height(40.dp).clip(GlowLayout.TileShape)
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
                        textStyle = GlowType.goalValueMinor.copy(color = Color.White, textAlign = TextAlign.Center), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        cursorBrush = SolidColor(Color.White),
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text(display, color = Color.White, style = GlowType.goalValueMinor, maxLines = 1)
                        if (unit != null) {
                            Text(
                                unit, Modifier.padding(start = 4.dp, bottom = 3.dp), color = GlowType.noteColor,
                                style = GlowType.secondary, maxLines = 1,
                            )
                        }
                    }
                }
            }
            RoundIconButton(
                AppIcons.Plus, stringResource(raiseLabel, label), { typing = false; refused = false; onChange(step(1)) },
                size = 40.dp, iconSize = 18.dp,
            )
        }
        if (refused) PageNote(GoalCopy.outOfRange(rangeNote), MetricColors.warn)
        extra()
    }
}

/** The steps goal moves by this many at a tap. */
private const val STEPS_STEP = 500

/** The sleep goal moves by this many minutes at a tap. */
private const val SLEEP_STEP = 15

/** The most characters typed into a goal. */
private const val MAX_TYPED = 6
