package org.openstrap.app.ui.theme

/**
 * The type tokens the app and the widgets share (board HaloTypeScale, approved 21 Nov 2026). Sizes are sp and line
 * heights sp, as drawn on the boards at 1x (1 board px is 1 dp), weights are CSS weights. The app's [GlowType] draws
 * them in Funnel, the widgets' WidgetType in the system sans. Plain numbers, so Compose, Glance and the widgets' fit
 * sums all read the same ones.
 *
 * The rules that go with them:
 * 1. Boards are drawn at 1x, and Android applies the screen's density. Nothing is converted by hand.
 * 2. Words are in sp and grow with the phone's font size.
 * 3. No text size outside this list where a token covers it.
 * 4. The one big number fits its box and never grows past its token.
 * 5. Widgets pick a layout by their size in dp, never by density.
 * 6. Checked at font size 1.0, 1.15 and 1.3.
 */
object TypeScale {
    /** Display 300, tabular: the widget timer, the time wheel's chosen row. */
    const val CLOCK_SP = 48f
    const val CLOCK_LINE_SP = 48f
    const val CLOCK_WEIGHT = 300

    /** Display 300: a hero's sentence, Moment's sentence and sport. */
    const val SENTENCE_SP = 21f
    const val SENTENCE_LINE_SP = 26.9f
    const val SENTENCE_WEIGHT = 300

    /** Sans 600: a hero card's title, every widget's title. */
    const val HERO_TITLE_SP = 16f
    const val HERO_TITLE_LINE_SP = 20f
    const val HERO_TITLE_WEIGHT = 600

    /** Sans 600: every card's title, the widgets' sport name, bpm and figures. */
    const val CARD_TITLE_SP = 15f
    const val CARD_TITLE_LINE_SP = 20f
    const val CARD_TITLE_WEIGHT = 600

    /** Sans 400: row names, buttons' neighbours, a sentence on an edge card, the widgets' words. */
    const val BODY_SP = 15f
    const val BODY_LINE_SP = 20f
    const val BODY_WEIGHT = 400

    /** Sans 600: a 36 high pill's label, the widgets' Open. */
    const val BUTTON_MINI_SP = 13f
    const val BUTTON_MINI_LINE_SP = 18f
    const val BUTTON_MINI_WEIGHT = 600

    /** Sans 400: labels, status lines, notes, the widgets' state word and "Since". */
    const val SECONDARY_SP = 13f
    const val SECONDARY_LINE_SP = 18f
    const val SECONDARY_WEIGHT = 400

    /** Sans 400: a smaller note, the zone chip. */
    const val NOTE_SMALL_SP = 12f
    const val NOTE_SMALL_LINE_SP = 15.6f
    const val NOTE_SMALL_WEIGHT = 400

    /** Sans 400, tabular: axes, times, tags, the widgets' figure labels. */
    const val CAPTION_SP = 11f
    const val CAPTION_LINE_SP = 14f
    const val CAPTION_WEIGHT = 400
}
