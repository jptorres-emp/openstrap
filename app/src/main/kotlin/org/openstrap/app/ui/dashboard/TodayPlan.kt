package org.openstrap.app.ui.dashboard

import org.openstrap.app.data.Moment
import org.openstrap.app.ui.glow.CardSize

/*
 * Which cards Today shows for a moment, in what order and at what size (spec 4.2), and how Edit Today changes that.
 * Plain functions, so every moment's layout and the pin and hide rules are tested without a screen.
 */

/**
 * A card on Today. [label] names it in Edit Today. Only [editable] cards can be hidden, only [pinnable] ones pinned
 * under the hero: those that make sense at any time of day.
 */
enum class TodayCard(val label: String, val editable: Boolean = true, val pinnable: Boolean = false) {
    Hero("", editable = false),
    Offer("Workout under way", editable = false),
    Suggestion("Workout to save", editable = false),
    Alerts("From your strap", editable = false),
    CheckIn("How do you feel"),
    BodyMorning("Your body this morning"),
    RightNow("Right now", pinnable = true),
    LastNight("Last night", pinnable = true),
    HeartRate("Heart rate", pinnable = true),
    Activity("Activity", pinnable = true),
    Recovery("Recovery", pinnable = true),
    Charge("Charge", pinnable = true),
    LoadToday("Load and time to recover", pinnable = true),
    Workout("The workout"),
    Week("Your week", pinnable = true),
    HabitAge("Habit Age", pinnable = true),
    EditToday("", editable = false),
}

/** A card in Today's list: its [size] on the grid, and whether it's the screen's [secondGlow] (spec 2.4). */
data class TodaySlot(val card: TodayCard, val size: CardSize, val secondGlow: Boolean = false)

/** What Today has to show beyond the moment's own cards; each false leaves its card out. */
data class TodayFacts(
    val isToday: Boolean = true,
    val offer: Boolean = false,
    val suggestion: Boolean = false,
    val alerts: Boolean = false,
    /** A finished workout to show after it (the after-workout moment). */
    val workout: Boolean = false,
    val healthspan: Boolean = false,
)

/** The moment Today is laid out for: a past day always shows how it began (spec 4.1). */
fun layoutMoment(moment: Moment?, isToday: Boolean): Moment? = if (!isToday) Moment.Waking else moment

/**
 * Today's cards for [moment], in order (spec 4.2). A null moment (today's recovery still loading) lays out like
 * waking before noon and like midday after, as Today did before moments, so nothing flashes when it arrives.
 * [morning] picks between those two.
 */
fun todayCards(moment: Moment?, facts: TodayFacts, morning: Boolean = true): List<TodaySlot> {
    val m = moment ?: if (morning) Moment.Waking else Moment.Midday
    val wide = { c: TodayCard -> TodaySlot(c, CardSize.Wide) }
    val strip = { c: TodayCard -> TodaySlot(c, CardSize.Strip) }
    val list = { c: TodayCard -> TodaySlot(c, CardSize.List) }
    val own = when (m) {
        Moment.Waking -> listOfNotNull(
            strip(TodayCard.CheckIn).takeIf { facts.isToday },
            list(TodayCard.BodyMorning), wide(TodayCard.LastNight), wide(TodayCard.HeartRate), wide(TodayCard.Activity),
        )
        // During a workout the hero is the workout, and the rest stays as the day was.
        Moment.Midday, Moment.During -> listOf(
            strip(TodayCard.LastNight), wide(TodayCard.Activity), wide(TodayCard.HeartRate), list(TodayCard.RightNow),
            wide(TodayCard.Recovery),
        )
        Moment.WorkoutWindow -> listOf(
            TodaySlot(TodayCard.Charge, CardSize.Wide, secondGlow = true), list(TodayCard.LoadToday), wide(TodayCard.Activity),
            strip(TodayCard.LastNight),
        )
        Moment.After -> listOfNotNull(
            wide(TodayCard.Workout).takeIf { facts.workout }, TodaySlot(TodayCard.Charge, CardSize.Wide, secondGlow = true),
            list(TodayCard.LoadToday), wide(TodayCard.Activity),
        )
        Moment.Evening, Moment.Night -> listOf(wide(TodayCard.Charge), wide(TodayCard.Activity), strip(TodayCard.LastNight))
    }
    // Things that need an answer come straight under the hero; the week and Habit Age close the list.
    return listOfNotNull(
        TodaySlot(TodayCard.Hero, CardSize.Large),
        strip(TodayCard.Offer).takeIf { facts.offer && facts.isToday },
        strip(TodayCard.Suggestion).takeIf { facts.suggestion && facts.isToday },
        strip(TodayCard.Alerts).takeIf { facts.alerts },
    ) + own + listOfNotNull(
        wide(TodayCard.Week),
        wide(TodayCard.HabitAge).takeIf { facts.healthspan },
        strip(TodayCard.EditToday),
    )
}

/** The card the hero of [moment] already is, so pinning it there would show it twice. */
fun heroSubject(moment: Moment?): TodayCard? = when (moment) {
    Moment.Waking -> TodayCard.Recovery
    Moment.Midday -> TodayCard.Charge
    else -> null
}

/** The size a pinned card takes in a moment that doesn't have it. */
private fun pinnedSize(card: TodayCard): CardSize = when (card) {
    TodayCard.RightNow, TodayCard.LoadToday -> CardSize.List
    else -> CardSize.Wide
}

/** What Edit Today changed: cards [hidden], and up to [MAX_PINS] [pinned] under the hero, first pinned first. */
data class TodayEdits(val hidden: Set<TodayCard> = emptySet(), val pinned: List<TodayCard> = emptyList()) {
    /** Pins [card], or unpins it. A third pin does nothing, and a pinned card is never hidden. */
    fun togglePin(card: TodayCard): TodayEdits = when {
        card in pinned -> copy(pinned = pinned - card)
        !card.pinnable || pinned.size >= MAX_PINS -> this
        else -> TodayEdits(hidden - card, pinned + card)
    }

    /** Hides [card], or shows it again. Hiding a pinned card unpins it. */
    fun toggleHidden(card: TodayCard): TodayEdits = when {
        card in hidden -> copy(hidden = hidden - card)
        !card.editable -> this
        else -> TodayEdits(hidden + card, pinned - card)
    }

    fun canPin(card: TodayCard) = card.pinnable && (card in pinned || pinned.size < MAX_PINS)

    companion object {
        const val MAX_PINS = 2

        /** From the names the settings store keeps; unknown names (a card since renamed) drop out. */
        fun parse(hidden: List<String>?, pinned: List<String>?): TodayEdits {
            fun cards(names: List<String>?) = names.orEmpty().mapNotNull { n -> TodayCard.entries.firstOrNull { it.name == n } }
            val pins = cards(pinned).filter { it.pinnable }.distinct().take(MAX_PINS)
            return TodayEdits(cards(hidden).filter { it.editable && it !in pins }.toSet(), pins)
        }
    }
}

/**
 * [slots] with the edits applied: hidden cards gone, pinned ones moved under the hero (after anything that needs an
 * answer), in pin order. A pinned card the moment doesn't have is added; one the hero already shows is left where
 * it is.
 */
fun applyEdits(slots: List<TodaySlot>, edits: TodayEdits, moment: Moment?): List<TodaySlot> {
    val pins = edits.pinned.filter { it != heroSubject(moment) }
    val pinned = pins.map { card -> slots.firstOrNull { it.card == card } ?: TodaySlot(card, pinnedSize(card)) }
    val rest = slots.filter { it.card !in edits.hidden && it.card !in pins }
    val urgent = setOf(TodayCard.Hero, TodayCard.Offer, TodayCard.Suggestion, TodayCard.Alerts)
    val at = rest.indexOfFirst { it.card !in urgent }.let { if (it < 0) rest.size else it }
    return rest.take(at) + pinned + rest.drop(at)
}

/**
 * The sport you usually do: the most frequent in [workouts] (sport, start), ties to the latest. Null without any.
 * Callers pass workouts you started, so a walk the strap noticed doesn't count.
 */
fun usualSport(workouts: List<Pair<Int, Long>>): Int? =
    workouts.groupBy { it.first }
        .maxWithOrNull(compareBy<Map.Entry<Int, List<Pair<Int, Long>>>> { it.value.size }.thenBy { e -> e.value.maxOf { it.second } })
        ?.key

/**
 * Whether the hero says last night is still on the strap (spec 4.3): on today's waking moment, with no sleep yet and the
 * last sync failed. The spec's other trigger, failing for 3 hours, needs a time of the last good sync the app doesn't
 * keep yet.
 */
fun syncStuck(moment: Moment?, isToday: Boolean, hasSleep: Boolean, syncFailed: Boolean): Boolean =
    isToday && moment == Moment.Waking && !hasSleep && syncFailed
