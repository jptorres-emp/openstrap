package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Box
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.R
import androidx.compose.ui.res.stringResource
import org.openstrap.app.presentation.Units
import org.openstrap.app.presentation.TimeOfDay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import org.openstrap.app.ui.components.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.openstrap.app.presentation.chargeShown
import org.openstrap.app.data.Moment
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.SyncRecord
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.LatestReading
import org.openstrap.app.ui.dashboard.SleepCopy
import org.openstrap.app.ui.dashboard.Tier
import org.openstrap.app.ui.dashboard.lookOr
import org.openstrap.app.ui.dashboard.todayPanes
import org.openstrap.app.ui.dashboard.varyLooks
import org.openstrap.app.ui.dashboard.habitAgeNew
import org.openstrap.app.ui.dashboard.items
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.presentation.Metric
import org.openstrap.app.ui.dashboard.TodayCard
import org.openstrap.app.presentation.TodayCopy
import org.openstrap.app.ui.dashboard.TodayEdits
import org.openstrap.app.ui.dashboard.TodayFacts
import org.openstrap.app.ui.dashboard.TodaySlot
import org.openstrap.app.ui.dashboard.applyEdits
import org.openstrap.app.ui.dashboard.CHARGE_FAILED
import org.openstrap.app.ui.dashboard.FAILED_SHORT
import org.openstrap.app.ui.dashboard.RecoveryFailure
import org.openstrap.app.ui.dashboard.STALE_AFTER_S
import org.openstrap.app.ui.dashboard.TRY_AGAIN
import org.openstrap.app.ui.dashboard.WAITING_LAST_NIGHT
import org.openstrap.app.ui.dashboard.lastTried
import org.openstrap.app.ui.dashboard.stuckCopy
import org.openstrap.app.ui.dashboard.syncStuck
import org.openstrap.app.ui.dashboard.upToMarker
import org.openstrap.app.presentation.AFTER_FIRST_NIGHT
import org.openstrap.app.ui.dashboard.FIRST_SCORE
import org.openstrap.app.ui.dashboard.PARTIAL_DAY
import org.openstrap.app.ui.dashboard.PairStep
import org.openstrap.app.ui.dashboard.Wear
import org.openstrap.app.presentation.WakingLead
import org.openstrap.app.ui.dashboard.pairSentence
import org.openstrap.app.presentation.wakingLead
import org.openstrap.app.ui.dashboard.todayCards
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.glow.pictures.EnergyRuler
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.ui.glow.draw.WINDOW_RIM
import org.openstrap.app.ui.glow.draw.AFTER_RIM
import org.openstrap.app.ui.glow.draw.EVENING_BODY
import org.openstrap.app.presentation.topSports
import org.openstrap.app.presentation.TabCopy

/*
 * Today (spec 4): one hero whose content follows the moment, then a stable set of cards for it ([todayCards]), with
 * Edit Today's pins and hides on top ([applyEdits]). The hero glows; the rest is edge light and lists, so the glow
 * keeps meaning "look here".
 */

/** The strap's state where Today needs it: paired at all, whether syncing has stopped, and how syncs went. */
class TodayStrap(
    val paired: Boolean = true,
    /** The pairing stage's step while there's no strap or it's connecting for the first time; null once paired (E3). */
    val pairing: PairStep? = null,
    /** Syncing has stopped ([syncStopped]), so cards whose data is old say how far it goes. */
    val stopped: Boolean = false,
    /** When syncs last ran and how they ended: the stuck hero's "Last tried 07:42" and its 3 h rule. */
    val record: SyncRecord = SyncRecord(),
    val canSync: Boolean = true,
    /** How old a card's newest reading is before it's marked; a debug preview marks every card at once. */
    val staleAfter: Long = STALE_AFTER_S,
)

@Composable
fun GlowTodayScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    /** The moment Today is laid out for ([org.openstrap.app.ui.dashboard.layoutMoment]); null while it loads. */
    moment: Moment?,
    onOpenMetric: (Metric) -> Unit = {},
    recovery: RecoveryResult? = null,
    /** Opens the Recovery screen over Today, from the evening hero's day in a sentence. */
    onOpenRecovery: () -> Unit = {},
    workouts: List<WorkoutAnalysis> = emptyList(),
    onOpenWorkout: (Long) -> Unit = {},
    onOpenHealthspan: () -> Unit = {},
    /** Opens the profile, for the Habit Age card's reminder to add a birth year or sex. */
    onOpenProfile: () -> Unit = {},
    /** The weekly Habit Age estimate, or why it's withheld; null hides the card. */
    healthspan: org.openstrap.app.data.Healthspan.Outcome? = null,
    /** The Sunday of the last Habit Age estimate opened, so Today stops offering it ([habitAgeNew]). */
    habitAgeSeenAsOf: LocalDate? = null,
    /** Per-day history for Your week; null leaves its steps out. */
    history: DailyHistory? = null,
    /** Edit Today's hidden and pinned cards. */
    edits: TodayEdits = TodayEdits(),
    onEditsChange: (TodayEdits) -> Unit = {},
    /** The strap's own Readiness for the day, shown as "Strap's estimate" while OpenStrap's score is withheld. */
    /** The strap's own score for the day's night ([org.openstrap.app.data.RecoveryResult.strapScore]). */
    strapScore: Int? = null,
    /** The morning check-in (1-5), asked on the waking moment. */
    checkIn: Int? = null,
    onCheckIn: (Int?) -> Unit = {},
    /** A stretch nobody started, offered for saving; null hides the strip. */
    suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion? = null,
    onSaveSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    /** Opens the ready screen for a sport, as Activity's start card does. */
    onStartSport: (Int) -> Unit = {},
    /** Opens the ready screen on its sport list. */
    onOtherSport: () -> Unit = {},
    /** A workout recording now: the hero during a workout leads back to it ([WorkoutShortcut]). */
    running: org.openstrap.app.device.WorkoutState.Recording? = null,
    onOpenRunning: () -> Unit = {},
    /** The live workout's glance and what its hero does (workouts spec 2.10). */
    liveHero: LiveHeroHooks = LiveHeroHooks(),
    /** Where the hero's light comes from for the moment ([org.openstrap.app.ui.dashboard.heroLightAngle]); null keeps its own. */
    heroLightAngle: Float? = null,
    strap: TodayStrap = TodayStrap(),
    /** Opens the Strap page, to pair. */
    onPair: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    /** When you usually go to sleep, for "Bedtime in 1 h 10". */
    usualBedtime: LocalTime? = null,
    /** Today's Recovery failed to work out; null when it didn't. */
    recoveryFailure: RecoveryFailure? = null,
    /** Works Recovery out again after a failure. */
    onRetryRecovery: () -> Unit = {},
    /** Opens a detail over Today: Load, time to recover, the week story. */
    onOpen: (org.openstrap.app.ui.dashboard.Detail) -> Unit = {},
    /** Your usual steps by the end of each hour, for the Activity card's ghost line; null until there's a usual. */
    usualSteps: List<Float>? = null,
    /** How much of the day the strap was worn, for the Partial day tag ([org.openstrap.app.ui.dashboard.wear]). */
    wear: Wear? = null,
    /** Today's Recovery is the first real score, marked once (E4). */
    firstScore: Boolean = false,
    /** Each day's steps and the best before, for the Steps card's goal tag ([ActivityHistory.stepsFacts]). Null climbs only to what the goal tells. */
    activity: org.openstrap.app.ui.dashboard.ActivityHistory? = null,
) {
    val list = rememberLazyListState()
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) EditTodaySheet(edits, onSave = { onEditsChange(it); editing = false }, onDismiss = { editing = false })
    // The card held on Today, lifted with its menu open while the rest dim (board HaloTodayHold).
    var held by remember { mutableStateOf<TodayCard?>(null) }
    val haptics = rememberHaptics()
    var partialInfo by rememberSaveable { mutableStateOf(false) }
    // Built once, so the cards given it can skip (audit K11).
    val partialTag = remember { TitleTag(PARTIAL_DAY) { partialInfo = true } }

    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val isToday = state.date == now.atZone(zone).toLocalDate()
    org.openstrap.app.ui.components.HoldLiveHeartRate(isToday)
    val rec = recovery
    val lastWorkout = workouts.maxByOrNull { it.end }
    val ctx = TodayContext(
        state, rec, liveHeartRate, workouts, lastWorkout, isToday, now.epochSecond, zone, moment,
        restingRange = rememberNormal(Metric.RestingHr, state.date, history),
        firstNights = rec != null && !rec.charge.anchored,
        stuck = strap.paired && syncStuck(isToday, state.asleepSeconds != null, strap.record.failingSince, now.epochSecond, zone),
        strapScore = strapScore,
        stopped = strap.stopped, staleAfter = strap.staleAfter,
        recoveryFailure = recoveryFailure?.takeIf { rec == null },
        partial = partialTag.takeIf { wear?.partial == true },
        firstScore = firstScore,
        bare = strap.pairing != null || !strap.paired || rec != null && !rec.charge.anchored,
    )
    if (partialInfo && wear != null) PartialDaySheet(wear, isToday, onDismiss = { partialInfo = false })
    // Before a strap is paired, or while it connects for the first time, the hero is the pairing stage (E3).
    val pairing = strap.pairing ?: PairStep.Ready.takeIf { !strap.paired }
    // The pairing stage and the first day have no week, no Start a workout and nothing to edit yet (boards HaloTodayNoStrap,
    // HaloTodayLoading, HaloTodayFirstDay).
    val facts = TodayFacts(
        isToday = isToday, suggestion = suggestion != null, alerts = state.alerts.isNotEmpty(),
        workout = lastWorkout != null && isToday,
        bare = ctx.bare,
        healthspan = healthspan?.result?.let { r ->
            val asOf = Instant.ofEpochSecond(r.asOf).atZone(zone).toLocalDate()
            habitAgeNew(asOf, habitAgeSeenAsOf == asOf, state.date, now.atZone(zone).toLocalDate())
        } == true,
    )
    // Before the first estimate Habit Age never shows on Today, pinned or not.
    val unavailable = if (healthspan?.result == null) setOf(TodayCard.HabitAge) else emptySet()
    val stepsGoal = LocalGoals.current.steps
    val stepsFacts = activity?.stepsFacts(state.date, state.steps, stepsGoal)
    val morning = LocalTime.now().hour < 12
    val live = liveHeartRate != null
    // The moment the cards are laid out for, with no moment yet resolved as todayCards does.
    val looksFor = moment ?: if (morning) Moment.Waking else Moment.Midday
    // Until the day's first real answer arrives (its rows and its Recovery) Today shows the day as loading, never the
    // first-night state those empty values would read as. Recovery ends as Ready or Failed, so this always ends. The
    // pairing hero needs neither.
    val loading = pairing == null && (!state.loaded || rec == null && recoveryFailure == null)
    val cardsAlpha = remember { Animatable(if (loading) 0f else 1f) }
    LaunchedEffect(loading) { if (!loading) cardsAlpha.animateTo(1f, tween(Motion.SWIFT, easing = Motion.easing)) }

    // The glow holds still while the list scrolls, so scrolling stays as smooth as on other screens.
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // No two cards next to each other look the same (spec 2.4). On two columns, side by side counts too.
            val wide = maxWidth >= GlowLayout.TwoColumnWidth
            // On columns too narrow for Smalls (the Fold's inner screen) a Small pair spans both (spec 2.4 Unfolded).
            val across = GlowLayout.smallsAcross(maxWidth)
            val slots = varyLooks(applyEdits(todayCards(moment, facts, morning), edits, moment, unavailable), looksFor, live, wide, across)
            val panes = todayPanes(slots, across)
            GlowScreenList(contentPadding, list) {
                add(Pane.Full, GlowBlocks.HEADER) { header() }
                if (loading) {
                    add(Pane.Full, GlowBlocks.cardsKey(listOf(TodayCard.Hero))) { LoadingHero(Modifier.fillMaxWidth().gridSize(CardSize.Wide)) }
                    return@GlowScreenList
                }
                // One block per grid row: a Small pair stays side by side, and on a wide screen in one column. Each is
                // keyed on its cards, so its state stays with them when Edit Today hides or pins one above (audit K5).
                packGrid(slots.indices.map { slots[it].size to it }).forEach { row ->
                    val block: @Composable () -> Unit = {
                        GridRowBlock(row) { i, m ->
                            val slot = slots[i]
                            // Before a strap is paired, everything under the pairing hero waits, dimmed.
                            val dim = if (pairing != null && slot.card != TodayCard.Hero && slot.card != TodayCard.EditToday) Modifier.alpha(0.45f) else Modifier
                            val look = slot.lookOr(looksFor, live)
                            // An unlit card drawn as a picture card skips its light.
                            val override = CardOverride(plain = look.tier == Tier.List)
                            // Every card Edit Today lists can be held for its menu, once there's a strap.
                            val actions = if (slot.card.editable && pairing == null) {
                                TodayHoldActions(slot.card, edits, onEditsChange) { editing = true }
                            } else {
                                null
                            }
                            val lift by animateFloatAsState(
                                when (held) { null -> 0f; slot.card -> 1f; else -> -1f },
                                tween(Motion.SWIFT, easing = Motion.easing), label = "hold",
                            )
                            CompositionLocalProvider(LocalCardOverride provides override) {
                                Column(
                                    m.then(dim)
                                        .holdToEdit(slot.card, actions != null) { held = slot.card; haptics.perform(HapticEvent.ScrubMark) }
                                        .holdActions(slot.card, actions)
                                        .graphicsLayer {
                                            alpha = cardsAlpha.value * (1f - HOLD_DIM * (-lift).coerceAtLeast(0f))
                                            val grow = 1f + HOLD_LIFT * lift.coerceAtLeast(0f)
                                            scaleX = grow; scaleY = grow
                                        },
                                ) {
                                    if (actions != null) TodayHoldMenu(held == slot.card, actions) { held = null }
                                    when (slot.card) {
                                        TodayCard.Hero -> Hero(
                                            ctx, strap, pairing, heroLightAngle, usualBedtime,
                                            running, onOpenRunning, liveHero, onStartSport, onOtherSport, onPair, onSyncNow,
                                            onOpenRecovery, onOpenWorkout, onRetryRecovery,
                                            onOpenScore = { onOpen(org.openstrap.app.ui.dashboard.Detail.OfScore(it)) },
                                            energyHidden = slots.none { it.card == TodayCard.Charge },
                                        )
                                        TodayCard.EditToday -> Box(
                                            Modifier.fillMaxWidth().padding(vertical = Space.S8), contentAlignment = Alignment.Center,
                                        ) {
                                            // A dark pill, no glass (board HaloTodayEvening, the foot of Today).
                                            HaloButton(
                                                stringResource(R.string.glow_today_edit), { editing = true },
                                                icon = AppIcons.SlidersHorizontal, size = HaloButtonSize.Card,
                                            )
                                        }
                                        else -> TodayRow(slot, ctx, healthspan, history, checkIn, onCheckIn, onOpenMetric,
                                            onOpenWorkout, onOpenHealthspan, onOpenProfile, suggestion,
                                            onSaveSuggestion, onDismissSuggestion, onOpen, usualSteps, onRetryRecovery,
                                            if (slot.size == CardSize.Small) Modifier.fillMaxSize() else Modifier, onStartSport = onStartSport,
                                            stepsFacts = stepsFacts)
                                    }
                                }
                            }
                        }
                    }
                    // The hero and strips span the width on a wide screen; the rows between take turns.
                    add(panes[row.items().first()], GlowBlocks.cardsKey(row.items().map { slots[it].card }), block)
                }
            }
        }
    }
}

/**
 * What Today's cards read, worked out once per screen. Equal by value and immutable, so a card given an equal one
 * skips rather than recompose with every pass of Today (audit K11).
 */
@androidx.compose.runtime.Immutable
private data class TodayContext(
    val state: DayUiState,
    val rec: RecoveryResult?,
    val live: Int?,
    val workouts: List<WorkoutAnalysis>,
    val lastWorkout: WorkoutAnalysis?,
    val isToday: Boolean,
    val now: Long,
    val zone: ZoneId,
    val moment: Moment?,
    /** Your normal resting heart rate, drawn as the resting range behind heart rate lines; null until known. */
    val restingRange: org.openstrap.app.data.History.Normal?,
    /** Before the strap has seen a night: cards keep their shape with a dash (spec 4.3). */
    val firstNights: Boolean,
    /** Last night is still on the strap (spec 4.3). */
    val stuck: Boolean,
    /** The strap's own Readiness for the day. */
    val strapScore: Int?,
    /** Syncing has stopped, and how old a reading is before its card is marked. */
    val stopped: Boolean,
    val staleAfter: Long,
    /** Today's Recovery failed to work out (spec 4.3). */
    val recoveryFailure: RecoveryFailure?,
    /** The Partial day tag for cards whose figures a partial day lowers (spec 4.3); null on a day worn enough. */
    val partial: TitleTag?,
    /** Today's Recovery is the first real score (E4). */
    val firstScore: Boolean,
    /** Pairing or the first day: the cards that need a history are left out ([TodayFacts.bare]). */
    val bare: Boolean = false,
) {
    /** "Up to 09:40" for a card whose [newest] reading is stale while syncing has stopped ([upToMarker]). */
    fun upTo(newest: Long?): String? = upToMarker(newest, now, stopped, isToday, zone, staleAfter)

    /**
     * The mark for a card with a partial day tag: a card carries one mark ([TitleMark]), and on a partial day "Partial
     * day" says why its figures run low, so it takes the place of "Up to 09:40" ([upTo]).
     */
    fun mark(newest: Long?): String? = upTo(newest).takeIf { partial == null }

    /** The newest reading of the day, from the series the strap records through it. */
    val newest: Long?
        get() = listOfNotNull(state.heartRate.lastOrNull(), state.stress.lastOrNull(), state.hrv.lastOrNull(), state.spo2.lastOrNull())
            .maxOfOrNull { it.x }

    val score get() = rec?.readiness?.score
    val alert get() = rec?.readiness?.strainAlert == true
    /**
     * Charge now, cut to a whole number as the Charge detail's headline is ([chargeShown]); none before the first
     * night, when it would only be the simulation's starting guess.
     */
    val charge get() = rec?.charge?.takeIf { it.anchored }?.latest?.let(::chargeShown)

    /** Why OpenStrap has no score ([TodayCopy.noScore]). */
    val noScore: TodayCopy.NoScore? get() = TodayCopy.noScore(rec?.readiness)

    /** The score, else the strap's estimate shown as such while OpenStrap's own is withheld (as on the Recovery tab). */
    val recoveryShown get() = TodayCopy.recoveryShown(rec?.readiness, strapScore, waiting)

    /** What a card with no value says while it waits. */
    val waiting: String
        get() = when {
            firstNights -> AFTER_FIRST_NIGHT
            stuck -> WAITING_LAST_NIGHT
            else -> NO_READING
        }

    /** The word a Small says in a number's place while it waits ([waiting] is the phrase for rows and Wides). */
    val waitingWord: String
        get() = org.openstrap.app.i18n.Strings.app.get(
            when {
                firstNights -> R.string.glow_today_word_after_night
                stuck -> R.string.glow_today_word_waiting
                // No night to score, as Last night says "No sleep" beside it.
                else -> R.string.glow_today_word_no_score
            },
        )

    /** The day's workouts you'd call workouts, oldest first: the strap's own "Activity" records aren't. */
    val dayWorkouts: List<WorkoutAnalysis>
        get() = workouts.filter { it.workout.start in state.dayStart..state.dayEnd && it.workout.sportType != Workouts.GENERIC_ACTIVITY }
            .sortedBy { it.workout.start }

    /** The evening and night, when Charge waits for sleep to fill it back. */
    val endOfDay get() = moment == Moment.Evening || moment == Moment.Night

    /**
     * Load against your usual. Only a day that's over is a whole day to compare with your usual one, so that's decided
     * by the date, as on the Activity tab and in the Load detail, not by the moment.
     */
    val loadStatus get() = rec?.let { TodayCopy.loadStatus(it.load, it.chronicLoad, endOfDay = !isToday) }

    /** Your usual workout time, as the workout window finds it ([org.openstrap.app.data.MomentResolver.usualWorkoutTime]). */
    val usualTime: LocalTime?
        get() = org.openstrap.app.data.MomentResolver.usualWorkoutTime(
            workouts.filter { it.startedByYou }.map { it.workout.start }, Instant.ofEpochSecond(now), zone,
        )

    /** Your usual sport: the one you start most, over the same 28 days as your usual workout time. */
    val usualSport: Int
        get() {
            val from = now - org.openstrap.app.data.MomentResolver.USUAL_WORKOUT_DAYS * 86_400L
            val yours = workouts.filter { it.startedByYou && it.workout.start >= from }.map { it.workout.sportType to it.workout.start }
            return org.openstrap.app.presentation.usualSport(yours) ?: topSports(workouts).first().sportType
        }
}

/* ----------------------------------------------- Hero ----------------------------------------------- */

/** Today before the day's first answer: one plain card holding the title's place with the waiting dash, no sentence to misread. */
@Composable
private fun LoadingHero(modifier: Modifier) {
    HaloCard(modifier) {
        Column {
            HaloTitle(stringResource(R.string.glow_today_title))
            Spacer(Modifier.height(Space.S24))
            WaitingDash(onTile = true)
        }
    }
}

@Composable
private fun Hero(
    ctx: TodayContext, strap: TodayStrap, pairing: PairStep?, lightAngle: Float?, bedtime: LocalTime?,
    running: org.openstrap.app.device.WorkoutState.Recording?, onOpenRunning: () -> Unit, liveHero: LiveHeroHooks,
    onStartSport: (Int) -> Unit, onOtherSport: () -> Unit, onPair: () -> Unit, onSyncNow: () -> Unit,
    onOpenRecovery: () -> Unit, onOpenWorkout: (Long) -> Unit, onRetryRecovery: () -> Unit,
    /**
     * A score's own detail, where a hero about it leads (spec 3.3): Recovery's from waking, Charge's from the Charge
     * heroes, as the Charge battery on Recovery does.
     */
    onOpenScore: (org.openstrap.app.ui.dashboard.Score) -> Unit,
    /** The Energy card is hidden in Edit Today, so the evening hero says Energy itself. */
    energyHidden: Boolean = false,
) {
    val onOpenCharge = { onOpenScore(org.openstrap.app.ui.dashboard.Score.Energy) }
    // Every hero is as high as its content with the card padding around it. The floor is only a Wide's height, under
    // every hero's content, so none shows an empty band.
    val m = Modifier.fillMaxWidth().gridSize(CardSize.Wide)
    // The hero's content crossfades when the moment moves on (spec 3.5); the rows below change only with it.
    val kind = when {
        pairing != null -> HeroKind.Pair
        ctx.stuck -> HeroKind.Stuck
        else -> when (ctx.moment) {
            Moment.During -> if (running != null) HeroKind.During else HeroKind.Midday
            Moment.After -> if (ctx.lastWorkout != null) HeroKind.After else HeroKind.Midday
            Moment.WorkoutWindow -> HeroKind.Window
            Moment.Midday -> HeroKind.Midday
            Moment.Evening, Moment.Night -> HeroKind.Evening
            Moment.Waking -> HeroKind.Waking
            null -> if (LocalTime.now().hour < 12 || !ctx.isToday) HeroKind.Waking else HeroKind.Midday
        }
    }.let {
        // No sleep last night: nothing for Recovery to score, so Charge leads from waking on (spec 4.3). Only once
        // the day has loaded, or every cold start would flash the no-sleep hero first.
        if (it == HeroKind.Waking && ctx.state.loaded && ctx.recoveryFailure == null && wakingLead(ctx.firstNights, ctx.state.asleepSeconds, ctx.isToday) == WakingLead.Charge) HeroKind.NoSleep
        else it
    }
    // The During hero keeps drawing while it fades out after the workout ends, when [running] is already gone. Kept
    // once each composition is applied, not written during it (audit K6). Only the fading hero reads it.
    val lastRunning = remember { androidx.compose.runtime.mutableStateOf(running) }
    androidx.compose.runtime.SideEffect { if (running != null) lastRunning.value = running }
    Crossfade(kind, animationSpec = tween(300), label = "hero") { k ->
        when (k) {
            HeroKind.Pair -> PairHero(pairing ?: PairStep.Ready, onPair, m)
            HeroKind.Stuck -> StuckHero(ctx, strap, onSyncNow, m)
            HeroKind.Waking -> WakingHero(ctx, lightAngle, { onOpenScore(org.openstrap.app.ui.dashboard.Score.Recovery) }, onRetryRecovery, m)
            HeroKind.Midday -> ChargeHero(ctx, lightAngle, onOpenCharge, onRetryRecovery, m)
            HeroKind.NoSleep -> ChargeHero(ctx, lightAngle, onOpenCharge, onRetryRecovery, m, noSleep = true)
            HeroKind.Window -> WindowHero(ctx, lightAngle, onStartSport, onOtherSport, m)
            HeroKind.After -> AfterHero(ctx, lightAngle, onOpenWorkout, m)
            HeroKind.Evening -> EveningHero(ctx, lightAngle, bedtime, energyHidden, onOpenRecovery, m)
            HeroKind.During -> (running ?: lastRunning.value)?.let { DuringHero(it, onOpenRunning, liveHero, m) }
        }
    }
}

private enum class HeroKind { Pair, Stuck, Waking, NoSleep, Midday, Window, After, Evening, During }

/**
 * Waking up: how you slept and what Recovery says, the score in dots with its word, and Charge at waking as text
 * beside it (one dotted number a card, spec 2.3). Recovery's one teal, lit from the lower left (spec 4.2). Without
 * OpenStrap's own score the strap's stands in, labelled as its estimate.
 */
@Composable
private fun WakingHero(ctx: TodayContext, lightAngle: Float?, onOpen: () -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    ctx.recoveryFailure?.let {
        return RecoveryFailedHero(it, heroStyle(MetricColors.readiness, lightAngle), onOpen, onRetry, modifier, gap = 24.dp)
    }
    val score = ctx.score
    val shown = ctx.recoveryShown
    val before = ctx.rec?.readinessTrend?.dropLast(1)?.lastOrNull()?.second
    val atWake = ctx.rec?.charge?.takeIf { it.anchored }?.atWake?.toInt()
    HaloCard(modifier, tone = CardTone.lit(heroStyle(MetricColors.readiness, lightAngle)), onClick = onOpen) {
        Column {
            HaloTitle(
                stringResource(R.string.glow_today_recovery_title),
                note = score?.let { TabCopy.vsYesterday(it, before) },
                tag = if (ctx.firstScore && score != null) TitleTag(FIRST_SCORE) else null,
            )
            if (ctx.state.loaded) HeroSentence(TodayCopy.waking(ctx.state.asleepSeconds, score, ctx.alert, ctx.noScore, ctx.firstNights, ctx.isToday, deleted = ctx.state.deletedSleep != null))
            // A hero leaves out a figure it doesn't have rather than show a lone dash; the sentence says why.
            if (shown.value == null && atWake == null) return@Column
            Spacer(Modifier.height(Space.S24))
            HeroReading(
                shown.value?.toString(),
                listOfNotNull(
                    shown.value?.let { shown.word ?: ctx.waiting },
                    atWake?.let { stringResource(R.string.glow_today_energy_at_waking, it) },
                ),
            )
        }
    }
}

/**
 * Midday: Charge as a light level, what the day took and what's left: Charge now in dots, recharged and drained as text
 * beside it. With [noSleep] it leads the morning too, saying no sleep was recorded (spec 4.3).
 */
@Composable
private fun ChargeHero(ctx: TodayContext, lightAngle: Float?, onOpen: () -> Unit, onRetry: () -> Unit, modifier: Modifier, noSleep: Boolean = false) {
    val c = ctx.rec?.charge
    val now = ctx.charge
    var info by rememberSaveable { mutableStateOf(false) }
    // The midday hero is Energy's orange lit from above, like every hero at its moment's angle (HaloTodayMidday).
    HaloCard(modifier, tone = CardTone.lit(heroStyle(MetricColors.charge, lightAngle ?: LightAngle.Above)), onClick = onOpen) {
        Column {
            val start = c?.takeIf { it.anchored }?.atWake?.toInt()
            HaloTitle(stringResource(R.string.glow_today_energy_title), note = start?.let {
                    stringResource(if (c.strapOffOvernight) R.string.glow_today_energy_at_start else R.string.glow_today_energy_waking, it)
                }, marker = ctx.mark(ctx.newest).takeIf { now != null }, tag = ctx.partial)
            // Charge is worked out with Recovery, so when that fails it says so rather than waiting for a first night.
            val failed = ctx.recoveryFailure != null
            HeroSentence(
                when {
                    failed -> CHARGE_FAILED
                    noSleep -> if (ctx.state.deletedSleep != null) TodayCopy.deletedNight(ctx.isToday) else TodayCopy.NO_SLEEP
                    else -> TodayCopy.charge(
                        now, c?.let { TabCopy.drainedAmount(it.drainedExercise + it.drainedStress + it.drainedLiving).toInt() },
                        c?.anchored == true, LocalTime.now().hour,
                    )
                },
            )
            if (failed) {
                Spacer(Modifier.height(Space.S24))
                HaloButton(TRY_AGAIN, onRetry, variant = HaloButtonVariant.Primary, size = HaloButtonSize.Card)
                return@Column
            }
            // The caveat marker: tapping it opens Charge's info sheet, which explains the estimated start. One caveat a
            // card (spec 7.1): a Partial day tag, or the no sleep sentence, already says the day is short of wear.
            if (c?.strapOffOvernight == true && !noSleep && ctx.partial == null) Text(
                stringResource(R.string.glow_today_strap_off),
                color = Ink.Secondary, style = GlowType.secondary,
                modifier = Modifier.padding(top = Space.S8)
                    .clickable(onClickLabel = stringResource(R.string.glow_today_about_energy)) { info = true },
            )
            // No lone dash in a hero: before the first night the sentence says Charge starts after it.
            if (now == null) return@Column
            Spacer(Modifier.height(Space.S24))
            HeroReading(
                now.toString(),
                if (c != null && c.anchored) listOf(
                    stringResource(R.string.glow_today_recharged, c.recharged.toInt()),
                    // After "Drained" the amount needs no minus.
                    stringResource(
                        R.string.glow_today_drained, TabCopy.drainedAmount(c.drainedExercise + c.drainedStress + c.drainedLiving),
                    ),
                ) else listOf(stringResource(R.string.glow_today_now)),
                // The ruler of the day's level, a white tick at now (HaloCharts, Energy level).
                above = { EnergyRuler(
                    now.toFloat(), Ink.Strong, Modifier.fillMaxWidth().height(20.dp),
                    dots = 20, track = Ink.Disabled,
                ) },
            )
        }
    }
    if (info) InfoSheet(
        org.openstrap.app.ui.dashboard.InfoCopy.of(org.openstrap.app.ui.dashboard.Score.Energy), MetricColors.charge,
        now?.let(org.openstrap.app.ui.dashboard.InfoCopy::chargeBar), now?.let { org.openstrap.app.ui.dashboard.InfoCopy.HERE },
        onDismiss = { info = false }, notes = org.openstrap.app.ui.dashboard.InfoCopy.chargeNotes(c),
    )
}

/**
 * Your usual workout time: the sport you usually do now with when, Recovery in dots with the resting rate and the last
 * time you did it beside it, and a way to start it. Heart red from the upper right with the rim raised (spec 4.2);
 * Recovery is a figure on the hero, not its colour (board HaloTodayWorkoutTime). Without a usual time it keeps the
 * title Recovery gives.
 */
@Composable
private fun WindowHero(ctx: TodayContext, lightAngle: Float?, onStart: (Int) -> Unit, onOther: () -> Unit, modifier: Modifier) {
    val sport = ctx.usualSport
    val usual = ctx.usualTime
    val last = ctx.workouts.filter { it.startedByYou && it.workout.sportType == sport && it.workout.start < ctx.now }
        .maxOfOrNull { it.workout.start }
    HaloCard(modifier, tone = CardTone.lit(heroStyle(MetricColors.heart, lightAngle, rim = WINDOW_RIM))) {
        Column {
            HaloTitle(
                if (usual != null) TodayCopy.windowUsualTitle(sport) else TodayCopy.windowTitle(ctx.score),
                note = usual?.let(TodayCopy::windowAround),
            )
            HeroSentence(TodayCopy.windowSentence(ctx.score, ctx.charge))
            val lines = listOfNotNull(
                ctx.state.restingHeartRate?.let { stringResource(R.string.core_today_window_resting, it) },
                last?.let { TodayCopy.windowLast(sport, it, ctx.now, ctx.zone) },
            )
            if (ctx.score != null || lines.isNotEmpty()) {
                Spacer(Modifier.height(Space.S20))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Space.S16)) {
                    ctx.score?.let { HeroFigure(it.toString(), stringResource(R.string.day_card_recovery), big = true) }
                    Column(Modifier.padding(bottom = Space.S2), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                        lines.forEach { Text(it, color = Color.White, style = GlowType.secondary, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(Space.S24))
            // The main button keeps its label whole: at large font sizes Other no longer shares its row, it goes under it.
            val start = stringResource(R.string.glow_today_start_sport, Workouts.sportName(sport).lowercase())
            val other = stringResource(R.string.glow_today_other_sport)
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > STACK_BUTTONS_FROM) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ButtonSpec.PairGap)) {
                    HaloButton(
                        start, { onStart(sport) }, Modifier.fillMaxWidth(),
                        variant = HaloButtonVariant.Primary, icon = AppIcons.PlayFill, size = HaloButtonSize.Card,
                    )
                    HaloButton(other, onOther, Modifier.fillMaxWidth(), variant = HaloButtonVariant.Glass, size = HaloButtonSize.Card)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonSpec.PairGap)) {
                    HaloButton(
                        start, { onStart(sport) }, Modifier.weight(1f),
                        variant = HaloButtonVariant.Primary, icon = AppIcons.PlayFill, size = HaloButtonSize.Card,
                    )
                    HaloButton(other, onOther, variant = HaloButtonVariant.Glass, size = HaloButtonSize.Card)
                }
            }
        }
    }
}

/** From this font scale on the workout hero's two buttons stack. */
private const val STACK_BUTTONS_FROM = 1.1f

/**
 * After a workout: how long since, and the heart coming down, drawn from the readings since it ended. The heart rate
 * now in dots, the end and resting rates as text beside it. Recovery teal, calm light from below, the rim at 80%
 * (spec 4.2).
 */
@Composable
private fun AfterHero(ctx: TodayContext, lightAngle: Float?, onOpenWorkout: (Long) -> Unit, modifier: Modifier) {
    val w = ctx.lastWorkout ?: return
    val since = ctx.state.heartRate.filter { it.x >= w.end }
    val nowHr = ctx.live ?: since.lastOrNull()?.y?.toInt()
    // Recovery teal, from below with the rim calmed: red stays for effort, so it stops once the run is done (spec 2.6, 4.2).
    val style = heroStyle(MetricColors.readiness, lightAngle ?: LightAngle.Right, rim = AFTER_RIM)
    HaloCard(modifier, tone = CardTone.lit(style), onClick = { onOpenWorkout(w.workout.start) }) {
        Column {
            HaloTitle(Workouts.sportName(w.workout.sportType), note = stringResource(R.string.glow_today_after_finished, Units.duration(ctx.now - w.end)))
            HeroSentence(TodayCopy.after(nowHr, ctx.state.restingHeartRate))
            Spacer(Modifier.height(Space.S20))
            AfterWorkoutBody(w)
        }
    }
}

/**
 * Evening and night: the day in a sentence (Load in it), the steps in dots, Energy left under them only when
 * [energyHidden] (its card, normally right under the hero, is hidden in Edit Today), and how
 * long until your usual bedtime. Evening violet from the lower right in the evening, Sleep blue from below at night
 * (spec 4.2), each in its mood.
 */
@Composable
private fun EveningHero(ctx: TodayContext, lightAngle: Float?, bedtime: LocalTime?, energyHidden: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    val goals = LocalGoals.current
    val sports = ctx.dayWorkouts.map { it.workout.sportType }
    val night = ctx.moment == Moment.Night
    // The evening glows at 0.8 and the night at 0.65 (boards HaloTodayEvening, HaloTodayNight).
    val style = heroStyle(if (night) MetricColors.sleepDeep else MetricColors.evening, lightAngle, rim = if (night) 0.65f else 0.8f)
    // The evening's violet is lit lighter so it reads as violet, not as a second night (spec 4.2).
    HaloCard(modifier, tone = CardTone.lit(if (night) style else style.copy(bodyLightness = EVENING_BODY)), onClick = onOpen) {
        Column {
            HaloTitle(
                stringResource(R.string.glow_today_title), note = TodayCopy.bedtimeIn(LocalTime.now(), bedtime),
                marker = ctx.mark(ctx.newest), tag = ctx.partial,
            )
            // A day with nothing recorded isn't judged: no verdict on zero steps.
            val empty = ctx.state.steps == 0 && sports.isEmpty() && ctx.state.heartRate.isEmpty()
            if (empty) {
                HeroSentence(stringResource(if (night) R.string.core_today_night_wind_down else R.string.core_today_evening_nothing))
                return@Column
            }
            // The count is in dots below, so the sentence says the steps against the goal instead.
            val day = TodayCopy.evening(sports, ctx.state.steps, goals.steps, ctx.loadStatus, stepCount = false)
            if (night) {
                // The night hero winds down (board HaloTodayNight): the day in a sentence, then your usual bedtime as
                // its figure. The steps are the Steps card's, right under it.
                HeroSentence(stringResource(R.string.core_today_night_wind_down_day, day))
                bedtime?.let {
                    Spacer(Modifier.height(Space.S24))
                    HeroReading(TimeOfDay.format(it), listOf(stringResource(R.string.glow_today_usual_bedtime)))
                }
                return@Column
            }
            HeroSentence(day)
            Spacer(Modifier.height(Space.S24))
            HeroReading(Units.count(ctx.state.steps, Units.DOT_LOCALE), listOf(stringResource(R.string.glow_today_steps)))
            // Load is said in the sentence, and Energy has its own card right under the hero. Only with that card
            // hidden in Edit Today does Energy come back here, last and full width, never a lone dash.
            val energy = ctx.charge?.takeIf { energyHidden }
            if (energy != null) Text(
                stringResource(R.string.glow_today_energy_left, energy), color = Ink.Secondary,
                style = GlowType.secondary,
                maxLines = 1, modifier = Modifier.padding(top = Space.S12),
            )
        }
    }
}

/**
 * What the During hero needs from the workout (workouts spec 2.10): its [glance], read only here so Today doesn't
 * recompose every second, what its buttons do ([onAction]), and whether it's on screen ([onShown]), so the floating
 * bar can stay away while it is.
 */
class LiveHeroHooks(
    val glance: () -> org.openstrap.app.ui.workout.LiveGlance? = { null },
    val onAction: (org.openstrap.app.ui.workout.GlanceAction) -> Unit = {},
    val onShown: (Boolean) -> Unit = {},
)

/**
 * During a workout: the workout at a glance ([LiveHero]), and under it, for a GPS workout, the last kilometre on a
 * quiet strip, so the two never look alike. Says when it's on screen, for the floating bar.
 */
@Composable
private fun DuringHero(r: org.openstrap.app.device.WorkoutState.Recording, onOpen: () -> Unit, hooks: LiveHeroHooks, modifier: Modifier) {
    val glance = hooks.glance() ?: return
    val onShown by rememberUpdatedState(hooks.onShown)
    DisposableEffect(Unit) { onDispose { onShown(false) } }
    Column(
        Modifier.onGloballyPositioned { c ->
            // On screen while any of it is inside the window's height.
            val b = c.boundsInWindow()
            onShown(b.height > 0f && b.bottom > 0f)
        },
        verticalArrangement = Arrangement.spacedBy(GlowLayout.ColumnGap),
    ) {
        LiveHero(glance, r.workout.sportType, glance.clock, onOpen, hooks.onAction, modifier)
        glance.split?.let { (title, line) ->
            HaloCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(TrendGood) }
                    Spacer(Modifier.width(Space.S12))
                    Column {
                        Text(title, color = Color.White, style = GlowType.body)
                        line?.let { Text(it, color = Ink.Secondary, style = GlowType.secondary) }
                    }
                }
            }
        }
    }
}

/**
 * No strap paired, or connecting for the first time: the pairing stage (E3). One sentence per [step], the strap still
 * and lit, the line to the phone changing under it, and one firm tick when it connects (spec 3.6). Find my strap opens
 * the Strap page, where pairing is done.
 */
@Composable
private fun PairHero(step: PairStep, onPair: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    LaunchedEffect(step) { if (step == PairStep.Connected) haptics.perform(HapticEvent.PairingDone) }
    // While the app looks or connects another start would only collide with the attempt, so the card isn't a button then.
    val busy = step == PairStep.Searching || step == PairStep.Connecting
    HaloCard(modifier, tone = CardTone.lit(heroStyle(StrapSilver, LightAngle.Below)), onClick = onPair.takeIf { !busy }) {
        Column {
            HaloTitle(stringResource(R.string.glow_today_pair_title))
            // The sentence and the button keep their room at every step, so the cards under the hero never jump.
            HeroSentence(pairSentence(step), Modifier.heightIn(min = PairSentenceHeight))
            Spacer(Modifier.height(Space.S20))
            PairingStage(step)
            Spacer(Modifier.height(Space.S20))
            Box(Modifier.fillMaxWidth().height(PairButtonSlot), contentAlignment = Alignment.CenterStart) {
                // A failed pairing offers a different next step: the Strap page, which says how to free the strap from Zepp.
                if (step != PairStep.Connected && !busy) HaloButton(
                    stringResource(if (step == PairStep.Failed) R.string.glow_today_open_strap else R.string.glow_today_find_strap), onPair,
                    Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    variant = HaloButtonVariant.Primary, size = HaloButtonSize.Card,
                )
            }
        }
    }
}

/** The room the pairing hero keeps for its sentence (the longest, a failed pairing's) and its button, at every step. */
private val PairSentenceHeight = 96.dp
private val PairButtonSlot = 48.dp

/**
 * The sync is stuck when it matters (spec 4.3): failing in the morning with last night still on the strap, or failing
 * for hours. One action, and when it was last tried.
 */
@Composable
private fun StuckHero(ctx: TodayContext, strap: TodayStrap, onSyncNow: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val copy = stuckCopy(ctx.state.asleepSeconds != null, ctx.newest?.let { TimeOfDay.format(it, ctx.zone) })
    HaloCard(modifier, tone = CardTone.lit(heroStyle(MetricColors.warn, LightAngle.Below))) {
        Column {
            HaloTitle(copy.title, note = lastTried(strap.record, ctx.zone))
            HeroSentence(copy.sentence)
            Text(copy.line, color = Ink.Strong, style = GlowType.secondary, modifier = Modifier.padding(top = Space.S8))
            Spacer(Modifier.height(Space.S32))
            HaloButton(
                stringResource(R.string.glow_today_sync_now),
                { if (strap.canSync) onSyncNow() else haptics.perform(HapticEvent.SyncFailed) },
                variant = HaloButtonVariant.Primary, size = HaloButtonSize.Card,
            )
        }
    }
}

/* ----------------------------------------------- Rows ----------------------------------------------- */

@Composable
private fun TodayRow(
    slot: TodaySlot, ctx: TodayContext, healthspan: org.openstrap.app.data.Healthspan.Outcome?, history: DailyHistory?,
    checkIn: Int?, onCheckIn: (Int?) -> Unit, onOpenMetric: (Metric) -> Unit,
    onOpenWorkout: (Long) -> Unit, onOpenHealthspan: () -> Unit, onOpenProfile: () -> Unit,
    suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion?, onSaveSuggestion: () -> Unit, onDismissSuggestion: () -> Unit,
    onOpen: (org.openstrap.app.ui.dashboard.Detail) -> Unit, usualSteps: List<Float>?, onRetryRecovery: () -> Unit,
    /** The size a Small takes in its row. */
    modifier: Modifier = Modifier,
    /** Opens Ready with a sport, for Start a workout. */
    onStartSport: (Int) -> Unit = {},
    /** The day's steps against the goal, for the Steps card's goal tag. */
    stepsFacts: org.openstrap.app.ui.dashboard.GoalFacts? = null,
) {
    val s = ctx.state
    // Each card draws its picture at the size its slot gives it (spec 4.2): a Small small, the rest wide.
    val size = pictureSize(slot.size)
    // Your normal over 30 days, as the Recovery tab's body signals measure against, so both say the same.
    @Composable fun normal(m: Metric) = rememberNormal(m, s.date, history)
    val openScore = { score: org.openstrap.app.ui.dashboard.Score -> onOpen(org.openstrap.app.ui.dashboard.Detail.OfScore(score)) }
    // Last night opens the Sleep score's detail, as the Sleep tab's hero does (spec 3.3).
    val onOpenSleep = { openScore(org.openstrap.app.ui.dashboard.Score.Sleep) }
    when (slot.card) {
        TodayCard.Suggestion -> suggestion?.let { SuggestionPanel(it, onSaveSuggestion, onDismissSuggestion, strip = true) }
        TodayCard.Alerts -> AlertsStrip(s.alerts, onOpenMetric)
        TodayCard.CheckIn -> CheckInStrip(checkIn, onCheckIn)
        TodayCard.BodyMorning -> BodyList(
            stringResource(if (ctx.bare) R.string.glow_today_body_today else R.string.glow_today_body_morning),
            listOf(
                BodyRow(
                    stringResource(R.string.glow_today_resting_hr), MetricColors.heart, s.restingHeartRate?.toFloat(),
                    normal(Metric.RestingHr), "", Metric.RestingHr,
                    source = if (s.restingEstimated) BodyValue.Estimated else BodyValue.Plain,
                ),
                // Night signals: last night's against the nights before, as the Sleep tab and their details read them.
                BodyRow(
                    stringResource(R.string.glow_today_hrv), MetricColors.hrv, SleepCopy.nightValue(s, Metric.Hrv),
                    SleepCopy.nightNormal(s, Metric.Hrv), "", Metric.Hrv, SleepCopy.learning(s, Metric.Hrv),
                ),
                // Blood oxygen leads with the day's latest reading, said as daytime or asleep, with its time once an hour
                // old. A single reading has no normal, so the row has no tag; the night's average, with its tag, is
                // on the Sleep and Recovery tabs.
                LatestReading.spo2(s).let { latest ->
                    BodyRow(
                        stringResource(R.string.glow_today_blood_oxygen), MetricColors.spo2, latest?.y,
                        null, "%", Metric.Spo2,
                        source = BodyValue.Latest(
                            latest?.let { LatestReading.spo2Line(it.x, LatestReading.sleepWindow(s), ctx.now, time = ::clock) }.orEmpty(),
                        ),
                    )
                },
                BodyRow(
                    stringResource(R.string.glow_today_breathing_rate), MetricColors.sleepLight, SleepCopy.nightValue(s, Metric.Breathing),
                    SleepCopy.nightNormal(s, Metric.Breathing), "", Metric.Breathing, SleepCopy.learning(s, Metric.Breathing),
                ),
            ),
            ctx.waiting, onOpenMetric,
        )
        TodayCard.LastNight ->
            if (slot.size == CardSize.Strip) LastNightStrip(s, onOpenSleep, stuck = ctx.stuck, firstNights = ctx.firstNights)
            else LastNightCard(s, ctx.firstNights, onOpenSleep, modifier, stuck = ctx.stuck, size = size, lit = slot.look?.tier == Tier.Glow)
        // A live reading is never stale.
        TodayCard.HeartRate -> HeartDayCard(
            s, ctx.live, size, { onOpenMetric(Metric.HeartRate) }, modifier, restingRange = ctx.restingRange,
            upTo = if (ctx.live == null) ctx.upTo(s.heartRate.lastOrNull()?.x) else null,
        )
        TodayCard.Stress -> StressDayCard(
            s, size, ctx.waiting, { onOpenMetric(Metric.Stress) }, modifier, now = ctx.now,
        )
        // Steps come in the same records as the rest of the day, so the day's newest reading is how far they go. The
        // count stays in dots in the evening too, under the hero's: the goal tag needs a number to sit by (spec 2.5).
        TodayCard.Activity -> ActivityCard(
            s, { onOpenMetric(Metric.Steps) }, modifier, usual = usualSteps, upTo = ctx.mark(ctx.newest), tag = ctx.partial,
            size = size, facts = stepsFacts,
        )
        TodayCard.Recovery -> RecoveryEarlierCard(
            ctx.recoveryShown, { openScore(org.openstrap.app.ui.dashboard.Score.Recovery) }, modifier, failure = ctx.recoveryFailure,
            onRetry = onRetryRecovery, size = size, recovery = ctx.rec, noScore = ctx.noScore, waitingWord = ctx.waitingWord,
        )
        TodayCard.Charge -> {
            val c = ctx.rec?.charge
            // What the day drained, gross, as the hero, Recovery's Drained Small and the Charge detail say it.
            val drained = c?.let { it.drainedExercise + it.drainedStress + it.drainedLiving }
            val status = when {
                // Worked out with Recovery: when that failed, say so here too. The card opens Recovery and its Try again.
                ctx.charge == null -> if (ctx.recoveryFailure != null) CHARGE_FAILED else ctx.waiting
                ctx.endOfDay -> stringResource(R.string.glow_today_charge_fills_back)
                drained != null && drained >= 1f -> stringResource(R.string.glow_today_drained, TabCopy.drainedAmount(drained))
                else -> stringResource(R.string.glow_today_charge_steady)
            }
            // The day's Charge so far beside the number, in the evening too, as on every other Charge card.
            val soFar = c?.takeIf { it.anchored && ctx.isToday }?.let { chargeSoFar(it.points, s.dayStart..s.dayEnd, ctx.now) }
            ChargeWideCard(
                ctx.charge, status, slot.secondGlow, { openScore(org.openstrap.app.ui.dashboard.Score.Energy) }, soFar = soFar,
                upTo = ctx.mark(ctx.newest).takeIf { ctx.charge != null }, tag = ctx.partial, small = slot.size == CardSize.Small,
                atWake = c?.takeIf { it.anchored }?.atWake?.toInt(),
                word = if (ctx.recoveryFailure != null) stringResource(R.string.glow_today_word_unavailable) else ctx.waitingWord,
            )
        }
        // Before and after a workout, as a pair: Load filling toward your usual, and the ring toward Ready.
        TodayCard.LoadToday -> LoadCard(
            ctx.rec?.load, ctx.rec?.chronicLoad, endOfDay = !ctx.isToday,
            when { ctx.recoveryFailure != null -> FAILED_SHORT; ctx.rec == null -> ctx.waiting; else -> TodayCopy.loadWord(ctx.loadStatus) },
            size, { openScore(org.openstrap.app.ui.dashboard.Score.Load) }, modifier,
            word = when {
                ctx.recoveryFailure != null -> stringResource(R.string.glow_today_word_unavailable)
                ctx.rec == null -> ctx.waitingWord
                else -> null
            },
        )
        TodayCard.Recover -> RecoverCard(
            latestRecoveryEstimate(ctx.workouts), ctx.now, ctx.zone, size, { openScore(org.openstrap.app.ui.dashboard.Score.Recover) }, modifier,
        )
        TodayCard.Workout -> ctx.lastWorkout?.let { w -> WorkoutCard(w, { onOpenWorkout(w.workout.start) }) }
        TodayCard.Week -> WeekCard(s, history, onClick = { onOpen(org.openstrap.app.ui.dashboard.Detail.Week) })
        // Habit Age's home is the foot of Recovery; Today shows the same card when there's a new estimate, or pinned.
        TodayCard.HabitAge -> HabitAgeEdgeCard(healthspan, onOpenHealthspan, onOpenProfile)
        // Never at night, whatever the plan says: the day's done (spec 4.2).
        TodayCard.StartWorkout -> if (ctx.moment != Moment.Night) StartWorkoutStrip(ctx.usualSport, onStartSport)
        // Right now is in no moment any more, and a pin of it saved before drops out.
        TodayCard.Hero, TodayCard.EditToday -> Unit
    }
}

/* ---------------------------------------------- Helpers --------------------------------------------- */

/** The day's Charge in quarter hours across [day], up to [now]; the quarters still to come are null. */
internal fun chargeSoFar(points: List<Recovery.ChargePoint>, day: LongRange, now: Long): List<Float?> {
    val step = 15 * 60L
    return org.openstrap.app.data.Summaries.evenly(points.filter { it.t <= now }, day, step, { it.t }, { it.value })
}

/** How far the other cards fade while one is held, and how much the held one grows (board HaloTodayHold). */
private const val HOLD_DIM = 0.7f
private const val HOLD_LIFT = 0.03f
