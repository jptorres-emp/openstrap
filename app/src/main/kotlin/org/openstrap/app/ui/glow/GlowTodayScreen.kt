package org.openstrap.app.ui.glow

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import java.time.LocalTime
import java.time.ZoneId
import org.openstrap.app.data.Moment
import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.StrapReadinessRow
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Hue
import org.openstrap.app.ui.dashboard.defaultLook
import org.openstrap.app.ui.dashboard.lookOr
import org.openstrap.app.ui.dashboard.todayPanes
import org.openstrap.app.ui.dashboard.varyLooks
import org.openstrap.app.ui.dashboard.habitAgeNew
import org.openstrap.app.ui.dashboard.items
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.TodayCard
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.TodayEdits
import org.openstrap.app.ui.dashboard.TodayFacts
import org.openstrap.app.ui.dashboard.TodaySlot
import org.openstrap.app.ui.dashboard.applyEdits
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.dashboard.sportNoun
import org.openstrap.app.ui.dashboard.syncStuck
import org.openstrap.app.ui.dashboard.todayCards
import org.openstrap.app.ui.theme.MetricColors

/*
 * Today (spec 4): one hero whose content follows the moment, then a stable set of cards for it ([todayCards]), with
 * Edit Today's pins and hides on top ([applyEdits]). The hero glows; the rest is edge light and lists, so the glow
 * keeps meaning "look here".
 */

/** The strap's state where Today needs it: paired at all, and whether the last sync failed and when it was seen to. */
class TodayStrap(
    val paired: Boolean = true,
    val syncFailed: Boolean = false,
    /** When the failure was first seen this run (epoch seconds), for "Last tried 07:42". */
    val failedAt: Long? = null,
    val canSync: Boolean = true,
)

@Composable
fun GlowTodayScreen(
    state: DayUiState,
    liveHeartRate: Int?,
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    /** The moment Today is laid out for ([org.openstrap.app.ui.dashboard.layoutMoment]); null while it loads. */
    moment: Moment?,
    /** Opens the Sleep screen over Today. */
    onOpenSleep: () -> Unit = {},
    onOpenMetric: (Metric) -> Unit = {},
    recovery: RecoveryResult? = null,
    /** Opens the Recovery screen over Today. */
    onOpenRecovery: () -> Unit = {},
    workouts: List<WorkoutAnalysis> = emptyList(),
    onOpenWorkout: (Long) -> Unit = {},
    onOpenHealthspan: () -> Unit = {},
    /** Opens the profile, for the Habit Age card's reminder to add a birth year or sex. */
    onOpenProfile: () -> Unit = {},
    /** The weekly Habit Age estimate, or why it's withheld; null hides the card. */
    healthspan: org.openstrap.app.data.Healthspan.Outcome? = null,
    /** The Sunday of the last Habit Age estimate opened, so Today stops offering it ([habitAgeNew]). */
    habitAgeSeenAsOf: Long? = null,
    /** Per-day history for Your week; null leaves its steps out. */
    history: DailyHistory? = null,
    /** Edit Today's hidden and pinned cards. */
    edits: TodayEdits = TodayEdits(),
    onEditsChange: (TodayEdits) -> Unit = {},
    /** The strap's own Readiness for the day, shown as "Strap's estimate" while OpenStrap's score is withheld. */
    strapReadiness: StrapReadinessRow? = null,
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
    /** An effort under way nobody started ([org.openstrap.app.data.AutoStart]); null hides the strip. */
    offer: org.openstrap.app.data.AutoStart.Offer? = null,
    onRecordOffer: () -> Unit = {},
    onDeclineOffer: () -> Unit = {},
    /** A workout recording now: the hero during a workout leads back to it ([WorkoutShortcut]). */
    running: org.openstrap.app.device.WorkoutState.Recording? = null,
    onOpenRunning: () -> Unit = {},
    onResumeRunning: () -> Unit = {},
    /** Where the hero's light comes from for the moment ([org.openstrap.app.ui.dashboard.heroLightAngle]); null keeps its own. */
    heroLightAngle: Float? = null,
    strap: TodayStrap = TodayStrap(),
    /** Opens the Strap page, to pair. */
    onPair: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    /** When you usually go to sleep, for "Bedtime in 1 h 10". */
    usualBedtime: LocalTime? = null,
) {
    val list = rememberLazyListState()
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) EditTodayDialog(edits, onSave = { onEditsChange(it); editing = false }, onDismiss = { editing = false })

    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val isToday = state.date == now.atZone(zone).toLocalDate()
    val rec = recovery?.takeIf { it.date == state.date }
    val lastWorkout = workouts.maxByOrNull { it.end }
    val ctx = TodayContext(
        state, rec, liveHeartRate, workouts, lastWorkout, isToday, now.epochSecond, zone, moment,
        restingRange = rememberNormal(Metric.RestingHr, state.date, history),
        firstNights = rec != null && !rec.charge.anchored,
        stuck = syncStuck(moment, isToday, state.asleepSeconds != null, strap.syncFailed),
        strapScore = strapReadiness?.takeIf { it.day == state.dayStart }?.score,
    )
    val facts = TodayFacts(
        isToday = isToday, offer = offer != null, suggestion = suggestion != null, alerts = state.alerts.isNotEmpty(),
        workout = lastWorkout != null && isToday,
        healthspan = healthspan?.result?.let { r ->
            habitAgeNew(Instant.ofEpochSecond(r.asOf).atZone(zone).toLocalDate(), habitAgeSeenAsOf == r.asOf, state.date, now.atZone(zone).toLocalDate())
        } == true,
    )
    // Before the first estimate Habit Age never shows on Today, pinned or not.
    val unavailable = if (healthspan?.result == null) setOf(TodayCard.HabitAge) else emptySet()
    val morning = LocalTime.now().hour < 12
    val live = liveHeartRate != null
    // The moment the cards are laid out for, with no moment yet resolved as todayCards does.
    val looksFor = moment ?: if (morning) Moment.Waking else Moment.Midday

    // The glow holds still while the list scrolls, so scrolling stays as smooth as on other screens.
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // No two cards next to each other look the same (spec 2.4). On two columns, side by side counts too.
            val wide = maxWidth >= GlowLayout.TwoColumnWidth
            val slots = varyLooks(applyEdits(todayCards(moment, facts, morning), edits, moment, unavailable), looksFor, live, wide)
            val panes = todayPanes(slots)
            GlowScreenList(contentPadding, list) {
                full { header() }
                // One block per grid row: a Small pair stays side by side, and on a wide screen in one column.
                packGrid(slots.indices.map { slots[it].size to it }).forEach { row ->
                    val block: @Composable () -> Unit = {
                        GridRowBlock(row) { i, m ->
                            val slot = slots[i]
                            // Before a strap is paired, everything under the pairing hero waits, dimmed.
                            val dim = if (!strap.paired && slot.card != TodayCard.Hero && slot.card != TodayCard.EditToday) Modifier.alpha(0.45f) else Modifier
                            // Where the look differs from the card's own, its edge card is told the side and colour to use.
                            val own = defaultLook(slot.copy(look = null), looksFor, live)
                            val look = slot.lookOr(looksFor, live)
                            val override = EdgeOverride(
                                edge = look.edge.takeIf { it != own.edge },
                                color = EdgeNeutral.takeIf { look.hue == Hue.Neutral && own.hue != Hue.Neutral },
                            )
                            CompositionLocalProvider(LocalEdgeOverride provides override) {
                                Column(m.then(dim)) {
                                    when (slot.card) {
                                        TodayCard.Hero -> Hero(
                                            ctx, strap, heroLightAngle, usualBedtime,
                                            running, onOpenRunning, onResumeRunning, onStartSport, onOtherSport, onPair, onSyncNow,
                                            onOpenRecovery, onOpenWorkout,
                                        )
                                        TodayCard.EditToday -> EditTodayButton({ editing = true })
                                        else -> TodayRow(slot, ctx, healthspan, history, checkIn, onCheckIn, onOpenSleep, onOpenMetric, onOpenRecovery,
                                            onOpenWorkout, onOpenHealthspan, onOpenProfile, offer, onRecordOffer, onDeclineOffer, suggestion,
                                            onSaveSuggestion, onDismissSuggestion, if (slot.size == CardSize.Small) Modifier.fillMaxSize() else Modifier)
                                    }
                                }
                            }
                        }
                    }
                    // The hero and strips span the width on a wide screen; the rows between take turns.
                    when (panes[row.items().first()]) {
                        Pane.Full -> full(block)
                        Pane.Left -> left(block)
                        Pane.Right -> right(block)
                    }
                }
            }
        }
    }
}

/** What Today's cards read, worked out once per screen. */
private class TodayContext(
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
) {
    val score get() = rec?.readiness?.score
    val alert get() = rec?.readiness?.strainAlert == true
    val charge get() = rec?.charge?.latest?.toInt()

    /** Why OpenStrap has no score: still learning comes first, since a learning night has no baseline to score against. */
    val noScore: TodayCopy.NoScore?
        get() {
            val r = rec?.readiness ?: return null
            return when {
                r.score != null -> null
                r.phase == Recovery.Phase.Withheld -> TodayCopy.NoScore.Learning(r.baselineNights, Recovery.SCORED_NIGHTS)
                r.withheld -> TodayCopy.NoScore.NoHeartData
                else -> null
            }
        }

    /** The strap's estimate, shown as such while OpenStrap's own score is withheld (as on the Recovery tab). */
    val estimate: Int? get() = rec?.readiness?.takeIf { score == null && it.showStrapEstimate }?.let { strapScore }

    val recoveryShown get() = TodayCopy.recoveryShown(score, alert, noScore, estimate, waiting)

    /** What a card with no value says while it waits. */
    val waiting: String
        get() = when {
            firstNights -> "After your first night"
            stuck -> "Waiting for last night"
            else -> "No reading yet"
        }

    /** The day's workouts you'd call workouts, oldest first: the strap's own "Activity" records aren't. */
    val dayWorkouts: List<WorkoutAnalysis>
        get() = workouts.filter { it.workout.start in state.dayStart..state.dayEnd && it.workout.sportType != Workouts.GENERIC_ACTIVITY }
            .sortedBy { it.workout.start }

    val endOfDay get() = moment == Moment.Evening || moment == Moment.Night

    val loadStatus get() = rec?.let { TodayCopy.loadStatus(it.load, it.chronicLoad, endOfDay) }

    /** Time to recover from the latest workout that says how long it needs, or null when none does. */
    val recover: Pair<String?, String>?
        get() {
            val w = workouts.filter { it.recoveryMinutes != null }.maxByOrNull { it.end } ?: return null
            return TodayCopy.recoverText(TodayCopy.recoverLeft(w.end, w.recoveryMinutes ?: 0, now), now, zone)
        }

    /** Your usual sport: the one you start most, over the same 28 days as your usual workout time. */
    val usualSport: Int
        get() {
            val from = now - org.openstrap.app.data.MomentResolver.USUAL_WORKOUT_DAYS * 86_400L
            val yours = workouts.filter { it.startedByYou && it.workout.start >= from }.map { it.workout.sportType to it.workout.start }
            return org.openstrap.app.ui.dashboard.usualSport(yours) ?: topSports(workouts).first().sportType
        }
}

/* ----------------------------------------------- Hero ----------------------------------------------- */

@Composable
private fun Hero(
    ctx: TodayContext, strap: TodayStrap, lightAngle: Float?, bedtime: LocalTime?,
    running: org.openstrap.app.device.WorkoutState.Recording?, onOpenRunning: () -> Unit, onResumeRunning: () -> Unit,
    onStartSport: (Int) -> Unit, onOtherSport: () -> Unit, onPair: () -> Unit, onSyncNow: () -> Unit,
    onOpenRecovery: () -> Unit, onOpenWorkout: (Long) -> Unit,
) {
    // Every hero is as high as its content with the card padding around it. The floor is only a Wide's height, under
    // every hero's content, so none shows an empty band.
    val m = Modifier.fillMaxWidth().gridSize(CardSize.Wide)
    // The hero's content crossfades when the moment moves on (spec 3.5); the rows below change only with it.
    val kind = when {
        !strap.paired -> HeroKind.Pair
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
    }
    Crossfade(kind, animationSpec = tween(300), label = "hero") { k ->
        when (k) {
            HeroKind.Pair -> PairHero(onPair, m)
            HeroKind.Stuck -> StuckHero(strap, onSyncNow, m)
            HeroKind.Waking -> WakingHero(ctx, lightAngle, onOpenRecovery, m)
            HeroKind.Midday -> ChargeHero(ctx, onOpenRecovery, m)
            HeroKind.Window -> WindowHero(ctx, lightAngle, onStartSport, onOtherSport, m)
            HeroKind.After -> AfterHero(ctx, lightAngle, onOpenWorkout, m)
            HeroKind.Evening -> EveningHero(ctx, lightAngle, bedtime, onOpenRecovery, m)
            HeroKind.During -> DuringHero(ctx, running!!, onOpenRunning, onResumeRunning, m)
        }
    }
}

private enum class HeroKind { Pair, Stuck, Waking, Midday, Window, After, Evening, During }

/** The hero's recipe: the dark core opposite the light, which the moment places. */
private fun heroStyle(color: Color, lightAngle: Float?, strength: Float = 0.6f) = GlowStyle.Bloom.copy(
    lightAngle = lightAngle, color = color,
    coreShape = CoreShape.Radial, coreX = 0.5f, coreY = 0.6f, coreWidth = 0.95f, coreHeight = 0.75f,
    coreBlur = 30.dp, toneWidth = 16.dp, toneShift = -10f, glowStrength = strength, bevel = 0.6f,
)

/**
 * Waking up: how you slept and what Recovery says, the score with its word, and Charge at waking as a fact. Without
 * OpenStrap's own score the strap's stands in, labelled as its estimate.
 */
@Composable
private fun WakingHero(ctx: TodayContext, lightAngle: Float?, onOpen: () -> Unit, modifier: Modifier) {
    val score = ctx.score
    val shown = ctx.recoveryShown
    val before = ctx.rec?.readinessTrend?.dropLast(1)?.lastOrNull()?.second
    GlowCard(heroStyle(overviewColor(shown.value, ctx.alert), lightAngle), modifier, onClick = onOpen) {
        Column {
            GlowHeader("Recovery", trailing = if (score != null && before != null) signed(score - before) + " vs yesterday" else null)
            HeroSentence(TodayCopy.waking(ctx.state.asleepSeconds, score, ctx.alert, ctx.noScore, ctx.firstNights, ctx.isToday))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Column(Modifier.weight(1f)) { HeroFigure(shown.value?.toString(), shown.word ?: ctx.waiting, big = true) }
                HeroFigure(ctx.rec?.charge?.atWake?.toInt()?.toString(), "Charge at waking", horizontal = androidx.compose.ui.Alignment.End)
            }
        }
    }
}

private fun signed(d: Int) = if (d > 0) "+$d" else if (d < 0) "−${-d}" else "Same"

/** Midday: Charge as a light level, what the day took and what's left, with recharged and drained. */
@Composable
private fun ChargeHero(ctx: TodayContext, onOpen: () -> Unit, modifier: Modifier) {
    val c = ctx.rec?.charge
    val now = ctx.charge
    // Charge is lit to its level from below (spec 2.5), so the moment's angle gives way here.
    GlowCard(GlowStyle.Ember.copy(lightLevel = (now ?: 0) / 100f), modifier, onClick = onOpen) {
        Column {
            GlowHeader("Charge", trailing = c?.atWake?.let { "${it.toInt()} at waking" })
            HeroSentence(TodayCopy.charge(now, c?.atWake?.toInt(), c?.anchored == true, LocalTime.now().hour))
            // TODO(step 6): the info sheet that explains an estimated start; the strap-off note says it for now.
            if (c?.strapOffOvernight == true) Text(
                "Strap off overnight, so today started from an estimate.",
                color = Color.White.copy(alpha = 0.7f), style = GlowType.secondary, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Column(Modifier.weight(1f)) { HeroFigure(now?.toString(), if (now == null) ctx.waiting else "Now", big = true) }
                if (c != null && c.anchored) Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroFigure("+${c.recharged.toInt()}", "Recharged", horizontal = androidx.compose.ui.Alignment.End)
                    val drained = (c.drainedExercise + c.drainedStress + c.drainedLiving).toInt()
                    HeroFigure("−$drained", "Drained", horizontal = androidx.compose.ui.Alignment.End)
                }
            }
        }
    }
}

/** Your usual workout time: whether you're ready for effort, and a way to start the sport you usually do now. */
@Composable
private fun WindowHero(ctx: TodayContext, lightAngle: Float?, onStart: (Int) -> Unit, onOther: () -> Unit, modifier: Modifier) {
    val sport = ctx.usualSport
    val noun = sportNoun(sport)
    GlowCard(heroStyle(overviewColor(ctx.score, ctx.alert), lightAngle, strength = 0.85f), modifier) {
        Column {
            GlowHeader(TodayCopy.windowTitle(ctx.score), trailing = ctx.score?.let { "Recovery $it" })
            HeroSentence(TodayCopy.window(noun, ctx.charge))
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton("Start ${noun ?: Workouts.sportName(sport).lowercase()}", { onStart(sport) }, Modifier.weight(1f), icon = true)
                HeroSecondaryButton("Other sport", onOther)
            }
        }
    }
}

/**
 * After a workout: how long since, and the heart coming down, drawn from the readings since it ended. Calm light from
 * below.
 */
@Composable
private fun AfterHero(ctx: TodayContext, lightAngle: Float?, onOpenWorkout: (Long) -> Unit, modifier: Modifier) {
    val w = ctx.lastWorkout ?: return
    val since = ctx.state.heartRate.filter { it.x >= w.end }
    val nowHr = ctx.live ?: since.lastOrNull()?.y?.toInt()
    val noun = sportNoun(w.workout.sportType) ?: "workout"
    GlowCard(heroStyle(MetricColors.heart, lightAngle, strength = 0.45f), modifier, onClick = { onOpenWorkout(w.workout.start) }) {
        Column {
            GlowHeader("Since your $noun", trailing = TodayCopy.duration(ctx.now - w.end))
            HeroSentence(TodayCopy.after(nowHr, ctx.state.restingHeartRate))
            // The drop since the end, from the readings the strap has sent. A live curve needs live readings kept
            // through the calm down (step 7).
            if (since.size >= 2) {
                // About 40 points over whatever time has passed: per-minute slots leave gaps that draw as lone dots.
                val step = maxOf(60L, (ctx.now - w.end) / 40)
                val values = remember(since, w.end, step) { bucket(since, w.end..ctx.now, step) }
                Spacer(Modifier.height(14.dp))
                // The line drops into your resting range, a dim band, once it's known.
                GlowLineChart(values, height = 64.dp, showAverage = false, band = ctx.restingRange?.let { it.low..it.high })
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // No readings around the end give no end heart rate: leave the figure out rather than show a dash.
                w.endHeartRate?.let { HeroFigure(it.toString(), "At the end") }
                HeroFigure(nowHr?.toString(), "Now")
                HeroFigure(ctx.state.restingHeartRate?.toString(), "Resting", horizontal = androidx.compose.ui.Alignment.End)
            }
        }
    }
}

/** Evening and night: the day in a sentence, steps, Load and Charge left, and how long until your usual bedtime. */
@Composable
private fun EveningHero(ctx: TodayContext, lightAngle: Float?, bedtime: LocalTime?, onOpen: () -> Unit, modifier: Modifier) {
    val goals = LocalGoals.current
    val nouns = ctx.dayWorkouts.map { sportNoun(it.workout.sportType) }
    GlowCard(heroStyle(MetricColors.sleepDeep, lightAngle), modifier, onClick = onOpen) {
        Column {
            GlowHeader("Today", trailing = TodayCopy.bedtimeIn(LocalTime.now(), bedtime))
            HeroSentence(TodayCopy.evening(nouns, ctx.state.steps, goals.steps, ctx.loadStatus))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroFigure("%,d".format(ctx.state.steps), "Steps")
                HeroFigure(ctx.rec?.load?.let { Math.round(it).toString() }, "Load")
                HeroFigure(ctx.charge?.toString(), "Charge left", horizontal = androidx.compose.ui.Alignment.End)
            }
        }
    }
}

/** During a workout: the live heart rate, number first, and the way back to the live screen. */
@Composable
private fun DuringHero(
    ctx: TodayContext, r: org.openstrap.app.device.WorkoutState.Recording, onOpen: () -> Unit, onResume: () -> Unit, modifier: Modifier,
) {
    val live = ctx.live
    val style = GlowStyle.chart(MetricColors.heart).let { if (live != null) it.copy(motion = GlowMotion.Heartbeat(live)) else it }
    GlowCard(style, modifier, onClick = onOpen) {
        Column {
            GlowHeader(Workouts.sportName(r.workout.sportType))
            Spacer(Modifier.height(18.dp))
            HeroFigure(live?.toString(), if (live != null) "bpm, live" else "Waiting for heart rate", big = true)
            Spacer(Modifier.height(28.dp))
            WorkoutShortcut(r, live, onOpen, onResume)
        }
    }
}

/**
 * No strap paired: the hero is where pairing starts. TODO(step 8): the pairing stage itself, the strap and the line
 * to the phone with a sentence per step.
 */
@Composable
private fun PairHero(onPair: () -> Unit, modifier: Modifier) {
    GlowCard(heroStyle(Color(0xFF4E8F86), LightAngle.Below), modifier, onClick = onPair) {
        Column {
            GlowHeader("Pair your Helio Strap")
            HeroSentence("Put the strap on its charger, close to this phone.")
            Spacer(Modifier.height(28.dp))
            HeroButton("Find my strap", onPair)
        }
    }
}

/** The morning sync is failing and last night hasn't come across yet (spec 4.3). */
@Composable
private fun StuckHero(strap: TodayStrap, onSyncNow: () -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    GlowCard(heroStyle(MetricColors.warn, LightAngle.Below, strength = 0.5f), modifier) {
        Column {
            GlowHeader("Last night", trailing = strap.failedAt?.let { "Last tried ${clockAt(it)}" })
            HeroSentence("Last night is still on the strap.")
            Text(
                "Keep it near your phone and it comes across.",
                color = Color.White.copy(alpha = 0.8f), style = GlowType.secondary, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(28.dp))
            HeroButton("Sync now", { if (strap.canSync) onSyncNow() else haptics.perform(HapticEvent.SyncFailed) })
        }
    }
}

/* ----------------------------------------------- Rows ----------------------------------------------- */

@Composable
private fun TodayRow(
    slot: TodaySlot, ctx: TodayContext, healthspan: org.openstrap.app.data.Healthspan.Outcome?, history: DailyHistory?,
    checkIn: Int?, onCheckIn: (Int?) -> Unit, onOpenSleep: () -> Unit, onOpenMetric: (Metric) -> Unit, onOpenRecovery: () -> Unit,
    onOpenWorkout: (Long) -> Unit, onOpenHealthspan: () -> Unit, onOpenProfile: () -> Unit,
    offer: org.openstrap.app.data.AutoStart.Offer?, onRecordOffer: () -> Unit, onDeclineOffer: () -> Unit,
    suggestion: org.openstrap.app.data.WorkoutSuggestions.Suggestion?, onSaveSuggestion: () -> Unit, onDismissSuggestion: () -> Unit,
    /** The size a Small takes in its row. */
    modifier: Modifier = Modifier,
) {
    val s = ctx.state
    val b = s.baselines
    when (slot.card) {
        TodayCard.Offer -> offer?.let { OfferPanel(it, onRecordOffer, onDeclineOffer, edge = EdgeKind.Live) }
        TodayCard.Suggestion -> suggestion?.let { SuggestionPanel(it, onSaveSuggestion, onDismissSuggestion, edge = EdgeKind.Earlier) }
        TodayCard.Alerts -> AlertsStrip(s.alerts, onOpenMetric)
        TodayCard.CheckIn -> CheckInStrip(checkIn, onCheckIn)
        TodayCard.BodyMorning -> BodyList(
            "Your body this morning",
            listOf(
                BodyRow("Resting heart rate", MetricColors.heart, s.restingHeartRate?.toFloat(), b.restingHr, 1.5f, "", Metric.RestingHr),
                BodyRow("HRV", MetricColors.hrv, s.hrv.avg()?.toFloat(), b.hrv, 3f, "", Metric.Hrv),
                BodyRow("Blood oxygen", MetricColors.spo2, s.spo2.avg()?.toFloat(), b.spo2, 1f, "%", Metric.Spo2),
                BodyRow("Breathing rate", MetricColors.sleepLight, s.breathRate.avg()?.toFloat(), b.breathRate, 1f, "", Metric.Breathing),
            ),
            ctx.waiting, onOpenMetric,
        )
        TodayCard.RightNow -> BodyList(
            "Right now",
            listOf(
                BodyRow("Stress", MetricColors.stress, s.stress.lastOrNull()?.y, b.stress, 4f, "", Metric.Stress),
                BodyRow("HRV", MetricColors.hrv, s.hrv.lastOrNull()?.y, b.hrv, 3f, "", Metric.Hrv),
            ),
            ctx.waiting, onOpenMetric,
        )
        TodayCard.LastNight ->
            if (slot.size == CardSize.Strip) LastNightStrip(s, onOpenSleep) else LastNightCard(s, ctx.firstNights, onOpenSleep)
        TodayCard.HeartRate -> HeartCard(s, ctx.live, { onOpenMetric(Metric.HeartRate) }, restingRange = ctx.restingRange)
        TodayCard.Activity -> ActivityCard(s, { onOpenMetric(Metric.Steps) })
        TodayCard.Recovery -> RecoveryEarlierCard(ctx.recoveryShown, onOpenRecovery)
        TodayCard.Charge -> {
            val c = ctx.rec?.charge
            val used = c?.atWake?.let { w -> c.latest?.let { (w - it).toInt() } }
            val status = when {
                ctx.charge == null -> ctx.waiting
                ctx.endOfDay -> "Sleep fills it back"
                used != null && used > 0 -> "Used $used since waking"
                else -> "Steady since waking"
            }
            // The day's Charge so far, beside the number, until the evening says sleep fills it back.
            val soFar = c?.takeIf { !ctx.endOfDay && it.anchored && ctx.isToday }?.let { chargeSoFar(it.points, s.dayStart..s.dayEnd, ctx.now) }
            ChargeWideCard(ctx.charge, status, slot.secondGlow, onOpenRecovery, soFar = soFar)
        }
        // After a workout, what it did as a Small pair: Load toward your usual (from below) and time to recover.
        TodayCard.LoadToday -> if (slot.size == CardSize.Small) SmallCard(
            MetricColors.load, "Load", ctx.rec?.load?.let { Math.round(it).toString() }, null,
            if (ctx.rec == null) ctx.waiting else TodayCopy.loadWord(ctx.loadStatus), modifier, kind = EdgeKind.Progress,
            edgeStatus = if (ctx.loadStatus == TodayCopy.LoadStatus.Above) EdgeStatus.Above else EdgeStatus.Usual,
        ) else LoadList(
            when (ctx.moment) {
                Moment.WorkoutWindow -> "Before you start"
                Moment.After -> "What the ${ctx.lastWorkout?.let { sportNoun(it.workout.sportType) } ?: "workout"} did"
                else -> "Load today"
            },
            ctx.rec?.load, TodayCopy.loadLine(ctx.loadStatus), ctx.recover,
        )
        TodayCard.Recover -> {
            val left = ctx.workouts.filter { it.recoveryMinutes != null }.maxByOrNull { it.end }
                ?.let { w -> TodayCopy.recoverShort(TodayCopy.recoverLeft(w.end, w.recoveryMinutes ?: 0, ctx.now), ctx.now, ctx.zone) }
            SmallCard(MetricColors.readinessDeep, "To recover", left?.first, "h", left?.second ?: "No estimate from the strap", modifier)
        }
        TodayCard.Workout -> ctx.lastWorkout?.let { w -> WorkoutCard(w, { onOpenWorkout(w.workout.start) }) }
        TodayCard.Week -> WeekCard(s, ctx.workouts, history)
        // Habit Age's home is the foot of Recovery; Today shows the same card when there's a new estimate, or pinned.
        TodayCard.HabitAge -> HabitAgeEdgeCard(healthspan, onOpenHealthspan, onOpenProfile)
        TodayCard.Hero, TodayCard.EditToday -> Unit
    }
}

/* ---------------------------------------------- Helpers --------------------------------------------- */

/** The day's Charge in quarter hours across [day], up to [now]; the quarters still to come are null. */
internal fun chargeSoFar(points: List<Recovery.ChargePoint>, day: LongRange, now: Long): List<Float?> {
    val step = 15 * 60L
    val n = ((day.last - day.first + 1) / step).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach { val i = ((it.t - day.first) / step).toInt(); if (i in 0 until n && it.t <= now) { sums[i] += it.value; counts[i]++ } }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/** Averages timed points into evenly spaced buckets across [range]; empty buckets are null (gaps). */
internal fun bucket(points: List<ChartPoint>, range: LongRange, seconds: Long): List<Float?> {
    val n = ((range.last - range.first + 1) / seconds).toInt().coerceAtLeast(2)
    val sums = FloatArray(n)
    val counts = IntArray(n)
    points.forEach {
        val i = ((it.x - range.first) / seconds).toInt()
        if (i in 0 until n) { sums[i] += it.y; counts[i]++ }
    }
    return List(n) { if (counts[it] == 0) null else sums[it] / counts[it] }
}

/**
 * A Recovery card's colour tells how the day looks before you read a number: teal when the score is good, amber when
 * it's middling or there are signs of strain, coral when it's low. With no score at all (still learning) it's a muted
 * teal: Recovery waiting, not grey "no data".
 */
internal fun overviewColor(score: Int?, strain: Boolean) = when {
    score == null -> Color(0xFF4E8F86)
    strain -> Color(0xFFF2A33A)
    score >= 70 -> MetricColors.hrv
    score >= 50 -> Color(0xFFF2A33A)
    else -> Color(0xFFF0605A)
}
