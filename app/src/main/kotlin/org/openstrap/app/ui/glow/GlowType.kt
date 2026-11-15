package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.theme.FunnelDisplay
import org.openstrap.app.ui.theme.FunnelSans

/**
 * The type roles of the design system (spec 2.3): Funnel Display for display roles, Funnel Sans for the rest. Every
 * text in the app should use one of these, so a role changes in one place. Colours are the caller's, except the
 * roles drawn dimmed, which name their alpha.
 */
object GlowType {
    /**
     * The title in a page's top bar and in a sheet's header, centred (boards HaloProfile, HaloQuietHoursSheet,
     * HaloDetailSteps): Display 600, 17 on 22.
     */
    val pageTitle = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)

    /** A sheet's question or heading in its body ("Quiet from", board HaloQuietHoursSheet): Display 600, 22 on 26. */
    val sheetTitle = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)

    /** The line a goal moment announces (board HaloGoalMoment): Display 600, 26 on 30. */
    val momentTitle = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp)

    /** A detail's verdict ("Ready to train", board HaloDetailRecovery), in the metric colour: Display 600, 24 on 26.4. */
    val verdict = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 26.4.sp)

    /** HIIT's phase word over the seconds, WORK or REST (boards HiitWork, HiitRest): Display 500, 30 on 34, spaced out. */
    val phaseWord = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 0.14.em)

    /** Mind and body's time inside its ring (board LiveMind): Display 300, 72 on 76, tabular. */
    val mindClock = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 72.sp, letterSpacing = (-0.02).em, lineHeight = 76.sp, fontFeatureSettings = "tnum")

    /** The moving time on the map's panel, beside its two figures (board RunMap): Display 300, 38 on 42, tabular. */
    val mapClock = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 38.sp, letterSpacing = (-0.02).em, lineHeight = 42.sp, fontFeatureSettings = "tnum")

    /** A run's distance, time and pace on its share card (board ShareRun): Display 300, 30 on 33, tabular. */
    val storyStat = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = (-0.01).em, lineHeight = 33.sp, fontFeatureSettings = "tnum")

    /** The sport at the top of a share card ("Push day", boards ShareStrength, ShareHiit): Display 500, 30 on 34. */
    val storyTitle = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp)

    /** The wordmark on a share card ([Wordmark]): Display 700, 16 on 20, drawn a little tight as the logo is. */
    val storyBrand = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.02).em)

    /** The date in the moment header ("Fri 13 Nov", boards HaloRecovery, HaloTodayMidday): Display 600, 20 on 24. */
    val headerDate = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp)

    /** The moment over the header date ("Waking up"): Sans 500, 13, drawn at [MOMENT_ALPHA] white. */
    val moment = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

    /** White for [moment] text. */
    const val MOMENT_ALPHA = 0.6f

    /**
     * Every card's title on every tier, and list titles (boards HaloRecovery, HaloDetailRecovery, HaloAllBadges): Sans
     * 600, 15 on 20. One style, so a lit card and the solid card beside it read as one family.
     */
    val cardTitle = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

    /** The title row of a hero card, a size up from [cardTitle] (boards HaloRecovery, HaloLiveRun, HaloReadyRun): Sans 600, 16 on 20. */
    val heroTitle = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)

    /**
     * The sentence that leads a hero (board HaloRecovery, "A quiet morning. ..."): Display 300, 21 on 26.9, three lines
     * at most. Display against the title's Sans, so title and sentence don't run together.
     */
    val sentence = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 21.sp, lineHeight = 26.9.sp)

    /** The sentence under a detail's verdict (boards HaloDetailRecovery, HaloDetailSteps): Display 300, 19 on 25.7. */
    val detailSentence = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 19.sp, lineHeight = 25.7.sp)

    /** Row names, buttons, a sentence on an edge card, the word after a number: Sans 400, 15 on 20. */
    val body = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)

    /**
     * Section labels over a panel, status lines, notes (boards HaloProfile, HaloRecovery, HaloLiveRun): Sans 400, 13 on 18,
     * drawn at [SECONDARY_ALPHA] white unless coloured. The boards' grey is [noteColor].
     */
    val secondary = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

    /** White for [secondary] text, about the boards' #9AA0AA on black. */
    const val SECONDARY_ALPHA = 0.62f

    /** The grey of every note, label and axis on the boards: #9AA0AA. */
    val noteColor = Color(0xFF9AA0AA)

    /** The dimmest grey on the boards, for footnotes and chevrons: #6E7480. */
    val footnoteColor = Color(0xFF6E7480)

    /** A smaller note, and a badge's name and line (boards HaloRecovery, HaloAllBadges): Sans 400, 12 on 15.6. */
    val noteSmall = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.6.sp)

    /** The small print at the foot of a page (board HaloProfile): Sans 400, 12 on 17.4, in [footnoteColor]. */
    val footnote = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.4.sp)

    /** A primary button's label, black on white or white on a tint (boards HaloQuietHoursSheet, HaloGoalMoment): Sans 600, 16 on 20. */
    val button = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)

    /** A secondary or in-card button's label (boards HaloDetailRecovery, HaloSummaryRun): Sans 600, 14 on 18. */
    val buttonSmall = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)

    /**
     * Axes, times, tags (boards HaloLiveRun, HaloDetailRecovery): Sans 400, 11 on 14, tabular so times line up, drawn
     * at [CAPTION_ALPHA] white.
     */
    val caption = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum")

    /** White for [caption] text. */
    const val CAPTION_ALPHA = 0.62f

    /**
     * A list row's value, beside its [body] name (boards HaloProfile, HaloDetailRecovery): Sans 400, 15 on 20, tabular
     * so a column of values lines up, drawn at [ROW_VALUE_ALPHA] white. Plain text, never dots.
     */
    val rowValue = TextStyle(fontFamily = FunnelSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")

    /** White for [rowValue] text. */
    const val ROW_VALUE_ALPHA = 0.8f

    /**
     * A figure on a card or a summary, the "+29" of Recharged and the distance of a run (boards HaloRecovery,
     * HaloSummaryRun, HaloLiveRun): Display 300, 28 on 28, tabular. Plain, since the dotted answer is the card's one big number.
     */
    val figure = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum")

    /** A Small that breaks down the card beside it (Recharged, Drained): the [figure]. */
    val smallValue = figure

    /** A larger figure beside a summary's big number (board HaloSummaryRun, QuietHours' hours): Display 300, 40 on 40, tabular. */
    val figureLarge = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum")

    /** A number inline in a line of a live screen (board HaloLiveRun, "156"): Display 300, 20 on 24, tabular. */
    val figureInline = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum")

    /** The far rows of a time wheel (board HaloQuietHoursSheet): Display 300, 24 on 28.8, white at [WHEEL_FAR_ALPHA]. */
    val wheelFar = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 24.sp, lineHeight = 28.8.sp, fontFeatureSettings = "tnum")

    /** The rows beside the chosen one on a time wheel: Display 300, 32 on 38.4, white at [WHEEL_NEAR_ALPHA]. */
    val wheelNear = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 38.4.sp, fontFeatureSettings = "tnum")

    /** The chosen row of a time wheel: Display 300, 48 on 57.6, white. */
    val wheelChosen = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 48.sp, lineHeight = 57.6.sp, fontFeatureSettings = "tnum")

    /** White for [wheelFar] text. */
    const val WHEEL_FAR_ALPHA = 0.18f

    /** White for [wheelNear] text. */
    const val WHEEL_NEAR_ALPHA = 0.4f

    /**
     * A goal's value on Ready, typed in place ("7.5 km", "1 h 15 min", "6 × 400 m"): Display 300, 44 on 44, tabular.
     * Plain, since the card's dotted number is the heart rate's.
     */
    val goalValue = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-0.02).em, lineHeight = 44.sp, fontFeatureSettings = "tnum")

    /** The smaller numbers beside a [goalValue] (the rest in "with 90 s rest between", the ×): Display 300, 26 on 28. */
    val goalValueMinor = TextStyle(fontFamily = FunnelDisplay, fontWeight = FontWeight.Light, fontSize = 26.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum")
}

/**
 * The sizes of the one big number the boards draw in Doto 700 (workout rules section 3, detail rules, boards HaloRecovery,
 * HaloTodayMidday, HaloDetailSteps, HaloLiveRun, HaloSummaryRun, HaloGoalMoment). [doto] is the board's font size in px.
 * [dot] is the DotSpec dot that stands in for it: the 5 by 7 matrix is 9.28 dots tall, and Doto's digits are about 0.7 em
 * tall, so dot = 0.7 * doto / 9.28. Widgets share DotSpec, so the number can't drift from them.
 */
@Suppress("MagicNumber") // each size is the board's Doto size, named by its entry
enum class BigNumber(val doto: Int) {
    /** Doto 120: the heart rate on the Fold half open, read from across a room (no board, the size it had before Halo). */
    Tabletop(120),

    /** Doto 76: the live workout's number (board HaloLiveRun). */
    Live(76),

    /** Doto 72: a detail's header number and a summary's (boards HaloDetailSteps, HaloSummaryRun). */
    Detail(72),

    /** Doto 68: the Today hero (board HaloTodayMidday). */
    Hero(68),

    /** Doto 64: a hero on Recovery, a ready screen's goal, a moment's figure (boards HaloRecovery, HaloGoalMoment). */
    Feature(64),

    /** Doto 52: a detail's score in its arc, a second card (boards HaloDetailRecovery, HaloRecovery). */
    Second(52),

    /** Doto 44: a lit tile's number (board HaloTodayMidday), "3 of 4" beside a ring. */
    Tile(44),

    /** Doto 40: a small card's number (board HaloRecovery). */
    Small(40),
    ;

    /** The DotSpec dot, in dp, that matches the board's Doto size. */
    val dot: Float get() = doto * DIGIT_EM / DotSize.ROWS_IN_DOTS

    companion object {
        /** A Doto digit's height as a share of its font size. An estimate: no Doto file is in the repo. */
        const val DIGIT_EM = 0.7f
    }
}

/**
 * The one big number of a screen, drawn in DotSpec dots at the boards' Doto size for [size]. Use this, not DotMatrixText
 * with a DotSize, on every Halo screen. One per screen: the Today hero, a detail's header, a live workout's hero, a
 * summary's, a goal moment's. Everything else is [GlowType.figure] or plain text. With a [fitWidth] a number wider than
 * it shrinks to fit, never grows past [size].
 */
@Composable
fun BigNumberText(text: String, size: BigNumber, modifier: Modifier = Modifier, color: Color = Color.White, fitWidth: Dp = Dp.Unspecified) {
    val base = size.dot / DotSize.Detail.dot.value
    val scale = if (fitWidth == Dp.Unspecified) base else minOf(base, fitWidth / dotTextWidth(text, DotSize.Detail))
    DotMatrixText(text, modifier, dot = DotSize.Detail, color = color, scale = scale)
}
