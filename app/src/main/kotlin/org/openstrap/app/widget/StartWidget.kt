package org.openstrap.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import org.openstrap.app.MainActivity
import org.openstrap.app.R
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.data.Workouts
import org.openstrap.app.device.WorkoutState
import org.openstrap.app.ui.theme.ControlSurface
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.heroStyle
import org.openstrap.app.presentation.WorkoutColors
import org.openstrap.app.ui.theme.SportColors
import org.openstrap.app.ui.glow.draw.drawIcon
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.SportIcons
import java.time.Instant
import java.time.ZoneId

/**
 * The Start a workout widget (widgets spec 3.5), the one action widget: home screen to Ready in one tap. The usual
 * sport with its play button at every size, the next four as tiles on the Wide, this week and the usual's last on the
 * Large; while a workout records, that workout with Open. An edge card lit from the right, toward Start, in heart ink
 * and the moment's mood. Play, a tile or the widget opens Ready with that sport, All sports the sport picker.
 */
class StartWidget : LoadingWidget<StartWidget.Shown>(WidgetKind.Start) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown = sampleShown()

    /** The debug gallery's sample, Moment's recording card too. */
    internal fun sampleShown(): Shown {
        val idle = WidgetSamples.start(Instant.now(), ZoneId.systemDefault())
        // The debug gallery's recording sample: a walk 24 minutes in at 112 bpm, zone 2.
        val model = if (WidgetRefresh.recordingOverride) {
            idle.copy(recording = WidgetSamples.recording(Instant.now(), ZoneId.systemDefault()))
        } else idle
        return Shown(model, GlowMood.Day)
    }

    @Composable
    override fun Content(data: Shown) = StartContent(data)

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: StartWidgetModel, val mood: GlowMood)

    companion object {
        /**
         * Every workout as the workout lists read them ([Workouts.listed]), the Outdoor or Indoor side last chosen per
         * sport and whether location is on, as Ready reads them, and the workout recording then. The mood from the
         * moment then ([widgetMood]).
         */
        internal fun load(data: WidgetData): Shown {
            val read = checkNotNull(data.start) { "not read for Start" }
            val recording = (read.state as? WorkoutState.Recording)?.let { r ->
                val w = r.workout
                val bpm = read.bpm.takeIf { !r.paused }
                val now = data.now.epochSecond
                StartRecordingInput(
                    w.sportType, w.start, WorkoutRecorder.activeSeconds(w, now), r.paused,
                    bpm = bpm, zone = WorkoutRecorder.liveZone(bpm, r.maxHeartRate, read.zones),
                    resting = r.resting || r.phase(now) is org.openstrap.app.data.Phase.Rest,
                    asking = r.endPrompt != null,
                    figures = recordingFigures(r, WorkoutRecorder.activeSeconds(w, now), read.live),
                )
            }
            val model = StartWidgetModel.from(read.workouts.orEmpty(), read.sides, read.location, recording, data.now, data.zone)
            return Shown(model, widgetMood(data, data.usualBedtime))
        }
    }
}

/**
 * The Large's three figures for a recording [r] [active] seconds in (workout-rules.md, section 7, rule 6), the
 * sport's own: sets, kg lifted and kcal for strength; distance, average pace and kcal with a distance; else the average
 * and peak heart rate and kcal. A figure not in yet is left out.
 */
internal fun recordingFigures(r: WorkoutState.Recording, active: Long, live: LiveWorkoutFigures?): List<Pair<String, String>> {
    val s = org.openstrap.app.i18n.Strings.app
    val kcal = live?.activeKcal?.toInt()?.toString() to s.get(R.string.workout_live_figure_active_kcal)
    val meters = r.workout.distanceMeters?.takeIf { it >= org.openstrap.app.presentation.Units.SHOWN_FROM_METERS }
    val all = when {
        r.strength -> listOf(
            r.setsCounted.toString() to s.get(R.string.workout_live_figure_sets),
            org.openstrap.app.data.Strength.kgLifted(r.sets)?.let { org.openstrap.app.data.Strength.kgText(it) } to
                s.get(R.string.workout_live_figure_kg_lifted),
            kcal,
        )
        meters != null -> listOf(
            org.openstrap.app.presentation.Units.km(meters, org.openstrap.app.presentation.Units.Precision.Workout) + " " +
                s.get(R.string.workout_glance_unit_km) to s.get(R.string.workout_live_figure_distance),
            org.openstrap.app.presentation.Pace.clock(active / (meters / METERS_PER_KM_D)) to s.get(R.string.workout_live_figure_avg_pace),
            kcal,
        )
        else -> listOf(
            live?.averageBpm?.toString() to s.get(R.string.workout_live_figure_avg_bpm),
            live?.peakBpm?.toString() to s.get(R.string.workout_live_figure_peak_bpm),
            kcal,
        )
    }
    return all.mapNotNull { (v, l) -> v?.let { it to l } }
}

/** Metres in a kilometre, for the average pace. */
private const val METERS_PER_KM_D = 1000.0

/** The Start widget's receiver. */
class StartWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StartWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Taps                                                                                              */
/* ------------------------------------------------------------------------------------------------ */

/** Ready with [sport] (spec 4), as a launcher shortcut opens it. */
internal fun openReady(context: Context, sport: Int): Intent =
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_READY_SPORT, sport).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** Ready on the sport picker: All sports. */
internal fun openSportPicker(context: Context): Intent =
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_READY_PICK, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

/** The live screen of the workout recording, as its notification opens it. */
internal fun openLiveWorkout(context: Context): Intent =
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SHOW_WORKOUT, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** The sport's name, bpm and the recording's figures: cardTitle. */
private val NameStyle = WidgetType.cardTitle

/** Open's word, black on its white pill. */
private val OpenStyle = WidgetType.button

/** Back to the card's 20 dp side padding, for every row but the tile captions, which may run into it. */
private val Inset = GlanceModifier.padding(horizontal = StartLayout.CAPTION_BLEED_DP.dp)

/**
 * The widget at the size Glance lays out, an unlit Halo card. The whole widget opens Ready
 * with the usual, or the live screen while a workout records.
 */
@Composable
internal fun StartContent(data: StartWidget.Shown) {
    val m = data.model
    val context = LocalContext.current
    val open = if (m.recording != null) openLiveWorkout(context) else openReady(context, m.usual.sportType)
    // The side padding is half the card's, so the tile captions can run into it; everything else pads back ([Inset]).
    // While a workout records, the recording is the news and the live screen shows the strap: no status line.
    // Unlit like the app's Start card, but lit in the sport's colour while a workout records, as its live screen is
    // (board HaloWidgets, States; workout-rules.md, one sport one colour). Paused, the paused grey.
    val side = WidgetSizes.PADDING_DP - StartLayout.CAPTION_BLEED_DP
    val content: @Composable ColumnScope.(WidgetFrame) -> Unit = { frame -> StartBody(m, frame) }
    if (m.recording != null) {
        val ink = recordingInk(m.recording)
        val light = heroStyle(ink, RECORDING_LIGHT_ANGLE).copy(mood = data.mood)
        GlowWidget(light, open, BITMAP_EXTRA, showStatus = false, sidePadding = side, content = content)
    } else {
        PictureWidget(open, BITMAP_EXTRA, sidePadding = side, content = content)
    }
}

/**
 * What a recording card is lit in (workout-rules.md, section 7, rule 2): the sport's colour, or the state that replaces
 * it, paused grey, rest teal, asking amber. Never heart red, never the zone.
 */
internal fun recordingInk(r: StartRecording): Color = when (r.state) {
    RecordingState.Paused -> Ink.Paused
    RecordingState.Rest -> Color(WorkoutColors.REST)
    RecordingState.Asking -> MetricColors.warn
    RecordingState.Recording -> sportInk(r.sport.sportType)
}

/** The light's angle while recording: from the right, toward Open, as the app's Start card's light travels. */
private const val RECORDING_LIGHT_ANGLE = 0f

/** What the widget shows at [frame]'s size: the usual and its tiles under the top row, or the recording. */
@Composable
private fun ColumnScope.StartBody(m: StartWidgetModel, frame: WidgetFrame) {
    val rec = m.recording
    when {
        rec != null && frame.bucket == WidgetSizes.Small ->
            Column(GlanceModifier.fillMaxWidth().defaultWeight().then(Inset)) { SmallRecording(rec, frame) }
        rec != null && frame.bucket == WidgetSizes.Wide -> WideRecording(m, rec, frame)
        rec != null -> LargeRecording(m, rec, frame)
        frame.bucket == WidgetSizes.Small -> Column(GlanceModifier.fillMaxWidth().defaultWeight().then(Inset)) { SmallContent(m, frame) }
        frame.bucket == WidgetSizes.Wide -> WideContent(m, frame)
        else -> LargeContent(m, frame)
    }
}

/** The top row every idle size opens with, like the other widgets' ([TopRowView]): the title and the strap status button. */
@Composable
private fun StartTop(f: WidgetFrame) {
    val small = f.bucket == WidgetSizes.Small
    val title = if (small) StartLayout.SHORT_TITLE else StartWidgetModel.TITLE
    val top = TopRow.of(title, null, f.status, f.contentWidth, f.fontScale, withWords = !small)
    Column(GlanceModifier.fillMaxWidth().then(if (small) GlanceModifier else Inset)) { TopRowView(title, null, top, f, above = 0f) }
}

/**
 * Small: the top row, then the usual's tile with play beside it and its name under them, on the grid (one flexible
 * space under the top row). The name is one line, cut with an ellipsis rather than wrapped.
 */
@Composable
private fun ColumnScope.SmallContent(m: StartWidgetModel, f: WidgetFrame) {
    val sz = StartLayout.GRID_SMALL
    StartTop(f)
    Gap()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UsualTile(m.usual, false, sz, f.density)
        Spacer(GlanceModifier.defaultWeight())
        Action(m, sz, f)
    }
    Gap(WidgetGrid.GAP_TIGHT_DP)
    // When the strap needs something its words take the name's line, as on the other Smalls, where the top row has no room.
    val attention = attentionWords(f)
    if (attention != null) AttentionText(attention) else Text(m.usual.name, style = NameStyle, maxLines = 1)
}

/** Wide: the top row, the usual row, the sport tiles (bare on this height, each read out by name). */
@Composable
private fun ColumnScope.WideContent(m: StartWidgetModel, f: WidgetFrame) {
    val top = TopRow.of(StartWidgetModel.TITLE, null, f.status, f.contentWidth, f.fontScale, withWords = true)
    val b = StartLayout.wide(m, f.contentWidth, f.contentHeight, f.fontScale, top.height)
    StartTop(f)
    Gap()
    UsualRow(m, b, f)
    b.tiles?.let {
        Flex()
        TileRow(it, f)
    }
}

/** Large: the Wide's, a hairline, then This week and the usual sport's last. */
@Composable
private fun ColumnScope.LargeContent(m: StartWidgetModel, f: WidgetFrame) {
    val top = TopRow.of(StartWidgetModel.TITLE, null, f.status, f.contentWidth, f.fontScale, withWords = true)
    val b = StartLayout.large(m, f.contentWidth, f.contentHeight, f.fontScale, top.height)
    StartTop(f)
    Gap()
    UsualRow(m, b, f)
    b.tiles?.let {
        Gap()
        TileRow(it, f)
    }
    Flex()
    Hairline()
    Gap()
    b.figures?.let { Figures(m, it) }
}

/** The usual's tile, its name over "Your usual, GPS on" with the location mark, and play; or the workout recording. */
@Composable
private fun UsualRow(m: StartWidgetModel, b: StartLayout.Block, f: WidgetFrame) {
    val sport = m.usual
    val words = b.row.words
    Row(GlanceModifier.fillMaxWidth().then(Inset), verticalAlignment = Alignment.CenterVertically) {
        UsualTile(sport, false, b.sizes, f.density)
        Spacer(GlanceModifier.width(StartLayout.ROW_GAP_DP.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(words.name, style = NameStyle, maxLines = 1)
            words.line?.let { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(line, style = WidgetType.secondary, maxLines = 1)
                    if (words.mark) {
                        Spacer(GlanceModifier.width(StartLayout.MARK_GAP_DP.dp))
                        Image(
                            ImageProvider(iconBitmap(SportIcons.Location, Ink.Secondary, StartLayout.MARK_DP, f.density)),
                            contentDescription = null,
                            modifier = GlanceModifier.size(StartLayout.MARK_DP.dp),
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.width(StartLayout.ROW_GAP_DP.dp))
        Action(m, b.sizes, f)
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Recording                                                                                         */
/* ------------------------------------------------------------------------------------------------ */

/**
 * Small while recording, as board HaloWidgetsWorkout draws it: the sport's name over a dot and "Recording", the live
 * timer at 40 sp, then the heart rate with its zone chip. The whole card opens the live screen. Every row is counted
 * ([StartLayout.smallRecording]), so none is cut by the card's edge.
 */
@Composable
private fun ColumnScope.SmallRecording(r: StartRecording, f: WidgetFrame) {
    val fit = StartLayout.smallRecording(r, f.contentWidth, f.contentHeight, f.fontScale)
    Text(fit.name, style = NameStyle, maxLines = 1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(dotBitmap(if (r.paused) Ink.Tertiary else Color.White, f.density, StartLayout.SMALL_DOT_DP)),
            contentDescription = null,
            modifier = GlanceModifier.size(StartLayout.SMALL_DOT_DP.dp),
            contentScale = ContentScale.FillBounds,
        )
        Spacer(GlanceModifier.width(StartLayout.DOT_GAP_DP.dp))
        Text(r.status, style = LitSecondary, maxLines = 1)
    }
    Flex()
    Timer(r, fit.timerSp, fit.timerLine)
    val heart = r.heart
    if (heart != null) {
        Gap(WidgetGrid.GAP_TIGHT_DP)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(heart, style = LitSecondary.copy(fontFamily = WidgetType.cardTitle.fontFamily), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (r.zone != null && r.zoneLabel != null) ZoneChip(r.zone, fit.chipLabel(r.zoneLabel), f)
        }
    }
}

/** Wide while recording: the sport's row with "Recording" and Open, then the timer as the hero. */
@Composable
private fun ColumnScope.WideRecording(m: StartWidgetModel, r: StartRecording, f: WidgetFrame) {
    val fit = StartLayout.wideRecording(r, f.contentWidth, f.contentHeight, f.fontScale)
    RecordingRow(m, r, fit, f)
    Flex()
    TimerRow(r, fit, f)
}

/** Large while recording: the Wide's, then the hairline and the week's figures, secondary to the timer. */
@Composable
private fun ColumnScope.LargeRecording(m: StartWidgetModel, r: StartRecording, f: WidgetFrame) {
    val fit = StartLayout.largeRecording(r, m, f.contentWidth, f.contentHeight, f.fontScale)
    RecordingRow(m, r, fit, f)
    Flex()
    TimerRow(r, fit, f)
    Gap()
    Hairline()
    Gap()
    fit.figures?.let { if (it.layout == StartLayout.FigureLayout.Live) LiveFigures(r) else Figures(m, it, lit = true) }
}

/**
 * Secondary words on the recording's light: white, not the grey of the dark cards, since the light is held to a
 * luminance white keeps 4.5:1 on ([limitLuminance]) and the grey doesn't.
 */
private val LitSecondary = WidgetType.secondaryShown

/** The recording's own figures, side by side, each value over its label (board HaloWidgetsWorkout, Large). */
@Composable
private fun LiveFigures(r: StartRecording) {
    Row(GlanceModifier.fillMaxWidth().then(Inset)) {
        r.figures.forEachIndexed { i, (value, label) ->
            if (i > 0) Spacer(GlanceModifier.width(StartLayout.FIGURE_GAP_DP.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(value, style = NameStyle, maxLines = 1)
                Spacer(GlanceModifier.height(FIGURE_LINE_GAP_DP.dp))
                Text(label, style = LitSecondary, maxLines = 1)
            }
        }
    }
}

/** The sport's tile, its name over the red dot and "Recording" (white and "Paused" while paused), and Open. */
@Composable
private fun RecordingRow(m: StartWidgetModel, r: StartRecording, fit: StartLayout.Recording, f: WidgetFrame) {
    Row(GlanceModifier.fillMaxWidth().then(Inset), verticalAlignment = Alignment.CenterVertically) {
        UsualTile(r.sport, true, fit.sizes, f.density)
        Spacer(GlanceModifier.width(StartLayout.ROW_GAP_DP.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(fit.name, style = NameStyle, maxLines = 1)
            if (fit.status) Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    ImageProvider(dotBitmap(if (r.paused) Ink.Tertiary else Color.White, f.density)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(StartLayout.DOT_DP.dp),
                    contentScale = ContentScale.FillBounds,
                )
                Spacer(GlanceModifier.width(StartLayout.DOT_GAP_DP.dp))
                Text(r.status, style = LitSecondary, maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(StartLayout.ROW_GAP_DP.dp))
        Action(m, fit.sizes, f)
    }
}

/** The timer, with "Since 11:58" beside it (on its baseline side) or under it, where the layout found room. */
@Composable
private fun TimerRow(r: StartRecording, fit: StartLayout.Recording, f: WidgetFrame) {
    if (fit.heart) {
        Column(GlanceModifier.fillMaxWidth().then(Inset)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Timer(r, fit.timerSp)
                Spacer(GlanceModifier.defaultWeight())
                Heart(r, f)
            }
            if (fit.since == StartLayout.SincePlace.Under) Text(r.since, style = LitSecondary, maxLines = 1)
        }
        return
    }
    when (fit.since) {
        StartLayout.SincePlace.Beside -> Row(GlanceModifier.fillMaxWidth().then(Inset), verticalAlignment = Alignment.Bottom) {
            Timer(r, fit.timerSp)
            Spacer(GlanceModifier.width(StartLayout.SINCE_GAP_DP.dp))
            Text(r.since, style = LitSecondary, maxLines = 1)
        }
        StartLayout.SincePlace.Under -> Column(GlanceModifier.fillMaxWidth().then(Inset)) {
            Timer(r, fit.timerSp)
            Text(r.since, style = LitSecondary, maxLines = 1)
        }
        StartLayout.SincePlace.None -> Box(GlanceModifier.fillMaxWidth().then(Inset)) { Timer(r, fit.timerSp) }
    }
}

/** The live heart rate over its zone chip (workout-rules.md, section 7, rule 5). */
@Composable
private fun Heart(r: StartRecording, f: WidgetFrame) {
    Column(horizontalAlignment = Alignment.End) {
        r.heart?.let { Text(it, style = NameStyle, maxLines = 1) }
        if (r.zoneLabel != null && r.zone != null) {
            Spacer(GlanceModifier.height(StartLayout.CHIP_ABOVE_DP.dp))
            ZoneChip(r.zone, r.zoneLabel, f)
        }
    }
}

/**
 * The zone as a mark, as the app's zone chip (workout-rules.md, Colour 3): a dot in the zone's colour with a thin white
 * ring, then "Zone 3", on a dark pill. The same on any card colour.
 */
@Composable
private fun ZoneChip(zone: Int, label: String, f: WidgetFrame) {
    val w = StartLayout.chipWidth(label, f.fontScale)
    val h = StartLayout.chipHeight(f.fontScale)
    Row(
        GlanceModifier.size(w.dp, h.dp).background(ImageProvider(chipBitmap(w, h, f.density)), ContentScale.FillBounds)
            .padding(horizontal = StartLayout.CHIP_PAD_X_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(ringedDotBitmap(Color(WorkoutColors.ZONES[zone.coerceIn(0, WorkoutColors.ZONES.lastIndex)]), f.density)),
            contentDescription = null,
            modifier = GlanceModifier.size(StartLayout.CHIP_DOT_DP.dp),
            contentScale = ContentScale.FillBounds,
        )
        Spacer(GlanceModifier.width(StartLayout.DOT_GAP_DP.dp))
        Text(label, style = WidgetType.note, maxLines = 1)
    }
}

/**
 * The live timer: a RemoteViews Chronometer, which ticks on its own with no widget updates, h:mm:ss or mm:ss, at
 * [sp] (so the system font scales it). Its base puts the workout's active time at now; paused, it stands still.
 */
@Composable
private fun Timer(r: StartRecording, sp: Float, line: Float? = null) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        val base = StartRecording.chronometerBase(SystemClock.elapsedRealtime(), System.currentTimeMillis(), r)
        setChronometer(R.id.widget_chronometer, base, null, !r.paused)
        setTextViewTextSize(R.id.widget_chronometer, TypedValue.COMPLEX_UNIT_SP, sp)
    }
    // Its line's height held for it: left to wrap, a launcher could lay the timer out shorter than its text and cut it.
    val height = line ?: StartLayout.timerLine(sp, context.resources.configuration.fontScale)
    Box(GlanceModifier.height(height.dp)) { AndroidRemoteViews(views) }
}

/** The Large's hairline, padded back to 20 dp outside the line: a background would fill a padding of its own. */
@Composable
private fun Hairline() {
    Box(GlanceModifier.fillMaxWidth().then(Inset)) {
        Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Ink.Faint))) {}
    }
}

/** Play, white with a black triangle, opening Ready with the usual; or Open while a workout records. */
@Composable
private fun Action(m: StartWidgetModel, sz: StartLayout.Sizes, f: WidgetFrame) {
    val context = LocalContext.current
    if (m.recording != null) {
        val w = StartLayout.openWidth(f.fontScale)
        val h = minOf(sz.play, StartLayout.OPEN_HEIGHT_DP)
        Box(
            GlanceModifier.size(w.dp, h.dp)
                .background(ImageProvider(pillBitmap(w, h, f.density)), ContentScale.FillBounds)
                .clickable(actionStartActivity(openLiveWorkout(context))),
            contentAlignment = Alignment.Center,
        ) { Text(StartWidgetModel.OPEN, style = OpenStyle, maxLines = 1) }
        return
    }
    Image(
        ImageProvider(playBitmap(sz, f.density)),
        contentDescription = m.usual.action,
        modifier = GlanceModifier.size(sz.play.dp).clickable(actionStartActivity(openReady(context, m.usual.sportType))),
        contentScale = ContentScale.FillBounds,
    )
}

/** The usual's tile at [sz]. A tap starts it too, or opens the workout recording. */
@Composable
private fun UsualTile(sport: StartSport, recording: Boolean, sz: StartLayout.Sizes, density: Float) {
    val context = LocalContext.current
    val tap = if (recording) openLiveWorkout(context) else openReady(context, sport.sportType)
    Image(
        ImageProvider(tileBitmap(SportIcons.of(sport.shownType), sz.tile, sz.tileCorner, sz.icon, density, lit = recording)),
        contentDescription = null,
        modifier = GlanceModifier.size(sz.tile.dp).clickable(actionStartActivity(tap)),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The next sports and All sports: the tiles spread edge to edge with equal room between (the first under the usual's
 * tile, the last under play), then their captions on one line, each in a box as wide as its neighbours leave
 * ([StartLayout.captionBoxes]), each centred under its tile, the first and last running into the side padding when
 * they're wider than their tile. Each row is at most ten children (Glance drops a row's children past ten). A tile
 * and its caption open the same.
 */
@Composable
private fun TileRow(tiles: StartLayout.Tiles, f: WidgetFrame) {
    val context = LocalContext.current
    val opens = tiles.sports.map { openReady(context, it.sportType) } + openSportPicker(context)
    val actions = tiles.sports.map { it.action } + StartWidgetModel.ALL_SPORTS_ACTION
    val icons = tiles.sports.map { SportIcons.of(it.shownType) } + AppIcons.MoreHorizontalCircle01
    val captions = tiles.captions
    val n = captions.size
    val icon = (tiles.tile * StartLayout.ICON_DP / StartLayout.SMALL_TILE_DP).coerceIn(16f, 24f)
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth().then(Inset)) {
            for (i in 0 until n) {
                if (i > 0) Spacer(GlanceModifier.defaultWeight())
                // A 48 dp wide touch area round the tile (it is as tall as the tile, its caption under it opens the same):
                // the first reaches inward from the left edge, the last from the right, so the tiles stay where they
                // line up with the usual's tile and play, and the row's height is the tile's.
                val wide = if (n * StartLayout.TOUCH_DP <= f.contentWidth) maxOf(tiles.tile, StartLayout.TOUCH_DP) else tiles.tile
                val side = when (i) {
                    0 -> Alignment.Horizontal.Start
                    n - 1 -> Alignment.Horizontal.End
                    else -> Alignment.Horizontal.CenterHorizontally
                }
                Box(
                    GlanceModifier.size(wide.dp, tiles.tile.dp).clickable(actionStartActivity(opens[i])),
                    contentAlignment = Alignment(side, Alignment.Vertical.CenterVertically),
                ) {
                    Image(
                        ImageProvider(tileBitmap(icons[i], tiles.tile, tiles.corner, icon, f.density)),
                        contentDescription = actions[i],
                        modifier = GlanceModifier.size(tiles.tile.dp),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
        if (!tiles.captioned) return@Column
        Spacer(GlanceModifier.height(StartLayout.CAPTION_GAP_DP.dp))
        // Placed at the real width, so the captions sit under tiles spread across it.
        val boxes = StartLayout.captionBoxes(captions, tiles.tile, tiles.captionSp, f.drawContentWidth, f.fontScale)
        val style = WidgetType.caption.copy(fontSize = tiles.captionSp.sp)
        // This row alone spans into the padding: x runs from the bleed's edge. At most ten children: a spacer before
        // each of the five captions.
        Row(GlanceModifier.fillMaxWidth()) {
            var x = -StartLayout.CAPTION_BLEED_DP
            boxes.forEachIndexed { i, (left, w) ->
                val gap = left - x
                if (gap > 0.5f) Spacer(GlanceModifier.width(gap.dp))
                Text(
                    captions[i], style = style.copy(textAlign = TextAlign.Center), maxLines = 1,
                    modifier = GlanceModifier.width(w.dp).clickable(actionStartActivity(opens[i])),
                )
                x = left + w
            }
        }
    }
}

/** This week and the usual's last: side by side, Caption over Body, or a row each when a value won't fit half. */
@Composable
private fun Figures(m: StartWidgetModel, fit: StartLayout.Figures, lit: Boolean = false) {
    when (fit.layout) {
        StartLayout.FigureLayout.Side -> Row(GlanceModifier.fillMaxWidth().then(Inset)) {
            FigureCell(StartWidgetModel.THIS_WEEK, m.weekLine, GlanceModifier.defaultWeight(), lit)
            Spacer(GlanceModifier.width(StartLayout.FIGURE_GAP_DP.dp))
            FigureCell(m.lastLabel, m.lastLine, GlanceModifier.defaultWeight(), lit)
        }
        StartLayout.FigureLayout.Rows -> Column(GlanceModifier.fillMaxWidth().then(Inset)) {
            FigureRow(StartWidgetModel.THIS_WEEK, m.weekLine, lit)
            Spacer(GlanceModifier.height(StartLayout.FIGURE_ROW_GAP_DP.dp))
            FigureRow(m.lastLabel, m.lastLine, lit)
        }
        // A recording's own figures are drawn by [LiveFigures]; the week's never take this layout.
        StartLayout.FigureLayout.Live,
        StartLayout.FigureLayout.Stacked -> Column(GlanceModifier.fillMaxWidth().then(Inset)) {
            FigureCell(StartWidgetModel.THIS_WEEK, m.weekLine, GlanceModifier.fillMaxWidth(), lit)
            Spacer(GlanceModifier.height(StartLayout.FIGURE_ROW_GAP_DP.dp))
            FigureCell(m.lastLabel, m.lastLine, GlanceModifier.fillMaxWidth(), lit)
        }
    }
}

/** A figure as a row: its Caption on the left, the value on the right. */
@Composable
private fun FigureRow(label: String, value: String, lit: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (lit) WidgetType.captionShown else WidgetType.caption, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text(value, style = WidgetType.body, maxLines = 1)
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the tiles, play and the mark beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.3

/** A sport's tile [sizeDp] square: the Control surface with [cornerDp] corners, the icon white at [iconDp] in the middle. */
internal fun tileBitmap(icon: ImageVector, sizeDp: Float, cornerDp: Float, iconDp: Float, density: Float, lit: Boolean = false): Bitmap {
    val s = sizeDp * density
    val r = cornerDp * density
    val i = iconDp * density
    // On a lit recording card the tile is see-through black, the card's light showing through (board HaloWidgetsWorkout).
    val fill = if (lit) Ink.Scrim else ControlSurface
    return renderToBitmap(s, s) { c ->
        c.drawRoundRect(0f, 0f, s, s, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill.toArgb() })
        drawIcon(c, icon, Color.White, (s - i) / 2, (s - i) / 2, i)
    }
}

/** The play button at [sz]: a white circle with the black play triangle, as Ready's Start. */
private fun playBitmap(sz: StartLayout.Sizes, density: Float): Bitmap = playBitmap(sz.play, sz.playIcon, density)

/** The play button [sizeDp] across with its triangle [iconDp]: a white circle, the black play icon in the middle. */
internal fun playBitmap(sizeDp: Float, iconDp: Float, density: Float): Bitmap {
    val s = sizeDp * density
    val i = iconDp * density
    return renderToBitmap(s, s) { c ->
        c.drawCircle(s / 2, s / 2, s / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.White.toArgb() })
        drawIcon(c, AppIcons.PlayFill, Color.Black, (s - i) / 2, (s - i) / 2, i)
    }
}

/** Open's white pill, [w] by [h] dp, fully round. */
internal fun pillBitmap(w: Float, h: Float, density: Float): Bitmap {
    val pw = w * density
    val ph = h * density
    return renderToBitmap(pw, ph) { c ->
        c.drawRoundRect(0f, 0f, pw, ph, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.White.toArgb() })
    }
}

/** The zone chip's dark pill, [w] by [h] dp. */
internal fun chipBitmap(w: Float, h: Float, density: Float): Bitmap {
    val pw = w * density
    val ph = h * density
    return renderToBitmap(pw, ph) { c ->
        c.drawRoundRect(0f, 0f, pw, ph, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.Scrim.toArgb() })
    }
}

/** The zone chip's dot: [color] inside a thin white ring, [StartLayout.CHIP_DOT_DP] across. */
internal fun ringedDotBitmap(color: Color, density: Float): Bitmap {
    val s = StartLayout.CHIP_DOT_DP * density
    return renderToBitmap(s, s) { c ->
        c.drawCircle(s / 2, s / 2, s / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Ink.Strong.toArgb() })
        c.drawCircle(s / 2, s / 2, s / 2 - RING_DP * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
    }
}

/** The white ring around the zone chip's dot. */
private const val RING_DP = 1.5f

/** The recording dot, [color], [StartLayout.DOT_DP] across. */
internal fun dotBitmap(color: Color, density: Float, sizeDp: Float = StartLayout.DOT_DP): Bitmap {
    val s = sizeDp * density
    return renderToBitmap(s, s) { c -> c.drawCircle(s / 2, s / 2, s / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }) }
}

/**
 * A sport's own colour, its family's (workout-rules.md, Tokens): what its live screen is lit in. The indoor side of a
 * sport takes its outdoor side's, as the app's sportFamilyColor.
 */
internal fun sportInk(type: Int): Color = when (Workouts.family(Workouts.tileType(type))) {
    Workouts.Family.Distance -> SportColors.distance
    Workouts.Family.IndoorCardio -> SportColors.indoorCardio
    Workouts.Family.Strength -> SportColors.strength
    Workouts.Family.Intervals -> SportColors.intervals
    Workouts.Family.MindBody -> SportColors.mindBody
    Workouts.Family.Games -> SportColors.games
}
