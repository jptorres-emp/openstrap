package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.runtime.ReadOnlyComposable
import org.openstrap.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.presentation.StressLevel
import org.openstrap.app.presentation.Units
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import org.openstrap.app.data.CheckIn
import org.openstrap.app.data.Workouts
import org.openstrap.app.data.db.HealthAlertRow
import org.openstrap.app.ui.components.cardNumber
import org.openstrap.app.ui.dashboard.DailyHistory
import org.openstrap.app.presentation.AFTER_FIRST_NIGHT
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.RECOVERY_FAILED
import org.openstrap.app.ui.dashboard.RecoveryFailure
import org.openstrap.app.ui.dashboard.TRY_AGAIN
import org.openstrap.app.ui.dashboard.WAITING_LAST_NIGHT
import org.openstrap.app.presentation.Metric
import org.openstrap.app.presentation.TodayCopy
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.glow.draw.LightAngle
import org.openstrap.app.ui.glow.draw.GlowStyle
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.glow.pictures.EnergyRuler
import org.openstrap.app.ui.dashboard.GoalFacts
import org.openstrap.app.ui.dashboard.GoalMetric
import org.openstrap.app.ui.dashboard.NewsTag
import org.openstrap.app.ui.dashboard.OffWords
import org.openstrap.app.ui.dashboard.goalTag
import org.openstrap.app.ui.dashboard.offTag
import org.openstrap.app.ui.dashboard.rimLit
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxHeight
import org.openstrap.app.ui.glow.draw.level
import org.openstrap.app.ui.glow.pictures.DayLine
import org.openstrap.app.ui.glow.pictures.DayStrip
import org.openstrap.app.ui.glow.pictures.LevelFill
import org.openstrap.app.ui.glow.pictures.PictureSize
import org.openstrap.app.ui.glow.pictures.WeekOfDots
import org.openstrap.app.ui.glow.pictures.WeekRings
import org.openstrap.app.ui.glow.pictures.dayStripRim
import org.openstrap.app.ui.glow.pictures.drawHourDots
import org.openstrap.app.ui.glow.pictures.levelFillRim
import org.openstrap.app.ui.glow.pictures.weekOfDotsRim

/*
 * The pieces Today is built from (spec 4.2): the hero's parts, strips, edge cards and lists. Each card has one home
 * on the screen and opens its detail. The words come from [TodayCopy].
 */

/** The smallest height a card of [size] takes: its grid height, so long text grows the card instead of clipping. */
internal fun Modifier.gridSize(size: CardSize): Modifier = GlowGrid.height(size)?.let { heightIn(min = it) } ?: this

/* ----------------------------------------------- Hero ----------------------------------------------- */

/** The hero's sentence, the answer the card leads with. */
@Composable
internal fun HeroSentence(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Color.White, style = GlowType.sentence, modifier = modifier.padding(top = Space.S12))

/** A number on the hero: dot-matrix digits, or the dim dash while there's none, with a [label] under it. */
@Composable
internal fun HeroFigure(
    value: String?, label: String, big: Boolean = false, horizontal: Alignment.Horizontal = Alignment.Start,
    /** The hero's size for a [big] figure, an edge card's otherwise; a Tall or second glow passes [DotSize.Second]. */
    dot: DotSize = if (big) DotSize.Hero else DotSize.Edge,
    modifier: Modifier = Modifier, labelLines: Int = 1,
) {
    Column(modifier, horizontalAlignment = horizontal, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        // A big figure is the card's number: it can move into the detail's headline (spec 3.5).
        if (value != null) HaloBigNumber(value, cardNumberSize(dot), if (big) Modifier.cardNumber(value) else Modifier)
        else WaitingDash(Modifier.padding(vertical = Space.S8), onTile = true)
        Text(label, color = Ink.Secondary, style = GlowType.secondary, maxLines = labelLines)
    }
}

/**
 * The hero's one dotted number (spec 2.3, one per card) with up to two short [lines] beside it on the digits' baseline:
 * its word, and the figures that go with it as text ("Energy at waking 84"). Without a [value] the lines stand alone.
 */
@Composable
internal fun HeroReading(
    value: String?, lines: List<String>, modifier: Modifier = Modifier,
    /** A picture over the lines, right-aligned (the Energy ruler on the midday hero, HaloTodayMidday). */
    above: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        // The card's number: it can move into the detail's headline (spec 3.5).
        if (value != null) {
            HaloBigNumber(value, cardNumberSize(DotSize.Hero), Modifier.cardNumber(value))
            Spacer(Modifier.width(Space.S12))
        }
        Column(
            Modifier.then(if (above != null) Modifier.weight(1f) else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (above != null) Space.S8 else Space.S2),
            horizontalAlignment = if (above != null) Alignment.End else Alignment.Start,
        ) {
            above?.invoke()
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.S2),
                horizontalAlignment = if (above != null) Alignment.End else Alignment.Start,
            ) {
                lines.forEach {
                    Text(it, color = Ink.Secondary, style = GlowType.secondary, maxLines = 1)
                }
            }
        }
    }
}

/* ---------------------------------------------- Strips ---------------------------------------------- */

/** A full-width strip: a state or an action, [CardSpec.StripHeight] high unless its words need more. */
@androidx.compose.runtime.NonRestartableComposable
@Composable
internal fun TodayStrip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    HaloCard(modifier, size = HaloCardSize.Strip, onClick = onClick, content = content)

/**
 * Last night, collapsed to one line once the morning is over (spec 4.2). [stuck]: it's still on the strap. Before
 * the first night it says when it fills, not that there was none.
 */
@Composable
internal fun LastNightStrip(state: DayUiState, onClick: () -> Unit, modifier: Modifier = Modifier, stuck: Boolean = false, firstNights: Boolean = false) =
    TodayStrip(modifier, onClick = onClick) {
        HaloTitle(stringResource(R.string.glow_today_last_night), note = TodayCopy.lastNight(
            state.asleepSeconds, state.sleep?.score,
        ) ?: when {
                firstNights -> AFTER_FIRST_NIGHT
                stuck -> WAITING_LAST_NIGHT
                else -> stringResource(R.string.glow_today_no_sleep)
            })
    }

/**
 * The morning check-in: how you feel, 1 to 5. It never changes the score; it's how the score gets checked. A strip,
 * so lit from below in the neutral light (spec 2.5): a question, not Recovery's.
 */
@Composable
internal fun CheckInStrip(feeling: Int?, onCheckIn: (Int?) -> Unit, modifier: Modifier = Modifier) =
    TodayStrip(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.glow_today_check_in_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (feeling != null) Text(CheckIn.label(feeling), color = Ink.Secondary, style = GlowType.secondary)
        }
        Spacer(Modifier.height(Space.S12))
        // Tapping the chosen one again takes the answer back.
        val values = (CheckIn.MIN..CheckIn.MAX).toList()
        GlowSegments(values.map { "$it" }, feeling?.let(values::indexOf) ?: -1, { onCheckIn(values[it].takeIf { v -> v != feeling }) })
    }

/**
 * Start a workout: a single-row grey card, the title with your usual sport under it ("Your usual, outdoor run") and a
 * play button, ringed by a travelling rainbow light ([movingBorder]), where a card's chevron would be (boards
 * HaloTodayWaking, HaloTodayAfter, HaloTodayEvening). One thing to do, so the whole row is the button; it opens Ready
 * with that sport.
 */
@Composable
internal fun StartWorkoutStrip(sport: Int, onStart: (Int) -> Unit, modifier: Modifier = Modifier) {
    val name = Workouts.sportName(sport)
    // A strip that does one thing is a button: its title is plain text, with no caret (spec 2.5, Tappable says so).
    TodayStrip(modifier, onClick = { onStart(sport) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S2)) {
                Text(stringResource(R.string.glow_today_start_title), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
                Text(
                    stringResource(
                        R.string.glow_today_start_usual,
                        name.split(" ").joinToString(" ") { w -> if (w.all { it.isUpperCase() || it.isDigit() }) w else w.lowercase() },
                    ),
                    color = Ink.Secondary,
                    style = GlowType.secondary, maxLines = 1,
                )
            }
            Spacer(Modifier.width(Space.S12))
            Box(
                Modifier.size(40.dp).movingBorder(CircleShape).clip(CircleShape).background(Greys.Raised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.PlayFill, null, tint = Color.White, modifier = Modifier.size(IconSize.GlyphS))
            }
        }
    }
}

/** What the strap warned about, in the words of its notification. Codes as in HealthAlert.Type. */
internal fun alertText(a: HealthAlertRow, strings: Strings = Strings.app): String = when (a.type) {
    1 -> strings.format(R.string.glow_today_alert_high_hr, a.value)
    2 -> strings.format(R.string.glow_today_alert_low_hr, a.value)
    3 -> strings.format(R.string.glow_today_alert_high_stress, a.value)
    4 -> strings.format(R.string.glow_today_alert_low_spo2, a.value)
    5 -> strings.get(R.string.glow_today_alert_irregular)
    else -> strings.get(R.string.glow_today_alert_other)
}

private fun alertMetric(a: HealthAlertRow) = when (a.type) { 3 -> Metric.Stress; 4 -> Metric.Spo2; else -> Metric.HeartRate }

/** "From your strap": the newest of the day's alerts, opening its metric, with how many more there were. */
@Composable
internal fun AlertsStrip(alerts: List<HealthAlertRow>, onOpenMetric: (Metric) -> Unit, modifier: Modifier = Modifier) {
    val newest = alerts.maxByOrNull { it.timestamp } ?: return
    TodayStrip(modifier, onClick = { onOpenMetric(alertMetric(newest)) }) {
        HaloTitle(stringResource(R.string.glow_today_alerts_title), note = if (alerts.size > 1) {
                pluralStringResource(R.plurals.glow_today_alerts_more, alerts.size - 1, alerts.size - 1)
            } else {
                clock(newest.timestamp)
            })
        Text(
            if (alerts.size > 1) {
                stringResource(R.string.glow_today_alert_at, alertText(newest), clock(newest.timestamp))
            } else {
                stringResource(R.string.glow_today_alert_line, alertText(newest))
            },
            color = Ink.Strong, style = GlowType.secondary,
        )
        // The wellness disclaimer is a footnote: it lives on the page the strip opens, not in a strip.
    }
}

/* ------------------------------------------- Edge cards --------------------------------------------- */

/** An edge card's number and the line under it; a dim dash and [status] while there's no number. */
@Composable
internal fun EdgeValue(
    value: String?, unit: String?, status: String?, statusColor: Color = Ink.Secondary,
    /** Whether a missing [value] shows the waiting dash; false where the [status] says all there is. */
    waiting: Boolean = true,
    /** An edge card's size; the second glow passes [DotSize.Second]. */
    dot: DotSize = DotSize.Edge,
) {
    if (value != null || waiting) Row(Modifier.padding(top = Space.S12), verticalAlignment = Alignment.Bottom) {
        if (value != null) HaloBigNumber(value, cardNumberSize(dot), Modifier.cardNumber(value))
        else WaitingDash(Modifier.padding(vertical = Space.S8), onTile = true)
        if (unit != null && value != null) Text(
            unit, color = Ink.Secondary, style = GlowType.secondary, modifier = Modifier.padding(start = Space.S8),
        )
    }
    if (status != null) Text(status, color = statusColor, style = GlowType.secondary, modifier = Modifier.padding(top = Space.S8),
        maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** A length of time as dot-matrix digits, the clock look the big figures have: "7:40". Text uses [Units.duration]. */
internal fun clockDuration(seconds: Long): String = Units.clockDuration(seconds, Units.DOT_LOCALE)

/* ------------------------------------------ Picture cards ------------------------------------------- */

/** How high a Wide's picture is on Today, between its title row and its number. */
private val WidePictureHeight = 52.dp

/**
 * A picture card's number (spec 2.5, Picture card): white dots with the unit after them, then the news [tag] 8 dp on
 * ([NumberWithTag]), dropping under the number where a Small can't hold both. A dim dash while there's no [value].
 */
@Composable
internal fun PictureNumber(value: String?, unit: String?, tag: NewsTag?, modifier: Modifier = Modifier) {
    if (value == null) WaitingDash(modifier.padding(vertical = Space.S8), onTile = true)
    else NumberWithTag(tag, modifier) {
        HaloBigNumber(value, cardNumberSize(DotSize.Edge), Modifier.cardNumber(value))
        // Its own item, so on a Small too narrow for both the unit drops under the number whole, never split.
        if (unit != null) Text(unit, color = Ink.Secondary, style = GlowType.secondary, maxLines = 1, softWrap = false)
    }
}

/**
 * One of Today's picture cards (spec 2.5, 4.2) at its [size]: the title row, the metric's [picture] in its [ink], the
 * number in white with its news [tag], and the [status] in Secondary. Colour never says how it's going: the ink draws
 * only the picture, and off your normal the [rim] lights beside the part that's off. A Small fills the [modifier] its
 * row gives it, picture in the middle and the number at the foot. On a Small a tag stands in for the status line, as
 * a list row's does. A Wide keeps both. A Wide puts its picture under the title row, or under the number with
 * [pictureLast] (Last night's stage bar). [dash] says whether a missing [value] shows the waiting dash, false where
 * the status says all there is.
 */
// The ink and the rim stay so the screens placing it compile: a Halo card has no coloured rim (Halo System board).
@Suppress("UnusedParameter")
@Composable
internal fun TodayPictureCard(
    ink: Color, title: String, size: PictureSize, value: String?, unit: String?, tag: NewsTag?, status: String?,
    modifier: Modifier = Modifier, note: String? = null, rim: Rim? = null, onClick: (() -> Unit)? = null,
    marker: String? = null, titleTag: TitleTag? = null, pictureLast: Boolean = false, dash: Boolean = true,
    picture: (@Composable (Modifier) -> Unit)? = null,
    /** A Small with no [value] says its state in this word, in the figure style, where the number would be. */
    word: String? = null,
) {
    val small = size == PictureSize.Small
    val m = if (small) modifier else modifier.fillMaxWidth().gridSize(CardSize.Wide)
    HaloCard(m, onClick = onClick, verticalArrangement = Arrangement.Top) {
        HaloTitle(title, note = note.takeIf { !small }, marker = marker, tag = titleTag)
        if (small) {
            if (picture != null) {
                Spacer(Modifier.height(Space.S12))
                picture(Modifier.fillMaxWidth().weight(1f))
                // As much room under the picture as over it, so a picture standing on a baseline (Steps' bars) doesn't
                // touch the number.
                Spacer(Modifier.height(Space.S12))
            } else Spacer(Modifier.weight(1f))
            if (value == null && word != null) FigureWord(word)
            else if (value != null || dash) PictureNumber(value, unit, tag)
            val line = status.takeIf { tag == null || value == null }
            if (line != null) Text(
                line, color = Ink.Secondary, style = GlowType.secondary,
                modifier = Modifier.padding(top = Space.S4), maxLines = if (value == null) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
            return@HaloCard
        }
        if (picture != null && !pictureLast) {
            Spacer(Modifier.height(Space.S12))
            picture(Modifier.fillMaxWidth().height(WidePictureHeight))
        }
        if (value != null || dash) PictureNumber(value, unit, tag, Modifier.padding(top = Space.S12))
        if (status != null) Text(
            status, color = Ink.Secondary, style = GlowType.secondary,
            modifier = Modifier.padding(top = Space.S8), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (picture != null && pictureLast) {
            Spacer(Modifier.height(Space.S12))
            picture(Modifier.fillMaxWidth())
        }
    }
}

/**
 * A state word (Learning, No reading yet) where a Small's number would be, in [GlowType.smallValue], the figure style
 * the Smalls already use for words. A word too wide for one line steps down to [GlowType.figureInline], and at that size
 * may take a second line (text rules T3 and T7).
 */
@Composable
internal fun FigureWord(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = Space.S4)) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.roundToPx() }
        val fits = remember(text, widthPx) {
            measurer.measure(text, GlowType.smallValue, softWrap = false, maxLines = 1).size.width <= widthPx
        }
        Text(
            text, color = Color.White, style = if (fits) GlowType.smallValue else GlowType.figureInline,
            maxLines = if (fits) 1 else 2, overflow = TextOverflow.Clip,
        )
    }
}

/** The size a Today picture card draws at, from its grid [size]: Smalls small, everything else wide. */
internal fun pictureSize(size: CardSize): PictureSize = if (size == CardSize.Small) PictureSize.Small else PictureSize.Wide

/** Waiting, still on the strap, or no sleep recorded: what Last night says without a night. */
@Composable
@ReadOnlyComposable
private fun noNight(firstNights: Boolean, stuck: Boolean): String =
    if (firstNights) AFTER_FIRST_NIGHT else if (stuck) WAITING_LAST_NIGHT else stringResource(R.string.glow_today_no_sleep)

/** The word a Small says in a number's place without a night: waiting for the first, still on the strap, or none. */
@Composable
@ReadOnlyComposable
private fun noNightWord(firstNights: Boolean, stuck: Boolean): String = stringResource(
    if (firstNights) {
        R.string.glow_today_word_after_night
    } else if (stuck) {
        R.string.glow_today_word_waiting
    } else {
        R.string.glow_today_word_no_sleep
    },
)

/**
 * Last night (spec 4.2): its stage bar in the stage colours, and Short or Long as its tag when the time asleep is off
 * your usual, with the rim in the corner. A Small has the sleep score in dots and the time asleep under it, as the page
 * it opens leads, or the time asleep in dots for a night without a score. A Wide, the card the morning is about,
 * has the sleep score in dots with its word and the time asleep under it, as the Sleep tab's hero does, then the
 * stage bar. [stuck]: it's still on the strap, so it waits rather than saying there was none.
 */
@Composable
internal fun LastNightCard(
    state: DayUiState, firstNights: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, stuck: Boolean = false,
    size: PictureSize = PictureSize.Wide,
) {
    val s = state.sleep
    val asleep = state.asleepSeconds
    val score = s?.score
    val off = org.openstrap.app.ui.dashboard.sleepLengthOffStatus(state)
    val tag = offTag(OffWords.Length, off)
    val totals = state.sleepBreakdown?.let { org.openstrap.app.ui.glow.draw.stageTotals(it.byStage) }.orEmpty()
    val picture: (@Composable (Modifier) -> Unit)? = if (totals.isEmpty()) null else { m ->
        Box(m, contentAlignment = Alignment.Center) {
            SegmentBar(
                totals.map { (stage, seconds) -> Segment(seconds.toFloat(), org.openstrap.app.ui.glow.draw.stageColor(stage)) },
                height = org.openstrap.app.ui.glow.draw.StageBarLook.HEIGHT_DP.dp, gap = org.openstrap.app.ui.glow.draw.StageBarLook.GAP_DP.dp,
                corner = org.openstrap.app.ui.glow.draw.StageBarLook.CORNER_DP.dp,
            )
        }
    }
    val rim = Rim.Corner.takeIf { rimLit(OffWords.Length, off) }
    val title = stringResource(R.string.glow_today_last_night)
    if (size == PictureSize.Small) {
        TodayPictureCard(
            MetricColors.sleepDeep, title, size, score?.toString() ?: asleep?.let(::clockDuration),
            stringResource(if (score != null) R.string.glow_today_score_unit else R.string.glow_today_hours_unit), tag,
            when {
                asleep == null -> if (stuck && !firstNights) stringResource(R.string.glow_today_for_last_night) else null
                score != null -> Units.duration(asleep)
                else -> null
            },
            modifier, rim = rim, onClick = onClick, picture = picture,
            word = if (asleep != null) null else noNightWord(firstNights, stuck),
        )
        return
    }
    // A night the strap gave no score shows the time asleep in its place, so its tag still has a number to sit by.
    TodayPictureCard(
        MetricColors.sleepDeep, title, size, score?.toString() ?: asleep?.let(::clockDuration),
        stringResource(if (score != null) R.string.glow_today_score_unit else R.string.glow_today_hours_unit), tag,
        when {
            asleep == null -> noNight(firstNights, stuck)
            score != null -> stringResource(
                R.string.glow_today_last_night_scored, org.openstrap.app.presentation.TabCopy.sleepScoreWord(score), Units.duration(asleep),
            )
            else -> null
        },
        modifier, note = s?.let { Ranges.times(it.start, it.end) }, rim = rim, onClick = onClick, pictureLast = true,
        dash = asleep == null, picture = picture,
    )
}

/**
 * Heart rate (line with scrub, chart kit): the latest reading and the day's line, with your resting range as a dim
 * band behind it when there's one ([restingRange], your normal resting heart rate). Hold the line to scrub: the big
 * number and the time under it follow the finger, and snap back on release. Lit from the right while the reading is
 * live, otherwise as a value at its usual. The live switch lives in the Heart rate detail, not here. Shared by Today
 * and the Activity tab.
 */
@Composable
internal fun HeartCard(
    state: DayUiState, live: Int?, onClick: () -> Unit, modifier: Modifier = Modifier,
    restingRange: org.openstrap.app.data.History.Normal? = null,
    /** "Up to 09:40" when the line has gone stale ([org.openstrap.app.ui.dashboard.upToMarker]). */
    upTo: String? = null,
) {
    val step = 10 * 60L
    val values = remember(state.heartRate, state.dayStart) { bucket(state.heartRate, state.dayStart..state.dayEnd, step) }
    var picked by remember(values) { mutableStateOf<Int?>(null) }
    val scrubbed = picked?.let { i -> values.getOrNull(i)?.let { it.roundToInt() to state.dayStart + i * step } }
    val now = scrubbed?.first ?: live ?: state.latestHeartRate
    HaloCard(modifier, size = HaloCardSize.Wide, onClick = onClick) {
        HaloTitle(stringResource(R.string.glow_today_heart_rate), note = restingNote(state), marker = upTo)
        val at = scrubbed?.second ?: state.heartRate.lastOrNull()?.x
        EdgeValue(
            now?.toString(), stringResource(R.string.glow_unit_bpm),
            // A held point is the average of a 10-minute stretch, not one reading.
            if (scrubbed != null) stringResource(R.string.core_today_heart_average_from, step / SECONDS_PER_MINUTE, clock(scrubbed.second))
            else TodayCopy.heartAt(live != null, at?.let(::clock)),
        )
        if (state.heartRate.size >= 2) {
            Spacer(Modifier.height(Space.S12))
            GlowLineChart(
                values, height = 72.dp, showAverage = false, color = MetricColors.heart, labels = listOf("00", "06", "12", "18", "24"),
                band = restingRange?.let { it.low..it.high }, onScrub = { picked = it },
            )
            Text(
                TodayCopy.heartLineNote(restingRange != null), color = Ink.Secondary,
                style = GlowType.secondary, modifier = Modifier.padding(top = Space.S8),
            )
        }
    }
}

/**
 * Heart rate on Today (spec 4.2): the day's line through your resting range ([restingRange], your normal resting
 * heart rate) as a dim band, the latest reading or the [live] one in dots. A reading moves all day, so the card has no
 * tag and no rim; resting heart rate carries its own. No scrub here: that lives on Activity's Wide and in the detail.
 */
@Composable
internal fun HeartDayCard(
    state: DayUiState, live: Int?, size: PictureSize, onClick: () -> Unit, modifier: Modifier = Modifier,
    restingRange: org.openstrap.app.data.History.Normal? = null, upTo: String? = null,
) {
    val values = remember(state.heartRate, state.dayStart, state.dayEnd) { bucket(state.heartRate, state.dayStart..state.dayEnd, HEART_STEP) }
    val now = live ?: state.latestHeartRate
    val at = TodayCopy.heartAt(live != null, state.heartRate.lastOrNull()?.x?.let(::clock))
    TodayPictureCard(
        MetricColors.heart, stringResource(R.string.glow_today_heart_rate), size, now?.toString(), stringResource(R.string.glow_unit_bpm),
        // The "Up to" marker already says when the line ends.
        null, at.takeIf { upTo == null && now != null },
        modifier, note = restingNote(state),
        onClick = onClick, marker = upTo,
        word = if (now == null) stringResource(R.string.glow_today_word_no_reading) else null,
        picture = if (state.heartRate.size < 2) null else { m -> DayLine(values, restingRange?.let { it.low..it.high }, MetricColors.heart, size, m) },
    )
}

/** The heart rate cards' note on the title row: the day's resting heart rate, marked as an estimate when the strap sent none. */
@Composable
@ReadOnlyComposable
internal fun restingNote(state: DayUiState): String? = state.restingHeartRate?.let {
    stringResource(if (state.restingEstimated) R.string.glow_today_resting_estimated else R.string.glow_today_resting, it)
}

/** Heart rate's line on Today: a point every ten minutes. */
private const val HEART_STEP = 10 * 60L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Stress on Today (spec 4.2): the day as one strip, calm to high ([DayStrip], [DayUiState.stressStrip]), the latest
 * reading in dots with its level under it ([StressLevel]). Only a High latest reading tags the card "High", with the
 * rim over the strip's latest stretch; the other levels carry neither.
 */
@Composable
internal fun StressDayCard(
    state: DayUiState, size: PictureSize, waiting: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, now: Long = java.time.Instant.now().epochSecond,
) {
    val levels = state.stressStrip
    val latest = org.openstrap.app.ui.dashboard.LatestReading.stress(state)
    val tag = latest?.let { org.openstrap.app.ui.dashboard.LatestReading.stressTag(StressLevel.of(it.y)) }
    TodayPictureCard(
        MetricColors.stress, stringResource(R.string.glow_today_stress), size, latest?.y?.roundToInt()?.toString(), null,
        tag,
        when {
            latest == null -> waiting
            // The number is the latest reading, whenever it was taken: its level, with the time once it is an hour old.
            else -> org.openstrap.app.ui.dashboard.LatestReading.stressLine(latest.x, latest.y, now, tagged = tag != null, time = ::clock)
        },
        modifier, rim = if (tag != null) dayStripRim(levels, size) else null, onClick = onClick,
        picture = if (levels.none { it != null }) null else { m -> DayStrip(levels, MetricColors.stress, size, m) },
    )
}

/**
 * Steps on Today (spec 4.2): today's hourly bars in lime, the count in dots with its goal tag after it ([goalTag]:
 * Goal met and up the ladder), and the pace in words under it. A Wide has "Goal 8,000" as the title row's note. No
 * progress bar: the bars and the tag say how far. [facts] are the day's goal facts ([org.openstrap.app.ui.dashboard
 * .ActivityHistory.stepsFacts]). Without them the tag climbs only as far as the goal itself tells.
 */
@Composable
internal fun ActivityCard(
    state: DayUiState, onClick: () -> Unit, modifier: Modifier = Modifier, usual: List<Float>? = null, upTo: String? = null,
    /** The Partial day tag when the strap was worn for part of the day ([TitleTag]). */
    tag: TitleTag? = null,
    size: PictureSize = PictureSize.Wide,
    facts: GoalFacts? = null,
) {
    val goal = org.openstrap.app.ui.dashboard.LocalGoals.current.steps
    val isToday = state.date == java.time.LocalDate.now()
    val now = java.time.LocalTime.now()
    val pace = org.openstrap.app.presentation.ActivityCopy.paceOf(state.steps, usual, isToday, now.hour, now.minute)
    // A Small is half a row: a count in the thousands leaves no room for "steps" beside it, and the unit dropping to a
    // line of its own squeezed the bars to a sliver. The title already says Steps, so a Small has the number alone and
    // only the goal share under it, one line like Heart rate's beside it; the pace waits on the Wide and the page.
    val small = size == PictureSize.Small
    TodayPictureCard(
        MetricColors.steps, stringResource(R.string.glow_today_steps_title), size, Units.count(state.steps, Units.DOT_LOCALE),
        if (small) null else stringResource(R.string.glow_today_steps), goalTag(GoalMetric.Steps, facts ?: GoalFacts(state.steps, goal)),
        if (small) org.openstrap.app.presentation.ActivityCopy.goalShare(state.steps, goal)
        else org.openstrap.app.presentation.ActivityCopy.activityLine(state.steps, goal, pace, isToday),
        modifier, note = stringResource(R.string.glow_today_steps_goal, Units.count(goal)), onClick = onClick, marker = upTo, titleTag = tag,
        picture = if (state.steps <= 0) null else { m -> HourBars(state.hourlySteps, MetricColors.steps, m) },
    )
}

/**
 * The day's steps hour by hour as dots in [ink], a dot per 250 steps (HaloCharts, Steps by hour): the latest hour with
 * steps in full ink and the rest a little dimmer.
 */
@Composable
// The hours arrive whole with each update and never change in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
private fun HourBars(hourly: List<Float>, ink: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) { drawHourDots(hourly, null, ink) }
}

/**
 * This morning's Recovery, later in the day (spec 4.2): a week of dots through the band of your usual
 * ([org.openstrap.app.data.RecoveryResult.readinessTrend], [org.openstrap.app.data.RecoveryResult.readinessUsual]),
 * the score in dots, and Above or Below usual with the rim by the latest dot when it's off. The same score the waking
 * hero shows: OpenStrap's, the strap's estimate labelled as such, or why there's none yet.
 */
@Composable
internal fun RecoveryEarlierCard(
    shown: TodayCopy.RecoveryShown, onClick: () -> Unit, modifier: Modifier = Modifier,
    /** Today's score failed to work out: yesterday's stays, dimmed, with a way to try again (spec 4.3). */
    failure: RecoveryFailure? = null, onRetry: () -> Unit = {},
    size: PictureSize = PictureSize.Wide,
    /** The day's Recovery, for its week of dots and its usual. Null draws the score alone. */
    recovery: org.openstrap.app.data.RecoveryResult? = null,
    /** Why there is no score ([TodayCopy.noScore]); a Small says it as a word and a count, a Wide as a phrase. */
    noScore: TodayCopy.NoScore? = null,
    /** What a Small without a score or a reason says in a number's place: the card is waiting ([waitingWord]). */
    waitingWord: String = "",
) {
    val title = stringResource(R.string.glow_today_recovery_title)
    if (failure != null) {
        // A Small has no room for Try again: it says it failed and opens Recovery, where Try again is.
        if (size == PictureSize.Small) TodayPictureCard(
            MetricColors.readiness, title, size, null, null, null, null, modifier, onClick = onClick, dash = false,
            word = stringResource(R.string.glow_today_word_unavailable),
        )
        else HaloCard(modifier, size = HaloCardSize.Wide, onClick = onClick) {
            HaloTitle(title, note = stringResource(R.string.glow_today_this_morning))
            RecoveryFailed(failure, onRetry)
        }
        return
    }
    val week = remember(recovery) { recovery?.readinessTrend?.map { it.second?.toFloat() }.orEmpty() }
    // The Small is its score over the last seven nights in dots, the latest big and full (board HaloTodayMidday).
    if (size == PictureSize.Small && shown.value != null) {
        TodayPictureCard(
            MetricColors.readiness, title, size, shown.value.toString(), null, null, shown.word ?: shown.line, modifier,
            onClick = onClick, picture = { m -> NightDots(week, MetricColors.readiness, m) },
        )
        return
    }
    val usual = recovery?.readinessUsual?.let { it.low..it.high }
    val off = org.openstrap.app.ui.dashboard.recoveryOffStatus(recovery)
    val small = size == PictureSize.Small
    // A Small without a number says the state as a word where the number would be, and a count under it (T3, T7). The
    // sentence form stays on the hero and the detail.
    val word = if (small && shown.value == null) noScoreWord(noScore, waitingWord) else null
    val smallLine = noScoreSmallLine(noScore)
    // A Wide's status is one phrase: what the score means for the day, or why there isn't one.
    val phrase = recoveryPhrase(shown, noScore, recovery)
    TodayPictureCard(
        MetricColors.readiness, title, size, shown.value?.toString(), shown.word.takeIf { size == PictureSize.Wide },
        // The strap's estimate isn't OpenStrap's score, so it's never said against your usual.
        offTag(OffWords.Usual, off).takeIf { !shown.estimate },
        if (small) { if (shown.value != null) shown.word else smallLine } else phrase,
        word = word,
        modifier = modifier, note = stringResource(R.string.glow_today_this_morning),
        rim = if (rimLit(OffWords.Usual, off) && !shown.estimate) weekOfDotsRim(week, usual, size) else null, onClick = onClick,
        picture = if (week.none { it != null }) null else { m -> WeekOfDots(week, usual, MetricColors.readiness, size, m) },
    )
}

/** What a Small without a score says in a number's place: the state as a word, or [waitingWord] while it is only waiting. */
@Composable
@ReadOnlyComposable
private fun noScoreWord(noScore: TodayCopy.NoScore?, waitingWord: String): String = when (noScore) {
    is TodayCopy.NoScore.Learning -> stringResource(R.string.glow_today_word_learning)
    TodayCopy.NoScore.NoHeartData -> stringResource(R.string.glow_today_word_no_score)
    null -> waitingWord
}

/** The count a Small without a score says under its word, or null when it has no reason to give. */
@Composable
@ReadOnlyComposable
private fun noScoreSmallLine(noScore: TodayCopy.NoScore?): String? = when (noScore) {
    is TodayCopy.NoScore.Learning ->
        pluralStringResource(R.plurals.core_today_no_score_learning_small, noScore.of, noScore.nights, noScore.of)
    TodayCopy.NoScore.NoHeartData -> stringResource(R.string.glow_today_few_readings)
    null -> null
}

/** A Wide's status phrase: what the score means for the day, or why there isn't one. */
@Composable
@ReadOnlyComposable
private fun recoveryPhrase(
    shown: TodayCopy.RecoveryShown, noScore: TodayCopy.NoScore?, recovery: org.openstrap.app.data.RecoveryResult?,
): String = when {
    shown.estimate -> noScorePhrase(noScore) ?: stringResource(R.string.core_today_own_score_not_ready_phrase)
    shown.value != null -> stringResource(meaningPhrase(shown.value, recovery?.readiness?.strainAlert == true))
    else -> noScorePhrase(noScore) ?: shown.line
}

/** Why there is no Recovery score, as a phrase for a Wide's status ("Learning, 5 of 7 nights"). */
@Composable
@ReadOnlyComposable
private fun noScorePhrase(why: TodayCopy.NoScore?): String? = when (why) {
    is TodayCopy.NoScore.Learning -> pluralStringResource(R.plurals.core_today_no_score_learning_phrase, why.of, why.nights, why.of)
    TodayCopy.NoScore.NoHeartData -> stringResource(R.string.core_today_no_score_heart_phrase)
    null -> null
}

/** What a Recovery score means for the day, as a phrase, by the same bands as [TodayCopy.recoveryMeaning]. */
private fun meaningPhrase(score: Int, alert: Boolean): Int = when {
    alert -> R.string.core_today_meaning_alert_phrase
    score >= org.openstrap.app.presentation.RecoveryBands.READY_FOR_MORE_FROM -> R.string.core_today_meaning_ready_for_more_phrase
    score >= org.openstrap.app.presentation.RecoveryBands.READY_FROM -> R.string.core_today_meaning_ready_phrase
    score >= org.openstrap.app.presentation.RecoveryBands.MODERATE_FROM -> R.string.core_today_meaning_moderate_phrase
    else -> R.string.core_today_meaning_low_phrase
}

/**
 * Load on Today (spec 4.2): a level filling to today's Load over a dim band of your usual ([LoadBands.usualRange]),
 * the Load in dots, and Above or Below usual with the rim past the band when it's off. A low Load is only so far until
 * the day is over ([org.openstrap.app.ui.dashboard.loadOffStatus]), so it's never news before then.
 */
/**
 * Recovery's last seven nights as dots (board HaloTodayMidday): each night's score sets how high its dot sits in a 24 dp
 * band, half bright, and the latest is bigger and full. A night with no score leaves its place empty.
 */
// The list arrives whole with each update and never changes in place.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
private fun NightDots(scores: List<Float?>, ink: Color, modifier: Modifier = Modifier) {
    val nights = scores.takeLast(NIGHTS)
    androidx.compose.foundation.Canvas(modifier) {
        val band = 24.dp.toPx()
        val bottom = (size.height + band) / 2
        val small = 3.5.dp.toPx()
        val big = 6.dp.toPx()
        val gap = 8.dp.toPx()
        val known = nights.filterNotNull()
        val low = known.minOrNull() ?: 0f
        val span = ((known.maxOrNull() ?: 0f) - low).takeIf { it > 0f }
        var x = 0f
        nights.forEachIndexed { i, s ->
            val last = i == nights.lastIndex
            val r = if (last) big else small
            if (s != null) {
                val lift = span?.let { (s - low) / it * (band - 2 * big) } ?: 0f
                drawCircle(ink.copy(alpha = if (last) 1f else 0.5f), r, Offset(x + r, bottom - r - lift))
            }
            x += 2 * r + gap
        }
    }
}

private const val NIGHTS = 7

private const val LOAD_LEVEL_SPAN = 1.5f
private const val LOAD_LEVEL_MIN = 0.15f
private const val LOAD_LEVEL_DEFAULT = 0.5f

/**
 * Load: a Small lit from below to its level in Load's violet (boards HaloTodayWorkoutTime, HaloTodayAfter, the level
 * look), else the picture card with its fill over your usual.
 */
@Composable
internal fun LoadCard(
    load: Float?, usual: Float?, endOfDay: Boolean, status: String, size: PictureSize, onClick: () -> Unit, modifier: Modifier = Modifier,
    /** What a Small says in a number's place while there is no [load]: a state word (Unavailable, No reading yet). */
    word: String? = null,
) {
    val off = org.openstrap.app.ui.dashboard.loadOffStatus(load, usual, endOfDay)
    val band = org.openstrap.app.presentation.LoadBands.usualRange(usual)
    if (size == PictureSize.Small && load != null) {
        // The level look: Load's violet rising from below to where today's Load stands against your usual.
        val level = band?.let { (load / (it.endInclusive * LOAD_LEVEL_SPAN)).coerceIn(LOAD_LEVEL_MIN, 1f) } ?: LOAD_LEVEL_DEFAULT
        HaloCard(modifier, tone = CardTone.lit(GlowStyle.level(MetricColors.load, level).copy(second = true)), onClick = onClick) {
            Column(Modifier.fillMaxHeight()) {
                HaloTitle(stringResource(R.string.glow_today_load))
                val offPill = offTag(OffWords.Usual, off)
                offPill?.let { NewsTagView(it, Modifier.padding(top = Space.S8)) }
                Spacer(Modifier.weight(1f))
                val shown = load.roundToInt().toString()
                HaloBigNumber(shown, cardNumberSize(DotSize.Edge), Modifier.cardNumber(shown))
                // The pill already says Above or Below usual, so the status line gives way to it, as on the other Smalls.
                if (offPill == null) Text(
                    status, color = Ink.Secondary, style = GlowType.secondary,
                    modifier = Modifier.padding(top = Space.S4), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    TodayPictureCard(
        MetricColors.load, stringResource(R.string.glow_today_load), size, load?.roundToInt()?.toString(), null, offTag(OffWords.Usual, off),
        status.takeIf { load != null || word == null || size != PictureSize.Small },
        modifier, rim = if (load != null && rimLit(OffWords.Usual, off)) levelFillRim(load, band, size) else null, onClick = onClick,
        word = word,
        picture = if (load == null) null else { m -> LevelFill(load, band, MetricColors.load, size, m) },
    )
}

/**
 * Time to recover on Today (spec 4.2): a ring filling toward Ready from the end of the [workout] that says how long it
 * needs, full once you're clear. The hours left in dots and when you're ready under them. A countdown, not a reading,
 * so never a tag or a rim.
 */
@Composable
internal fun RecoverCard(
    workout: org.openstrap.app.data.WorkoutAnalysis?, now: Long, zone: ZoneId, size: PictureSize, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val left = workout?.let { TodayCopy.recoverLeft(it.end, it.recoveryMinutes ?: 0, now) }
    val text = left?.let { TodayCopy.recoverShort(it, now, zone) }
    val share = recoverShare(workout?.recoveryMinutes, left)
    TodayPictureCard(
        MetricColors.load, stringResource(R.string.glow_today_to_recover), size, text?.first, stringResource(R.string.glow_today_hours_unit), null,
        if (text == null && size == PictureSize.Small) null else text?.second ?: stringResource(R.string.glow_today_recover_no_estimate),
        modifier, onClick = onClick, dash = false,
        word = if (text == null) stringResource(R.string.glow_today_word_no_estimate) else null,
        // Energy's ruler: 16 dots in Recovery's teal up to the white mark where you are, the rest still to go.
        picture = if (left == null) null else { m ->
            EnergyRuler(share * 100f, MetricColors.readiness, m.height(24.dp), dots = RECOVER_DOTS, track = Ink.Disabled)
        },
    )
}

private const val RECOVER_DOTS = 16

/**
 * How far Time to recover's ring has filled toward Ready: the share of the workout's [recoveryMinutes] gone, with
 * [left] seconds still to go. Full once you're clear, or with no estimate to count down.
 */
internal fun recoverShare(recoveryMinutes: Int?, left: Long?): Float {
    val total = (recoveryMinutes ?: 0) * 60L
    return if (left == null || total <= 0) 1f else 1f - left.toFloat() / total
}

/** In place of a Recovery that failed: yesterday's score dimmed, the line, and Try again. */
@Composable
internal fun RecoveryFailed(failure: RecoveryFailure, onRetry: () -> Unit) {
    failure.yesterday?.let { y ->
        Column(Modifier.alpha(FAILED_ALPHA)) { EdgeValue(y.toString(), stringResource(R.string.glow_today_yesterday), null) }
    }
    Text(RECOVERY_FAILED, color = Ink.Strong, style = GlowType.secondary, modifier = Modifier.padding(top = Space.S8))
    HaloButton(TRY_AGAIN, onRetry, Modifier.padding(top = Space.S12), size = HaloButtonSize.Card, variant = HaloButtonVariant.Glass)
}

/** How dim a value kept from before is, beside a failure. */
internal const val FAILED_ALPHA = 0.45f

/**
 * Charge as a wide card: the screen's second glow, a level whose light fills across from the left to the value, the
 * Charge battery of Recovery on its side (spec 2.1). Where that would sit next to another glow it steps down to a level
 * edge card whose light fills across to the value, only as high as its content. [status] says what's happening to it. [soFar] is the day's
 * Charge so far (evenly spaced, nulls still to come), drawn beside the number on the level card (ghost line, chart
 * kit). TODO(step 6): the ghost of your usual Charge by this time needs past days' Charge hour by hour. The engine
 * simulates Charge only for the day shown (with a week's lead-in to settle it); past days would each need that whole
 * simulation re-run, and nothing keeps its results, so only today's line shows.
 */
@Composable
internal fun ChargeWideCard(
    now: Int?, status: String, secondGlow: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, soFar: List<Float?>? = null,
    upTo: String? = null, tag: TitleTag? = null, small: Boolean = false, atWake: Int? = null,
    /** What the Small says in the number's place while there is no Energy: a state word (Unavailable, After a night). */
    word: String? = null,
) {
    val m = modifier.fillMaxWidth().gridSize(CardSize.Wide)
    if (secondGlow) {
        HaloCard(
            m,
            tone = CardTone.lit(GlowStyle.Ember.copy(lightLevel = (now ?: 0) / 100f, lightAngle = LightAngle.Left, second = true)),
            onClick = onClick,
        ) {
            Column {
                HaloTitle(stringResource(R.string.glow_today_energy_title), marker = upTo, tag = tag)
                if (soFar == null || soFar.count { it != null } < 2) EdgeValue(now?.toString(), null, status, Ink.Strong, dot = DotSize.Second)
                else Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) { EdgeValue(now?.toString(), null, status, Ink.Strong, dot = DotSize.Second) }
                    Spacer(Modifier.width(Space.S12))
                    GhostLine(soFar, Color.White, 100f, Modifier.weight(1f), height = 52.dp)
                }
            }
        }
    } else if (small) HaloCard(modifier, onClick = onClick, verticalArrangement = Arrangement.Top) {
        // Energy as a Small (board HaloTodayNight): the figure, what happens to it, the ruler at the foot.
        HaloTitle(stringResource(R.string.glow_today_energy_title), marker = upTo, tag = tag)
        if (now == null && word != null) FigureWord(word, Modifier.padding(top = Space.S12)) else EdgeValue(now?.toString(), null, status)
        Spacer(Modifier.weight(1f))
        if (now != null) EnergyRuler(
            now.toFloat(), MetricColors.charge, Modifier.fillMaxWidth().height(16.dp),
            dots = 16, track = Ink.Edge,
        )
    } else HaloCard(modifier, onClick = onClick, verticalArrangement = Arrangement.Top) {
        // Energy as a plain Wide (boards HaloTodayEvening, HaloTodayAfter): the figure and its line, the ruler beside.
        HaloTitle(
            stringResource(R.string.glow_today_energy_title),
            note = atWake?.let { stringResource(R.string.glow_today_energy_waking, it) }, marker = upTo, tag = tag,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { EdgeValue(now?.toString(), null, status) }
            if (now != null) EnergyRuler(
                now.toFloat(), MetricColors.charge, Modifier.width(150.dp).height(18.dp),
                dots = 22, track = Ink.Edge,
            )
        }
    }
}

/** The workout just done, after it: how long it was, the average heart rate and the zone it mostly sat in. Lit from the left, about earlier. */
@Composable
internal fun WorkoutCard(w: org.openstrap.app.data.WorkoutAnalysis, onClick: () -> Unit, modifier: Modifier = Modifier) {
    HaloCard(modifier, size = HaloCardSize.Wide, onClick = onClick) {
        HaloTitle(Workouts.sportName(w.workout.sportType), note = Ranges.times(w.shownStart, w.end))
        val mostly = w.zoneSeconds.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
        EdgeValue(
            dotClock(w.activeSeconds.coerceAtLeast(0)), stringResource(R.string.glow_today_workout_long),
            workoutLine(w.workout.averageHeartRate, mostly?.let { it + 1 }),
        )
        // Zone bar (chart kit): how the time was spent.
        ZoneBar(w.zoneSeconds, org.openstrap.app.ui.workout.ZONE_COLORS, Modifier.padding(top = Space.S8))
    }
}

/**
 * What the after-workout hero shows of the run (board HaloTodayAfter): its distance, else its length, as the one dotted
 * number, the length and the average heart rate beside it, and the time in zones as a bar under.
 */
@Composable
internal fun AfterWorkoutBody(w: org.openstrap.app.data.WorkoutAnalysis) = Column {
    val metres = w.distanceMeters?.takeIf { it >= MIN_KM_METRES }
    val mostly = w.zoneSeconds.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.index
    HeroReading(
        metres?.let { Units.km(it, Units.Precision.Day, Units.DOT_LOCALE) } ?: dotClock(w.activeSeconds.coerceAtLeast(0)),
        listOfNotNull(
            Units.duration(w.activeSeconds.coerceAtLeast(0)),
            // A side line is one fact of at most 20 characters: the zones are in the bar under it.
            workoutLine(w.workout.averageHeartRate, if (w.workout.averageHeartRate != null) null else mostly?.let { it + 1 }),
        ),
    )
    Spacer(Modifier.height(Space.S12))
    ZoneBar(w.zoneSeconds, org.openstrap.app.ui.workout.ZONE_COLORS, Modifier)
}

private const val MIN_KM_METRES = 1000f

/** The workout card's line: its average heart rate and the zone it mostly sat in, either alone, or null with neither. */
@Composable
@ReadOnlyComposable
private fun workoutLine(average: Int?, zone: Int?): String? = when {
    average != null && zone != null -> stringResource(R.string.glow_today_workout_average_zone, average, zone)
    average != null -> stringResource(R.string.glow_today_workout_average, average)
    zone != null -> stringResource(R.string.glow_today_workout_zone, zone)
    else -> null
}

/**
 * Your week (spec 4.2): Monday to Sunday as a ring a day in Steps' lime ([WeekRings]), full for a day at the goal, a
 * day to come its faint track alone, and "4 of 7 days at goal" as the title row's note. Unlit, with no number: the
 * rings are the card. Today's steps are the live count, the days before come from Steps' daily [history]. It opens the
 * week story.
 */
@Composable
internal fun WeekCard(
    state: DayUiState, history: DailyHistory?, modifier: Modifier = Modifier,
    /** Opens the week story. */
    onClick: (() -> Unit)? = null,
) {
    val goal = org.openstrap.app.ui.dashboard.LocalGoals.current.steps
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    // The phone's own week, as everywhere else and on the page it opens ([TodayCopy.thisWeek]).
    val firstDay = remember { org.openstrap.app.data.Weeks.firstDay() }
    val start = org.openstrap.app.data.Weeks.start(state.date, firstDay)
    val end = start.plusDays(org.openstrap.app.data.Weeks.DAYS - 1L)
    val stored by remember(start, history) {
        history?.invoke(Metric.Steps.daily, start, end) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val today = java.time.LocalDate.now()
    val week = remember(stored, state.date, state.steps, goal, today) {
        org.openstrap.app.ui.dashboard.stepsWeek(
            state.date, state.steps, goal, stored.associate { it.date to it.value.roundToInt() }, today, firstDay,
        )
    }
    val shares = week.days.map { (_, steps) -> steps?.let { if (goal > 0) it.toFloat() / goal else 0f } }
    val atGoal = week.days.count { (_, steps) -> steps != null && goal > 0 && steps >= goal }
    HaloCard(modifier, size = HaloCardSize.Wide, onClick = onClick) {
        HaloTitle(
            stringResource(R.string.glow_today_week_title),
            note = pluralStringResource(R.plurals.glow_today_week_at_goal, week.days.size, atGoal, week.days.size),
        )
        Spacer(Modifier.height(Space.S12))
        WeekRings(
            shares, MetricColors.steps, PictureSize.Wide, Modifier.fillMaxWidth().height(WidePictureHeight),
            today = week.days.indexOfFirst { (day, _) -> day == state.date },
        )
        Row(Modifier.fillMaxWidth().padding(top = Space.S8)) {
            week.days.forEach { (day, _) ->
                Text(
                    day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    Modifier.weight(1f), color = if (day == state.date) Color.White else GlowType.footnoteColor,
                    style = GlowType.caption, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

/**
 * A Small (spec 2.3): a number and a word only, in the edge light, filling the [modifier] its row gives it. The title
 * at the top, the number and its word at the foot. [extra] goes between them, for the nap's stage strip. With no
 * number the word alone stands in its place in Body, rather than a dash over it ("Recovered"), unless it's [waiting]:
 * a word is never drawn at a number's size.
 */
// The ink and the rim stay so the screens placing it compile: a Halo card has no coloured rim (Halo System board).
@Suppress("UnusedParameter")
@Composable
internal fun SmallCard(
    color: Color, title: String, value: String?, unit: String?, status: String?, modifier: Modifier = Modifier,
    rim: Rim? = null, statusColor: Color = Ink.Secondary,
    onClick: (() -> Unit)? = null, extra: (@Composable () -> Unit)? = null,
    /** Waiting for its first value: the dim dash with [status] under it, rather than the word in its place. */
    waiting: Boolean = false,
    /** The number as plain text ([GlowType.smallValue]), for a Small that breaks down the card beside it. */
    plain: Boolean = false,
) {
    HaloCard(modifier, onClick = onClick, verticalArrangement = Arrangement.Top) {
        HaloTitle(title)
        Spacer(Modifier.weight(1f))
        if (value == null && status != null && !waiting) {
            extra?.invoke()
            Text(status, color = Color.White, style = GlowType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            return@HaloCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            when {
                value == null -> WaitingDash(Modifier.padding(vertical = Space.S8), onTile = true)
                plain -> Text(value, Modifier.cardNumber(value), color = Color.White, style = GlowType.smallValue, maxLines = 1)
                else -> HaloBigNumber(value, cardNumberSize(DotSize.Edge), Modifier.cardNumber(value))
            }
            if (unit != null && value != null) Text(
            unit, color = Ink.Secondary, style = GlowType.secondary, modifier = Modifier.padding(start = Space.S8),
        )
        }
        extra?.invoke()
        if (status != null) Text(status, color = statusColor, style = GlowType.secondary, modifier = Modifier.padding(top = Space.S8),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------------------------------------------- Lists ----------------------------------------------- */

/** A row of a body list: a value against your [normal] ([org.openstrap.app.ui.glow.rememberNormal]). */
internal class BodyRow(
    val name: String, val color: Color, val value: Float?, val normal: org.openstrap.app.data.History.Normal?, val unit: String, val metric: Metric,
    /** What the row says while its normal is forming, "Learning, 5 of 7 nights"; null for the plain word. */
    val learning: String? = null,
    /** Where [value] comes from, when it isn't a plain figure read against [normal]. */
    val source: BodyValue = BodyValue.Plain,
)

/** What a [BodyRow]'s value is. */
internal sealed interface BodyValue {
    /** The day's figure, read against your normal. */
    data object Plain : BodyValue

    /** unstrap's own estimate, which the row says. */
    data object Estimated : BodyValue

    /**
     * The day's latest reading: the row says [line] (when it was taken, awake or asleep) instead of a word against your
     * normal, and it has no tag or direction, since a single reading has no baseline of its own.
     */
    data class Latest(val line: String) : BodyValue
}

/**
 * Body values against your normal, each opening its metric, read and rounded as the Recovery tab's body signals are.
 * [waiting] is what an empty row says.
 */
@Composable
internal fun BodyList(
    title: String, rows: List<BodyRow>, waiting: String, onOpenMetric: (Metric) -> Unit, modifier: Modifier = Modifier, upTo: String? = null,
) {
    ListPanel(modifier, title = title, note = stringResource(R.string.glow_today_against_normal), marker = upTo) {
        rows.forEachIndexed { i, r ->
            val source = r.source
            val direction = if (source is BodyValue.Latest) null else org.openstrap.app.data.History.status(r.value, r.normal)
            val tag = org.openstrap.app.ui.dashboard.bodyTag(r.metric, direction)
            MetricRow(
                r.name, r.color, r.value?.let { r.metric.format(it) + r.unit },
                // The tag already says which way it is off, so the word against your normal gives way to it.
                when {
                    r.value == null -> waiting
                    source is BodyValue.Latest -> source.line
                    r.normal == null && r.learning != null -> r.learning
                    source == BodyValue.Estimated ->
                        if (tag != null) stringResource(R.string.glow_today_estimated)
                        else stringResource(R.string.glow_today_body_estimated, TodayCopy.normalWord(direction))
                    tag != null -> null
                    else -> TodayCopy.normalWord(direction)
                },
                tag = tag, onClick = { onOpenMetric(r.metric) }, divider = i > 0,
                icon = r.metric.rowIcon(),
            )
        }
    }
}

