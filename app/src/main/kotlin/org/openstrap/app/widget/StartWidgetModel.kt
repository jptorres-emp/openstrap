package org.openstrap.app.widget

import org.openstrap.app.R
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.TimeOfDay
import org.openstrap.app.presentation.sportNoun
import org.openstrap.app.data.Weeks
import org.openstrap.app.presentation.topSports
import org.openstrap.app.presentation.WorkoutCopy
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.theme.TypeScale

/**
 * A sport on the Start widget. [sportType] is what a tap hands Ready (`EXTRA_READY_SPORT`), which then opens on the
 * side you last chose for it; [shownType] is that side's own sport, which the icon is of. Both sides of a run or a ride
 * have one name ("Run"), as everywhere in the app.
 */
data class StartSport(
    val sportType: Int,
    val shownType: Int,
    /** In the usual row, Body 500: "Run". */
    val name: String,
    /** The picker's short name ("Run", "Walk", "Strength"): the name when the full one won't fit a line. */
    val shortName: String,
    /**
     * Under its tile, one line: the short name, or the full one where the short one would read as the usual or a
     * sport before it. [StartWidgetModel.from] settles it against the others.
     */
    val caption: String,
    /** What TalkBack reads for its tile and play: "Start run". */
    val action: String,
) {
    companion object {
        /** [type] as Ready opens it with the [sides] you last chose. */
        fun of(type: Int, sides: Map<Int, Boolean>): StartSport {
            val indoor = Workouts.indoorSide(type, sides)
            val shown = Workouts.sidesOf(type)?.type(indoor == true) ?: type
            return named(type, shown)
        }

        /** A workout's own [type], with no side to choose: the one recording. */
        fun recorded(type: Int): StartSport = named(type, type)

        private fun named(type: Int, shown: Int) = StartSport(
            sportType = type,
            shownType = shown,
            name = Workouts.sportName(shown),
            // The picker's search names tiles so: everyday sports by their short name, the rest in full.
            shortName = Workouts.shortName(shown),
            caption = Workouts.shortName(shown),
            action = WorkoutCopy.startLabel(shown),
        )
    }
}

/**
 * The workout recording, as the widget reads it from the session: its sport type, its start (epoch seconds), its
 * active seconds so far (pauses left out, as the live screen counts) and whether it's paused.
 */
data class StartRecordingInput(
    val sportType: Int, val start: Long, val activeSeconds: Long, val paused: Boolean,
    /** The live heart rate and its zone (0 to 4) while it's known and the workout isn't paused. */
    val bpm: Int? = null, val zone: Int? = null,
    /** Resting between sets or intervals, or asked whether it ended; [paused] wins over both. */
    val resting: Boolean = false, val asking: Boolean = false,
    /** The Large's figures so far, value over label, the sport's own (workout-rules.md, section 7, rule 6). */
    val figures: List<Pair<String, String>> = emptyList(),
)

/**
 * What a recording card is lit in and says under the sport (workout-rules.md, section 7, rules 2 and 3): the sport's
 * colour while it records, and the three states that replace it.
 */
enum class RecordingState { Recording, Paused, Rest, Asking }

/**
 * A workout under way, which every size shows in place of the usual: the sport, "Recording" (or "Paused"), "Since
 * 11:58" and a live timer of the active time, [activeMillis] at [atMillis] (epoch ms), standing still while [paused].
 */
data class StartRecording(
    val sport: StartSport,
    val since: String,
    val status: String,
    val activeMillis: Long,
    val atMillis: Long,
    val paused: Boolean,
    /** "152 bpm" and "Zone 4" with its [zone] (0 to 4) while the heart rate is known; null otherwise. */
    val heart: String? = null,
    val zoneLabel: String? = null,
    val zone: Int? = null,
    val state: RecordingState = if (paused) RecordingState.Paused else RecordingState.Recording,
    /** The Large's figures so far, value over label; empty shows the week's instead. */
    val figures: List<Pair<String, String>> = emptyList(),
) {
    companion object {
        val RECORDING: String get() = Strings.app.get(R.string.system_widget_recording)
        val PAUSED: String get() = Strings.app.get(R.string.system_widget_paused)

        /**
         * The Chronometer's base on the [elapsedRealtime] clock (ms) for [r] at [nowMillis] (epoch ms): the active
         * time so far back from now, so it reads the workout's time and ticks on its own. Paused, the time at the
         * pause, and the Chronometer is stopped.
         */
        fun chronometerBase(elapsedRealtime: Long, nowMillis: Long, r: StartRecording): Long =
            elapsedRealtime - (r.activeMillis + if (r.paused) 0L else (nowMillis - r.atMillis).coerceAtLeast(0L))
    }
}

/**
 * What the Start widget shows (widgets spec 3.5), the same model at every size: the Small shows [usual] with "Your
 * usual", the Wide adds [usualLine] and the [next] sports as tiles, the Large adds this week and the usual sport's last.
 * While [recording], every size shows that instead, with Open in place of play.
 */
data class StartWidgetModel(
    val usual: StartSport,
    /** The Wide's and Large's line under the name: "Your usual, GPS on". */
    val usualLine: String,
    /** GPS is on for the usual: the line carries the location mark. */
    val gpsOn: Boolean,
    /** The next sports by use, more than four: the layout shows the first four whose captions fit ([StartLayout.tiles]). */
    val next: List<StartSport>,
    /** "2 workouts, 1 h 35". */
    val weekLine: String,
    /** "Last run", and its "Tuesday, 5.2 km". */
    val lastLabel: String,
    val lastLine: String,
    val recording: StartRecording?,
) {
    companion object {
        val USUAL: String get() = Strings.app.get(R.string.system_widget_start_usual)
        val ALL_SPORTS: String get() = Strings.app.get(R.string.system_widget_start_all_sports)
        val ALL_SPORTS_ACTION: String get() = Strings.app.get(R.string.system_widget_start_all_sports_action)
        val THIS_WEEK: String get() = Strings.app.get(R.string.system_widget_this_week)
        val OPEN: String get() = Strings.app.get(R.string.system_widget_open)
        /** The Wide's and Large's title row, only while the strap status shows (spec 5): the picker's name. */
        val TITLE: String get() = Strings.app.get(R.string.widget_start_name)
        val NO_WORKOUTS: String get() = Strings.app.get(R.string.system_widget_start_no_workouts)
        val NONE_YET: String get() = Strings.app.get(R.string.system_widget_start_none_yet)

        /** Other's sport type, whose last workout is "Last workout". */
        private const val OTHER = 5

        /** How many sports by use to offer before the everyday ones pad the list. */
        const val BY_USE = 8

        /**
         * The widget from every listed workout ([Workouts.listed]), the [sides] last chosen per sport on Ready, whether
         * the phone's [location] can be used now and the workout [recording], at [now].
         * The usual sport and the next ones are [topSports]' order, then [Workouts.EVERYDAY]'s once those run out
         * ([Workouts.DEFAULT_SPORTS] lead it before there's a history), each once as Ready would open it.
         */
        fun from(
            workouts: List<WorkoutAnalysis>, sides: Map<Int, Boolean>, location: Boolean, recording: StartRecordingInput?,
            now: Instant, zone: ZoneId, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app,
        ): StartWidgetModel {
            val sports = (topSports(workouts, BY_USE).map { it.sportType } + Workouts.EVERYDAY)
                .map { StartSport.of(it, sides) }
                // One tile a sport: a run is one sport on either side, as Ready opens it.
                .distinctBy { Workouts.tileType(it.shownType) }
            val usual = sports.first()
            // A caption says which sport it is: the short name unless the usual or a tile before it already has it.
            val said = mutableSetOf(usual.shortName)
            val next = sports.drop(1).map { sp -> if (said.add(sp.shortName)) sp else sp.copy(caption = sp.name).also { said += sp.name } }
            val gpsOffered = Workouts.gpsOffered(usual.sportType, Workouts.indoorSide(usual.sportType, sides))
            val today = now.atZone(zone).toLocalDate()
            val weekStart = Weeks.start(today, Weeks.firstDay(locale)).atStartOfDay(zone).toEpochSecond()
            // As Today and Your week count them: the strap's own "Activity" records aren't workouts.
            val week = workouts.filter {
                it.workout.sportType != Workouts.GENERIC_ACTIVITY && it.workout.start in weekStart..now.epochSecond
            }
            val last = workouts.filter { Workouts.tileType(it.workout.sportType) == Workouts.tileType(usual.sportType) && it.workout.start <= now.epochSecond }
                .maxByOrNull { it.workout.start }
            return StartWidgetModel(
                usual = usual,
                usualLine = strings.get(
                    when {
                        gpsOffered && location -> R.string.system_widget_start_usual_gps_on
                        gpsOffered -> R.string.system_widget_start_usual_gps_off
                        else -> R.string.system_widget_start_usual
                    },
                ),
                gpsOn = gpsOffered && location,
                next = next,
                weekLine = weekLine(week.size, week.sumOf { it.activeSeconds }, strings),
                lastLabel = lastLabel(usual.sportType, strings),
                lastLine = last?.let { a ->
                    val day = recentDay(Instant.ofEpochSecond(a.workout.start).atZone(zone).toLocalDate(), today, locale)
                    strings.format(R.string.system_widget_start_last_line, day, fact(a, locale, strings))
                } ?: strings.get(R.string.system_widget_start_none_yet),
                recording = recording?.let { r ->
                    val state = recordingState(r)
                    StartRecording(
                        StartSport.recorded(r.sportType),
                        strings.format(R.string.system_widget_since, TimeOfDay.format(r.start, zone, locale = locale)),
                        strings.get(statusWord(state)),
                        r.activeSeconds * 1000L, now.toEpochMilli(), r.paused,
                        heart = r.bpm?.let { strings.format(R.string.core_notif_bpm, it) },
                        zoneLabel = r.zone?.let { strings.format(R.string.core_workout_zone_number, it + 1) },
                        zone = r.zone,
                        state = state,
                        figures = r.figures,
                    )
                },
            )
        }

        /** The state a recording card shows: paused wins, then the stop question, then rest. */
        private fun recordingState(r: StartRecordingInput): RecordingState = when {
            r.paused -> RecordingState.Paused
            r.asking -> RecordingState.Asking
            r.resting -> RecordingState.Rest
            else -> RecordingState.Recording
        }

        /** The word under the sport for [state]. */
        private fun statusWord(state: RecordingState): Int = when (state) {
            RecordingState.Paused -> R.string.system_widget_paused
            RecordingState.Rest -> R.string.system_widget_rest
            RecordingState.Asking -> R.string.system_widget_still_going
            RecordingState.Recording -> R.string.system_widget_recording
        }

        /** "2 workouts, 1 h 35", "1 workout, 40 min", "No workouts yet". */
        fun weekLine(count: Int, seconds: Long, strings: Strings = Strings.app): String =
            if (count == 0) strings.get(R.string.system_widget_start_no_workouts)
            else strings.plural(R.plurals.system_widget_start_week, count, count, Units.duration(seconds))

        /** "Last run" for a run outdoors or on a treadmill, "Last row", "Last HIIT", and "Last workout" for Other. */
        fun lastLabel(sportType: Int, strings: Strings = Strings.app): String {
            val tile = Workouts.tileType(sportType)
            if (tile == OTHER) return strings.get(R.string.system_widget_start_last_workout)
            // A noun where the sport has one ("Last row", "Last ride"), else its short name ("Last HIIT").
            val name = sportNoun(sportType, strings) ?: Workouts.shortName(tile)
            return strings.format(R.string.system_widget_start_last, if (name.any { it.isLowerCase() }) name.lowercase() else name)
        }

        /** A workout's one figure here: its distance ("5.2 km"), else its sets ("4 sets"), else how long it was. */
        fun fact(a: WorkoutAnalysis, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
            a.distanceMeters?.let { Units.distance(it, Units.Precision.Day, a.distanceFromSteps, locale) }?.let { return it }
            a.sets?.takeIf { it > 0 }?.let { return strings.plural(R.plurals.system_widget_start_sets, it, it) }
            return Units.duration(a.activeSeconds)
        }
    }
}


/**
 * The sums that fit the Start widget into its real cell at any system font size (spec 2.1). Nothing on this widget
 * wraps (the user's call, 2026-10-25): every text is one line, and what doesn't fit takes a shorter form (the sport's
 * short name, "Since 17:42"), a smaller caption, or gives its place. Widths and heights in dp at the content size
 * ([WidgetFrame.contentWidth]), text with [WidgetText]'s rough sums, which err wide.
 */
object StartLayout {
    /** The usual's tile and the play button (spec 3.5), and the next sports' tiles. */
    const val TILE_DP = 44f
    const val TILE_CORNER_DP = 16f
    const val SMALL_TILE_DP = 38f
    const val SMALL_TILE_CORNER_DP = 12f
    const val PLAY_DP = 44f
    const val ICON_DP = 20f
    const val PLAY_ICON_DP = 18f

    /** The 12 dp location mark after "GPS on", and the room before it. */
    const val MARK_DP = 12f
    const val MARK_GAP_DP = 4f

    /** Open's sides, either side of its word. */
    const val OPEN_PAD_DP = 12f

    /** Open's pill height, a short pill however tall the row (board HaloWidgetsWorkout: 28 high, never a circle). */
    const val OPEN_HEIGHT_DP = 30f

    /** Between the usual row's tile, words and play (the mockup's), and the Small's words and play. */
    const val ROW_GAP_DP = 12f
    const val SMALL_GAP_DP = 8f

    /** Between the tile and play on the Small with a status. */
    const val STATUS_PLAY_GAP_DP = 12f

    /** Between a tile and its caption ([FIGURE_LINE_GAP_DP] is a figure's Caption to its Body). */
    const val CAPTION_GAP_DP = 4f

    /** Between the two figures side by side, and between captions in the tile row. */
    const val FIGURE_GAP_DP = 12f
    const val CAPTION_SPACE_DP = 4f

    /** Between the figures' rows. */
    const val FIGURE_ROW_GAP_DP = 8f

    /** The least room between the blocks that share out the spare height. */
    const val MIN_GAP_DP = WidgetGrid.GAP_DP

    /** The tile row at its fullest: four sports and All sports. */
    const val COLUMNS = 5

    /** Captions at the Caption role's 11 sp, then a touch smaller before a sport gives its place. */
    val CAPTION_SIZES = listOf(WidgetText.CAPTION_SP, 10f)


    /** Open's width at [fontScale]: its word in Body 500 with [OPEN_PAD_DP] each side. */
    fun openWidth(fontScale: Float): Float = WidgetText.width(StartWidgetModel.OPEN, WidgetText.BUTTON_SP, fontScale) + 2 * OPEN_PAD_DP

    /**
     * The Wide's or Large's top row (spec 5): "Start a workout" with the status button, and its words when the strap
     * needs something, at the right. Always there, as in every widget, whatever the strap says.
     */
    fun statusTop(line: StatusLine?, size: androidx.compose.ui.unit.DpSize, width: Float, fontScale: Float): TopRow =
        TopRow.of(StartWidgetModel.TITLE, null, line, width, fontScale, withWords = size != WidgetSizes.Small)

    /** Whether [text] fits [room] dp on one line at [sp]. */
    fun fits(text: String, sp: Float, room: Float, fontScale: Float): Boolean = WidgetText.width(text, sp, fontScale) <= room

    private fun body(fontScale: Float) = WidgetText.line(WidgetText.BODY_SP, fontScale)
    private fun secondary(fontScale: Float) = WidgetText.line(WidgetText.SECONDARY_SP, fontScale)
    private fun caption(fontScale: Float, sp: Float = WidgetText.CAPTION_SP) = WidgetText.line(sp, fontScale)

    /** The words beside or under the usual's tile: the [name] and [line] shown (null for no line), and the mark. */
    data class Words(val name: String, val line: String?, val mark: Boolean)

    /**
     * The usual's tile, the play button (Open's height while recording) and the sport tiles, dp. A cell with height to
     * spare grows them toward the mockup's (a 48 dp usual tile, play up to 52, sport tiles up to 44) before the rest
     * goes to the gaps, so a tall cell reads as one block rather than a hole.
     */
    data class Sizes(val tile: Float, val play: Float, val sportTile: Float) {
        val tileCorner: Float get() = TILE_CORNER_DP * tile / TILE_DP
        val sportCorner: Float get() = SMALL_TILE_CORNER_DP * sportTile / SMALL_TILE_DP
        val icon: Float get() = (tile * ICON_DP / TILE_DP).coerceIn(ICON_DP, 24f)
        val sportIcon: Float get() = (sportTile * ICON_DP / SMALL_TILE_DP).coerceIn(16f, 24f)
        val playIcon: Float get() = play * PLAY_ICON_DP / PLAY_DP

        /** What sits at the end of the usual row: play, or Open [OPEN_PAD_DP] either side of its word. */
        fun action(recording: Boolean, fontScale: Float): Float = if (recording) openWidth(fontScale) else play
    }

    /**
     * The Wide's and Large's one size (the grid: one size a bucket, never grown to fill a cell): a 40 dp tile and play,
     * 32 dp sport tiles. The Wide's 120 dp of content holds the top row, this row and bare tiles exactly.
     */
    val ROW_SIZES = listOf(Sizes(40f, 40f, 32f))

    /** The idle Small's tile and play: 40 dp each, side by side under the top row. */
    val GRID_SMALL = Sizes(40f, 40f, 0f)

    /** The Small's tile and play, largest first. */
    val SMALL_SIZES = listOf(Sizes(56f, 52f, 0f), Sizes(48f, 48f, 0f), Sizes(TILE_DP, PLAY_DP, 0f))

    /**
     * The Small: [actionTop] puts play (or Open) in the top row beside the tile instead of at the foot beside the
     * words, and [tile] is false when even the top row can't hold the tile beside Open.
     */
    data class Small(val actionTop: Boolean, val tile: Boolean, val words: Words, val sizes: Sizes) {
        /** How many gaps share the spare height: above, between and below the two blocks. */
        val gaps: Int get() = 3
    }

    /** The Small's two blocks' height: the top (tile, or tile and action) and the foot. */
    fun smallHeight(s: Small, fontScale: Float): Float {
        val words = body(fontScale) + (if (s.words.line != null) secondary(fontScale) else 0f)
        return if (s.actionTop) maxOf(s.sizes.tile, s.sizes.play) + words else s.sizes.tile + maxOf(s.sizes.play, words)
    }

    /**
     * The Small in [width] by [height] dp. The mockup's layout when the full name fits beside play at the foot: tile
     * at the top, the name over [lines]'s first that fits and play at the foot. Else play goes up beside the tile and
     * the words take the whole width. Each at the largest [SMALL_SIZES] that fits, then the same with the short name.
     * Every text one line.
     */
    fun small(sport: StartSport, lines: List<String>, recording: Boolean, width: Float, height: Float, fontScale: Float): Small {
        val words = body(fontScale) + secondary(fontScale)
        for (name in listOf(sport.name, sport.shortName).distinct()) {
            for (sz in SMALL_SIZES) {
                val action = sz.action(recording, fontScale)
                val beside = width - action - SMALL_GAP_DP
                val line = lines.firstOrNull { fits(it, WidgetText.SECONDARY_SP, beside, fontScale) } ?: continue
                if (fits(name, WidgetText.BODY_SP, beside, fontScale) && sz.tile + MIN_GAP_DP + maxOf(sz.play, words) <= height) {
                    return Small(actionTop = false, tile = true, words = Words(name, line, false), sizes = sz)
                }
            }
            if (!fits(name, WidgetText.BODY_SP, width, fontScale)) continue
            val line = lines.firstOrNull { fits(it, WidgetText.SECONDARY_SP, width, fontScale) }
            for (sz in SMALL_SIZES) {
                val action = sz.action(recording, fontScale)
                if (sz.tile + SMALL_GAP_DP + action > width) continue
                if (maxOf(sz.tile, sz.play) + MIN_GAP_DP + words <= height) return Small(true, true, Words(name, line, false), sz)
            }
            val sz = SMALL_SIZES.last()
            val tile = sz.tile + SMALL_GAP_DP + sz.action(recording, fontScale) <= width
            val fitsLine = maxOf(sz.tile, sz.play) + MIN_GAP_DP + words <= height
            return Small(true, tile, Words(name, line?.takeIf { fitsLine }, false), sz)
        }
        return Small(true, false, Words(sport.shortName, null, false), SMALL_SIZES.last())
    }

    /**
     * The Small while the strap status shows (spec 5), laid out as the other widgets' Smalls: the [top] row (the
     * [title] at the left, null when it gave way, and the status button at the top right), then the usual's tile with
     * play right beside it at [sizes], then the [words]; [spread] shares the spare height, half a gap above and below.
     */
    data class StatusSmall(val title: String?, val top: TopRow, val sizes: Sizes, val words: Words, val spread: Spread)

    /** The status Small's title: the picker's name, or "Start" when that won't fit beside the button. */
    val SHORT_TITLE: String get() = Strings.app.get(R.string.system_widget_start_short_title)

    /** The status Small's tile and play below [SMALL_SIZES]' last, for the smallest cells at the largest fonts. */
    val STATUS_SHRINK = listOf(Sizes(40f, 40f, 0f), Sizes(36f, 36f, 0f))

    /** The least gap in the status Small when it's short of room. */
    const val STATUS_MIN_GAP_DP = WidgetGrid.GAP_DP

    /**
     * The Small in [width] by [height] dp with the status [line]: the top row, the tile and play, the name over "Your
     * usual". The tile and play grow from 44 dp on a tall cell until no gap passes [Spread.TARGET_GAP_DP]. Short of
     * room "Your usual" goes, then the title (the button stays), then the tile and play shrink. The full name, else
     * the short one; one line each.
     */
    fun statusSmall(sport: StartSport, line: StatusLine, width: Float, height: Float, fontScale: Float): StatusSmall {
        val beside = StatusButton.SIZE_DP + StatusButton.TITLE_GAP_DP
        val title = listOf(StartWidgetModel.TITLE, SHORT_TITLE).firstOrNull { WidgetText.fitWidth(it, TopRow.TITLE_SIZES.last(), fontScale) + beside <= width } ?: SHORT_TITLE
        val name = if (fits(sport.name, WidgetText.BODY_SP, width, fontScale)) sport.name else sport.shortName
        val usual = StartWidgetModel.USUAL.takeIf { fits(it, WidgetText.SECONDARY_SP, width, fontScale) }
        fun at(sz: Sizes, withLine: Boolean, withTitle: Boolean): StatusSmall {
            val shown = title.takeIf { withTitle }
            val top = TopRow.of(shown.orEmpty(), null, line, width, fontScale, withWords = false)
            val words = Words(name, usual?.takeIf { withLine }, false)
            val wordsH = body(fontScale) + (if (words.line != null) secondary(fontScale) else 0f)
            return StatusSmall(shown, top, sz, words, Spread.of(top.height, maxOf(sz.tile, sz.play), wordsH))
        }
        val wide = { sz: Sizes -> sz.tile + STATUS_PLAY_GAP_DP + sz.play <= width }
        val ladder = SMALL_SIZES.reversed().filter(wide)
        val drops = listOf(true to true, false to true, false to false)
        for ((withLine, withTitle) in drops) {
            val grown = ladder.map { at(it, withLine, withTitle) }
            if (grown.isNotEmpty() && grown.first().spread.fits(height, MIN_GAP_DP)) return Spread.grow(grown, height, MIN_GAP_DP) { it.spread }
        }
        val shrink = listOfNotNull(ladder.firstOrNull()) + STATUS_SHRINK
        for ((withLine, withTitle) in drops) for (sz in shrink) {
            at(sz, withLine, withTitle).takeIf { it.spread.fits(height, STATUS_MIN_GAP_DP) }?.let { return it }
        }
        return at(STATUS_SHRINK.last(), withLine = false, withTitle = false)
    }

    /** The status button's top in the status Small's content [height] dp high, dp: as every Small's top row. */
    fun statusSmallButtonTop(s: StatusSmall, height: Float): Float = s.spread.edge(height) + (s.top.height - StatusButton.SIZE_DP) / 2

    /** The usual row (or the recording's): its [words] and its height. */
    data class UsualRow(val words: Words, val height: Float)

    /**
     * The usual row in [width] dp at [sizes]: the tile, the name over the first of [lines] that fits (with the
     * location mark when [mark] and there's room for it), play or Open at the end. The short name when the full one
     * won't fit. All on one line each.
     */
    fun usualRow(sport: StartSport, lines: List<String>, mark: Boolean, recording: Boolean, sizes: Sizes, width: Float, fontScale: Float): UsualRow {
        val room = width - sizes.tile - 2 * ROW_GAP_DP - sizes.action(recording, fontScale)
        val name = if (fits(sport.name, WidgetText.BODY_SP, room, fontScale)) sport.name else sport.shortName
        val withMark = room - MARK_DP - MARK_GAP_DP
        val words = when {
            mark && fits(lines.first(), WidgetText.SECONDARY_SP, withMark, fontScale) -> Words(name, lines.first(), true)
            else -> Words(name, lines.firstOrNull { fits(it, WidgetText.SECONDARY_SP, room, fontScale) }, false)
        }
        return UsualRow(words, maxOf(sizes.tile, sizes.play, body(fontScale) + secondary(fontScale)))
    }

    /** The lines the usual row tries, longest first: "Your usual, GPS on", then "Your usual". */
    fun usualLines(m: StartWidgetModel): List<String> = listOf(m.usualLine, StartWidgetModel.USUAL).distinct()


    /**
     * The tile row: the [sports] before All sports, [tile] dp tiles spread from the content's left edge to its right,
     * evenly spaced, so the first lines up with the usual's tile and All sports with play. Captions at [captionSp], one
     * line each, every one centred under its tile.
     */
    data class Tiles(val sports: List<StartSport>, val tile: Float, val captionSp: Float, val height: Float) {
        /** False for the last resort's bare tiles. */
        val captioned: Boolean get() = captionSp > 0f

        val corner: Float get() = SMALL_TILE_CORNER_DP * tile / SMALL_TILE_DP
        val captions: List<String> get() = sports.map { it.caption } + StartWidgetModel.ALL_SPORTS
    }

    /** The tile row's height with [tile] dp tiles and captions at [sp]. */
    fun tilesHeight(tile: Float, sp: Float, fontScale: Float): Float = tile + CAPTION_GAP_DP + caption(fontScale, sp)

    /** The least a tap target is, dp. */
    const val TOUCH_DP = 48f

    /** The centre of tile [i] of [n] spread across [width] dp: the first at the left edge, the last at the right. */
    fun centre(i: Int, n: Int, tile: Float, width: Float): Float = tile / 2 + i * (width - tile) / (n - 1)

    /**
     * How far a caption may run past the content's edge into the card's side padding: half of it. The widget's own
     * padding there is [CAPTION_BLEED_DP] and every other row pads itself back in ([StartWidget]).
     */
    const val CAPTION_BLEED_DP = WidgetSizes.PADDING_DP / 2

    /**
     * Where each caption needs to be, as (left, width) in dp from the content's left edge: centred under its tile,
     * the first and last too, so it may start left of 0 or end past [width] (by at most [CAPTION_BLEED_DP] when it
     * fits). Widths are [captionWidth]'s: this decides what fits, never the box a caption is drawn in ([captionBoxes]).
     */
    fun captionSpans(captions: List<String>, tile: Float, sp: Float, width: Float, fontScale: Float): List<Pair<Float, Float>> {
        val n = captions.size
        return captions.mapIndexed { i, c ->
            val w = captionWidth(c, sp, fontScale)
            (centre(i, n, tile, width) - w / 2) to w
        }
    }

    /**
     * The room a caption needs, dp: its width by letter (a W or an m is wider than an l, which the flat
     * [WidgetText.width] misses: "Swim" showed as "Swi..." on the phone) with [CAPTION_MARGIN] on top, so a tight
     * caption gives its place to the next sport instead of being cut.
     */
    fun captionWidth(text: String, sp: Float, fontScale: Float): Float = WidgetText.fitWidth(text, sp, fontScale)

    /** How much wider than its letters' sum a caption is taken to be ([WidgetText.FIT_MARGIN]). */
    const val CAPTION_MARGIN = WidgetText.FIT_MARGIN

    /** [text]'s width in ems by letter ([WidgetText.letterEms]). */
    fun letterEms(text: String): Float = WidgetText.letterEms(text)

    /**
     * True when the captions' spans keep [CAPTION_SPACE_DP] apart and stay within [CAPTION_BLEED_DP] of the content's
     * edges.
     */
    fun captionsFit(captions: List<String>, tile: Float, sp: Float, width: Float, fontScale: Float): Boolean {
        val spans = captionSpans(captions, tile, sp, width, fontScale)
        if (spans.any { (l, w) -> l < -CAPTION_BLEED_DP || l + w > width + CAPTION_BLEED_DP }) return false
        return spans.zipWithNext().all { (a, b) -> a.first + a.second + CAPTION_SPACE_DP <= b.first }
    }

    /**
     * The box each caption is drawn in, (left, width) dp from the content's left edge: centred on its tile, and as
     * wide as its neighbours and the bleed into the padding leave, not as wide as the sum says, so a caption the sum
     * underrates still shows whole. The room between two captions is split down the middle of the free space.
     */
    fun captionBoxes(captions: List<String>, tile: Float, sp: Float, width: Float, fontScale: Float): List<Pair<Float, Float>> {
        val n = captions.size
        val spans = captionSpans(captions, tile, sp, width, fontScale)
        val bounds = listOf(-CAPTION_BLEED_DP) +
            (1 until n).map { k -> (spans[k - 1].first + spans[k - 1].second + spans[k].first) / 2 } +
            (width + CAPTION_BLEED_DP)
        return (0 until n).map { i ->
            val c = centre(i, n, tile, width)
            val half = minOf(c - bounds[i], bounds[i + 1] - c).coerceAtLeast(0f)
            (c - half) to 2 * half
        }
    }

    /**
     * The tile row in [width] by at most [room] dp with [tile] dp tiles and [columns] columns (sports and All
     * sports), the caption at 11 sp before 10. With [skip] false only the sports in their order of use, the first ones
     * each in its place, so every size that has room shows the same row. With [skip] true one whose caption would run
     * into its neighbours gives its place to the next that fits. Null when that can't fill the row.
     */
    fun tiles(next: List<StartSport>, width: Float, room: Float, fontScale: Float, tile: Float, columns: Int, skip: Boolean): Tiles? {
        if (columns * tile + (columns - 1) * CAPTION_SPACE_DP > width) return null
        for (sp in CAPTION_SIZES) {
            val height = tilesHeight(tile, sp, fontScale)
            if (height > room) continue
            val chosen = if (!skip) next.take(columns - 1).takeIf { first ->
                first.size == columns - 1 && captionsFit(first.map { it.caption } + StartWidgetModel.ALL_SPORTS, tile, sp, width, fontScale)
            } else mutableListOf<StartSport>().apply {
                for (s in next) {
                    if (size == columns - 1) break
                    val trial = (this + s).map { it.caption }
                    // The placed ones so far, with All sports at the far end where it will be.
                    val placed = trial + List(columns - 1 - trial.size) { "" } + StartWidgetModel.ALL_SPORTS
                    if (captionsFit(placed, tile, sp, width, fontScale)) add(s)
                }
            }
            if (chosen != null && chosen.size == columns - 1) return Tiles(chosen, tile, sp, height)
        }
        return null
    }

    /**
     * The tile row at [tile] dp: the sports in order at the most columns that hold them, then one giving its place, then bare
     * tiles without captions as the last resort ([bareTiles]).
     */
    fun tiles(next: List<StartSport>, width: Float, room: Float, fontScale: Float, tile: Float = SMALL_TILE_DP): Tiles? {
        for (skip in listOf(false, true)) for (columns in COLUMNS downTo 2) {
            tiles(next, width, room, fontScale, tile, columns, skip)?.let { return it }
        }
        return bareTiles(next, width, room, tile)
    }

    /**
     * The last resort, a large font in a short cell: the tiles without captions (each read out by name), so the row
     * stays rather than leaving a hole.
     */
    fun bareTiles(next: List<StartSport>, width: Float, room: Float, tile: Float): Tiles? =
        if (tile <= room && COLUMNS * tile + (COLUMNS - 1) * CAPTION_SPACE_DP <= width) Tiles(next.take(COLUMNS - 1), tile, 0f, tile) else null

    /** How the Large's two figures sit, always a label over its value. Every value stays on one line in all three. */
    enum class FigureLayout {
        /** Side by side, Caption over Body, as the mockup. */
        Side,

        /** A recording's own figures, up to three side by side, each its label over its value. */
        Live,

        /** One under the other, Caption over Body across the whole width. */
        Stacked,
    }

    /** [valueSp] is the values' size: Body, or a step down while both must still sit side by side. */
    data class Figures(val layout: FigureLayout, val height: Float, val valueSp: Float = WidgetText.BODY_SP)

    /**
     * This week and the usual's last in [width] dp, a value never wrapped: side by side when both values fit half the
     * width at Body, else a step down at Secondary and then Caption (the tiles above keep their room), and only then
     * one under the other.
     */
    fun figures(m: StartWidgetModel, width: Float, fontScale: Float): Figures {
        val half = (width - FIGURE_GAP_DP) / 2
        for (sp in listOf(WidgetText.BODY_SP, WidgetText.SECONDARY_SP, WidgetText.CAPTION_SP)) {
            if (fits(m.weekLine, sp, half, fontScale) && fits(m.lastLine, sp, half, fontScale)) {
                return Figures(FigureLayout.Side, caption(fontScale) + FIGURE_LINE_GAP_DP + WidgetText.line(sp, fontScale), sp)
            }
        }
        val pair = caption(fontScale) + FIGURE_LINE_GAP_DP + body(fontScale)
        return Figures(FigureLayout.Stacked, 2 * pair + FIGURE_ROW_GAP_DP)
    }

    /**
     * The Wide or Large laid out: the [sizes] it's drawn at, the usual [row], the [tiles] (none while recording, or
     * when not even a bare row fits), the Large's [figures], and the height of it all without gaps, so the spare
     * height is what the gaps share ([gaps] of them: above, between each block, below).
     */
    data class Block(
        val sizes: Sizes, val row: UsualRow, val tiles: Tiles?, val figures: Figures?, val content: Float, val gaps: Int,
        /** The status title row's height above the usual row (spec 5), 0 for none; counted in [content] and [gaps]. */
        val top: Float = 0f,
    ) {
        /** The Wide's gap above, between and below in a cell [height] dp high: all alike. */
        fun gap(height: Float): Float = ((height - content) / gaps).coerceAtLeast(0f)

        /**
         * The gap above and below the block in a cell [height] dp high, for the Large: half of each gap between, so
         * the card doesn't end in a band under its figures (the padding is under it already). The gaps between take
         * the rest.
         */
        fun edgeGap(height: Float): Float = ((height - content) / (gaps - 1) / 2).coerceAtLeast(0f)
    }

    /**
     * The Wide in [width] by [height] dp: the largest [ROW_SIZES] whose tile row still holds four sports and All
     * sports, else the one that holds the most.
     */
    fun wide(m: StartWidgetModel, width: Float, height: Float, fontScale: Float, top: Float = 0f): Block = block(m, width, height, fontScale, large = false, top)

    /** The Large: as the Wide, with the hairline and the figures under the tiles. */
    fun large(m: StartWidgetModel, width: Float, height: Float, fontScale: Float, top: Float = 0f): Block = block(m, width, height, fontScale, large = true, top)

    /** [top] is the status title row's height above it all ([statusTop]), 0 for none. */
    private fun block(m: StartWidgetModel, width: Float, height: Float, fontScale: Float, large: Boolean, top: Float): Block {
        val figures = if (large) figures(m, width, fontScale) else null
        val under = (figures?.let { it.height + 1f + 2 * MIN_GAP_DP } ?: 0f) + (if (top > 0f) top + MIN_GAP_DP else 0f)
        fun row(sz: Sizes) = usualRow(m.usual, usualLines(m), m.gpsOn, false, sz, width, fontScale)
        fun block(sz: Sizes, row: UsualRow, tiles: Tiles?): Block {
            val content = top + row.height + (tiles?.height ?: 0f) + (figures?.let { it.height + 1f } ?: 0f)
            // Above and below, and between the title row, the row, the tiles, the hairline and the figures.
            val gaps = 2 + (if (top > 0f) 1 else 0) + (if (tiles != null) 1 else 0) + (if (figures != null) 2 else 0)
            return Block(sz, row, tiles, figures, content, gaps, top)
        }
        val rows = ROW_SIZES.associateWith { row(it) }
        fun room(sz: Sizes) = height - rows.getValue(sz).height - under - MIN_GAP_DP
        // The sports in their order first, at the most columns and the largest sizes that hold them, so every size
        // shows the same row or the start of it. Only when not even two fit in order does one give its place.
        // Captions go last of all.
        for (skip in listOf(false, true)) for (columns in COLUMNS downTo 2) for (sz in ROW_SIZES) {
            tiles(m.next, width, room(sz), fontScale, sz.sportTile, columns, skip)?.let { return block(sz, rows.getValue(sz), it) }
        }
        for (sz in ROW_SIZES) bareTiles(m.next, width, room(sz), sz.sportTile)?.let { return block(sz, rows.getValue(sz), it) }
        val last = ROW_SIZES.last()
        return block(last, rows.getValue(last), null)
    }

    /* -------------------------------------------------------------------------------------------- */
    /* Recording: the live timer as the hero                                                         */
    /* -------------------------------------------------------------------------------------------- */

    /** The timer's largest size per layout, sp, and the least it steps down to. */
    const val TIMER_SMALL_SP = 40f
    const val TIMER_WIDE_SP = 44f
    const val TIMER_LARGE_SP = TypeScale.CLOCK_SP
    const val TIMER_MIN_SP = 20f
    const val TIMER_STEP_SP = 2f

    /** Below this the Small recording leaves the sport's tile out rather than squeeze it beside Open. */
    const val MIN_TILE_DP = 32f

    /** The red recording dot before "Recording", and the room after it. */
    const val DOT_DP = 8f
    const val DOT_GAP_DP = 4f

    /** The zone chip (workout-rules.md, Colour 3): a ringed dot and "Zone 3" on a dark pill, padded so. */
    const val CHIP_PAD_X_DP = 9f
    const val CHIP_PAD_Y_DP = 3f
    const val CHIP_DOT_DP = 9f
    /** Between bpm and the chip under it. */
    const val CHIP_ABOVE_DP = 4f

    /** The zone chip's width for [label] at [fontScale]. */
    fun chipWidth(label: String, fontScale: Float): Float =
        2 * CHIP_PAD_X_DP + CHIP_DOT_DP + DOT_GAP_DP + WidgetText.width(label, WidgetText.NOTE_SP, fontScale)

    /** The zone chip's height at [fontScale]. */
    fun chipHeight(fontScale: Float): Float = 2 * CHIP_PAD_Y_DP + WidgetText.line(WidgetText.NOTE_SP, fontScale)

    /** Between the timer and "Since 11:58" beside it. */
    const val SINCE_GAP_DP = 12f

    /** The timer's widest text, h:mm:ss past an hour: sized for it from the start so it never outgrows its room. */
    const val TIMER_PATTERN = "0:00:00"

    /**
     * The timer's width at [sp], dp: a Chronometer is a TextView, its size in sp, so it scales with the system font
     * ([fontScale]) as the sums do. By letter with the captions' margin.
     */
    fun timerWidth(sp: Float, fontScale: Float): Float = letterEms(TIMER_PATTERN) * sp * fontScale * CAPTION_MARGIN

    /** One line of the timer at [sp], dp. */
    fun timerLine(sp: Float, fontScale: Float): Float = WidgetText.line(sp, fontScale)

    /** Where "Since 11:58" goes: beside the timer, under it, or nowhere when there's no room. */
    enum class SincePlace { Beside, Under, None }

    /**
     * A recording layout: the sport's tile at [tile] dp and Open [open] dp high in the top row, the [name] shown, the
     * "Recording" line under it when [status] (the Wide and Large), the timer at [timerSp], "Since 11:58" at [since],
     * the Large's [figures], and the height of it all without gaps, which [gaps] share.
     */
    data class Recording(
        val tile: Float, val open: Float, val name: String, val status: Boolean, val timerSp: Float, val since: SincePlace,
        val figures: Figures?, val content: Float, val gaps: Int,
        /** The heart rate and zone at the timer's right (the Wide's and Large's), in place of "Since" there. */
        val heart: Boolean = false,
        /** The Small's timer box height, dp (its sp times [TIMER_LINE_EM]); null takes the font's line. */
        val timerLine: Float? = null,
        /** The Small's zone chip is the dot and the zone's number only, when the heart rate and "Zone 1" won't share the row. */
        val compactChip: Boolean = false,
    ) {
        val sizes: Sizes get() = Sizes(tile, open, 0f)

        /** [label] as the chip shows it: whole, or its digits alone ([compactChip]). */
        fun chipLabel(label: String): String = if (compactChip) label.filter { it.isDigit() }.ifEmpty { label } else label

        /** As [Block.edgeGap]: the Large's gap above and below, half of those between. */
        fun edgeGap(height: Float): Float = ((height - content) / (gaps - 1) / 2).coerceAtLeast(0f)
    }

    /** The heights the timer steps through from [top], largest first. */
    private fun timerSizes(top: Float): List<Float> = generateSequence(top) { it - TIMER_STEP_SP }.takeWhile { it >= TIMER_MIN_SP }.toList()

    /** The Small recording's dot before "Recording" (board: 6 dp), and its timer's box height as a share of its size in px. */
    const val SMALL_DOT_DP = 6f
    const val TIMER_LINE_EM = 1.1f

    /**
     * The Small recording (board HaloWidgetsWorkout): the sport's name, a dot and "Recording", one flexible space, the
     * timer at [TIMER_SMALL_SP] (smaller only when mm:ss or h:mm:ss won't fit across, as at a large font), then the
     * heart rate with its zone chip. Heights: 20 + 18 + the timer's box + 4 + the chip's row, which is 110 dp or under
     * at the board's sizes, so no row is ever cut. Paused, "Paused" takes the dot's line.
     */
    fun smallRecording(r: StartRecording, width: Float, height: Float, fontScale: Float): Recording {
        val name = if (fits(r.sport.name, WidgetText.CARD_TITLE_SP, width, fontScale)) r.sport.name else r.sport.shortName
        val pattern = if (r.activeMillis >= HOUR_MS) TIMER_PATTERN else TIMER_SHORT_PATTERN
        // What is always under or over the timer: the name, the status line and the heart rate row, with the gap above it.
        val chip = if (r.zoneLabel != null) chipHeight(fontScale) else 0f
        val heartRow = if (r.heart != null) WidgetGrid.GAP_TIGHT_DP + maxOf(secondary(fontScale), chip) else 0f
        val fixed = WidgetText.line(WidgetText.CARD_TITLE_SP, fontScale) + secondary(fontScale) + heartRow
        val sp = timerSizes(TIMER_SMALL_SP).firstOrNull {
            letterEms(pattern) * it * fontScale <= width && it * fontScale * TIMER_LINE_EM + fixed <= height
        } ?: TIMER_MIN_SP
        val heartW = r.heart?.let { WidgetText.fitWidth(it, WidgetText.SECONDARY_SP, fontScale) } ?: 0f
        val chipW = r.zoneLabel?.let { chipWidth(it, fontScale) } ?: 0f
        val compact = r.zoneLabel != null && heartW + SMALL_GAP_DP + chipW > width
        return Recording(
            0f, 0f, name, true, sp, SincePlace.None, null, 0f, 0, heart = r.heart != null,
            timerLine = sp * fontScale * TIMER_LINE_EM, compactChip = compact,
        )
    }

    private const val HOUR_MS = 3_600_000L
    private const val TIMER_SHORT_PATTERN = "00:00"

    /** The Wide recording: the top row, then the timer with "Since" beside or under it, as large as fits. */
    fun wideRecording(r: StartRecording, width: Float, height: Float, fontScale: Float): Recording =
        rowRecording(r, null, TIMER_WIDE_SP, width, height, fontScale)

    /** The Large recording: the Wide's, then the hairline and This week and the usual's last, as idle. */
    fun largeRecording(r: StartRecording, m: StartWidgetModel, width: Float, height: Float, fontScale: Float): Recording {
        // The workout's own figures (workout-rules.md, section 7, rule 6); the week's only before any are in.
        val figures = if (r.figures.isNotEmpty()) {
            Figures(FigureLayout.Live, body(fontScale) + FIGURE_LINE_GAP_DP + secondary(fontScale))
        } else figures(m, width, fontScale)
        return rowRecording(r, figures, TIMER_LARGE_SP, width, height, fontScale)
    }

    /**
     * The Wide's and Large's recording in [width] by [height] dp: the top row (the sport's tile, its name over the red
     * dot and "Recording", Open), then the timer as large as fits up to [top] sp, "Since 11:58" beside it when the
     * width allows, else under it when the height does, else left out. The biggest timer wins over the biggest tile.
     */
    /** The heart rate over its zone, at the timer's right: the wider of the two lines; null with no heart rate. */
    private fun heartWidth(r: StartRecording, fontScale: Float): Float? = r.heart?.let { heart ->
        val zone = r.zoneLabel?.let { chipWidth(it, fontScale) } ?: 0f
        maxOf(WidgetText.width(heart, WidgetText.CARD_TITLE_SP, fontScale), zone)
    }

    /** The heart rate over its zone chip: bpm, then the chip under it when there's a zone. */
    private fun heartHeight(r: StartRecording, fontScale: Float): Float =
        body(fontScale) + if (r.zoneLabel != null) CHIP_ABOVE_DP + chipHeight(fontScale) else 0f

    /** The sport's name that fits in [room] (else its short name), and whether "Recording" fits under it. */
    private fun topRowWords(r: StartRecording, room: Float, fontScale: Float): Pair<String, Boolean> {
        val name = if (fits(r.sport.name, WidgetText.BODY_SP, room, fontScale)) r.sport.name else r.sport.shortName
        return name to fits(r.status, WidgetText.SECONDARY_SP, room - DOT_DP - DOT_GAP_DP, fontScale)
    }

    /** "Since 11:58" [beside] the timer, else under it when it [fitsUnder], else left out. */
    private fun sincePlace(beside: Boolean, fitsUnder: Boolean): SincePlace = when {
        beside -> SincePlace.Beside
        fitsUnder -> SincePlace.Under
        else -> SincePlace.None
    }

    private fun rowRecording(r: StartRecording, figures: Figures?, top: Float, width: Float, height: Float, fontScale: Float): Recording {
        val under = figures?.let { it.height + 1f + 2 * MIN_GAP_DP } ?: 0f
        val gaps = if (figures != null) 5 else 3
        val sinceW = WidgetText.width(r.since, WidgetText.SECONDARY_SP, fontScale)
        val heartW = heartWidth(r, fontScale)
        val heartH = heartHeight(r, fontScale)
        for (sp in timerSizes(top)) for (sz in ROW_SIZES) {
            val (name, status) = topRowWords(r, width - sz.tile - 2 * ROW_GAP_DP - openWidth(fontScale), fontScale)
            val row = maxOf(sz.tile, sz.play, body(fontScale) + if (status) secondary(fontScale) else 0f)
            val timerW = timerWidth(sp, fontScale)
            // With a heart rate, it takes the timer's right; a timer too wide to leave it room steps down.
            if (timerW + (heartW?.let { SINCE_GAP_DP + it } ?: 0f) > width) continue
            val timer = maxOf(timerLine(sp, fontScale), if (heartW != null) heartH else 0f)
            val beside = heartW == null && timerW + SINCE_GAP_DP + sinceW <= width
            val since = sincePlace(beside, row + timer + secondary(fontScale) + MIN_GAP_DP + under <= height)
            val block = row + timer + (if (since == SincePlace.Under) secondary(fontScale) else 0f)
            if (block + MIN_GAP_DP + under > height) continue
            val total = block + (figures?.let { it.height + 1f } ?: 0f)
            return Recording(sz.tile, sz.play, name, status, sp, since, figures, total, gaps, heart = heartW != null)
        }
        val sz = ROW_SIZES.last()
        val row = maxOf(sz.tile, sz.play, body(fontScale))
        return Recording(sz.tile, sz.play, r.sport.shortName, false, TIMER_MIN_SP, SincePlace.None, figures,
            row + timerLine(TIMER_MIN_SP, fontScale) + (figures?.let { it.height + 1f } ?: 0f), gaps)
    }
}
