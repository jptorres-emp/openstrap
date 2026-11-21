package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.Strength
import org.openstrap.app.data.Structure
import org.openstrap.app.data.WorkoutSetup.GoalKind
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.SavedWorkout
import org.openstrap.app.ui.dashboard.LocalHeartZones
import org.openstrap.app.ui.workout.StartCopy

/*
 * Ready's goal for each family (board HaloReadyEveryKind): the goal type on the page, the hero, and the settings the
 * hero's ruler doesn't set in a card under it. Each opens on what Ready holds and hands every change back.
 */

/**
 * A distance or cardio sport's goal: its [kinds] as tabs (or the Goal menu with five), then the hero for the [kind]
 * picked: last time with none, a distance or time on a ruler, the zone buttons, or run reps with each rep and the rest
 * under it, and Save intervals and Saved under those ([saved], [onSaveIntervals]). A zone goal's Your zones opens the
 * Heart rate zones page ([onYourZones]). [gps] says the distance comes from GPS rather than steps; [lastPace] is last
 * time's seconds a km.
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections", "LongParameterList")
@Composable
internal fun CardioGoal(
    type: Int,
    kinds: List<GoalKind>,
    kind: GoalKind,
    draft: GoalDraft,
    onDraft: (GoalDraft) -> Unit,
    gps: Boolean,
    hasDistance: Boolean,
    lastPace: Double?,
    maxHr: Int?,
    nowMillis: Long,
    last: ReadyLast?,
    look: HeroLook,
    onYourZones: () -> Unit = {},
    saved: List<SavedWorkout> = emptyList(),
    onSaveIntervals: (name: String, reps: Structure.Reps) -> Unit = { _, _ -> },
) {
    GoalType(kinds.map { kindName(it, type) }, kinds.indexOf(kind)) { onDraft(draft.copy(kind = kinds[it])) }
    when (kind) {
        GoalKind.None -> LastTimeHero(cardio = true, last, look, noGoalWords(Workouts.family(type), hasDistance))
        GoalKind.Distance -> LookHero(
            look, distanceFigure(draft.meters), stringResource(R.string.glow_unit_km), distanceLine(draft.meters, gps, lastPace),
            input = HeroInput(distanceFigure(draft.meters), stringResource(R.string.glow_unit_km), decimal = true) { t ->
                typedMeters(t, StartCopy.MAX_KM)?.let { onDraft(draft.copy(meters = it)) }
            },
        ) { DistanceRuler(draft.meters, { onDraft(draft.copy(meters = it)) }, look.tint) }
        GoalKind.Time -> TimeGoalHero(draft.seconds, { onDraft(draft.copy(seconds = it)) }, look, nowMillis)
        GoalKind.Zone -> ZoneHero(draft.zones, { onDraft(draft.copy(zones = it)) }, look, LocalHeartZones.current, maxHr, onYourZones)
        GoalKind.Intervals -> {
            val reps = draft.reps
            val unit = repsUnit(reps)
            LookHero(
                look, "${reps.rounds}", unit, repsLine(reps, lastPace),
                input = countInput(reps.rounds, 1..StartCopy.MAX_REPS) { onDraft(draft.copy(reps = reps.copy(rounds = it))) },
            ) {
                CountRuler(
                    reps.rounds, 1..StartCopy.MAX_REPS, { onDraft(draft.copy(reps = reps.copy(rounds = it))) }, ReadyRulerSpec.RepStep,
                    "${reps.rounds} $unit", look.tint,
                )
            }
            val shorter = StartCopy.stepRep(reps, up = false)
            val longer = StartCopy.stepRep(reps, up = true)
            ReadySettings(
                listOf(
                    StepSetting(
                        stringResource(R.string.ready_each_rep), StartCopy.repText(reps),
                        { onDraft(draft.copy(reps = shorter)) }.takeIf { shorter != reps },
                        { onDraft(draft.copy(reps = longer)) }.takeIf { longer != reps },
                    ),
                    timeSetting(stringResource(R.string.ready_rest), reps.rest, REST_STEP, REP_REST) {
                        onDraft(draft.copy(reps = reps.copy(rest = it)))
                    },
                ),
            )
            SavedIntervals(reps, saved, { onDraft(draft.copy(reps = it)) }) { name -> onSaveIntervals(name, reps) }
        }
    }
}

/**
 * Strength: Open or Plan. A plan is its [sets] on the ruler, when it would end, and the rest between sets under it
 * ([restSeconds]); Open shows last time. [onSets] gets the sets, null for Open; Plan opens on [planSets].
 */
@Suppress("LongParameterList")
@Composable
internal fun StrengthGoal(
    sets: Int?,
    onSets: (Int?) -> Unit,
    planSets: Int,
    restSeconds: Long,
    onRest: (Long) -> Unit,
    nowMillis: Long,
    last: ReadyLast?,
    look: HeroLook,
) {
    GoalType(listOf(stringResource(R.string.ready_tab_open), StartCopy.PLAN), if (sets != null) 1 else 0) {
        onSets(if (it == 1) planSets else null)
    }
    if (sets == null) {
        LastTimeHero(cardio = false, last, look, noGoalWords(Workouts.Family.Strength, hasDistance = false))
        return
    }
    val unit = pluralStringResource(R.plurals.ready_hero_sets, sets)
    LookHero(
        look, "$sets", unit, endsAbout(sets * SET_SECONDS + (sets - 1) * restSeconds, nowMillis),
        input = countInput(sets, Strength.SETS) { onSets(it) },
    ) {
        CountRuler(sets, Strength.SETS, { onSets(it) }, ReadyRulerSpec.SetStep, "$sets $unit", look.tint)
    }
    val rest = stringResource(R.string.ready_rest_between_sets)
    ReadySettings(listOf(timeSetting(rest, restSeconds, REST_STEP, Strength.Rest.RANGE_S, onRest)))
}

/**
 * HIIT: Free, Timed or Every minute. Timed rounds have their work and rest under the hero, every minute rounds how
 * often each starts; Free shows last time. A tab picked opens on last time's rounds of its kind ([saved]).
 */
@Composable
internal fun HiitGoal(rounds: Structure?, onRounds: (Structure?) -> Unit, saved: String?, last: ReadyLast?, look: HeroLook) {
    val tab = when (rounds) {
        is Structure.Timed -> 1
        is Structure.EveryMinute -> 2
        else -> 0
    }
    GoalType(
        listOf(stringResource(R.string.ready_tab_free), stringResource(R.string.ready_tab_timed), StartCopy.EVERY_MINUTE), tab,
    ) { onRounds(hiitTab(it, rounds, saved)) }
    when (rounds) {
        is Structure.Timed -> {
            HiitHero(rounds, onRounds, look)
            ReadySettings(
                listOf(
                    timeSetting(stringResource(R.string.ready_work), rounds.work, HIIT_STEP, HIIT_WORK) {
                        onRounds(rounds.copy(work = it))
                    },
                    timeSetting(stringResource(R.string.ready_rest), rounds.rest, HIIT_STEP, HIIT_REST) {
                        onRounds(rounds.copy(rest = it))
                    },
                ),
            )
        }
        is Structure.EveryMinute -> {
            HiitHero(rounds, onRounds, look)
            ReadySettings(
                listOf(
                    timeSetting(stringResource(R.string.ready_round_every), rounds.period, REST_STEP, EVERY_PERIOD) {
                        onRounds(rounds.copy(period = it))
                    },
                ),
            )
        }
        else -> LastTimeHero(cardio = false, last, look, noGoalWords(Workouts.Family.Intervals, hasDistance = false))
    }
}

/** Mind and body: Open, or a Time on the ruler with when it would end. */
@Composable
internal fun MindGoal(draft: GoalDraft, onDraft: (GoalDraft) -> Unit, nowMillis: Long, last: ReadyLast?, look: HeroLook) {
    val timed = draft.kind == GoalKind.Time
    GoalType(listOf(stringResource(R.string.ready_tab_open), StartCopy.TIME), if (timed) 1 else 0) {
        onDraft(draft.copy(kind = if (it == 1) GoalKind.Time else GoalKind.None))
    }
    if (timed) TimeGoalHero(draft.seconds, { onDraft(draft.copy(seconds = it)) }, look, nowMillis)
    else LastTimeHero(cardio = false, last, look, noGoalWords(Workouts.Family.MindBody, hasDistance = false))
}

/**
 * A game: Free, or Periods on the ruler with each period's length and the break under it. Periods opens on last
 * time's ([saved]), else halves.
 */
@Composable
internal fun GamesGoal(
    periods: Structure.Periods?, onPeriods: (Structure.Periods?) -> Unit, saved: String?, last: ReadyLast?, look: HeroLook,
) {
    GoalType(listOf(stringResource(R.string.ready_tab_free), stringResource(R.string.ready_tab_periods)), if (periods != null) 1 else 0) {
        onPeriods(if (it == 1) Structure.parse(saved) as? Structure.Periods ?: Structure.HALVES else null)
    }
    if (periods == null) {
        LastTimeHero(cardio = false, last, look, noGoalWords(Workouts.Family.Games, hasDistance = false))
        return
    }
    val unit = pluralStringResource(R.plurals.ready_hero_periods, periods.rounds)
    LookHero(
        look, "${periods.rounds}", unit,
        stringResource(R.string.ready_hero_periods_about, (StartCopy.totalTime(periods) ?: 0) / READY_S_PER_MIN),
        input = countInput(periods.rounds, PERIODS) { onPeriods(periods.copy(rounds = it)) },
    ) {
        CountRuler(
            periods.rounds, PERIODS, { onPeriods(periods.copy(rounds = it)) }, ReadyRulerSpec.PeriodStep,
            "${periods.rounds} $unit", look.tint,
        )
    }
    ReadySettings(
        listOf(
            timeSetting(stringResource(R.string.ready_each_period), periods.length, MINUTE_STEP, PERIOD_LENGTH) {
                onPeriods(periods.copy(length = it))
            },
            timeSetting(stringResource(R.string.ready_break), periods.rest, MINUTE_STEP, PERIOD_BREAK) {
                onPeriods(periods.copy(rest = it))
            },
        ),
    )
}

/** Whether the hero shows last time rather than a goal: no goal, Open or Free. */
internal fun showsLastTime(family: Workouts.Family, kind: GoalKind, sets: Int?, rounds: Structure?, draft: GoalDraft): Boolean =
    when (family) {
        Workouts.Family.Distance, Workouts.Family.IndoorCardio -> kind == GoalKind.None
        Workouts.Family.Strength -> sets == null
        Workouts.Family.Intervals -> rounds !is Structure.Timed && rounds !is Structure.EveryMinute
        Workouts.Family.MindBody -> draft.kind != GoalKind.Time
        Workouts.Family.Games -> rounds !is Structure.Periods
    }

/** A time setting under the hero: [name], [seconds] as it reads, stepped by [step] within [range]. */
private fun timeSetting(name: String, seconds: Long, step: Long, range: LongRange, set: (Long) -> Unit): StepSetting =
    StepSetting(
        name, StartCopy.shortTime(seconds),
        stepAction(seconds, step, range, up = false, set), stepAction(seconds, step, range, up = true, set),
    )

/** HIIT's tab picked: Free, Timed (last time's timed rounds, else Tabata) or Every minute (last time's, else the preset). */
private fun hiitTab(tab: Int, now: Structure?, saved: String?): Structure? {
    val warm = now?.warmUp ?: false
    val last = Structure.parse(saved)
    return when (tab) {
        1 -> (last as? Structure.Timed ?: Structure.TABATA).warm(warm)
        2 -> (last as? Structure.EveryMinute ?: Structure.EVERY_MINUTE).warm(warm)
        else -> null
    }
}

/** How long a set takes, for strength's "Ends about": a minute's work. */
private const val SET_SECONDS = 60L
private const val REST_STEP = 15L
private val REP_REST = 0L..900L
private const val HIIT_STEP = 5L
private val HIIT_WORK = 5L..600L
private val HIIT_REST = 0L..600L
private val EVERY_PERIOD = 15L..600L
private const val MINUTE_STEP = 60L
private val PERIOD_LENGTH = 60L..5400L
private val PERIOD_BREAK = 0L..1800L
private val PERIODS = 1..8
