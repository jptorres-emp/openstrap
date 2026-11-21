package org.openstrap.app.ui.glow

import androidx.compose.runtime.ReadOnlyComposable
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.TimeOfDay
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.Units
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import org.openstrap.app.ui.components.Motion
import org.openstrap.app.ui.components.rememberAnimationsOn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.openstrap.app.ui.components.dayHeld
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.openstrap.app.R
import org.openstrap.app.data.DayStat
import org.openstrap.app.i18n.Strings
import org.openstrap.app.data.History
import org.openstrap.app.data.Weeks
import org.openstrap.app.presentation.ChartPoint
import org.openstrap.app.ui.components.LocalCardsGrow
import org.openstrap.app.ui.components.detailNumber
import org.openstrap.app.ui.components.detailSurface
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.Score
import org.openstrap.app.presentation.DetailCopy
import org.openstrap.app.ui.dashboard.InfoCopy
import org.openstrap.app.presentation.Metric
import org.openstrap.app.ui.dashboard.NIGHTLY
import org.openstrap.app.ui.dashboard.RELATED
import org.openstrap.app.ui.dashboard.RELATED_SCORES
import org.openstrap.app.presentation.Range
import org.openstrap.app.presentation.StressLevel
import org.openstrap.app.ui.dashboard.avg
import org.openstrap.app.ui.dashboard.colorOf
import org.openstrap.app.ui.dashboard.LatestReading
import org.openstrap.app.ui.dashboard.SleepCopy
import org.openstrap.app.ui.dashboard.siblingsOf
import java.time.LocalDate
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.glow.pictures.DayStrip
import org.openstrap.app.ui.glow.pictures.PictureSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors

/* ------------------------------------------- Shared pieces ------------------------------------------ */

/** [date] as a heading: "Today", "Yesterday", the weekday within the week, then the date ([DayWords.relative]). */
internal fun dayName(date: LocalDate): String = DayWords.relative(date, LocalDate.now())

/* ---------------------------------------------- Template --------------------------------------------- */

/**
 * What a detail's hero says: the [value] in dots, the [label] beside it ("ms, day average", or the time or date under
 * the finger while scrubbing) and the status [word] under it in the metric's colour. Without a value the [word] is the
 * state ("Learning", "No reading") and stands large in the value's place, the [label] under it says why, never a lone
 * dash ([DetailHeadline.none]).
 */
internal data class DetailHeadline(
    val value: String?, val label: String, val word: String?, val unit: String? = null,
    /** The line under the status word when the label doesn't carry it ("At 09:10"); with it, [unit] is only the unit. */
    val context: String? = null,
) {
    /**
     * The unit that sits beside the value and the context that sits under the status word (board HaloDetailHeart: "bpm" by
     * the number, "Live, 15:40" under it). [unit] when given, else a short first word before a comma ("bpm, day average").
     */
    fun parts(): Pair<String?, String?> = when {
        context != null -> unit to context
        unit != null -> {
            val rest = label.removePrefix(unit).removePrefix(",").trim()
            unit to rest.replaceFirstChar { it.uppercase() }.ifBlank { null }
        }
        else -> labelParts()
    }

    /** A short first word before a comma is the unit ("bpm, day average"), a lone short word is only a unit. */
    private fun labelParts(): Pair<String?, String?> {
        val head = label.substringBefore(", ", "")
        return when {
            head.isNotEmpty() && ' ' !in head && head.length <= UNIT_LENGTH ->
                head to label.substringAfter(", ").replaceFirstChar { it.uppercase() }
            ' ' !in label && label.length <= UNIT_LENGTH -> label to null
            else -> null to label.replaceFirstChar { it.uppercase() }.ifBlank { null }
        }
    }

    companion object {
        /** No value: the state as a word, and what it waits for or why. */
        fun none(state: String, why: String) = DetailHeadline(null, why, state)
    }
}

/** The longest a unit is ("steps", "kcal"), which tells it from the start of a sentence. */
private const val UNIT_LENGTH = 8

/**
 * The day a detail is read for, as the host knows it ([picked]: the strip's pill moves to it at once) and whether the
 * page still shows the last day while the picked one loads ([pending]: drawn dimmed, [dayHeld]). One model for every
 * detail, so none of them dims or moves its pill differently.
 */
internal class DetailDay(val picked: LocalDate?, val pending: Boolean)

internal val LocalDetailDay = androidx.compose.runtime.compositionLocalOf { DetailDay(null, false) }

/** A detail the switcher can move to: its [title], and whether it's the one showing. */
internal class DetailSibling(val title: String, val current: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val open: () -> Unit)

/** The siblings of [detail] for its switcher, each opening in its place through [onSwitch]. */
internal fun siblings(detail: Detail, onSwitch: (Detail) -> Unit): List<DetailSibling> = siblingsOf(detail).map { d ->
    val title = when (d) {
        is Detail.OfMetric -> d.metric.title
        is Detail.OfScore -> d.score.title
        else -> ""
    }
    DetailSibling(title, d == detail, d.rowIcon()) { if (d != detail) onSwitch(d) }
}


/**
 * The metric detail template (spec 6.1), one for every number. The hero, tinted with [color] and fading to black,
 * carries back, the switcher across [siblings] ([title] on it), the (i) that opens the info sheet ([onInfo]), the
 * [headline] and the seven-day [strip] ([onDay] picks a day). Under it the [ranges] as pills when there's more than
 * one, then [body]: the chart, the sentence and the rows. [heroValue] replaces the value row with a score gauge (the
 * Recovery arc, the Sleep horizon), centred with its label and status word under it; [heroPicture] is the card's picture under the value, above the days; [heroSide] sits to the right of
 * a plain value row (heart rate's Live switch). While a chart is scrubbed, [scrubbed] gives the reading under the
 * finger in the headline's place, on the value row in the gauge's place. It's read only inside the hero.
 * [headlineDay] is the day the [headline] is for, when that isn't the strip's: a page that holds the last day's
 * figures while the next is worked out ([DayNumber]).
 */
@Composable
internal fun DetailTemplate(
    title: String,
    color: Color,
    siblings: List<DetailSibling>,
    onInfo: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    headline: DetailHeadline,
    strip: List<DetailCopy.StripDay>,
    onDay: (LocalDate) -> Unit,
    ranges: List<Range>,
    range: Range,
    onRange: (Range) -> Unit,
    heroValue: (@Composable () -> Unit)? = null,
    heroSide: (@Composable () -> Unit)? = null,
    heroPicture: (@Composable () -> Unit)? = null,
    /** Under the status and context, still in the headline: a count's goal bar. */
    heroBelow: (@Composable () -> Unit)? = null,
    scrubbed: () -> DetailHeadline? = { null },
    headlineDay: LocalDate? = strip.firstOrNull { it.selected }?.date,
    body: LazyListScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val list = rememberLazyListState()
    var switching by rememberSaveable { mutableStateOf(false) }
    val gutter = GlowLayout.Gutter
    val day = LocalDetailDay.current
    // The pill follows the day picked at once, whatever day's figures the page still holds (P2).
    val days = remember(strip, day.picked) {
        val picked = day.picked
        if (picked == null || strip.none { it.date == picked }) strip else strip.map { it.copy(selected = it.date == picked) }
    }
    // Rows inside a detail open the next one with a crossfade, not by growing (spec 3.5).
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }, LocalCardsGrow provides false) {
        Box(Modifier.fillMaxSize().dayHeld(day.pending)) {
        HaloPageList(contentPadding, variant = PageVariant.Detail, state = list, gutter = gutter) {
            item {
                DetailHero(color, gutter) {
                    // A scrub is read here, so only the hero follows the finger, not the whole detail (audit K4).
                    val scrub = scrubbed()
                    val shown = scrub ?: headline
                    // A scrubbed reading takes the gauge's place, as the value row.
                    val gauge = heroValue.takeIf { scrub == null }
                    val onSwitch = { switching = true }
                    // The detail's header (HaloDetailRecovery board): back, the title, which opens the switcher when there's
                    // something to switch to, and the (i), both flat circles over the wash.
                    HaloPageHeader(title, onBack, variant = HeaderVariant.Detail, onTitle = if (siblings.size > 1) onSwitch else null) {
                        HaloIconButton(
                            AppIcons.Info, stringResource(R.string.glow_metrics_about, title), onInfo, variant = HaloIconVariant.OverWash,
                        )
                    }
                    val (unit, context) = shown.parts()
                    // A score: the gauge on the left, the status word and context stacked to its right (rules, score headline).
                    if (gauge != null) Row(
                        Modifier.fillMaxWidth().padding(top = HeadlineGap), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.S16),
                    ) {
                        gauge()
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
                            if (shown.word != null && shown.value != null) Text(
                                shown.word, color = statusTint(color), style = GlowType.verdict, maxLines = 2,
                            )
                            (context ?: shown.label.takeIf { it.isNotBlank() })?.let {
                                Text(it, color = Ink.Secondary, style = GlowType.secondary)
                            }
                        }
                    } else Row(Modifier.fillMaxWidth().padding(top = HeadlineGap), verticalAlignment = Alignment.Top) {
                        // The frame is fixed: the number row, the status line and the context line are always laid out,
                        // an empty one keeping its height, so the page never moves with the day's data (P1).
                        Column(
                            Modifier.weight(1f).padding(horizontal = Space.S4), verticalArrangement = Arrangement.spacedBy(Space.S8),
                        ) {
                            if (shown.value == null) {
                                // No number: the state in words, large in the number's place, never a lone dash.
                                Box(contentAlignment = Alignment.CenterStart) {
                                    HaloBigNumber("0", BigNumber.Detail, Modifier.alpha(0f).clearAndSetSemantics {})
                                    Text(
                                        shown.word ?: stringResource(R.string.glow_no_reading_yet), color = Color.White,
                                        style = GlowType.verdict, maxLines = 2,
                                    )
                                }
                                Text(" ", style = GlowType.cardTitle, maxLines = 1)
                                Text(shown.label.ifEmpty { " " }, color = Ink.Secondary, style = GlowType.secondary)
                            } else {
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                                    DayNumber(shown.value, headlineDay, Modifier.detailNumber(shown.value))
                                    if (unit != null) Text(
                                        unit, color = Ink.Secondary, style = GlowType.body, maxLines = 1,
                                        modifier = Modifier.padding(bottom = Space.S8),
                                    )
                                }
                                Text(shown.word ?: " ", color = statusTint(color), style = GlowType.cardTitle, maxLines = 1)
                                Text(context?.ifEmpty { " " } ?: " ", color = Ink.Secondary, style = GlowType.secondary)
                            }
                            heroBelow?.invoke()
                        }
                        heroSide?.invoke()
                    }
                    // The card's picture, under the value and above the days, as the gauges sit.
                    if (heroPicture != null) Column(Modifier.fillMaxWidth().padding(top = Space.S16)) { heroPicture() }
                    if (days.isNotEmpty()) WeekStrip(days, onDay, Modifier.padding(top = Space.S20))
                }
            }
            if (ranges.size > 1) item {
                GlowSegments(
                    ranges.map { it.label }, ranges.indexOf(range), { onRange(ranges[it]) }, role = Role.Tab, size = SegmentSize.Compact,
                )
            }
            body()
        }
        }
    }
    if (switching) SwitcherSheet(siblings, color, onDismiss = { switching = false })
}

/**
 * The hero's figure in dots, moving to the next day's rather than cutting. A figure that is a plain number counts from
 * the one shown to the new one, and when the two are close (a few points, which a count would only step through) it
 * rolls instead, the old one sliding out as the new one slides in. Never from zero, and not with the system's
 * animations off.
 *
 * [day] is the day [value] is for, which on a page that holds the last day's figures while the next day is worked out
 * (Energy, Load) is the held day, not the one just picked: the figure and its day arrive together, however late. Only
 * a change that comes with another day moves; a scrub or a live reading lands at once, and so does the first change on
 * a day whose figure came in the same as the last day's.
 */
@Composable
internal fun DayNumber(value: String, day: LocalDate?, modifier: Modifier = Modifier, size: BigNumber = BigNumber.Detail) {
    val animate = rememberAnimationsOn()
    val parsed = remember(value) { PlainNumber.parse(value) }
    val count = remember { Animatable(parsed?.number ?: 0f) }
    val shown = remember { Shown(value, day, parsed?.number) }
    val moving = day != shown.day && value != shown.text
    val from = if (count.isRunning) count.value else shown.number
    val to = parsed?.number
    val step = stepOf(animate && moving, from, to)
    SideEffect {
        shown.text = value
        shown.day = day
        shown.number = to
        if (step == Step.Count) shown.countFrom = from
    }
    LaunchedEffect(value, day) {
        if (to == null) return@LaunchedEffect
        if (step == Step.Count && from != null) {
            count.snapTo(from)
            count.animateTo(to, tween(COUNT_MS, easing = Motion.easing))
        } else {
            count.snapTo(to)
        }
        shown.countFrom = null
    }
    val up = (to ?: 0f) > (from ?: 0f)
    AnimatedContent(
        targetState = value, modifier = modifier, label = "day number",
        transitionSpec = {
            if (step == Step.Roll) roll(up) else ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
        },
    ) { text -> HaloBigNumber(countedText(text, value, parsed, count, step, from, shown.countFrom), size) }
}

/**
 * What the figure reads this frame: the count while it runs. Until it starts, its starting figure, so the new day's
 * number never shows for a frame before the count runs back up to it (2,500 to 4,500 read 4, then 2, then 4).
 */
private fun countedText(
    text: String, value: String, parsed: PlainNumber?, count: Animatable<Float, *>, step: Step, from: Float?, waiting: Float?,
): String {
    if (parsed == null || text != value) return text
    val at = when {
        count.isRunning -> count.value
        step == Step.Count -> from
        else -> waiting
    }
    return at?.let(parsed::write) ?: text
}

/** How a figure gets to the next day's: at once, counting through the numbers between, or rolling when they're close. */
private enum class Step { Land, Count, Roll }

private fun stepOf(moving: Boolean, from: Float?, to: Float?): Step = when {
    !moving || from == null || to == null -> Step.Land
    abs(to - from) < ROLL_BELOW -> Step.Roll
    else -> Step.Count
}

/** The figure last shown, the day it was for, and the figure a count starts from while it waits to run. */
private class Shown(var text: String, var day: LocalDate?, var number: Float?, var countFrom: Float? = null)

/** The old figure slides out one way and the new one in from the other, up for a higher one, with a fade. */
private fun roll(up: Boolean): ContentTransform {
    val slide = tween<IntOffset>(ROLL_MS, easing = Motion.easing)
    val fade = tween<Float>(ROLL_MS, easing = Motion.easing)
    val sign = if (up) 1 else -1
    return ContentTransform(
        slideInVertically(slide) { sign * it / ROLL_TRAVEL } + fadeIn(fade),
        slideOutVertically(slide) { -sign * it / ROLL_TRAVEL } + fadeOut(fade),
        sizeTransform = SizeTransform(clip = false),
    )
}

/** How long a figure takes to count to the next day's, and to roll; and a change under [ROLL_BELOW] rolls. */
private const val COUNT_MS = 600
private const val ROLL_MS = 320
private const val ROLL_BELOW = 25f

/** A roll travels a third of the figure's height. */
private const val ROLL_TRAVEL = 3

/** A whole part this many digits long is grouped by the dot font, so a plain number that long counts as grouped. */
private const val GROUPED_FROM_DIGITS = 4

/** A figure written as a plain number ("72", "8432", "26 120", "14.6"): its value, and how to write another like it. */
private class PlainNumber(val number: Float, decimals: Int, grouped: Boolean) {
    /** Made once, not on every frame of a count. Only the composition writes with it, on the main thread. */
    private val format = java.text.NumberFormat.getNumberInstance(Units.DOT_LOCALE).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        isGroupingUsed = false
    }
    private val grouped = grouped

    /** Grouped the dot font's way ([Units.dotGrouped]) when the figure was. */
    fun write(n: Float): String = format.format(n.toDouble()).let { if (grouped) Units.dotGrouped(it) else it }

    companion object {
        private val SHAPE = Regex("""^(\d{1,3}([, \u2009]\d{3})+|\d+)(\.\d+)?$""")

        fun parse(text: String): PlainNumber? =
            if (!SHAPE.matches(text)) null
            // Grouped when it has a mark, or is long enough that the dot font would group it.
            else text.replace(",", "").replace(" ", "").replace(Units.DOT_GROUP_SPACE.toString(), "").let {
                PlainNumber(it.toFloat(), text.substringAfter('.', "").length, it.substringBefore('.').length >= GROUPED_FROM_DIGITS)
            }
    }
}

/** The header buttons over a detail's wash (HaloDetailRecovery board: rgba(0,0,0,0.35)). */

/** The wash's box (HaloDetailRecovery board: 420 tall, an ellipse 120% by 90% centred at 50% by -12%). */
private val WashHeight = 420.dp

/**
 * The hero: the metric's wash at the top of the page, as wide as the screen (HaloDetailRecovery, HaloDetailHeart
 * boards). The colour is 0.55 at the centre, 0.22 at 38% and clear at 72%, so it fades into the black with no edge and
 * no hairline along a foot. Its corners stay its own, rounded at the foot only.
 */
/** Between the header row and the headline number, so the dots never crowd the back button (board HaloDetailHrv: 16 + 8). */
private val HeadlineGap = 24.dp

@Composable
private fun DetailHero(color: Color, gutter: Dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(bottomStart = GlowLayout.CardCorner, bottomEnd = GlowLayout.CardCorner)
    Column(
        Modifier
            // Out to the screen's edges, past the list's gutter.
            .layout { measurable, constraints ->
                val extra = gutter.roundToPx() * 2
                val w = constraints.maxWidth + extra
                val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            }
            // The card tapped grows into this surface (spec 3.5).
            .detailSurface(hero = true, shape)
            .clip(shape)
            .drawBehind {
                val boxH = WashHeight.toPx()
                val rx = size.width * 1.2f
                val ry = boxH * 0.9f
                val center = Offset(size.width / 2, -boxH * 0.12f)
                val brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.55f), 0.38f to color.copy(alpha = 0.22f), 0.72f to Color.Transparent,
                    center = center, radius = rx,
                )
                // The circle is squashed to the board's ellipse about its centre.
                withTransform({ scale(1f, ry / rx, pivot = center) }) {
                    drawRect(brush, Offset(0f, center.y), Size(size.width, (size.height - center.y) * rx / ry))
                }
            }
            .padding(start = gutter, end = gutter, top = Space.S4, bottom = Space.S24),
    ) { content() }
}

/**
 * The seven days (board HaloDetailHeart): each a 62 tall squircle with its letter (11) over its date (15), the day shown on
 * a white pill with black text, and a short 12 by 3 bar under days with data, none under days without. No dots.
 */
@Composable
private fun WeekStrip(days: List<DetailCopy.StripDay>, onDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.S4)) {
        days.forEach { d ->
            val show = stringResource(R.string.glow_metrics_show_day, d.label)
            // The pill moves to the day tapped, and its bar comes and goes, rather than cutting.
            val quick = tween<Color>(Motion.QUICK)
            val pill by animateColorAsState(if (d.selected) Color.White else Color.Transparent, quick, label = "day pill")
            val ink by animateColorAsState(if (d.selected) Color.Black else Color.White, quick, label = "day ink")
            val bar by animateColorAsState(
                when {
                    !d.hasData -> Color.Transparent
                    d.selected -> Color.Black
                    else -> Ink.Tertiary
                },
                quick, label = "day bar",
            )
            Column(
                Modifier.weight(1f).height(ChipSpec.DayStrip).clip(Corner.Pill)
                    .background(pill)
                    .clickable(onClickLabel = show, role = Role.Button) { onDay(d.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.S4, Alignment.CenterVertically),
            ) {
                Text(d.label, color = ink.copy(alpha = 0.7f), style = GlowType.caption, maxLines = 1)
                Text(d.date.dayOfMonth.toString(), color = ink, style = GlowType.cardTitle, maxLines = 1)
                Box(Modifier.size(12.dp, 3.dp).background(bar, CircleShape))
            }
        }
    }
}

/**
 * The switcher's choices, as a sheet over the detail (board HaloDetailSwitcher): one plain panel of rows, each a 32 tile
 * (the metric's colour with a dark icon for the one showing, #17181C with a white one for the rest), the name, and
 * "Showing" in the metric's colour on the one that is.
 */
@Composable
private fun SwitcherSheet(siblings: List<DetailSibling>, color: Color, onDismiss: () -> Unit) {
    HaloSheet(onDismiss) {
        HaloCard(fill = CardFill.Rows) {
            siblings.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); s.open() }.topHairline(i > 0, OnSurface.line)
                        .heightIn(min = RowSpec.MinHeight).rowSides().padding(vertical = RowSpec.Pad),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S12),
                ) {
                    Box(
                        Modifier.size(IconSize.TileS).clip(Corner.Pill).background(if (s.current) color else Greys.Raised),
                        contentAlignment = Alignment.Center,
                    ) {
                        s.icon?.let { Icon(it, null, tint = if (s.current) Color.Black else Color.White, modifier = Modifier.size(17.dp)) }
                    }
                    Text(
                        s.title, Modifier.weight(1f), color = Color.White,
                        style = if (s.current) GlowType.cardTitle else GlowType.body, maxLines = 1,
                    )
                    if (s.current) Text(stringResource(R.string.glow_metrics_showing), color = color, style = GlowType.secondary)
                }
            }
        }
    }
}


/** The one time-aware sentence under the chart. */
@Composable
internal fun DetailSentence(text: String) {
    Text(text, color = Color.White, style = GlowType.detailSentence, modifier = Modifier.padding(horizontal = Space.S4))
}

/** What a chart says while there's nothing to draw, in the chart's own 200 dp so the page below it stays where it was (P1). */
@Composable
internal fun DetailEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = Space.S8).height(DetailChartHeight), contentAlignment = Alignment.Center) {
        Text(text, color = Ink.Secondary, style = GlowType.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/* ------------------------------------------- Metric detail ------------------------------------------ */

private val HOURS = listOf("00", "06", "12", "18", "24")

/**
 * A day's value of [metric]: the live or latest heart rate, the resting heart rate, the day's steps, HRV from the night's
 * readings, else the average of the day's readings.
 */
internal fun dayValue(metric: Metric, state: DayUiState, live: Int? = null): Float? = when (metric) {
    Metric.HeartRate -> (live ?: state.latestHeartRate)?.toFloat()
    Metric.RestingHr -> state.restingHeartRate?.toFloat()
    Metric.Steps -> state.steps.toFloat().takeIf { it > 0f }
    // The night's average, the one HRV figure on every screen ([NightMetrics.sleepHrv]).
    Metric.Hrv -> SleepCopy.nightValue(state, Metric.Hrv)
    // The latest reading, as heart rate: blood oxygen's night average is only on the Sleep and Recovery tabs
    // ([SleepCopy.nightValue]); stress is judged by that reading's level ([StressLevel]).
    Metric.Spo2, Metric.Stress -> LatestReading.of(metric, state)?.y
    Metric.SkinTemperature -> state.skinTemperature.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
    Metric.Breathing -> state.breathRate.takeIf { it.isNotEmpty() }?.map { it.y }?.average()?.toFloat()
}

/**
 * A metric's detail (spec 6.1) in the [DetailTemplate]: the day's value and its readings, or a week, month or year of
 * daily values (weekly for a year) against your normal, with related metrics under them. [onSwitch] puts a sibling in
 * this one's place, [onOpen] pushes another detail, [onSelectDay] changes the day shown everywhere.
 *
 * Scoped to the [night], as the Sleep tab's night vitals open it, Day and Week come from the sleep alone ([SleepCopy]):
 * the night's average and readings while asleep, the last 7 nights, against your normal of the nights before, as the
 * Sleep tab reads them. Month and Year come from the per-day series ([SleepCopy.nightDaily]), and their label says
 * whether those are nights or whole days. [recovery] is the day's, for the Recovery row of those that relate to it.
 */
@Composable
fun GlowMetricDetailScreen(
    metric: Metric,
    night: Boolean,
    state: DayUiState,
    history: DailyHistory,
    liveHeartRate: Int?,
    canUseLive: Boolean,
    onOpen: (Detail) -> Unit,
    onSwitch: (Detail) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    recovery: org.openstrap.app.data.RecoveryResult? = null,
) {
    var rangeIndex by rememberSaveable(metric, night) { mutableIntStateOf(0) }
    val range = Range.entries[rangeIndex]
    val end = state.date
    val today = LocalDate.now()
    val strip0 = DetailCopy.strip(end, today, emptySet())
    val to = maxOf(end, strip0.last().date)
    val from = minOf(end.minusDays(maxOf(range.days, History.NORMAL_DAYS) - 1L), strip0.first().date)
    val daily = if (night) SleepCopy.nightDaily(metric) else metric.daily
    val fresh by remember(daily, from, to, history) { history(daily, from, to) }.collectAsStateWithLifecycle(null)
    // Another day's load keeps the last day's rows, and the day they end on, until it arrives, so the Usual line, the
    // normal band, the status word and the strip's bars never blink off and on (P2). A change of range or metric drops them.
    val loaded = rememberHeld(metric, night, range, fresh = fresh?.let { end to it })
    val stats = loaded?.second
    val dataEnd = loaded?.first ?: end
    // Worked out once per load, not on every pass of the screen (audit K4).
    val normal = remember(stats, dataEnd) { stats?.let { History.normal(it, dataEnd) } }
    val normalDays = remember(stats, dataEnd) { stats?.count { it.date in dataEnd.minusDays(History.NORMAL_DAYS - 1L)..dataEnd } ?: 0 }
    // The night's own: its normal from the nights before, and the last 7 nights. Day and Week read against these.
    val nightNormal = remember(night, state, metric) { if (night) SleepCopy.nightNormal(state, metric) else null }
    val nightDays = remember(night, state, metric) { if (night) SleepCopy.nightNormalNights(state, metric) else 0 }
    val nightWeek = remember(night, state, metric) { if (night) SleepCopy.nightWeek(state, metric) else emptyList() }
    val fromNight = night && (range == Range.Day || range == Range.Week)
    val nightly = if (night) range == Range.Day || range == Range.Week || SleepCopy.dailyIsNightly(metric) else metric in NIGHTLY
    val isToday = end == today
    // Live only on today's heart rate by day, and only with the strap connected.
    val heartToday = !night && metric == Metric.HeartRate && isToday
    val live = liveHeartRate.takeIf { heartToday && canUseLive }
    org.openstrap.app.ui.components.HoldLiveHeartRate(heartToday && canUseLive)
    var scrub by remember(metric, night, range, end) { mutableStateOf<DetailHeadline?>(null) }
    var info by rememberSaveable(metric, night) { mutableStateOf(false) }
    // A count's goal bar shows on the day (board HaloDetailSteps).
    val goalBar = !night && metric == Metric.Steps && range == Range.Day
    val goal = org.openstrap.app.ui.dashboard.LocalGoals.current.steps

    // The range's number and the daily values its chart draws (weekly over a year).
    val dataStart = dataEnd.minusDays(range.days - 1L)
    val summary = remember(stats, range, dataStart, dataEnd) {
        stats?.takeIf { range != Range.Day }?.let { History.summary(it, dataStart, dataEnd) }
    }
    val value = when {
        night && range == Range.Day -> SleepCopy.nightValue(state, metric)
        night && range == Range.Week -> nightWeek.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        range == Range.Day -> dayValue(metric, state, live)
        else -> summary?.average
    }
    // Heart rate's normal is a daily average, not a reading, so the day's reading gets no word against it; nor does a
    // night's, nor a latest reading (blood oxygen, stress), which has no baseline of its own.
    val latestReading = !night && range == Range.Day && metric in LatestReading.METRICS
    val status = when {
        fromNight -> History.status(value, nightNormal)
        range == Range.Year || (range == Range.Day && metric == Metric.HeartRate) || latestReading -> null
        else -> History.status(value, normal)
    }
    val label = if (night) SleepCopy.nightLabel(metric, range) else DetailCopy.valueLabel(
        metric, range, live = live != null, today = isToday, estimated = metric == Metric.RestingHr && state.restingEstimated,
        latest = LatestReading.of(metric, state)?.let {
            DetailCopy.Latest(
                clock(it.x), asleep = metric == Metric.Spo2 && LatestReading.spo2Asleep(state),
                level = if (metric == Metric.Stress) StressLevel.of(it.y) else null,
            )
        },
    )
    // What the day is read against your usual with: the status of its value, or for stress, whose headline is the latest
    // reading, of the day's average, the figure the sentence under its chart reads.
    val dayStatus = if (latestReading && metric == Metric.Stress) History.status(state.stress.avg()?.toFloat(), normal) else status
    // The normal the value is read against, for skin temperature's change from it, which its label says after what the
    // number is. A day's only: a longer range's value is an average over days, not a level to place against one night.
    val headNormal = (if (fromNight) nightNormal else normal).takeIf { range == Range.Day }
    // Skin temperature takes the reading's colour: grey-white at your normal, cyan cooler, rose warmer.
    val color = if (metric == Metric.SkinTemperature) {
        MetricColors.temperatureFor(value?.let { v -> headNormal?.let { v - it.mean } })
    } else colorOf(metric)
    // The time the day's data ends at, which a headline says so a partial day reads as one: the last heart rate reading.
    val dataAt = state.heartMinutes.lastOrNull()?.first ?: state.heartRate.maxOfOrNull { it.x }
    val learning = (if (fromNight) nightNormal else normal) == null && range != Range.Year && !latestReading && metric != Metric.HeartRate
    val word = when {
        status != null && metric == Metric.SkinTemperature && headNormal != null && value != null ->
            DetailCopy.skinChangeWord(metric, value - headNormal.mean, status)
        status != null -> DetailCopy.statusWord(status)
        // A value on a learning day still says its state, with how far along it is.
        value != null && learning -> DetailCopy.learningNormal(if (fromNight) nightDays else normalDays, nightly)
        else -> null
    }
    // The value of a day's reading, null on a night, a range, or no reading.
    val dayValue = if (!night && range == Range.Day) value else null
    val headline = when {
        // Every Signal, Vital and Count reading says its status word and when it was taken (rules 3).
        dayValue != null && metric == Metric.HeartRate -> DetailHeadline(
            metric.format(dayValue, Units.DOT_LOCALE), "",
            stringResource(if (dayValue < CALM_BELOW_BPM) R.string.glow_detail_hr_calm else R.string.glow_detail_hr_raised),
            metric.unit,
            context = if (live != null) {
                stringResource(R.string.glow_detail_live)
            } else {
                dataAt?.let { stringResource(R.string.core_today_heart_at, clock(it)) }.orEmpty()
            },
        )
        dayValue != null && metric == Metric.Spo2 -> DetailHeadline(
            metric.format(dayValue, Units.DOT_LOCALE), "", stringResource(spo2Word(dayValue)), metric.unit,
            context = LatestReading.of(metric, state)?.let {
                LatestReading.spo2Line(it.x, LatestReading.sleepWindow(state), Long.MAX_VALUE / 2, Strings.app, ::clock)
            }.orEmpty(),
        )
        dayValue != null && metric == Metric.Stress -> DetailHeadline(
            metric.format(dayValue, Units.DOT_LOCALE), "", StressLevel.of(dayValue).word(Strings.app), null,
            context = LatestReading.of(metric, state)?.let { stringResource(R.string.core_today_heart_at, clock(it.x)) }.orEmpty(),
        )
        dayValue != null && metric == Metric.RestingHr -> DetailHeadline(
            metric.format(dayValue, Units.DOT_LOCALE), "", word, metric.unit,
            context = stringResource(if (isToday) R.string.core_detail_none_last_night else R.string.core_detail_none_that_night) +
                if (state.restingEstimated) ", " + stringResource(R.string.glow_detail_estimated) else "",
        )
        dayValue != null && metric == Metric.Steps -> DetailHeadline(
            metric.format(dayValue, Units.DOT_LOCALE), "",
            stringResource(
                if (dayValue >= goal) R.string.glow_detail_steps_goal_reached else R.string.glow_detail_steps_below_goal,
            ),
            metric.unit,
            context = if (isToday) dataAt?.let { stringResource(R.string.glow_detail_steps_so_far_to, clock(it)) }
                ?: stringResource(R.string.core_detail_none_so_far_today) else stringResource(R.string.core_detail_none_that_day),
        )
        value != null -> DetailHeadline(metric.format(value, Units.DOT_LOCALE), label, word)
        night && range == Range.Day -> SleepCopy.nightNoValue(state.sleep != null, isToday).let { (w, why) -> DetailHeadline.none(w, why) }
        night && range == Range.Week ->
            DetailHeadline.none(stringResource(R.string.glow_no_reading_yet), stringResource(R.string.glow_metrics_last_7_nights))
        range != Range.Day && stats == null ->
            DetailHeadline.none(stringResource(R.string.glow_loading), DetailCopy.noValue(metric, range, isToday))
        else -> DetailHeadline.none(stringResource(R.string.glow_no_reading_yet), DetailCopy.noValue(metric, range, isToday))
    }
    // A count's best day and daily average over the days loaded: the last 30, or the range's own.
    val countDays = remember(stats, range, dataEnd, metric) {
        if (metric != Metric.Steps) emptyList() else {
            val span = if (range == Range.Day) History.NORMAL_DAYS else range.days
            stats.orEmpty().filter { it.date in dataEnd.minusDays(span - 1L)..dataEnd }
        }
    }
    val figures = detailFigures(
        metric, night, range, state, summary, nightWeek.map { it.second }, if (fromNight) nightNormal else normal,
        value, stats?.let { History.summary(it, dataEnd.minusDays(6), dataEnd)?.average }, nightly, goal, countDays,
    )
    val withData = remember(stats, nightWeek) {
        stats.orEmpty().map { it.date }.toSet() + nightWeek.filter { it.second != null }.map { it.first }
    }

    // The picture, which crossfades when the range changes while the header and figures stay put (P6).
    val picture: @Composable (Range) -> Unit = { r ->
        val rStart = dataEnd.minusDays(r.days - 1L)
        if (night && r == Range.Day) {
            NightChart(metric, state, color) { scrub = it }
        } else if (night && r == Range.Week) {
            val values = nightWeek.map { it.second }
            if (values.all { it == null }) {
                DetailEmpty(stringResource(R.string.glow_metrics_none_last_7_nights))
            } else {
                Column(Modifier.padding(top = Space.S8)) {
                    DetailColumns(
                        values, color, nightWeek.map { DayWords.weekdayShort(it.first) },
                        baseline = nightNormal?.mean, band = nightNormal?.let { it.low..it.high },
                        bandLabel = stringResource(R.string.glow_detail_your_normal), format = { metric.format(it) },
                        onScrub = { i ->
                            scrub = i?.let { nightWeek[it] }?.let { (d, v) ->
                                v?.let {
                                    val word = DetailCopy.statusWord(History.status(it, nightNormal))
                                    val unitAndDay = Strings.app.format(R.string.glow_metrics_unit_day, metric.unit, DayWords.short(d))
                                    DetailHeadline(metric.format(it, Units.DOT_LOCALE), unitAndDay, word)
                                }
                            }
                        },
                    )
                }
            }
        } else if (r == Range.Day) {
            DayChart(metric, state, color, value != null, normalOf = { m -> rememberNormal(m, end, history) }) { scrub = it }
        } else {
            val inRange = stats
            when {
                inRange == null -> DetailEmpty(stringResource(R.string.glow_loading))
                inRange.none { it.date in rStart..dataEnd } -> DetailEmpty(stringResource(R.string.glow_metrics_none_in_range))
                else -> {
                    val stepsGoal = goal.takeIf { metric == Metric.Steps }
                    HistoryChart(metric, r, inRange, normal, rStart, dataEnd, color, nightly, stepsGoal) { scrub = it }
                }
            }
        }
    }

    DetailTemplate(
        metric.title, color, siblings(Detail.OfMetric(metric, night), onSwitch), onInfo = { info = true }, onBack = onBack,
        contentPadding = contentPadding, headline = headline,
        strip = DetailCopy.strip(end, today, withData), onDay = onSelectDay,
        ranges = Range.entries, range = range, onRange = { rangeIndex = it.ordinal }, scrubbed = { scrub },
        // A count's goal: a solid bar with its mark, and "of 8,000" (board HaloDetailSteps). Laid out with no steps too.
        heroBelow = if (goalBar) {
            { DetailGoalBar((value ?: 0f) / goal, color, stringResource(R.string.glow_detail_of_goal, Units.count(goal))) }
        } else null,
    ) {
        item { RangeCrossfade(range, picture) }
        if (night && range == Range.Day) {
            val sentence = when {
                value == null -> null
                metric == Metric.HeartRate -> SleepCopy.lowestHeart(state).let { low ->
                    SleepCopy.nightHeart(low?.y?.let(Math::round), low?.x?.let(::clock), Math.round(value), isToday)
                }
                else -> DetailCopy.day(status, org.openstrap.app.presentation.DaySource.Night, isToday, nightDays)
            }
            if (sentence != null) item { DetailSentence(sentence) }
        } else if (night && range == Range.Week) {
            val values = nightWeek.map { it.second }
            val sentence = if (metric == Metric.HeartRate) SleepCopy.heartWeek(values.map { it?.let(Math::round) })
            else DetailCopy.span(Range.Week, DetailCopy.levels(values, nightNormal), nightly = true, normalKnown = nightNormal != null, days = nightDays)
            if (sentence != null) item { DetailSentence(sentence) }
        } else if (range == Range.Day) {
            val sentence = when {
                value == null || (latestReading && metric == Metric.Spo2) -> null
                // The day's stored lowest and highest readings, the strap's own maximum when higher, not the chart's averages.
                metric == Metric.HeartRate -> state.heartRateRange?.let { (low, high) -> DetailCopy.heartDay(low, high, isToday) }
                // A count's sentence is about its goal, which the bar shows, not a repeat of the status word.
                metric == Metric.Steps -> Strings.app.format(
                    if (value >= goal) R.string.glow_detail_steps_done else R.string.glow_detail_steps_to_go,
                    Units.count(Math.round(goal - value).coerceAtLeast(0)),
                )
                else -> DetailCopy.day(dayStatus, org.openstrap.app.presentation.daySource(metric), isToday, normalDays)
            }
            if (sentence != null) item { DetailSentence(sentence) }
        } else {
            val inRange = stats
            val sentence = when {
                inRange == null || summary == null -> null
                range == Range.Year ->
                    History.weekly(inRange.filter { it.date in dataStart..dataEnd }).takeIf { it.isNotEmpty() }?.let { w ->
                        DetailCopy.year(metric.format(w.minOf { it.value }), metric.format(w.maxOf { it.value }), metric.unit)
                    }
                else -> {
                    val days = generateSequence(dataStart) { it.plusDays(1) }.takeWhile { !it.isAfter(dataEnd) }.toList()
                    val byDate = inRange.associate { it.date to it.value }
                    DetailCopy.span(range, DetailCopy.levels(days.map { byDate[it] }, normal), nightly, normal != null, normalDays)
                }
            }
            if (sentence != null) item { DetailSentence(sentence) }
        }
        if (figures != null) item { HaloFigures(figures, Modifier.padding(top = Space.S4)) }
        val related = RELATED[metric].orEmpty()
        val relatedScores = RELATED_SCORES[metric].orEmpty()
        if (related.isNotEmpty() || relatedScores.isNotEmpty() || night) {
            item { RelatedList(related, relatedScores, state, recovery, history, onOpen, night = night) }
        }
    }

    if (info) {
        // Scoped to the night, the sheet reads the night against your earlier nights, as Day and Week do.
        val sheetStatus = when {
            night -> History.status(SleepCopy.nightValue(state, metric), nightNormal)
            range == Range.Year -> null
            latestReading -> dayStatus
            else -> History.status(value, normal)
        }
        InfoSheet(
            if (night) InfoCopy.ofNight(metric) else InfoCopy.of(metric), color, InfoCopy.normalBar(sheetStatus),
            // Heart rate's night has no normal of its own, so there's no line about one.
            if (night && metric == Metric.HeartRate) null
            else if (night) InfoCopy.normalLine(nightNormal, nightDays, metric::format, metric.unit, nightly = true)
            else InfoCopy.normalLine(normal, normalDays, metric::format, metric.unit, nightly),
            onDismiss = { info = false },
        )
    }
}

/** Heart rate under this many beats a minute is Calm in a detail's headline, above it Raised. */
private const val CALM_BELOW_BPM = 100f

/** Blood oxygen's word for a reading: from 95 % Normal, from 90 % Slightly low, else Low. */
private fun spo2Word(percent: Float): Int = when {
    percent >= SPO2_NORMAL_FROM -> R.string.glow_detail_spo2_normal
    percent >= SPO2_SLIGHTLY_LOW_FROM -> R.string.glow_detail_spo2_slightly_low
    else -> R.string.glow_detail_spo2_low
}

private const val SPO2_NORMAL_FROM = 95f
private const val SPO2_SLIGHTLY_LOW_FROM = 90f

/**
 * [diff] as "+7 bpm" or "−11 bpm", written by [show] (which gives a size without a sign). A difference that shows as
 * zero has no sign, "0 bpm", not "+0 bpm".
 */
internal fun signedFigure(diff: Float, show: (Float) -> String?): String? {
    val size = show(kotlin.math.abs(diff)) ?: return null
    val zero = size.takeWhile { it.isDigit() || it == '.' || it == ',' }.all { it == '0' || it == '.' || it == ',' }
    return if (zero || diff == 0f) size else (if (diff < 0f) "−" else "+") + size
}

/** How far the shown day's [value] is from [normal]'s mean: last night's against your normal, which the status word agrees with. */
internal fun vsNormal(value: Float?, normal: Float?): Float? = if (value != null && normal != null) value - normal else null


/**
 * The night's readings of [metric] while asleep as a line to scrub, from falling asleep to waking ([SleepCopy.nightSeries]);
 * scrubbing moves the headline.
 */
@Composable
private fun NightChart(metric: Metric, state: DayUiState, color: Color, onScrub: (DetailHeadline?) -> Unit) {
    val session = state.sleep
    val series = SleepCopy.nightSeries(state, metric)
    if (session == null || series.isEmpty()) {
        DetailEmpty(
            if (session == null) {
                SleepCopy.noNight(state.date == LocalDate.now())
            } else {
                stringResource(R.string.glow_metrics_none_asleep)
            },
        )
        return
    }
    val window = session.start..session.end
    // Heart rate and breathing come every few minutes in sleep, the rest more sparsely.
    val slot = if (metric == Metric.HeartRate || metric == Metric.Breathing) 5 * 60L else 15 * 60L
    val values = remember(series, window) { bucket(series, window, slot) }
    Column(Modifier.padding(top = Space.S8)) {
        DetailLineChart(
            values, color, listOf(clock(window.first), clock(window.last)), yRange = SPO2_AXIS.takeIf { metric == Metric.Spo2 },
            format = metric::format,
            onScrub = { i ->
                onScrub(i?.let { values[it] }?.let { v -> scrubAt(metric, v, window.first + i * slot, slot) })
            },
        )
    }
}

/**
 * A scrubbed point of [metric]'s line in the headline: its value and the time it starts. The line is made of averages
 * over [slot] seconds, so it says so: "bpm, 5 min average from 22:30".
 */
private fun scrubAt(metric: Metric, v: Float, at: Long, slot: Long, strings: Strings = Strings.app) = DetailHeadline(
    metric.format(v, Units.DOT_LOCALE),
    if (metric.unit.isBlank()) {
        strings.format(R.string.glow_metrics_average_from_time, slot / SECONDS_PER_MINUTE, clock(at))
    } else {
        strings.format(R.string.glow_metrics_unit_average_from_time, metric.unit, slot / SECONDS_PER_MINUTE, clock(at))
    },
    null,
)

private const val SECONDS_PER_MINUTE = 60L

/** A held hour's steps in the headline: "steps, 09:00 to 10:00". */
private fun hourSteps(hour: Int, steps: Float, strings: Strings = Strings.app): DetailHeadline {
    val (from, to) = TimeOfDay.minutes(hour * MINUTES_PER_HOUR) to TimeOfDay.minutes((hour + 1) * MINUTES_PER_HOUR)
    return DetailHeadline(
        Units.count(Math.round(steps), Units.DOT_LOCALE), strings.format(R.string.glow_metrics_steps_hours, Ranges.of(from, to)), null,
    )
}

private const val MINUTES_PER_HOUR = 60

/** Blood oxygen's chart scale, so the lowest reading doesn't sit on the bottom edge: a fixed 80 to 100 %. */
private val SPO2_AXIS = 80f..100f

/**
 * The selected day's own chart, 200 dp tall whatever the data (the frame never moves with it, P1): steps by the hour,
 * the week of resting heart rates from your normal, or the day's readings as a line to scrub, which moves the headline
 * ([onScrub], null on release).
 */
@Composable
private fun DayChart(metric: Metric, state: DayUiState, color: Color, hasValue: Boolean, normalOf: @Composable (Metric) -> History.Normal?, onScrub: (DetailHeadline?) -> Unit) {
    val day = state.dayStart..state.dayEnd
    val (series, window) = when (metric) {
        Metric.HeartRate -> state.heartRate to day
        Metric.Hrv -> state.hrv to day
        Metric.Spo2 -> state.spo2 to day
        Metric.Stress -> state.stress to day
        Metric.SkinTemperature -> state.skinTemperature to day
        Metric.Breathing -> state.breathRate to (state.sleep?.let { it.start..it.end } ?: day)
        else -> emptyList<ChartPoint>() to day
    }
    val empty = !hasValue || when (metric) {
        Metric.Steps -> false
        Metric.RestingHr -> state.restingTrend.none { it.value != null }
        else -> series.isEmpty()
    }
    if (empty) {
        DetailEmpty(
            if (metric == Metric.Breathing) {
                stringResource(R.string.glow_metrics_breathing_in_sleep)
            } else {
                stringResource(R.string.glow_metrics_none_this_day)
            },
        )
        return
    }
    Column(Modifier.padding(top = Space.S8)) {
        when (metric) {
            // Holding an hour puts its steps in the headline. The hour now is white (board HaloDetailSteps). Hours that
            // haven't happened yet are not drawn, rather than drawn as zero.
            Metric.Steps -> {
                val nowHour = java.time.LocalTime.now().hour.takeIf { state.date == LocalDate.now() }
                val hours = remember(state.hourlySteps, nowHour) {
                    state.hourlySteps.mapIndexed { h, v -> if (nowHour != null && h > nowHour) null else v }
                }
                DetailColumns(
                    hours, color, HOURS, alpha = 0.75f, highlight = nowHour,
                    format = { Units.count(Math.round(it), Units.DOT_LOCALE) },
                    onScrub = { h -> onScrub(h?.let { hourSteps(it, state.hourlySteps[it]) }) },
                )
            }
            // A vital: the week's nights as columns from your normal, the band behind them (rules, Vital).
            Metric.RestingHr -> {
                val normal = normalOf(Metric.RestingHr)
                DetailColumns(
                    state.restingTrend.map { it.value }, color, state.restingTrend.map { it.label },
                    highlight = state.restingTrend.indexOfLast { it.value != null },
                    baseline = normal?.mean, band = normal?.let { it.low..it.high },
                    bandLabel = stringResource(R.string.glow_detail_your_normal), format = { metric.format(it) },
                )
            }
            else -> {
                // Readings come every minute for heart rate, sparser for the rest.
                val slot = if (metric == Metric.HeartRate) 5 * 60L else 15 * 60L
                val values = remember(series, window) { bucket(series, window, slot) }
                val labels = if (window == day) HOURS else listOf(clock(window.first), clock(window.last))
                // The hour bands run from the stored minute readings, so the day's low and high are on the chart.
                val spread = if (metric == Metric.HeartRate) {
                    remember(state.heartMinutes, window, slot, values.size) { spreadOf(state.heartMinutes, window, slot, values.size) }
                } else null
                // Blood oxygen's hours inside the sleep window are shaded, so a night reading shows as one.
                val sleep = LatestReading.sleepWindow(state)
                val shade = if (metric == Metric.Spo2) LatestReading.shadeIndices(sleep, window, slot, values.size) else null
                val stripe = metric == Metric.Stress && state.stressStrip.any { it != null }
                // Stress's day as one strip, calm to high, is part of its picture and shares its 200 dp.
                DetailLineChart(
                    values, color, labels, shade = shade, spread = spread,
                    shadeLabel = stringResource(R.string.glow_detail_asleep_note).takeIf { shade != null },
                    hourBands = window == day && metric != Metric.SkinTemperature,
                    height = if (metric == Metric.Stress) STRESS_LINE_HEIGHT else DetailChartHeight,
                    yRange = (SPO2_AXIS.takeIf { metric == Metric.Spo2 }) ?: state.heartRateRange?.takeIf { metric == Metric.HeartRate }
                        ?.let { (lo, hi) -> lo.toFloat()..hi.toFloat() },
                    format = metric::format,
                    onScrub = { i ->
                        onScrub(i?.let { values[it] }?.let { v -> scrubAt(metric, v, window.first + i * slot, slot) })
                    },
                )
                if (metric == Metric.Stress) {
                    if (stripe) DetailPicture(height = 24.dp) { m -> DayStrip(state.stressStrip, color, PictureSize.Wide, m) }
                    else Spacer(Modifier.height(Space.S24 + Space.S12 + Space.S4))
                    Text(
                        stringResource(R.string.glow_detail_stress_strip_note), color = GlowType.noteColor, style = GlowType.noteSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Stress's line leaves room under it for its strip and the note that says what the strip is. */
private val STRESS_LINE_HEIGHT = 140.dp

/** Each slot's lowest and highest reading, from the stored [points] (time, value), for the bands behind a line. */
private fun spreadOf(
    points: List<Pair<Long, Int>>, window: LongRange, slot: Long, count: Int,
): List<ClosedFloatingPointRange<Float>?> {
    val lows = FloatArray(count) { Float.MAX_VALUE }
    val highs = FloatArray(count) { -Float.MAX_VALUE }
    points.forEach { (t, v) ->
        val i = ((t - window.first) / slot).toInt()
        if (i in 0 until count) {
            lows[i] = minOf(lows[i], v.toFloat())
            highs[i] = maxOf(highs[i], v.toFloat())
        }
    }
    return List(count) { if (highs[it] < lows[it]) null else lows[it]..highs[it] }
}

/**
 * A week, month or year of daily values (weekly over a year), by the kind of data (rules 2): a Signal (heart rate, blood
 * oxygen, stress) as a low to high capsule per day with the average marked; a count as bars with a dashed [goal]; the
 * rest as columns from your normal, its band behind them. Scrubbing puts the day under the finger in the headline.
 */
@Composable
private fun HistoryChart(
    metric: Metric, range: Range, stats: List<DayStat>, normal: History.Normal?, start: LocalDate, end: LocalDate, color: Color,
    nightly: Boolean, goal: Int?, onScrub: (DetailHeadline?) -> Unit,
) {
    val (values, labels, dates) = remember(stats, range, start, end) { series(range, stats, start, end) }
    val signal = metric in SIGNALS
    val ranges = remember(stats, range, start, end) { if (signal) rangeStats(range, stats, start, end) else emptyList() }
    fun held(i: Int?) = i?.let { values[it] }?.let { v ->
        val d = dates[i]
        val label = if (range == Range.Year) {
            Strings.app.format(R.string.glow_metrics_week_of, DayWords.dayMonth(d))
        } else {
            DayWords.short(d)
        }
        val unitAndDay = if (metric.unit.isEmpty()) {
            label
        } else {
            Strings.app.format(R.string.glow_metrics_unit_day, metric.unit, label)
        }
        val word = if (signal) null else DetailCopy.statusWord(History.status(v, normal))
        DetailHeadline(metric.format(v, Units.DOT_LOCALE), unitAndDay, word)
    }
    Column(Modifier.padding(top = Space.S8), verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        // A count stands on the axis; the rest run from your normal, its band behind them (rules, Vital and Count).
        val count = metric == Metric.Steps
        val few = values.size <= FEW_DAYS
        val showBandNote = normal != null && !few && !count && !signal
        if (showBandNote) Text(
            stringResource(R.string.glow_detail_band_note), color = GlowType.noteColor, style = GlowType.noteSmall,
            modifier = Modifier.padding(horizontal = Space.S4),
        )
        if (signal) DetailRanges(
            ranges, color, labels, format = { metric.format(it) }, onScrub = { i -> onScrub(held(i)) },
        ) else DetailColumns(
            values, color, labels, alpha = if (count) 0.75f else 0.55f,
            baseline = normal?.mean?.takeIf { !count }, band = normal?.takeIf { !count }?.let { it.low..it.high },
            bandLabel = stringResource(R.string.glow_detail_your_normal),
            goal = goal?.toFloat(),
            format = { metric.format(it) },
            onScrub = { i -> onScrub(held(i)) },
        )
        if (normal == null && !count && !signal) Text(
            DetailCopy.learningNormal(stats.count { it.date in end.minusDays(History.NORMAL_DAYS - 1L)..end }, nightly),
            color = Ink.Secondary, style = GlowType.secondary,
        )
    }
}

/** The metrics that move all day, which a longer range draws as low to high capsules (rules, Signal). */
private val SIGNALS = setOf(Metric.HeartRate, Metric.Spo2, Metric.Stress)

/** A week's chart has this many days or fewer. */
private const val FEW_DAYS = 7

/** The ranges' stats aligned with [series]'s dates: each day's low, average and high, or a week's over a year; null for a gap. */
internal fun rangeStats(range: Range, stats: List<DayStat>, start: LocalDate, end: LocalDate): List<DayStat?> {
    val inRange = stats.filter { it.date in start..end }
    return if (range == Range.Year) {
        val firstDay = Weeks.firstDay()
        val weeks = History.weekly(inRange, firstDay).associateBy { it.date }
        generateSequence(Weeks.start(start, firstDay)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.map { weeks[it] }.toList()
    } else {
        val byDate = inRange.associateBy { it.date }
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { byDate[it] }.toList()
    }
}

/** Evenly spaced values for the chart: one a day, or one a week over a year; gaps are null. Also axis labels and each point's date. */
internal fun series(
    range: Range, stats: List<DayStat>, start: LocalDate, end: LocalDate,
): Triple<List<Float?>, List<String>, List<LocalDate>> {
    val inRange = stats.filter { it.date in start..end }
    val dates: List<LocalDate>
    val byDate: Map<LocalDate, Float>
    if (range == Range.Year) {
        // The chart's weeks and History's must start on the same day, or no week finds its average.
        val firstDay = Weeks.firstDay()
        dates = generateSequence(Weeks.start(start, firstDay)) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
        byDate = History.weekly(inRange, firstDay).associate { it.date to it.value }
    } else {
        dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        byDate = inRange.associate { it.date to it.value }
    }
    val labels = when (range) {
        Range.Week -> dates.map { DayWords.weekdayShort(it) }
        Range.Month -> listOf(0f, 1 / 3f, 2 / 3f, 1f).map { DayWords.dayMonth(dates[((dates.size - 1) * it).toInt()]) }
        else -> listOf(0f, 1 / 3f, 2 / 3f, 1f).map { DayWords.monthShort(dates[((dates.size - 1) * it).toInt()]) }
    }
    return Triple(dates.map { byDate[it] }, labels, dates)
}

/**
 * Related metrics as list rows, each with the day's value and its word against your normal, opening its detail. With
 * [sleepRow], as a night-scoped detail has, the night's Sleep score leads.
 */
// The scores come from the metric's fixed definition and never change in place, so a stable list type wouldn't skip more often.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun RelatedList(
    related: List<Metric>, scores: List<Score>, state: DayUiState, recovery: org.openstrap.app.data.RecoveryResult?,
    history: DailyHistory, onOpen: (Detail) -> Unit, night: Boolean = false,
) {
    // A night's Sleep score leads, unless the metric lists it among its own related scores, where it takes its place.
    val sleepFirst = night && Score.Sleep !in scores
    RelatedPanel {
        if (sleepFirst) SleepScoreRow(state, divider = false, onOpen)
        related.forEachIndexed { i, m -> RelatedMetricRow(m, state, history, divider = i > 0 || sleepFirst, onOpen, nightScoped = night) }
        scores.forEachIndexed { i, score ->
            val divider = i > 0 || related.isNotEmpty() || sleepFirst
            when (score) {
                Score.Sleep -> SleepScoreRow(state, divider, onOpen)
                Score.Recovery -> RecoveryScoreRow(recovery?.readiness, divider, onOpen)
                else -> ActivityScoreRow(score, state, recovery, divider, onOpen)
            }
        }
    }
}

/**
 * The three figures under a metric's chart, always three so the page keeps its shape, a dash for one that can't be
 * worked out yet (boards HaloDetailHeart, HaloDetailHeartWeek, HaloDetailHrv, HaloDetailSteps). A Signal (heart rate,
 * blood oxygen, stress) gives its low, average and high for the range shown. A Count (steps) gives its goal, best day
 * and daily average. A Vital gives your normal, the last 7 nights' (or days') average and how far the shown [value] sits
 * from your normal, the same value the status word and the headline read.
 */
// The night values arrive whole with each update and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
@ReadOnlyComposable
private fun detailFigures(
    metric: Metric, night: Boolean, range: Range, state: DayUiState, summary: History.Summary?, nightValues: List<Float?>,
    normal: History.Normal?, value: Float?, weekAverage: Float?, nightly: Boolean, goal: Int, countDays: List<DayStat>,
): List<Pair<String?, String>>? {
    fun show(v: Float?) = v?.let { "${metric.format(it, Units.DOT_LOCALE)} ${metric.unit}".trim() }
    return when {
        metric in SIGNALS -> {
            val (low, avg, high) = signalTrio(metric, night, range == Range.Day, state, summary, nightValues)
            listOf(
                show(low) to stringResource(R.string.glow_detail_low),
                show(avg) to stringResource(R.string.glow_detail_average),
                show(high) to stringResource(R.string.glow_detail_high),
            )
        }
        metric == Metric.Steps -> listOf(
            show(goal.toFloat()) to stringResource(R.string.glow_detail_goal),
            show(countDays.maxOfOrNull { it.value }) to stringResource(R.string.glow_detail_best_day),
            show(countDays.takeIf { it.isNotEmpty() }?.map { it.value }?.average()?.toFloat()) to
                stringResource(R.string.glow_detail_daily_average),
        )
        else -> {
            val last7 = if (night) nightValues.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.toFloat() else weekAverage
            val vs = vsNormal(value, normal?.mean)
            listOf(
                show(normal?.mean) to stringResource(R.string.glow_detail_your_normal),
                show(last7) to
                    stringResource(if (nightly) R.string.glow_detail_seven_night_average else R.string.glow_detail_seven_day_average),
                vs?.let { signedFigure(it, ::show) } to stringResource(R.string.glow_detail_vs_normal),
            )
        }
    }
}

/** A Signal's low, average and high for [detailFigures], from the night, the day's stored readings or a longer range's summary. */
// The night values arrive whole with each update and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
private fun signalTrio(
    metric: Metric, night: Boolean, day: Boolean, state: DayUiState, summary: History.Summary?, nightValues: List<Float?>,
): Triple<Float?, Float?, Float?> = when {
    night && day && metric == Metric.HeartRate -> {
        val bpm = state.sleepHeartMinutes.map { it.second.toFloat() }
        Triple(bpm.minOrNull(), SleepCopy.nightValue(state, Metric.HeartRate), bpm.maxOrNull())
    }
    night && day -> SleepCopy.nightSeries(state, metric).map { it.y }.let {
        Triple(it.minOrNull(), it.takeIf { v -> v.isNotEmpty() }?.average()?.toFloat(), it.maxOrNull())
    }
    night -> nightValues.filterNotNull().let {
        Triple(it.minOrNull(), it.takeIf { v -> v.isNotEmpty() }?.average()?.toFloat(), it.maxOrNull())
    }
    day && metric == Metric.HeartRate ->
        Triple(state.heartRateRange?.first?.toFloat(), state.heartRate.avg()?.toFloat(), state.heartRateRange?.second?.toFloat())
    day -> (if (metric == Metric.Spo2) state.spo2 else state.stress).map { it.y }.let {
        Triple(it.minOrNull(), it.takeIf { v -> v.isNotEmpty() }?.average()?.toFloat(), it.maxOrNull())
    }
    else -> Triple(summary?.lowest?.value, summary?.average, summary?.highest?.value)
}

/** The "Related" list at the foot of a detail: the details next to this one, each a row opening it. */
@Composable
internal fun RelatedPanel(content: @Composable ColumnScope.() -> Unit) =
    DetailPanel(Modifier.padding(top = Space.S8), title = stringResource(R.string.glow_metrics_related), content = content)

/** A score as a related row: its [value] and [word] beside its name and icon, opening its detail. */
@Composable
internal fun ScoreRow(score: Score, color: Color, value: String?, word: String, divider: Boolean, onOpen: (Detail) -> Unit) = MetricRow(
    score.title, color, value, word, onClick = { onOpen(Detail.OfScore(score)) }, divider = divider, icon = score.rowIcon(),
)

/** One of Steps' Activity neighbours as a related row, with the value and line the Activity tab gives it. */
@Composable
private fun ActivityScoreRow(
    score: Score, state: DayUiState, recovery: org.openstrap.app.data.RecoveryResult?, divider: Boolean, onOpen: (Detail) -> Unit,
) {
    val a = recovery?.activity
    when (score) {
        Score.HeartPoints -> {
            val points = state.pai?.total?.let(Math::round)
            ScoreRow(
                score, ActivityLime, points?.toString(),
                points?.let {
                    org.openstrap.app.presentation.TabCopy.heartPoints(it, org.openstrap.app.presentation.ActivityCopy.HEART_POINTS_TARGET)
                } ?: AFTER_NEXT_SYNC,
                divider, onOpen,
            )
        }
        Score.ActiveMinutes -> ScoreRow(
            score, ActivityLime, a?.weekEquivalent?.toString(),
            a?.let {
                org.openstrap.app.presentation.TabCopy.activeMinutes(it.weekEquivalent, org.openstrap.app.data.Activity.MVPA_WEEKLY_TARGET)
            } ?: NO_VALUE,
            divider, onOpen,
        )
        Score.Calories -> {
            val e = recovery?.calories
            val active = e?.activeKcal?.let { kotlin.math.round(it).toInt() }
            ScoreRow(
                score, org.openstrap.app.ui.theme.MetricColors.calories, active?.let { Units.count(it) },
                e?.let {
                    org.openstrap.app.ui.dashboard.CaloriesCopy.rowLine(
                        active ?: 0, org.openstrap.app.ui.dashboard.LocalGoals.current.calories, kotlin.math.round(it.totalKcal).toInt(),
                    )
                } ?: stringResource(R.string.glow_activity_add_profile_to_see),
                divider, onOpen,
            )
        }
        else -> {
            val distance = a?.distanceMeters ?: state.distanceMeters?.toFloat()
            ScoreRow(
                score, org.openstrap.app.ui.theme.MetricColors.steps, distance?.let { Units.km(it, Units.Precision.Day) },
                if (distance == null) NO_VALUE else org.openstrap.app.presentation.ActivityCopy.distanceSource(a?.distanceMeters != null),
                divider, onOpen,
            )
        }
    }
}

/**
 * The night's Sleep score as a related row, with its word. A night with no score says so, as the Sleep tab does, rather
 * than "Nothing for this night" beside a night that is drawn.
 */
@Composable
internal fun SleepScoreRow(state: DayUiState, divider: Boolean, onOpen: (Detail) -> Unit) {
    val score = state.sleep?.score
    ScoreRow(
        Score.Sleep, org.openstrap.app.ui.theme.MetricColors.sleepDeep, score?.toString(),
        score?.let(org.openstrap.app.presentation.TabCopy::sleepScoreWord)
            ?: if (state.sleep != null) stringResource(R.string.glow_sleep_no_score) else NO_NIGHT_VALUE,
        divider, onOpen,
    )
}

/**
 * The day's Recovery as a related row, with its word: what the Recovery tab shows ([TodayCopy.recoveryShown]), so a day
 * with no score says why, or shows the strap's estimate when [strapScore] is given, rather than "No reading".
 */
@Composable
internal fun RecoveryScoreRow(
    ready: org.openstrap.app.data.Recovery.Readiness?, divider: Boolean, onOpen: (Detail) -> Unit, strapScore: Int? = null,
) {
    val shown = org.openstrap.app.presentation.TodayCopy.recoveryShown(ready, strapScore, NO_VALUE)
    ScoreRow(
        Score.Recovery, org.openstrap.app.ui.theme.MetricColors.readiness, shown.value?.toString(), shown.word ?: shown.line, divider, onOpen,
    )
}

/**
 * A body signal as a related row: the day's value and its word against your normal, marked when it's off normal. On a
 * page scoped to the night ([nightScoped]) blood oxygen is the night's average, elsewhere its latest reading, as
 * stress's is; stress's High tag judges that reading's level.
 */
@Composable
internal fun RelatedMetricRow(
    m: Metric, state: DayUiState, history: DailyHistory, divider: Boolean, onOpen: (Detail) -> Unit, nightScoped: Boolean = false,
) {
    // A night signal reads as everywhere else: last night's against the nights before (SleepCopy.NIGHT_SIGNALS).
    val night = m in SleepCopy.NIGHT_SIGNALS || (m == Metric.Spo2 && nightScoped)
    // Blood oxygen and stress on a day-scoped page are their latest reading, which has no normal to be read against.
    val latest = m in LatestReading.METRICS && !night
    val dayNormal = rememberNormal(m, state.date, history)
    val normal = if (night) SleepCopy.nightNormal(state, m) else dayNormal
    val v = if (night) SleepCopy.nightValue(state, m) else dayValue(m, state)
    // The heart rate moves all day, so it has no word against a normal; nor has a latest reading.
    val status = if (m == Metric.HeartRate || latest) null else History.status(v, normal)
    // A percent stays on its number, as on Recovery ("99%"): the line under it is the word, not the unit.
    val tag = relatedTag(m, v, status)
    MetricRow(
        // The unit stays on the number, as the boards write "60 bpm" and "57 ms", so the line under it is only the word.
        m.title, colorOf(m), v?.let { if (m == Metric.Spo2) "${m.format(it)}%" else "${m.format(it)} ${m.unit}".trim() },
        when {
            v == null -> NO_VALUE
            // Blood oxygen on a night page is the night's average.
            night && m == Metric.Spo2 -> stringResource(R.string.glow_today_spo2_night_average)
            // A night signal whose normal is still forming says so, with the count, as on every tab.
            night && normal == null -> SleepCopy.learning(state, m)
            // The time shows once the reading is an hour old or more.
            latest -> latestLine(m, state, java.time.Instant.now().epochSecond, tagged = tag != null, time = ::clock)
            // The tag already says which way it is off.
            tag != null -> null
            else -> DetailCopy.statusWord(status)
        },
        tag = tag, onClick = { onOpen(Detail.OfMetric(m, night)) }, divider = divider,
        icon = m.rowIcon(),
    )
}

/** A related row's tag: stress is judged by the level of its reading [v] (High is tagged), the rest by [status] against their normal. */
private fun relatedTag(m: Metric, v: Float?, status: Int?): org.openstrap.app.ui.dashboard.NewsTag? =
    if (m == Metric.Stress) v?.let { LatestReading.stressTag(StressLevel.of(it)) } else org.openstrap.app.ui.dashboard.bodyTag(m, status)

/**
 * The line under [m]'s latest reading of the day in [state] (blood oxygen or stress) at [now]; null without a reading,
 * or for a stress reading whose High [tagged] tag says it already and is fresh.
 */
internal fun latestLine(
    m: Metric, state: DayUiState, now: Long, strings: Strings = Strings.app, tagged: Boolean = false, time: (Long) -> String,
): String? {
    val at = LatestReading.of(m, state) ?: return null
    return if (m == Metric.Spo2) LatestReading.spo2Line(at.x, LatestReading.sleepWindow(state), now, strings, time)
    else LatestReading.stressLine(at.x, at.y, now, tagged, strings, time)
}
