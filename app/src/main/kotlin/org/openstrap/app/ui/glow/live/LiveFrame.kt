@file:Suppress("MatchingDeclarationName")

package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.glow.HaloNote
import org.openstrap.app.ui.glow.SideTag
import org.openstrap.app.ui.glow.HeaderVariant
import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.BannerKind
import org.openstrap.app.ui.glow.HaloBanner
import org.openstrap.app.ui.glow.HaloCard
import org.openstrap.app.ui.glow.HaloPageColumn
import org.openstrap.app.ui.glow.HaloTitle
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import org.openstrap.app.presentation.Units
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.openstrap.app.ui.glow.HaloIconButton
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.openstrap.app.data.Workouts
import org.openstrap.app.presentation.WorkoutWords
import org.openstrap.app.presentation.sportNoun
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.components.HapticEvent
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.ui.components.rememberHaptics
import org.openstrap.app.ui.glow.*
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.SportIcons
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import org.openstrap.app.ui.theme.FunnelSans
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.shape.RoundedCornerShape
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/** What every live screen can do to the workout, passed down as one. */
@androidx.compose.runtime.Immutable
internal class LiveControls(
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onEnd: () -> Unit,
    val onMinimize: () -> Unit,
    val onEndAtPrompt: () -> Unit,
    val onKeepGoing: () -> Unit,
)

/**
 * The frame every live screen shares (workouts spec 2.3, 6): minimize, the sport in the workout's [color] and the
 * strap's state along the top, then the moving time large ([time], on the screens built for each sport), the screen's
 * own [content], what any workout may need to say (the strap away, "Looks like you've stopped"), and the controls row
 * at the bottom in the same place on every screen: [lap] on the left (Lap or Skip, or nothing), Pause or Resume in the
 * middle and largest, and the square to hold to end. Back tucks it away.
 *
 * With [paused] given, a paused workout shows it in place of [content], and the controls become End workout (held, so
 * ending still takes a hold) and Resume. [status] adds lines to the strap's on the top right (GPS and its accuracy).
 */
@Composable
@Suppress("UnusedParameter")
internal fun LiveFrame(
    r: WorkoutState.Recording,
    color: Color,
    connected: Boolean,
    contentPadding: PaddingValues,
    controls: LiveControls,
    lap: (@Composable () -> Unit)? = null,
    /** The total time, read by its own text so only the clock is composed again each second. */
    time: (() -> Long)? = null,
    /** Kept for the screens that pass it: the total is always small under the sport's name now. */
    smallTime: Boolean = false,
    battery: Int? = null,
    status: List<Pair<String, Color>> = emptyList(),
    paused: (@Composable ColumnScope.() -> Unit)? = null,
    /** The [content] takes the height left above the buttons itself (a weighted pager), so nothing pushes them down. */
    fill: Boolean = false,
    /** The pill's words when more than the sport's name ("HIIT, Tabata"). */
    title: String? = null,
    backdrop: androidx.compose.ui.graphics.Brush? = null,
    /** Mind and body (board LiveMind): the sport's name plain and centred, no strap status, and two dark buttons. */
    quiet: Boolean = false,
    /** Under a quiet screen's buttons, the line saying a tap lights it; left out where there's no room for it. */
    tapHint: Boolean = true,
    /**
     * A page under a bar shared by the pages ([LiveBottomBar]): the note, the prompt and the controls are drawn once
     * under the pager, so the page only leaves this much room for them.
     */
    bottomRoom: Dp? = null,
    /** Pause is the one white tap, unless the screen has its own next step (Set done, Start set): then that is white. */
    pauseWhite: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = controls.onMinimize)
    val landscape = LocalLiveLandscape.current
    // On its side the screen keeps a wider column than a page's, so the content and the controls fit side by side.
    CompositionLocalProvider(LocalPageColumn provides if (landscape) GlowLayout.MaxContentWidth else LocalPageColumn.current) {
        HaloPageColumn(contentPadding, scroll = false) {
            LiveTopBar(r, connected, controls, title, time, status, battery, quiet)
            if (landscape) {
                LandscapePanes(
                    r, connected, controls, bottomRoom != null, paused,
                    frameControls = { FrameControls(r, controls, lap, quiet, tapHint, pauseWhite) },
                    content = content,
                )
                return@HaloPageColumn
            }
            if (r.paused && paused != null) paused() else content()
            if (bottomRoom != null && !r.paused) {
                Spacer(Modifier.height(bottomRoom))
                return@HaloPageColumn
            }
            if (!connected && !r.paused) {
                HaloNote(WorkoutCopy.STRAP_GONE_NOTE)
            }
            if (r.endFailed) NotSavedNotice(controls.onEnd)
            r.endPrompt?.let { at -> StoppedPrompt(r, at, controls.onEndAtPrompt, controls.onKeepGoing) }
            if (!fill || r.paused) Spacer(Modifier.weight(1f))
            FrameControls(r, controls, lap, quiet, tapHint, pauseWhite)
        }
    }
}

/**
 * The live screen is on its side and whole (not half of a folded phone): [LiveFrame] puts the controls beside the
 * content instead of under it. Set by [GlowWorkoutLiveScreen].
 */
@Suppress("CompositionLocalAllowlist", "ComposeCompositionLocalUsage")
internal val LocalLiveLandscape = staticCompositionLocalOf { false }

/**
 * A phone on its side (or an unfolded one) is too short for the screen above its controls: the controls take a column
 * on the right, under the moving time when it's large, and the screen's own [content] the rest, scrolling only when
 * the height can't hold it, so the buttons never cover it. The strap note and the stopped prompt go first on the left,
 * where they're seen. A page under a shared bar ([sharedBar]) leaves the right to that bar ([LiveBottomBar]).
 */
@Composable
private fun ColumnScope.LandscapePanes(
    r: WorkoutState.Recording,
    connected: Boolean,
    controls: LiveControls,
    sharedBar: Boolean,
    paused: (@Composable ColumnScope.() -> Unit)?,
    frameControls: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Paused, a page under the shared bar is the whole screen, as it is upright.
    val bar = sharedBar && !r.paused
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val side = landscapeControlsWidth(maxWidth)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(LandscapeGap)) {
            ScrollPane(Modifier.weight(1f).fillMaxHeight(), Arrangement.spacedBy(Space.S12)) {
                if (!bar && !connected && !r.paused) HaloNote(WorkoutCopy.STRAP_GONE_NOTE)
                if (!bar && r.endFailed) NotSavedNotice(controls.onEnd)
                if (!bar) r.endPrompt?.let { at -> StoppedPrompt(r, at, controls.onEndAtPrompt, controls.onKeepGoing) }
                if (r.paused && paused != null) paused() else content()
            }
            if (bar) return@Row
            // Centred in the height, and like the content, scrolling rather than cut on a screen too short for it.
            val centred = Arrangement.spacedBy(Space.S12, Alignment.CenterVertically)
            ScrollPane(Modifier.width(side).fillMaxHeight(), centred, Alignment.CenterHorizontally) {
                frameControls()
            }
        }
    }
}

/**
 * A pane of a phone on its side: [content] at least the pane's height, so the screens' weights still share it out
 * when everything fits, and scrolling when the height can't hold it, so nothing is cut.
 */
@Composable
private fun ScrollPane(
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment, content = content,
        )
    }
}

/**
 * A short phone on its side ([compact] and [LocalLiveLandscape]): its content pane scrolls rather than squeeze, so a
 * part that takes the height left over ([Modifier.weight]) keeps its own height instead, or it would be squeezed to
 * nothing under its neighbours.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun shortLandscape(compact: Boolean): Boolean = compact && LocalLiveLandscape.current

/** How wide the controls' column is on a phone on its side: the controls row's own width where there's room. */
internal fun landscapeControlsWidth(width: Dp): Dp = (width * CONTROLS_SHARE).coerceIn(260.dp, 360.dp)

private const val CONTROLS_SHARE = 0.42f

/** Between the content and the controls' column on a phone on its side. */
private val LandscapeGap = 24.dp

/**
 * The controls at the foot of the screen, or in their own column on a phone on its side: End workout and Resume when
 * [hasPaused] shows the paused view, the two quiet buttons ([quiet]), or else the controls row.
 */
@Composable
private fun ColumnScope.FrameControls(
    r: WorkoutState.Recording, controls: LiveControls, lap: (@Composable () -> Unit)?, quiet: Boolean, tapHint: Boolean,
        pauseWhite: Boolean,
) {
    // One controls row on every screen, paused too: Resume takes Pause's place as the white tap (board HaloLivePaused).
    if (quiet && tapHint && !r.paused) ControlsHint(stringResource(R.string.workout_live_tap_to_light), alpha = 1f)
    // 8 under the row on top of the page's 16, so the row stands 24 from the bottom, where [LiveBottomBar] puts it: a
    // padding rather than a spacer, which the page's gap would push a further 12.
    ControlsRow(r, controls, lap, true, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Space.S8), pauseWhite)
}

/** A line under the controls saying how to use them, centred. Quieter on the dimmed mind screen ([alpha]). */
@Composable
private fun ControlsHint(text: String, alpha: Float = 1f) = Text(
    text, color = HintInk.copy(alpha = alpha), style = GlowType.noteSmall,
    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
)

/**
 * The controls row, in the same place on every live screen (workout-rules.md, section 1; board HaloLiveRun): a 72 / 1fr
 * / 72 grid with [lap] on the left (or nothing), Pause or Resume in the middle, an 88 circle that is the one white tap
 * unless [pauseWhite] is false, and Stop on the right, held. Each side button has its label under it.
 */
@Composable
internal fun ControlsRow(
    r: WorkoutState.Recording, controls: LiveControls, lap: (@Composable () -> Unit)?, labelled: Boolean, modifier: Modifier = Modifier,
    pauseWhite: Boolean = true,
) {
    // At most a thumb's reach wide, so on a wide screen (an unfolded or half-folded phone) Lap and End stay by Pause.
    Row(modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
        val pauseLabel = stringResource(if (r.paused) R.string.workout_live_resume else R.string.workout_live_pause)
        Box(Modifier.width(72.dp).height(88.dp), contentAlignment = Alignment.Center) { if (lap != null) lap() }
        Spacer(Modifier.weight(1f))
        // The label of Pause is its description only; the boards put no word under the white circle.
        val white = pauseWhite || r.paused
        HaloIconButton(
            if (r.paused) AppIcons.PlayFill else AppIcons.PauseFill, pauseLabel, if (r.paused) controls.onResume else controls.onPause,
            variant = if (white) HaloIconVariant.Primary else HaloIconVariant.Control, size = HaloIconSize.Pause,
        )
        Spacer(Modifier.weight(1f))
        // The label may be wider than the button at a large font: it centres on it and runs over the gutter, never cut.
        Box(Modifier.width(72.dp), contentAlignment = Alignment.TopCenter) {
            Labelled(stringResource(R.string.workout_live_hold_to_end), labelled) { HoldToEnd(controls.onEnd) }
        }
    }
}

/** A control on the controls row, with its [label] under it (12, #C9CED6). */
@Composable
private fun Labelled(label: String, show: Boolean, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        // The side buttons are 60 tall, centred on Pause's 88 so the row reads as one line.
        Box(Modifier.height(88.dp), contentAlignment = Alignment.Center) { button() }
        if (show) Text(
            label, color = LiveLabelInk, style = GlowType.noteSmall, maxLines = 1, softWrap = false,
            modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
        )
    }
}

/** The controls row's left button: Lap, which marks a lap, or Skip ([skip]), a 60 squircle with its label under it. */
@Composable
internal fun LapButton(onLap: () -> Unit, label: Boolean = false, skip: Boolean = false) {
    val text = stringResource(if (skip) R.string.workout_live_skip else R.string.workout_live_lap)
    SideAction(text, if (skip) AppIcons.SkipFill else SportIcons.Lap, onLap, label)
}

/** A side action of the controls row: a 60 [HaloIconButton] circle holding an icon, its [label] under it (board HaloLiveRun, Lap). */
@Composable
internal fun SideAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, label: Boolean = true) {
    Labelled(text, label) {
        HaloIconButton(icon, text, onClick, size = HaloIconSize.Live)
    }
}

/** The grey of a side button's hint under the controls (workout-rules.md, Frame). */
private val HintInk = Ink.Disabled

/** The total time under the sport's name, read in its own scope so only it is composed again each second. */
@Composable
private fun TotalTime(time: () -> Long, paused: Boolean) {
    val suffix = if (paused) ", " + stringResource(R.string.workout_live_status_paused).lowercase() else ""
    Text(
        workoutClock(time()) + suffix, color = GlowType.noteColor,
        style = GlowType.secondary.copy(fontFeatureSettings = "tnum"), maxLines = 1,
    )
}

/**
 * The top bar of every live screen (workout-rules.md, Frame): a 96 / 1fr / 96 grid. Minimise at the left, the sport in
 * its colour with a dot before it and the total time under it in the middle, the strap and GPS lines at the right.
 * [title] is the words when more than the sport's name; [quiet] leaves the strap lines out (mind and body).
 */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun LiveTopBar(
    r: WorkoutState.Recording, connected: Boolean, controls: LiveControls, title: String?, time: (() -> Long)?,
    status: List<Pair<String, Color>>, battery: Int?, quiet: Boolean,
) {
    val color = liveColor(r)
    HaloPageHeader(
        variant = HeaderVariant.Workout,
        start = { HaloIconButton(AppIcons.CaretDown, stringResource(R.string.workout_live_minimize), controls.onMinimize) },
        centre = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(
                    title ?: Workouts.sportName(r.workout.sportType), color = color,
                    style = GlowType.cardTitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Where it's done, beside the one name (board HaloRunNaming): Indoor on a treadmill or a bike indoors.
                SideTag(r.workout.sportType)
            }
            if (time != null) TotalTime(time, r.paused)
        } },
    ) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.S2)) {
            if (quiet) return@Column
            val strap = when {
                !connected -> WorkoutWords.STRAP_GONE
                battery != null -> stringResource(R.string.workout_live_status_strap_battery, battery)
                else -> stringResource(R.string.workout_live_status_strap)
            }
            Text(strap, color = if (connected) GlowType.noteColor else TrendBad, style = GlowType.noteSmall, maxLines = 1)
            if (!r.paused) status.forEach { (text, _) -> Text(text, color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1) }
        }
    }
}

/**
 * What the run's views share under their pager, fixed while the views slide: the strap note, the stopped prompt, the
 * page dots following the swipe ([position]) and the controls. [onHeight] reports its height above the bottom padding.
 * On a phone on its side ([LocalLiveLandscape]) it's a column beside the pager instead, as wide as [modifier] makes it.
 */
@Composable
internal fun LiveBottomBar(
    r: WorkoutState.Recording, connected: Boolean, contentPadding: PaddingValues, controls: LiveControls, lap: (@Composable () -> Unit)?,
    /** The pager's page plus how far it's swiped, read here so a swipe composes only the bar again. */
    position: () -> Float, count: Int, onPage: (Int) -> Unit, onHeight: (Dp) -> Unit, modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val items: @Composable ColumnScope.() -> Unit = {
        if (!connected) HaloNote(WorkoutCopy.STRAP_GONE_NOTE)
        if (r.endFailed) NotSavedNotice(controls.onEnd)
        r.endPrompt?.let { at -> StoppedPrompt(r, at, controls.onEndAtPrompt, controls.onKeepGoing) }
        val at = position()
        val view = stringResource(R.string.workout_live_view_of, kotlin.math.round(at).toInt() + 1, count)
        Box(Modifier.fillMaxWidth().semantics { contentDescription = view }) {
            PageDots(kotlin.math.round(at).toInt(), count, at, onPage)
        }
        ControlsRow(r, controls, lap, labelled = true, Modifier.align(Alignment.CenterHorizontally))
    }
    if (LocalLiveLandscape.current) {
        val end = contentPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + GlowLayout.Gutter
        // Centred in the height, scrolling only when the stopped prompt makes it taller than the screen.
        ScrollPane(
            modifier.fillMaxHeight().statusBarsPadding()
                .padding(top = contentPadding.calculateTopPadding() + Space.S8, bottom = contentPadding.calculateBottomPadding(),
                    end = end),
            Arrangement.spacedBy(Space.S12, Alignment.CenterVertically), Alignment.CenterHorizontally, items,
        )
        return
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val side = org.openstrap.app.ui.glow.GlowLayout.pageSide(maxWidth, org.openstrap.app.ui.glow.LocalPageColumn.current,
            org.openstrap.app.ui.glow.GlowLayout.Gutter)
        Column(
            Modifier.fillMaxWidth().padding(bottom = contentPadding.calculateBottomPadding())
                .onSizeChanged { onHeight(with(density) { it.height.toDp() }) }
                .padding(start = side, end = side, bottom = Space.S24),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
            content = items,
        )
    }
}

/**
 * Two small dots for the live views, the current one white; tapping one goes there (the map keeps its own drags).
 * With [position] (the pager's page plus how far it's swiped) the white passes from one dot to the next as it slides.
 */
@Composable
internal fun PageDots(current: Int, count: Int, position: Float? = null, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                // 48 dp square to tap, laid out as tall as before so the live screens keep their height: the 10 dp above
                // and below reach into the bottom bar's 14 dp gaps, never over the controls.
                Modifier.layout { measurable, constraints ->
                    val tap = measurable.measure(constraints)
                    val row = DotsRowHeight.roundToPx()
                    layout(tap.width, row) { tap.place(0, (row - tap.height) / 2) }
                }.size(DotTap).clip(CircleShape).pressClickable({ onSelect(i) }, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val lit = if (position != null) (1f - kotlin.math.abs(position - i)).coerceIn(0f, 1f) else if (i == current) 1f else 0f
                // The current view is an 18 wide white pill, the others 6 dots (board HaloLiveRun, "View 1 of 2").
                Box(Modifier.size(width = (6f + 12f * lit).dp,
                    height = 6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.lerp(DotOff, Color.White, lit)))
            }
        }
    }
}

private val DotOff = Greys.Line

/** A page dot's tap target, and the height the dots' row takes in the bottom bar. */
private val DotTap = 48.dp
private val DotsRowHeight = 28.dp

/** A distance for a live figure: "4.02 km", the unit after the number. */
@Composable
@ReadOnlyComposable
internal fun km(meters: Float): String =
    Units.km(meters, Units.Precision.Workout) + " " + stringResource(R.string.workout_live_unit_km)

/** A speed for a live figure: "24.5 km/h", or null while there is none. */
@Composable
@ReadOnlyComposable
internal fun kmh(speed: String?): String? = speed?.let { it + " " + stringResource(R.string.workout_live_unit_kmh) }

/** End was tried and couldn't save: the workout records on, and Try again runs End again. */
@Composable
internal fun NotSavedNotice(onTryAgain: () -> Unit) {
    HaloBanner(
        BannerKind.Problem, stringResource(R.string.workout_live_not_saved_title),
        note = stringResource(R.string.workout_live_not_saved_note),
    ) {
        HaloButton(
            stringResource(R.string.workout_live_try_again), onTryAgain, Modifier.fillMaxWidth(), HaloButtonVariant.Primary,
        )
    }
}

/** "Looks like you've stopped": end where the wearer stopped, or keep going. The strap has buzzed already. */
@Composable
internal fun StoppedPrompt(r: WorkoutState.Recording, at: Long, onEnd: () -> Unit, onKeep: () -> Unit) {
    val sport = sportNoun(r.workout.sportType) ?: stringResource(R.string.glow_today_workout_noun)
    HaloBanner(
        BannerKind.Info, stringResource(if (r.inCar) R.string.workout_live_stopped_car_title else R.string.workout_live_stopped_title),
        note = stringResource(
            if (r.inCar) R.string.workout_live_stopped_car_note else R.string.workout_live_stopped_note, clock(at),
            sport,
        ),
    ) {
        ButtonPair(
            stringResource(R.string.workout_live_keep_going), onKeep, stringResource(R.string.workout_live_stopped_end_at, clock(at)),
            onEnd,
        )
    }
}

/**
 * Stop (workout-rules.md, Frame; board HaloLiveRun): a 60 circle ([HaloIconSurface]) holding an 18 white rounded square inside a 44
 * ring. Held, a white arc fills the ring and the workout ends when it completes at [HOLD_MS]; letting go early empties it.
 */
@Composable
internal fun HoldToEnd(onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val haptics = rememberHaptics()
    val described = stringResource(R.string.workout_live_hold_to_end_description)
    // A 60 icon button's circle, with the hold's own press handling and ring in place of a tap.
    HaloIconSurface(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    coroutineScope {
                        val hold = launch {
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            haptics.perform(HapticEvent.WorkoutEnd)
                            onEnd()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            hold.cancel()
                            progress.animateTo(0f, tween(200))
                        }
                    }
                })
            }
            .semantics { contentDescription = described },
        size = HaloIconSize.Live,
    ) {
        Canvas(Modifier.size(ButtonSpec.IconDefault)) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Ink.Line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (progress.value > 0f) drawArc(Color.White, -90f, 360f * progress.value, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round))
            val side = 18.dp.toPx()
            drawRoundRect(Color.White, Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side),
                CornerRadius(5.dp.toPx()))
        }
    }
}

internal val EndRed = MetricColors.heart
private const val HOLD_MS = 2000

/** A live clock in white, its own scope: the second it reads composes it again, not the screen around it. */
@Composable
internal fun ClockText(time: () -> Long, style: androidx.compose.ui.text.TextStyle, maxLines: Int = Int.MAX_VALUE) {
    Text(workoutClock(time()), color = Color.White, style = style, maxLines = maxLines)
}

/** Now in epoch seconds, ticking on each whole second: the clock every live screen counts from. */
@Composable
internal fun nowTicking(): Long = nowTickingState().value

/** [nowTicking] as state, read only where the second shows, so what holds it isn't composed again each second. */
@Composable
internal fun nowTickingState(): androidx.compose.runtime.State<Long> = produceState(System.currentTimeMillis() / 1000) {
    while (true) {
        value = System.currentTimeMillis() / 1000
        delay(1000 - System.currentTimeMillis() % 1000)
    }
}

/**
 * The live heart rate over the map (board HaloLiveMap): a dark see-through pill with a white heart, the bpm in Display
 * Light 18, "bpm" in grey and the zone's chip (workout-rules.md, Colour). Paused or
 * without a reading, it says so.
 */
@Composable
internal fun LitHeartPill(bpm: Int?, maxHr: Int?, paused: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val heartZones = LocalHeartZones.current
    Row(
        modifier.height(height).clip(CircleShape).background(OverMapSurface).padding(horizontal = Space.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        if (bpm == null) {
            Text(WorkoutCopy.noHeartRate(paused), color = GlowType.noteColor, style = GlowType.secondary)
            return@Row
        }
        Icon(AppIcons.Heartbeat, null, tint = Color.White, modifier = Modifier.size(IconSize.GlyphS))
        Text("$bpm", color = Color.White, style = GlowType.figureInline)
        val zone = maxHr?.let { WorkoutRecorder.liveZone(bpm, it, heartZones) }
        Text(stringResource(R.string.workout_live_unit_bpm), color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1)
        zone?.let { org.openstrap.app.ui.glow.ZoneChip(it) }
    }
}

/** Over the map: near black, a little see-through (board HaloLiveMap: rgba(12,13,16,0.82)). */
internal val OverMapSurface = Greys.OverMap
