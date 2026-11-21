@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.HeartZones
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutTarget
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.glow.live.heroTint
import org.openstrap.app.ui.workout.StartCopy

/*
 * Ready's hero for each kind of goal (board HaloReadyEveryKind): the big number, what sets it (a ruler, the zone
 * buttons) and one line; or, with no goal, last time.
 */

/** The hero's light: the sport's [color] lit from [angle]; [tint] is the hero's own words and the ruler's labels. */
@Immutable
internal class HeroLook(val color: Color, val angle: Float) {
    val tint: Color get() = heroTint(color)
}

/** [GoalHero] in [look]. */
@Composable
internal fun LookHero(
    look: HeroLook,
    number: String?,
    unit: String?,
    line: String?,
    caption: String? = null,
    words: String? = null,
    size: BigNumber = BigNumber.Live,
    lineAction: (@Composable () -> Unit)? = null,
    input: HeroInput? = null,
    control: (@Composable () -> Unit)? = null,
) = GoalHero(look.color, look.angle, number, unit, line, caption, words, size, lineAction, input, control)

/** A time goal's hero: the minutes, the ruler, and when it would end. */
@Composable
internal fun TimeGoalHero(seconds: Long, onSeconds: (Long) -> Unit, look: HeroLook, nowMillis: Long) {
    val (n, unit) = timeFigure(seconds)
    LookHero(
        look, n, unit, endsAbout(seconds, nowMillis),
        input = HeroInput("${seconds / SECONDS_A_MIN}", stringResource(R.string.glow_unit_min), decimal = false) { t ->
            typedSeconds(t, StartCopy.MAX_HOURS)?.let(onSeconds)
        },
    ) { TimeRuler(seconds, onSeconds, look.tint) }
}

/**
 * A zone goal's hero: "2 to 3" heart zones, the five zone buttons, and the heart rates to keep between with Your zones
 * beside them, which opens the Heart rate zones page ([onYourZones]).
 */
@Suppress("LongParameterList")
@Composable
internal fun ZoneHero(
    zones: WorkoutTarget.Zones,
    onZones: (WorkoutTarget.Zones) -> Unit,
    look: HeroLook,
    heart: HeartZones,
    maxHr: Int?,
    onYourZones: () -> Unit,
) {
    val number = if (zones.low == zones.high) "${zones.low + 1}"
        else stringResource(R.string.glow_ready_hero_zone_range, zones.low + 1, zones.high + 1)
    val line = if (maxHr == null) StartCopy.ZONE_NOTE else {
        val to = heart.endBpm(zones.high, maxHr)
        val from = heart.startBpm(zones.low, maxHr)
        if (to != null) stringResource(R.string.ready_hero_zone_keep, from, to) else stringResource(R.string.ready_hero_zone_keep_up, from)
    }
    LookHero(
        look, number, pluralStringResource(R.plurals.ready_hero_heart_zones, zones.high - zones.low + 1), line, size = BigNumber.Feature,
        control = { ZoneButtons(zones, onZones) },
        lineAction = {
            HaloButton(
                stringResource(R.string.ready_your_zones), onYourZones, variant = HaloButtonVariant.Glass,
                size = HaloButtonSize.Small, icon = org.openstrap.app.ui.theme.AppIcons.PencilEdit,
            )
        },
    )
}

/** HIIT's hero: the rounds, the ruler, and all of their time. */
@Composable
internal fun HiitHero(rounds: Structure, onRounds: (Structure) -> Unit, look: HeroLook) {
    val unit = pluralStringResource(R.plurals.ready_hero_rounds, rounds.rounds)
    val all = StartCopy.timeValue(StartCopy.totalTime(rounds) ?: 0)
    LookHero(
        look, "${rounds.rounds}", unit,
        stringResource(if (rounds is Structure.EveryMinute) R.string.ready_hero_every_in_all else R.string.ready_hero_in_all, all),
        input = countInput(rounds.rounds, 1..StartCopy.MAX_ROUNDS) { n ->
            onRounds(
                when (rounds) {
                    is Structure.Timed -> rounds.copy(rounds = n)
                    is Structure.EveryMinute -> rounds.copy(rounds = n)
                    else -> rounds
                },
            )
        },
    ) {
        CountRuler(
            rounds.rounds, 1..StartCopy.MAX_ROUNDS,
            {
                onRounds(
                    when (rounds) {
                        is Structure.Timed -> rounds.copy(rounds = it)
                        is Structure.EveryMinute -> rounds.copy(rounds = it)
                        else -> rounds
                    },
                )
            },
            ReadyRulerSpec.RoundStep, "${rounds.rounds} $unit", look.tint, labelEvery = HIIT_LABEL_EVERY,
        )
    }
}

/**
 * The hero with no goal: "No goal, last session Wed" over last time's big figure and the rest in a line ([cardio]
 * says goal, else plan); with no workout of the sport yet, "No goal" over [words], what is recorded.
 */
@Composable
internal fun LastTimeHero(type: Int, cardio: Boolean, last: ReadyLast?, look: HeroLook, words: String) {
    val hero = last?.hero
    if (last == null || hero == null) {
        val caption = stringResource(if (cardio) R.string.ready_no_goal else R.string.ready_no_plan)
        LookHero(look, null, null, null, caption = caption, words = words)
        return
    }
    val caption = if (cardio) stringResource(R.string.ready_last_no_goal, last.day)
        else stringResource(R.string.ready_last_no_plan, last.day)
    LookHero(look, hero.number, hero.unit, hero.line, caption = caption)
}

/** What is recorded with no goal, by family, where there is no last time to show. */
internal fun noGoalWords(family: Workouts.Family, hasDistance: Boolean): String = when (family) {
    Workouts.Family.Strength -> StartCopy.OPEN_SETS_NOTE
    Workouts.Family.Intervals -> StartCopy.HIIT_FREE_NOTE
    Workouts.Family.MindBody -> StartCopy.MIND_OPEN_NOTE
    Workouts.Family.Games -> StartCopy.gamesNote(null)
    else -> StartCopy.noGoalNote(hasDistance)
}

/** Under run intervals: about how long the reps and rests take at your pace, or what the strap does where that can't be told. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun repsLine(reps: Structure.Reps, lastPace: Double?): String {
    val rep = reps.seconds?.toDouble() ?: reps.meters?.let { m -> lastPace?.let { m / HERO_METERS_PER_KM * it } }
        ?: return StartCopy.intervalsNote(reps)
    val total = reps.rounds * rep + (reps.rounds - 1) * reps.rest
    return stringResource(R.string.ready_hero_reps_about, (total / READY_S_PER_MIN).toInt().coerceAtLeast(1))
}

/** Under a distance goal: from your steps indoors, else about how long at last time's pace, else what the strap does. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun distanceLine(meters: Int, gps: Boolean, lastPace: Double?): String = when {
    !gps -> stringResource(R.string.ready_hero_from_steps)
    lastPace != null -> stringResource(
        R.string.ready_hero_pace_about, (meters / HERO_METERS_PER_KM * lastPace / READY_S_PER_MIN).toInt().coerceAtLeast(1),
    )
    else -> StartCopy.distanceNote(meters)
}

private const val HIIT_LABEL_EVERY = 4
private const val SECONDS_A_MIN = 60L
private const val HERO_METERS_PER_KM = 1000.0
