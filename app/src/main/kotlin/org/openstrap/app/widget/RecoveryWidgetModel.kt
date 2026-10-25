package org.openstrap.app.widget

import org.openstrap.app.data.Recovery
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.ui.dashboard.AFTER_FIRST_NIGHT
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.TabCopy
import org.openstrap.app.ui.dashboard.TodayCopy
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * One day's Recovery as the widget reads it, taken from the app's own result and copy, so the widget says what the
 * Recovery tab and Today's Recovery card say ([of]).
 */
data class RecoveryDay(
    val date: LocalDate,
    /** As Today shows it everywhere: OpenStrap's score, the strap's estimate, or why there's none. */
    val shown: TodayCopy.RecoveryShown,
    /** What the score means for today, as Today's Recovery card says it; null without one. */
    val sentence: String?,
    /** Before the strap has seen a night. */
    val firstNights: Boolean,
    /** The night Recovery scored: its resting heart rate (bpm), HRV (ms) and sleep score. */
    val restingHr: Float?,
    val hrv: Float?,
    val sleep: Int?,
    /** The 7 days ending on [date], oldest first, null where there was no score. */
    val week: List<Pair<LocalDate, Int?>>,
) {
    companion object {
        /**
         * [r] as the Recovery tab and Today show it, with the strap's own [strapScore] for the day standing in while
         * OpenStrap's is withheld. The figures are the night's own values the score was worked out from, the strap's
         * where those failed the validity rule, and the Sleep contributor (the strap's sleep score, else OpenStrap's).
         */
        fun of(r: RecoveryResult, strapScore: Int?): RecoveryDay {
            val ready = r.readiness
            val score = ready.score
            val night = r.tonight
            return RecoveryDay(
                date = r.date,
                shown = TodayCopy.recoveryShown(ready, strapScore, TabCopy.RECOVERY_WAITING),
                sentence = score?.let { TodayCopy.recoveryMeaning(it, ready.strainAlert) },
                firstNights = !r.charge.anchored,
                restingHr = night?.validRestingHr ?: ready.fallbackRestingHr,
                hrv = night?.hrv?.takeIf { night.hrvLn != null } ?: ready.fallbackHrv,
                sleep = ready.contributors.firstOrNull { it.factor == Recovery.Factor.Sleep }?.score ?: night?.strapScore,
                week = r.readinessTrend,
            )
        }
    }
}

/** A figure under the score: a Caption over a Body value. */
data class RecoveryFigure(val label: String, val value: String)

/** A day's bar in the Large's week: its letter, its score (null for none) and whether it's the day shown. */
data class RecoveryWeekDay(val letter: String, val score: Int?, val shown: Boolean)

/**
 * What the Recovery widget shows (widgets spec 3.4), the same model at every size: the Small shows the title, score
 * and word, the Wide adds the note and [figures], the Large adds [sentence] and [week]. Without a score there's only
 * the title and [waiting].
 */
data class RecoveryWidgetModel(
    /** The day the score is for, which a tap opens. */
    val date: LocalDate?,
    val title: String,
    /** "This morning", or "Yesterday" while this morning's score isn't in; null while waiting. */
    val note: String?,
    /** The number in dots, and the word beside it. */
    val score: String?,
    val word: String?,
    /** In place of the score, in Secondary: "After your first night" and the like. */
    val waiting: String?,
    /** The Large's sentence. */
    val sentence: String?,
    val figures: List<RecoveryFigure>,
    /** Seven days, the shown one last; empty when fewer than two have a score, as the Recovery tab's trend. */
    val week: List<RecoveryWeekDay>,
) {
    /** What TalkBack reads for the dotted number: "78, Ready". */
    val scoreDescription: String? get() = score?.let { s -> word?.let { "$s, $it" } ?: s }

    companion object {
        const val TITLE = "Recovery"
        const val THIS_MORNING = "This morning"
        const val YESTERDAY = "Yesterday"

        /** A figure the night didn't give. */
        const val NO_READING = "No reading"

        /**
         * The widget for [today], or [yesterday] with "Yesterday" as the note while this morning's score isn't in.
         * Before any: the title and why, "After your first night" before the strap has seen one. Null days are ones
         * not worked out (or that failed), which wait like a day with no night yet.
         */
        fun from(today: RecoveryDay?, yesterday: RecoveryDay?, locale: Locale = Locale.getDefault()): RecoveryWidgetModel {
            today?.takeIf { it.shown.value != null }?.let { return scored(it, THIS_MORNING, locale) }
            yesterday?.takeIf { it.shown.value != null }?.let { return scored(it, YESTERDAY, locale) }
            val line = when {
                today == null -> TabCopy.RECOVERY_WAITING
                today.firstNights -> AFTER_FIRST_NIGHT
                else -> today.shown.line
            }
            return RecoveryWidgetModel(null, TITLE, null, null, null, line, null, emptyList(), emptyList())
        }

        private fun scored(d: RecoveryDay, note: String, locale: Locale): RecoveryWidgetModel {
            val week = d.week.takeIf { w -> w.count { it.second != null } >= 2 }.orEmpty().mapIndexed { i, (day, score) ->
                RecoveryWeekDay(day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), score, i == d.week.lastIndex)
            }
            return RecoveryWidgetModel(
                date = d.date,
                title = TITLE,
                note = note,
                score = d.shown.value.toString(),
                word = d.shown.word,
                waiting = null,
                // The strap's estimate has no sentence of its own: its line says why OpenStrap's score isn't ready.
                sentence = d.sentence ?: d.shown.line,
                figures = figures(d),
                week = week,
            )
        }

        /** Resting HR "52 bpm", HRV "64 ms", Sleep "82". */
        fun figures(d: RecoveryDay): List<RecoveryFigure> = listOf(
            RecoveryFigure("Resting HR", d.restingHr?.let { "${Metric.RestingHr.format(it)} ${Metric.RestingHr.unit}" } ?: NO_READING),
            RecoveryFigure("HRV", d.hrv?.let { "${Metric.Hrv.format(it)} ${Metric.Hrv.unit}" } ?: NO_READING),
            RecoveryFigure("Sleep", d.sleep?.toString() ?: NO_READING),
        )
    }
}

/** The sums behind the Large's score row: how wide the week's columns can be beside the score and its word. */
object RecoveryLayout {
    /** Between the dotted score and its word, and between the word and the week, dp. */
    const val WORD_GAP_DP = 8f
    const val WEEK_GAP_DP = 12f

    /** The word's size, Body. */
    const val WORD_SP = 15f

    /** A rough width for [text] at [sp] in the system sans, dp ([WidgetText.width]). */
    fun textWidth(text: String?, sp: Float, fontScale: Float = 1f): Float = WidgetText.width(text, sp, fontScale)

    /**
     * The week's column width beside a score [dots] dp wide and [word], in [content] dp of width: as wide as
     * [WeekBarsLook] allows, narrower when a long word or a large font needs the room.
     */
    fun weekColumn(content: Float, dots: Float, word: String?, fontScale: Float = 1f, days: Int = 7): Float =
        org.openstrap.app.ui.glow.draw.WeekBarsLook.column(days, content - dots - WORD_GAP_DP - textWidth(word, WORD_SP, fontScale) - WEEK_GAP_DP)

    /** Each day's bar as a share of a full 100, null where the day has no score. */
    fun barValues(week: List<RecoveryWeekDay>): List<Float?> = week.map { d -> d.score?.let { it.coerceIn(0, 100) / 100f } }
}
