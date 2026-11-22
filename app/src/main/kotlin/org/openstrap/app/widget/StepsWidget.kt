package org.openstrap.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import org.openstrap.app.R
import org.openstrap.app.data.DayWindow
import org.openstrap.app.data.DetailRoutes
import org.openstrap.app.data.Summaries
import org.openstrap.app.data.WidgetKind
import org.openstrap.app.ui.glow.draw.GlowMood
import org.openstrap.app.ui.glow.draw.drawStepsHourDots
import org.openstrap.app.ui.glow.draw.drawWeekRingsDotted
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors

/**
 * The Steps widget (widgets spec 3.3): today's steps against the goal, glanceable all day. The Wide adds what's to go
 * and the day so far as the Steps card's line, the Large this week day by day. An edge card lit from the bottom, a
 * value, in Steps' lime and the moment's mood, as Today's Steps card. The whole widget opens the Steps detail.
 */
class StepsWidget : LoadingWidget<StepsWidget.Shown>(WidgetKind.Steps) {
    override fun shown(data: WidgetData, context: Context): Shown = load(data)

    override fun sample(): Shown = Shown(WidgetSamples.steps(java.time.LocalDate.now()), GlowMood.Day)

    @Composable
    override fun Content(data: Shown) = StepsContent(data)

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: StepsWidgetModel, val mood: GlowMood)

    companion object {
        /**
         * Today's steps as Today's Steps card counts them: the synced minutes with the strap's live total on top
         * ([org.openstrap.app.data.LiveSteps]), the day's line from the minutes hour by hour, the goal from settings,
         * and this week's earlier days from the daily totals the app's week shows. The mood from the moment now
         * ([widgetMood]).
         */
        internal fun load(data: WidgetData): Shown {
            val read = checkNotNull(data.steps) { "not read for Steps" }
            val at = data.now.atZone(data.zone)
            val today = at.toLocalDate()
            val goal = read.goal.getOrThrow()
            val model = read.day?.let { d ->
                val hourly = Summaries.hourlySteps(d.minutes, DayWindow.of(today, data.zone).from)
                StepsWidgetModel.from(today, d.steps, hourly, at.hour, goal, d.past)
            } ?: StepsWidgetModel.from(today, 0, List(24) { 0f }, at.hour, goal, emptyMap())
            return Shown(model, widgetMood(data, data.usualBedtime))
        }
    }
}

/** The Steps widget's receiver. */
class StepsWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Steps' lime, as Today's Steps card. */
private val Hue = MetricColors.steps

/** The widget at the size Glance lays out: a dark picture card, as Today's Steps card. */
@Composable
private fun StepsContent(data: StepsWidget.Shown) {
    val m = data.model
    val open = openMetric(LocalContext.current, DetailRoutes.STEPS, m.date)
    PictureWidget(open, BITMAP_EXTRA) { frame ->
        when (frame.bucket) {
            WidgetSizes.Small -> SmallContent(m, frame)
            WidgetSizes.Wide -> WideContent(m, frame)
            else -> LargeContent(m, frame)
        }
    }
}

/** Small: the top row, then the steps with "today", then the hours as pills (the goal bar before a step) and the share of the goal. */
@Composable
private fun ColumnScope.SmallContent(m: StepsWidgetModel, f: WidgetFrame) {
    // While the strap needs something the line says so ("Pair your strap"), as the Wide's top row does. The words take the
    // share's place in the fit, so the Small never drops them while the share would have fitted.
    val note = attentionWords(f)
    // With words to say, the hour pills give way to the 4 dp goal bar, so the words have the room.
    val pills = m.showLine && note == null
    val fit = StepsLayout.small(m.smallText, note ?: m.share, f.contentWidth, f.contentHeight, f.fontScale, f.status, pills = pills, words = note != null)
    TopRowView(m.title, null, fit.top, f, 0f)
    Gap()
    // Each block one child of the column: Glance drops a column's children past ten.
    Column(GlanceModifier.fillMaxWidth()) {
        DotNumber(m.smallText, fit.dot, f.density, m.description)
        if (fit.word) {
            Spacer(GlanceModifier.height(StepsLayout.SMALL_WORD_GAP_DP.dp))
            Text(m.word, style = WidgetType.word, maxLines = 1)
        }
    }
    Flex()
    Column(GlanceModifier.fillMaxWidth()) {
        if (fit.bar) StepsLine(m, StepsLayout.SMALL_PILLS_DP.takeIf { pills }, f, labels = false)
        if (fit.share) {
            if (fit.bar) Spacer(GlanceModifier.height(StepsLayout.SMALL_BAR_GAP_DP.dp))
            if (note != null) AttentionText(note) else Text(m.share, style = WidgetType.secondary, maxLines = 1)
        }
    }
}

/** Wide: the top row with the goal, the steps with what's to go, the day so far as a line (or the goal bar). */
@Composable
private fun ColumnScope.WideContent(m: StepsWidgetModel, f: WidgetFrame) {
    val fit = StepsLayout.wide(m, f.contentWidth, f.contentHeight, f.fontScale, f.status)
    TopRowView(m.title, m.goalNote, fit.top, f, 0f)
    Gap()
    NumberRow(m, fit.row, f)
    Flex()
    StepsLine(m, fit.line, f)
}

/**
 * Large: the Wide's number, the goal bar, and this week as dotted rings. The rings are its one dotted chart, so the
 * hours step back to the bar (HaloCharts: one dotted chart a card).
 */
@Composable
private fun ColumnScope.LargeContent(m: StepsWidgetModel, f: WidgetFrame) {
    val fit = StepsLayout.large(m, f.contentWidth, f.contentHeight, f.fontScale, f.status)
    TopRowView(m.title, m.goalNote, fit.top, f, 0f)
    Gap()
    NumberRow(m, fit.row, f)
    Flex()
    if (fit.week != null) GoalBar(m.progress, f) else StepsLine(m, fit.line, f)
    Gap()
    fit.week?.let { WeekRow(m, it, f.density) }
}

/** The steps in dots with "today" beside them when it fits, what's to go (or "Goal met") on the right. */
@Composable
private fun NumberRow(m: StepsWidgetModel, row: StepsLayout.NumberRow, f: WidgetFrame) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        DotNumber(m.stepsText, row.dot, f.density, m.description)
        if (row.word) {
            Spacer(GlanceModifier.width(StepsLayout.WORD_GAP_DP.dp))
            Text(m.word, style = WidgetType.word, maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(m.toGo, style = WidgetType.secondary, maxLines = 1)
    }
}

/**
 * The day so far as Today's Steps card draws it, [height] dp high: the hours as lime dots, a dot per 250 steps, the
 * latest hour full and the rest dimmer ([drawStepsHourDots], HaloCharts Steps by hour), with the hours under them when
 * [labels] and there's room. The goal bar instead when there's no room or no step yet.
 */
@Composable
private fun StepsLine(m: StepsWidgetModel, height: Float?, f: WidgetFrame, labels: Boolean = true) {
    if (!m.showLine || height == null) {
        // No line (a large font, or none to draw): the goal bar shows the progress instead.
        GoalBar(m.progress, f)
        return
    }
    // The hours under the dots as the Steps card has them, when the line is tall enough to keep a readable picture over them.
    val labels = labels && height - StepsLayout.hourLabelsHeight(f.fontScale) >= StepsLayout.MIN_PICTURE_DP
    val picture = if (labels) height - StepsLayout.hourLabelsHeight(f.fontScale) else height
    val w = f.drawContentWidth * f.density
    val h = picture * f.density
    val bitmap = renderDrawScope(w, h, f.density) { drawStepsHourDots(m.hourly, Hue) }
    Column(GlanceModifier.fillMaxWidth()) {
        Image(
            ImageProvider(bitmap),
            contentDescription = LocalContext.current.getString(R.string.system_widget_steps_line_description, m.share),
            modifier = GlanceModifier.fillMaxWidth().height(picture.dp),
            contentScale = ContentScale.FillBounds,
        )
        if (labels) {
            Spacer(GlanceModifier.height(StepsLayout.HOUR_LABELS_GAP_DP.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                StepsLayout.HOUR_LABELS.forEachIndexed { i, hour ->
                    if (i > 0) Spacer(GlanceModifier.defaultWeight())
                    Text(hour, style = WidgetType.caption, maxLines = 1)
                }
            }
        }
    }
}

/** The goal bar: 4 dp, lime on white at 10%, as far as the goal, full past it. */
@Composable
private fun GoalBar(progress: Float, f: WidgetFrame) {
    Image(
        ImageProvider(goalBar(progress, f.drawContentWidth, f.density)),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(StepsLayout.BAR_DP.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/** "This week" and how many days met the goal on one line, then a ring a day under it, from the week's first day. */
@Composable
private fun WeekRow(m: StepsWidgetModel, week: StepsLayout.Week, density: Float) {
    // One child of the Large's column: Glance drops a column's children past ten.
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (week.title) {
                Text(StepsWidgetModel.THIS_WEEK, style = WidgetType.secondaryShown, maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
            }
            Text(m.weekLine, style = WidgetType.secondary, maxLines = 1)
        }
        Spacer(GlanceModifier.height(StepsLayout.WEEK_LINE_GAP_DP.dp))
        val description = LocalContext.current.getString(R.string.system_widget_steps_week_description)
        val today = m.week.map { it.letter to (it.state == StepsDay.Today) }
        val rings = ImageProvider(weekBitmap(m, week.column, density))
        WeekChart(rings, description, today, week.column, height = StepsLayout.ringHeight(week.column))
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** Room for the dots, the line, the bar and the week beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.35

/** The goal bar [width] dp wide at [density]: the track, then the fill as far as [progress]. */
private fun goalBar(progress: Float, width: Float, density: Float): Bitmap {
    val w = width * density
    val h = StepsLayout.BAR_DP * density
    val r = h / 2
    return renderToBitmap(w, h) { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawRoundRect(0f, 0f, w, h, r, r, paint.apply { color = Ink.Edge.toArgb() })
        val fill = w * progress.coerceIn(0f, 1f)
        if (fill > 0f) c.drawRoundRect(0f, 0f, fill.coerceAtLeast(h), h, r, r, paint.apply { color = Hue.toArgb() })
    }
}

/**
 * This week as dotted rings, as Today's Your week card draws them ([drawWeekRingsDotted]), a ring per column over its
 * letter. Today's ring and every day that met the goal are in Steps' lime, the others the light grey the card gives its
 * past days; each ring is drawn in its own column's clip so one picture holds both inks.
 */
private fun weekBitmap(m: StepsWidgetModel, column: Float, density: Float): Bitmap {
    val height = StepsLayout.ringHeight(column) * density
    val shares = StepsWidgetModel.ringShares(m.week, m.goal)
    return renderDrawScope(WeekBarsLook.width(m.week.size, column) * density, height, density) {
        val slot = size.width / m.week.size
        m.week.forEachIndexed { i, day ->
            val marked = day.state == StepsDay.Today || day.state == StepsDay.Met
            clipRect(left = slot * i, right = slot * (i + 1)) {
                drawWeekRingsDotted(shares, if (marked) Hue else Ink.Strong, WEEK_RING_STROKE_DP * density, i)
            }
        }
    }
}

/** The rings' stroke, as Today's Your week on a Small. */
private const val WEEK_RING_STROKE_DP = 3f
