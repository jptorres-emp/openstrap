package org.openstrap.app.widget

import org.openstrap.app.ui.dashboard.clockText
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The strap status line as a widget shows it (widgets spec 5): the [status] and its wordings, longest first, so a
 * narrow top row takes a shorter one before the line drops.
 */
data class StatusLine(val status: StrapStatus, val options: List<String>) {
    /** The whole line, as [strapStatusLine] words it: what TalkBack reads whichever wording shows. */
    val full: String get() = options.first()

    companion object {
        /**
         * [status]'s wordings at [now] (epoch seconds): the line ("Last sync Tuesday"), then shorter ones that keep its
         * sense beside the line's icon ("Last sync Tue", "Tuesday", "Tue"). Never a dot, semicolon or dash.
         */
        fun of(status: StrapStatus, now: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): StatusLine {
            val full = strapStatusLine(status, now, zone, locale)
            val shorter = when (status) {
                StrapStatus.NotPaired -> listOf("Pair strap")
                is StrapStatus.LowBattery -> listOf("${status.percent}%")
                StrapStatus.Syncing -> emptyList()
                is StrapStatus.StaleSync -> {
                    val then = Instant.ofEpochSecond(status.at).atZone(zone).toLocalDate()
                    val days = ChronoUnit.DAYS.between(then, Instant.ofEpochSecond(now).atZone(zone).toLocalDate())
                    when {
                        days <= 0 -> listOf(clockText(status.at, zone))
                        days == 1L -> listOf("Yesterday")
                        days < 7 -> {
                            val day = then.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                            val short = then.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            listOf("Last sync $short", day, short)
                        }
                        else -> listOf(full.removePrefix("Last sync "))
                    }
                }
            }
            return StatusLine(status, (listOf(full) + shorter).distinct())
        }
    }
}

/**
 * The status line's pill (spec 5): black at 45%, fully round, a [ICON_DP] icon and Secondary type in white, one line.
 * It takes the top row in place of the title or the moment's name, so a layout fits it by its [Pill.height] there.
 */
object StatusPill {
    const val ICON_DP = 12f
    const val ICON_GAP_DP = 5f
    const val PAD_H_DP = 9f
    const val PAD_V_DP = 3f

    /** Black at this, under the pill. */
    const val ALPHA = 0.45f

    /** Secondary, then Caption size, before a shorter wording. */
    val SIZES = listOf(WidgetText.SECONDARY_SP, WidgetText.CAPTION_SP)

    /** Between the pill and a note kept on its right. */
    const val NOTE_GAP_DP = 12f

    /** The pill as shown: its wording [text] at [sp]. */
    data class Pill(val line: StatusLine, val text: String, val sp: Float) {
        fun height(fontScale: Float): Float = StatusPill.height(sp, fontScale)
        fun width(fontScale: Float): Float = StatusPill.width(text, sp, fontScale)
    }

    /** A pill's height with its text at [sp], dp. */
    fun height(sp: Float, fontScale: Float): Float = WidgetText.line(sp, fontScale) + 2 * PAD_V_DP

    /** A pill's width for [text] at [sp], dp: by letter with [WidgetText.FIT_MARGIN], so it's never cut. */
    fun width(text: String, sp: Float, fontScale: Float): Float = 2 * PAD_H_DP + ICON_DP + ICON_GAP_DP + WidgetText.fitWidth(text, sp, fontScale)

    /** The first wording of [line] that fits [room] dp, at the first size that does; null when none does or no line. */
    fun fit(line: StatusLine?, room: Float, fontScale: Float): Pill? {
        line ?: return null
        for (o in line.options) for (sp in SIZES) if (width(o, sp, fontScale) <= room) return Pill(line, o, sp)
        return null
    }
}

/**
 * A widget's top row (spec 5): the [pill] in the title's place when the strap needs something and a wording fits, the
 * title otherwise; whether the [note] on the right still fits; and the row's [height], which every layout's sums use.
 */
data class TopRow(val pill: StatusPill.Pill?, val note: Boolean, val height: Float) {
    companion object {
        /**
         * The top row in [width] dp: [title] at [titleSp] with [note] on the right, or the status [line]'s pill in the
         * title's place with the note kept while both fit. The pill is the news, so the note goes first.
         */
        fun of(title: String, note: String?, line: StatusLine?, width: Float, fontScale: Float, titleSp: Float = WidgetText.TITLE_SP): TopRow {
            val noteW = note?.let { StatusPill.NOTE_GAP_DP + WidgetText.fitWidth(it, WidgetText.SECONDARY_SP, fontScale) }
            val pill = StatusPill.fit(line, width, fontScale)
            if (pill != null) {
                val keep = noteW != null && pill.width(fontScale) + noteW <= width
                return TopRow(pill, keep, maxOf(pill.height(fontScale), if (keep) WidgetText.line(WidgetText.SECONDARY_SP, fontScale) else 0f))
            }
            val keep = noteW != null && WidgetText.fitWidth(title, titleSp, fontScale) + noteW <= width
            return TopRow(null, keep, WidgetText.line(titleSp, fontScale))
        }
    }
}

/**
 * How a layout shares its spare height (spec 2.1 and step 5's rule): [content] dp of blocks with [inner] gaps between
 * them, half a gap above the first and below the last, all alike.
 */
data class Spread(val content: Float, val inner: Int) {
    /** The gap between blocks in [height] dp. */
    fun gap(height: Float): Float = ((height - content) / (inner + 1)).coerceAtLeast(0f)

    /** The half gap above the first block and below the last. */
    fun edge(height: Float): Float = gap(height) / 2

    /** Whether it fits [height] with at least [minGap] between the blocks. */
    fun fits(height: Float, minGap: Float): Boolean = content + inner * minGap <= height + 0.01f

    companion object {
        /** The most a gap should be on a tall cell: the parts grow before a gap passes it. */
        const val TARGET_GAP_DP = 32f

        /** [blocks] laid out: their heights, the gaps between them. */
        fun of(vararg blocks: Float?): Spread {
            val shown = blocks.filterNotNull().filter { it > 0f }
            return Spread(shown.sum(), (shown.size - 1).coerceAtLeast(0))
        }

        /**
         * The first of [ladder] (least grown first) that fits [height] with [minGap] and brings the gap to
         * [TARGET_GAP_DP] or under, else the most grown that fits, else the first.
         */
        fun <T> grow(ladder: List<T>, height: Float, minGap: Float, spread: (T) -> Spread): T {
            val fitting = ladder.filter { spread(it).fits(height, minGap) }
            return fitting.firstOrNull { spread(it).gap(height) <= TARGET_GAP_DP } ?: fitting.lastOrNull() ?: ladder.first()
        }
    }
}
