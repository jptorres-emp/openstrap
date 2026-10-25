package org.openstrap.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LocalAppWidgetOptions
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openstrap.app.MainActivity
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.DayWindow
import org.openstrap.app.device.WidgetUpdater
import org.openstrap.app.ui.dashboard.momentMood
import org.openstrap.app.ui.glow.DotSize
import org.openstrap.app.ui.glow.EdgeKind
import org.openstrap.app.ui.glow.EdgeStatus
import org.openstrap.app.ui.glow.GlowMood
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.dotLayout
import org.openstrap.app.ui.glow.draw.drawWeekBars
import org.openstrap.app.ui.glow.draw.edgePaint
import org.openstrap.app.ui.glow.draw.renderDots
import org.openstrap.app.ui.glow.draw.renderEdge
import org.openstrap.app.ui.glow.draw.renderToBitmap
import org.openstrap.app.ui.glow.draw.weekBars
import org.openstrap.app.ui.glow.edgeLight
import org.openstrap.app.ui.theme.MetricColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Recovery widget (widgets spec 3.4): this morning's score and word in the same spot every day, with what's
 * behind it on the Wide and the sentence and the last seven days on the Large. An edge card lit from the left, about
 * earlier, in Recovery's ink and the moment's mood, as Today's Recovery card after the morning. The whole widget opens
 * the Recovery screen.
 */
class RecoveryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        // Loaded once here rather than in the content, which Glance composes once per size; again on each refresh.
        val shown = MutableStateFlow(load(context))
        launch { WidgetRefresh.version(WidgetKind.Recovery).drop(1).collectLatest { shown.value = load(context) } }
        provideContent {
            val data by shown.collectAsState()
            RecoveryContent(data)
        }
    }

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: RecoveryWidgetModel, val mood: GlowMood)

    companion object {
        private const val TAG = "OpenStrap"

        /**
         * Today's Recovery, worked out as the app does ([org.openstrap.app.data.HealthRepository.recovery]), and
         * yesterday's while this morning's isn't in; the mood from the moment now ([widgetMomentInputs]).
         */
        internal suspend fun load(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Shown {
            val container = (context.applicationContext as OpenStrapApp).container
            val profile = container.settings.profile.first()
            val dao = container.database.healthDao()
            suspend fun day(date: LocalDate) = try {
                val r = container.health.recovery(date, profile)
                val dayStart = DayWindow.of(date, zone).from
                r to RecoveryDay.of(r, dao.strapReadinessOnce(dayStart, dayStart).firstOrNull()?.score)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "widgets: Recovery for $date not worked out: $e")
                null
            }
            val today = now.atZone(zone).toLocalDate()
            val result = day(today)
            val yesterday = if (result?.second?.shown?.value == null) day(today.minusDays(1))?.second else null
            val model = RecoveryWidgetModel.from(result?.second, yesterday)
            val mood = WidgetRefresh.moodOverride ?: try {
                momentMood(widgetMomentInputs(container, result?.first?.usualBedtime, now, zone).resolve(now, zone))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "widgets: moment for the mood not worked out: $e")
                GlowMood.Day
            }
            return Shown(model, mood)
        }
    }
}

/** The Recovery widget's receiver, which the launcher knows it by. */
class RecoveryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecoveryWidget()

    /** The first Recovery widget placed: midnight's alarm goes on, and Moment stops leading with Recovery (spec 3.1). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        placedChanged(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        placedChanged(context)
    }

    /** The last one removed: the alarm comes off when nothing else is placed. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        placedChanged(context)
    }

    private fun placedChanged(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
        WidgetUpdater.changed(context, setOf(WidgetKind.Moment))
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Recovery's ink. */
private val Ink = MetricColors.readiness

/** The v2 type roles in the system sans (spec 6.2): sizes and weights as the app's, colours as its alphas. */
private object WidgetType {
    val title = TextStyle(color = ColorProvider(Color.White), fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val hero = TextStyle(color = ColorProvider(Color.White), fontSize = 20.sp, fontFamily = FontFamily("sans-serif-light"))
    val body = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp)
    val word = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.72f)), fontSize = 15.sp)
    val secondary = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.72f)), fontSize = 13.sp)
    val caption = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.50f)), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val captionShown = caption.copy(color = ColorProvider(Color.White))
}

/**
 * The widget at the size Glance lays out ([LocalSize]): the edge light behind, drawn at the cell's real size, then
 * the size's content. Any size the launcher gives lands on one of the three ([WidgetSizes.bucket]).
 */
@Composable
private fun RecoveryContent(data: RecoveryWidget.Shown) {
    val context = LocalContext.current
    val size = LocalSize.current
    val bucket = WidgetSizes.bucket(size.width.value, size.height.value)
    val metrics = context.resources.displayMetrics
    val (w, h) = WidgetSizes.drawSize(bucket, reportedSizes(LocalAppWidgetOptions.current, context))
    // Every size's bitmaps go to the launcher at once: scaled down together if they'd pass the limit (spec 6.2).
    val allPixels = WidgetSizes.all.sumOf { (it.width.value * it.height.value).toDouble() } * BITMAP_EXTRA * metrics.density * metrics.density
    val density = metrics.density * WidgetSizes.bitmapScale(allPixels.toLong(), WidgetSizes.bitmapBudget(metrics.widthPixels, metrics.heightPixels))
    val corner = WidgetSizes.corner(systemCorner(context))
    val model = data.model
    val open = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_METRIC, RECOVERY_ROUTE)
        .apply { model.date?.let { putExtra(MainActivity.EXTRA_METRIC_DAY, it.toString()) } }
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    Box(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(corner.dp)
            .background(ImageProvider(background(data.mood, w, h, corner, density)), ContentScale.FillBounds)
            .clickable(actionStartActivity(open)),
    ) {
        Column(GlanceModifier.fillMaxSize().padding(WidgetSizes.PADDING_DP.dp)) {
            when {
                model.score == null -> Waiting(model)
                bucket == WidgetSizes.Small -> SmallContent(model, density)
                bucket == WidgetSizes.Wide -> WideContent(model, density)
                else -> LargeContent(model, density, size.width.value - 2 * WidgetSizes.PADDING_DP, context.resources.configuration.fontScale)
            }
        }
    }
}

/** Small: the title, then the score and its word at the foot. */
@Composable
private fun ColumnScope.SmallContent(m: RecoveryWidgetModel, density: Float) {
    TitleRow(m.title, null)
    Spacer(GlanceModifier.defaultWeight())
    Score(m, DotSize.Edge, density)
    Spacer(GlanceModifier.height(6.dp))
    m.word?.let { Text(it, style = WidgetType.word, maxLines = 1) }
}

/** Wide: the title row with "This morning", the score and word, the three figures. */
@Composable
private fun ColumnScope.WideContent(m: RecoveryWidgetModel, density: Float) {
    TitleRow(m.title, m.note)
    Spacer(GlanceModifier.defaultWeight())
    ScoreRow(m, density)
    Spacer(GlanceModifier.defaultWeight())
    Figures(m.figures)
}

/** Large: the Wide plus the sentence and the last seven days beside the score. */
@Composable
private fun ColumnScope.LargeContent(m: RecoveryWidgetModel, density: Float, contentWidth: Float, fontScale: Float) {
    TitleRow(m.title, m.note)
    Spacer(GlanceModifier.height(8.dp))
    m.sentence?.let { Text(it, style = WidgetType.hero, maxLines = 3) }
    Spacer(GlanceModifier.defaultWeight())
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        ScoreRow(m, density)
        if (m.week.isNotEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            val dots = dotLayout(m.score.orEmpty(), DotSize.Second, 1f).width
            Week(m.week, RecoveryLayout.weekColumn(contentWidth, dots, m.word, fontScale), density)
        }
    }
    Spacer(GlanceModifier.defaultWeight())
    Figures(m.figures)
}

/** Before a score: the title, then why there's none in Secondary. */
@Composable
private fun Waiting(m: RecoveryWidgetModel) {
    TitleRow(m.title, null)
    Spacer(GlanceModifier.height(6.dp))
    m.waiting?.let { Text(it, style = WidgetType.secondary, maxLines = 4) }
}

/**
 * The top row: the title, with its note on the right. Step 6's strap status line takes this row's place when the
 * strap needs something ([strapStatus]).
 */
@Composable
private fun TitleRow(title: String, note: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = WidgetType.title, maxLines = 1)
        if (note != null) {
            Spacer(GlanceModifier.defaultWeight())
            Text(note, style = WidgetType.secondary, maxLines = 1)
        }
    }
}

/** The score in dots, read out as "78, Ready". */
@Composable
private fun Score(m: RecoveryWidgetModel, dot: DotSize, density: Float) {
    val text = m.score ?: return
    val layout = dotLayout(text, dot, 1f)
    Image(
        ImageProvider(renderDots(text, dot, density)),
        contentDescription = m.scoreDescription,
        modifier = GlanceModifier.size(layout.width.dp, layout.height.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/** The score with its word beside it. */
@Composable
private fun ScoreRow(m: RecoveryWidgetModel, density: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Score(m, DotSize.Second, density)
        m.word?.let {
            Spacer(GlanceModifier.width(RecoveryLayout.WORD_GAP_DP.dp))
            Text(it, style = WidgetType.word, maxLines = 1)
        }
    }
}

/** Resting HR, HRV and Sleep in a row, each a Caption over a Body value. */
@Composable
private fun Figures(figures: List<RecoveryFigure>) {
    Row(GlanceModifier.fillMaxWidth()) {
        figures.forEachIndexed { i, f ->
            if (i > 0) Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(f.label, style = WidgetType.caption, maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
                Text(f.value, style = WidgetType.body, maxLines = 1)
            }
        }
    }
}

/** The last seven days as solid bars, the day shown in Recovery's ink, each day's letter under its bar. */
@Composable
private fun Week(week: List<RecoveryWeekDay>, column: Float, density: Float) {
    val width = WeekBarsLook.width(week.size, column)
    val bars = weekBars(RecoveryLayout.barValues(week), density, column)
    val bitmap = renderToBitmap(width * density, WeekBarsLook.HEIGHT_DP * density) { drawWeekBars(it, bars, week.indexOfFirst { d -> d.shown }, Ink) }
    Column {
        Image(
            ImageProvider(bitmap),
            contentDescription = "Recovery each day for the last seven days",
            modifier = GlanceModifier.size(width.dp, WeekBarsLook.HEIGHT_DP.dp),
            contentScale = ContentScale.FillBounds,
        )
        Spacer(GlanceModifier.height(4.dp))
        Row {
            week.forEachIndexed { i, d ->
                if (i > 0) Spacer(GlanceModifier.width(WeekBarsLook.GAP_DP.dp))
                Box(GlanceModifier.width(column.dp), contentAlignment = Alignment.Center) {
                    Text(
                        d.letter,
                        style = (if (d.shown) WidgetType.captionShown else WidgetType.caption).copy(textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** The route `open_metric` takes for the Recovery screen ([org.openstrap.app.ui.dashboard.Detail.fromRoute]). */
internal const val RECOVERY_ROUTE = "Recovery"

/** Room for the dots and the week beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25

/**
 * The edge card's light (left, about earlier, in Recovery's ink at [mood]) for a widget [w] by [h] dp, with
 * [corner] dp corners, at [density] px per dp: the same [edgePaint] Today's Recovery card draws.
 */
private fun background(mood: GlowMood, w: Float, h: Float, corner: Float, density: Float): Bitmap {
    val paint = edgePaint(edgeLight(EdgeStatus.Usual, EdgeKind.Earlier), Ink, null, mood, w * density, h * density, density)
    return renderEdge(paint.copy(radius = corner * density))
}

/** The launcher's widget corner, dp, on Android 12 and later; null before. */
private fun systemCorner(context: Context): Float? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    runCatching { context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / context.resources.displayMetrics.density }.getOrNull()
} else null

/**
 * The sizes the launcher says the widget has, dp, the one for the current orientation first: Android 12's list, or
 * the portrait (min width, max height) and landscape (max width, min height) pair before it.
 */
private fun reportedSizes(options: Bundle, context: Context): List<Pair<Float, Float>> {
    val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sizes.isNullOrEmpty()) {
            val all = sizes.map { it.width to it.height }
            return if (portrait) all.sortedByDescending { it.second / it.first } else all.sortedBy { it.second / it.first }
        }
    }
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
    val p = minW to maxH
    val l = maxW to minH
    return if (portrait) listOf(p, l) else listOf(l, p)
}
