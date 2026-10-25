package org.openstrap.app.widget

import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.dashboard.TabCopy
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.clockText
import org.openstrap.app.ui.glow.topSports
import org.openstrap.app.ui.workout.WorkoutCopy
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * A sport on the Start widget. [sportType] is what a tap hands Ready (`EXTRA_READY_SPORT`), which then opens on the
 * side you last chose for it; [shownType] is that side's own sport, which the icon and names are of, so the widget
 * says what Ready will start ("Treadmill" for a run last done indoors).
 */
data class StartSport(
    val sportType: Int,
    val shownType: Int,
    /** In the usual row, Body 500: "Outdoor run". */
    val name: String,
    /** The picker's short name ("Run", "Walk", "Strength"): the name when the full one won't fit a line. */
    val shortName: String,
    /**
     * Under its tile, one line: the short name, or the full one where the short one would read as the usual or a
     * sport before it ("Treadmill" beside your outdoor runs). [StartWidgetModel.from] settles it against the others.
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

/** A workout under way, which every size shows in place of the usual: the sport and "Recording since 17:42". */
data class StartRecording(val sport: StartSport, val since: String)

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
        const val USUAL = "Your usual"
        const val ALL_SPORTS = "All sports"
        const val ALL_SPORTS_ACTION = "Choose a sport"
        const val THIS_WEEK = "This week"
        const val OPEN = "Open"
        const val NO_WORKOUTS = "No workouts yet"
        const val NONE_YET = "None yet"

        /** How many sports by use to offer before the everyday ones pad the list. */
        const val BY_USE = 8

        /**
         * The widget from every listed workout ([Workouts.listed]), the [sides] last chosen per sport on Ready, whether
         * the phone's [location] can be used now and the workout [recording] (its sport type and start), at [now].
         * The usual sport and the next ones are [topSports]' order, then [Workouts.EVERYDAY]'s once those run out
         * ([Workouts.DEFAULT_SPORTS] lead it before there's a history), each once as Ready would open it.
         */
        fun from(
            workouts: List<WorkoutAnalysis>, sides: Map<Int, Boolean>, location: Boolean, recording: Pair<Int, Long>?,
            now: Instant, zone: ZoneId, locale: Locale = Locale.getDefault(),
        ): StartWidgetModel {
            val sports = (topSports(workouts, BY_USE).map { it.sportType } + Workouts.EVERYDAY)
                .map { StartSport.of(it, sides) }
                .distinctBy { it.shownType }
            val usual = sports.first()
            // A caption says which sport it is: the short name unless the usual or a tile before it already has it.
            val said = mutableSetOf(usual.shortName)
            val next = sports.drop(1).map { sp -> if (said.add(sp.shortName)) sp else sp.copy(caption = sp.name).also { said += sp.name } }
            val gpsOffered = Workouts.gpsOffered(usual.sportType, Workouts.indoorSide(usual.sportType, sides))
            val today = now.atZone(zone).toLocalDate()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toEpochSecond()
            // As Today and Your week count them: the strap's own "Activity" records aren't workouts.
            val week = workouts.filter { it.workout.sportType != Workouts.GENERIC_ACTIVITY && it.workout.start in monday..now.epochSecond }
            val last = workouts.filter { Workouts.tileType(it.workout.sportType) == Workouts.tileType(usual.sportType) && it.workout.start <= now.epochSecond }
                .maxByOrNull { it.workout.start }
            return StartWidgetModel(
                usual = usual,
                usualLine = when {
                    gpsOffered && location -> "$USUAL, GPS on"
                    gpsOffered -> "$USUAL, GPS off"
                    else -> USUAL
                },
                gpsOn = gpsOffered && location,
                next = next,
                weekLine = weekLine(week.size, week.sumOf { it.activeSeconds }),
                lastLabel = lastLabel(usual.sportType),
                lastLine = last?.let { a ->
                    val day = recentDay(Instant.ofEpochSecond(a.workout.start).atZone(zone).toLocalDate(), today, locale)
                    "$day, ${fact(a)}"
                } ?: NONE_YET,
                recording = recording?.let { (type, start) -> StartRecording(StartSport.recorded(type), "Recording since ${clockText(start, zone)}") },
            )
        }

        /** "2 workouts, 1 h 35", "1 workout, 40 min", "No workouts yet". */
        fun weekLine(count: Int, seconds: Long): String = when (count) {
            0 -> NO_WORKOUTS
            1 -> "1 workout, ${TodayCopy.duration(seconds)}"
            else -> "$count workouts, ${TodayCopy.duration(seconds)}"
        }

        /** "Last run" for a run outdoors or on a treadmill, "Last HIIT", and "Last workout" for Other. */
        fun lastLabel(sportType: Int): String = when (val name = Workouts.shortName(Workouts.tileType(sportType))) {
            "Other" -> "Last workout"
            else -> "Last " + if (name.any { it.isLowerCase() }) name.lowercase() else name
        }

        /** A workout's one figure here: its distance ("5.2 km"), else its sets ("4 sets"), else how long it was. */
        fun fact(a: WorkoutAnalysis): String {
            a.distanceMeters?.takeIf { it >= 10f }?.let { return TabCopy.distanceText(it, a.distanceFromSteps) }
            a.sets?.takeIf { it > 0 }?.let { return if (it == 1) "1 set" else "$it sets" }
            return TodayCopy.duration(a.activeSeconds)
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
    const val OPEN_PAD_DP = 14f

    /** Between the usual row's tile, words and play (the mockup's), and the Small's words and play. */
    const val ROW_GAP_DP = 12f
    const val SMALL_GAP_DP = 8f

    /** Between a tile and its caption, and a figure's Caption and its Body. */
    const val CAPTION_GAP_DP = 4f
    const val FIGURE_LINE_GAP_DP = 2f

    /** Between the two figures side by side, and between captions in the tile row. */
    const val FIGURE_GAP_DP = 12f
    const val CAPTION_SPACE_DP = 4f

    /** Between the figures' rows. */
    const val FIGURE_ROW_GAP_DP = 8f

    /** The least room between the blocks that share out the spare height. */
    const val MIN_GAP_DP = 8f

    /** The tile row at its fullest: four sports and All sports. */
    const val COLUMNS = 5

    /** Captions at the Caption role's 11 sp, then a touch smaller before a sport gives its place. */
    val CAPTION_SIZES = listOf(WidgetText.CAPTION_SP, 10f)

    /** "Recording since 17:42" where it won't fit: "Since 17:42". */
    fun shortSince(since: String): String = since.replaceFirst("Recording since", "Since")

    /** Open's width at [fontScale]: its word in Body 500 with [OPEN_PAD_DP] each side. */
    fun openWidth(fontScale: Float): Float = WidgetText.width(StartWidgetModel.OPEN, WidgetText.BODY_SP, fontScale) + 2 * OPEN_PAD_DP

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

    /** The Wide's and Large's sizes, largest first; the last only for a short Wide at a large font. */
    val ROW_SIZES = listOf(Sizes(48f, 52f, 44f), Sizes(48f, 48f, 40f), Sizes(TILE_DP, PLAY_DP, SMALL_TILE_DP), Sizes(TILE_DP, PLAY_DP, 32f))

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

    /** The recording row's lines: "Recording since 17:42", then "Since 17:42". */
    fun recordingLines(r: StartRecording): List<String> = listOf(r.since, shortSince(r.since)).distinct()

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
    fun captionWidth(text: String, sp: Float, fontScale: Float): Float = letterEms(text) * sp * fontScale * CAPTION_MARGIN

    /** How much wider than its letters' sum a caption is taken to be. */
    const val CAPTION_MARGIN = 1.15f

    /** [text]'s width in ems in the system sans at Medium, by letter, rounded up: Roboto's advances. */
    fun letterEms(text: String): Float = text.sumOf { c ->
        when {
            c == ' ' || c in "iljI.,:!|'" -> 0.27
            c in "ftr" -> 0.37
            c in "mwMW" -> 0.88
            c.isUpperCase() -> 0.68
            c.isDigit() -> 0.58
            else -> 0.57
        }
    }.toFloat()

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

    /** How the Large's two figures sit. Every value stays on one line in all three. */
    enum class FigureLayout {
        /** Side by side, Caption over Body, as the mockup. */
        Side,

        /** A row each: the Caption on the left, the value on the right. */
        Rows,

        /** One under the other, Caption over Body across the whole width. */
        Stacked,
    }

    data class Figures(val layout: FigureLayout, val height: Float)

    /**
     * This week and the usual's last in [width] dp, a value never wrapped: side by side when both values fit half the
     * width, else a row each with the Caption beside the value, else one under the other.
     */
    fun figures(m: StartWidgetModel, width: Float, fontScale: Float): Figures {
        val half = (width - FIGURE_GAP_DP) / 2
        val pair = caption(fontScale) + FIGURE_LINE_GAP_DP + body(fontScale)
        if (fits(m.weekLine, WidgetText.BODY_SP, half, fontScale) && fits(m.lastLine, WidgetText.BODY_SP, half, fontScale)) {
            return Figures(FigureLayout.Side, pair)
        }
        fun row(label: String, value: String) =
            WidgetText.width(label, WidgetText.CAPTION_SP, fontScale) + FIGURE_GAP_DP + WidgetText.width(value, WidgetText.BODY_SP, fontScale) <= width
        if (row(StartWidgetModel.THIS_WEEK, m.weekLine) && row(m.lastLabel, m.lastLine)) {
            return Figures(FigureLayout.Rows, 2 * body(fontScale) + FIGURE_ROW_GAP_DP)
        }
        return Figures(FigureLayout.Stacked, 2 * pair + FIGURE_ROW_GAP_DP)
    }

    /**
     * The Wide or Large laid out: the [sizes] it's drawn at, the usual [row], the [tiles] (none while recording, or
     * when not even a bare row fits), the Large's [figures], and the height of it all without gaps, so the spare
     * height is what the gaps share ([gaps] of them: above, between each block, below).
     */
    data class Block(val sizes: Sizes, val row: UsualRow, val tiles: Tiles?, val figures: Figures?, val content: Float, val gaps: Int) {
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
    fun wide(m: StartWidgetModel, width: Float, height: Float, fontScale: Float): Block = block(m, width, height, fontScale, large = false)

    /** The Large: as the Wide, with the hairline and the figures under the tiles. */
    fun large(m: StartWidgetModel, width: Float, height: Float, fontScale: Float): Block = block(m, width, height, fontScale, large = true)

    private fun block(m: StartWidgetModel, width: Float, height: Float, fontScale: Float, large: Boolean): Block {
        val rec = m.recording
        val figures = if (large) figures(m, width, fontScale) else null
        val under = figures?.let { it.height + 1f + 2 * MIN_GAP_DP } ?: 0f
        fun row(sz: Sizes) = usualRow(
            rec?.sport ?: m.usual, rec?.let { recordingLines(it) } ?: usualLines(m), rec == null && m.gpsOn, rec != null, sz, width, fontScale,
        )
        fun block(sz: Sizes, row: UsualRow, tiles: Tiles?): Block {
            val content = row.height + (tiles?.height ?: 0f) + (figures?.let { it.height + 1f } ?: 0f)
            // Above and below, and between the row, the tiles, the hairline and the figures.
            val gaps = 2 + (if (tiles != null) 1 else 0) + (if (figures != null) 2 else 0)
            return Block(sz, row, tiles, figures, content, gaps)
        }
        if (rec != null) {
            val blocks = ROW_SIZES.map { block(it, row(it), null) }
            return blocks.firstOrNull { it.content <= height } ?: blocks.last()
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
}
