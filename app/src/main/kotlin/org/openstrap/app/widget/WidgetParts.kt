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
import androidx.compose.ui.unit.DpSize
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
import kotlinx.coroutines.launch
import org.openstrap.app.AppContainer
import org.openstrap.app.MainActivity
import org.openstrap.app.device.WidgetUpdater
import org.openstrap.app.ui.dashboard.momentMood
import org.openstrap.app.ui.glow.EdgeLight
import org.openstrap.app.ui.glow.GlowMood
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.dotLayout
import org.openstrap.app.ui.glow.draw.edgePaint
import org.openstrap.app.ui.glow.draw.renderDots
import org.openstrap.app.ui.glow.draw.renderEdge
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/*
 * What every widget shares (widgets spec 6): loading and reloading in provideGlance, the receiver's placed callbacks,
 * the edge card frame (light, corners, tap), the type roles, the title row and the dotted number. Each widget keeps
 * only its own content.
 */

/**
 * A widget that loads its [T] once per Glance session, outside the content Glance composes once per size, and again
 * each time its [kind]'s version moves on ([WidgetRefresh]). Every widget has the three sizes ([WidgetSizes]).
 */
abstract class LoadingWidget<T>(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.all)

    /** What the widget shows now. */
    protected abstract suspend fun loadShown(context: Context): T

    /** The widget at the size Glance lays out. */
    @Composable
    protected abstract fun Content(data: T)

    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        val shown = MutableStateFlow(loadShown(context))
        launch { WidgetRefresh.version(kind).drop(1).collectLatest { shown.value = loadShown(context) } }
        provideContent {
            val data by shown.collectAsState()
            Content(data)
        }
    }
}

/**
 * A widget's receiver, which the launcher knows it by. Placing the first or removing the last of any widget turns
 * midnight's alarm on or off, and Moment follows what's placed (spec 3.1, the duplicate rule).
 */
abstract class PlacedWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        placedChanged(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        placedChanged(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        placedChanged(context)
    }

    private fun placedChanged(context: Context) {
        WidgetUpdater.scheduleMidnight(context)
        WidgetUpdater.changed(context, setOf(WidgetKind.Moment))
    }
}

/**
 * The mood to light a widget in (spec 2.2): the moment's now, from the same inputs Today reads ([widgetMomentInputs])
 * with [usualBedtime] from Recovery, or the debug gallery's. Day when the moment can't be worked out.
 */
internal suspend fun widgetMood(container: AppContainer, usualBedtime: LocalTime?, now: Instant, zone: ZoneId): GlowMood =
    WidgetRefresh.moodOverride ?: try {
        momentMood(widgetMomentInputs(container, usualBedtime, now, zone).resolve(now, zone))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(WIDGET_TAG, "widgets: moment for the mood not worked out: $e")
        GlowMood.Day
    }

/** Opens the detail at [route] (`open_metric`, [org.openstrap.app.ui.dashboard.Detail.fromRoute]) for [day]. */
internal fun openMetric(context: Context, route: String, day: LocalDate?): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_METRIC, route)
        .apply { day?.let { putExtra(MainActivity.EXTRA_METRIC_DAY, it.toString()) } }
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

internal const val WIDGET_TAG = "OpenStrap"

/* ------------------------------------------------------------------------------------------------ */
/* The frame                                                                                         */
/* ------------------------------------------------------------------------------------------------ */

/**
 * What a widget's content is laid out with: the [bucket] Glance picked (the smallest cell the layout must fit, so
 * every fit sum uses it), the cell's real size [drawWidth] by [drawHeight] dp that bitmaps are drawn at so they fill
 * it without stretching, [density] for bitmaps (inside the budget) and the system [fontScale].
 */
class WidgetFrame(
    val bucket: DpSize,
    val drawWidth: Float,
    val drawHeight: Float,
    val density: Float,
    val fontScale: Float,
    /** The real cell to fit the content to ([WidgetSizes.fitSize]), dp. */
    val fitWidth: Float = bucket.width.value,
    val fitHeight: Float = bucket.height.value,
) {
    /** The content's width and height in the real cell, inside the padding, dp: what decides what fits. */
    val contentWidth: Float get() = fitWidth - 2 * WidgetSizes.PADDING_DP
    val contentHeight: Float get() = fitHeight - 2 * WidgetSizes.PADDING_DP

    /** The content's real width, dp, for bitmaps that span it. */
    val drawContentWidth: Float get() = drawWidth - 2 * WidgetSizes.PADDING_DP
}

/**
 * An edge card widget at the size Glance lays out ([LocalSize]): [light] in [ink] at [mood] behind, drawn at the
 * cell's real size, the whole widget opening [open], the content inside the padding. [bitmapExtra] is the room the
 * content's bitmaps take beside the backgrounds in the budget (spec 6.2): every size's bitmaps go to the launcher at
 * once and are scaled down together if they'd pass the limit.
 */
@Composable
internal fun EdgeWidget(light: EdgeLight, ink: Color, mood: GlowMood, open: Intent, bitmapExtra: Double, content: @Composable ColumnScope.(WidgetFrame) -> Unit) {
    val context = LocalContext.current
    val size = LocalSize.current
    val bucket = WidgetSizes.bucket(size.width.value, size.height.value)
    val metrics = context.resources.displayMetrics
    val reported = reportedSizes(LocalAppWidgetOptions.current, context)
    val (w, h) = WidgetSizes.drawSize(bucket, reported)
    val (fitW, fitH) = WidgetSizes.fitSize(bucket, reported)
    val allPixels = WidgetSizes.all.sumOf { (it.width.value * it.height.value).toDouble() } * bitmapExtra * metrics.density * metrics.density
    val density = metrics.density * WidgetSizes.bitmapScale(allPixels.toLong(), WidgetSizes.bitmapBudget(metrics.widthPixels, metrics.heightPixels))
    val corner = WidgetSizes.corner(systemCorner(context))
    val frame = WidgetFrame(bucket, w, h, density, context.resources.configuration.fontScale, fitW, fitH)
    Box(
        GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(corner.dp)
            .background(ImageProvider(edgeBackground(light, ink, mood, w, h, corner, density)), ContentScale.FillBounds)
            .clickable(actionStartActivity(open)),
    ) {
        Column(GlanceModifier.fillMaxSize().padding(WidgetSizes.PADDING_DP.dp)) { content(frame) }
    }
}

/**
 * An edge card's light ([light] in [ink] at [mood]) for a widget [w] by [h] dp, with [corner] dp corners, at
 * [density] px per dp: the same [edgePaint] the app's edge cards draw.
 */
internal fun edgeBackground(light: EdgeLight, ink: Color, mood: GlowMood, w: Float, h: Float, corner: Float, density: Float): Bitmap {
    val paint = edgePaint(light, ink, null, mood, w * density, h * density, density)
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

/* ------------------------------------------------------------------------------------------------ */
/* Type, title, number                                                                               */
/* ------------------------------------------------------------------------------------------------ */

/** The v2 type roles in the system sans (spec 6.2): sizes and weights as the app's, colours as its alphas. */
internal object WidgetType {
    val title = TextStyle(color = ColorProvider(Color.White), fontSize = WidgetText.TITLE_SP.sp, fontWeight = FontWeight.Medium)
    val hero = TextStyle(color = ColorProvider(Color.White), fontSize = 20.sp, fontFamily = FontFamily("sans-serif-light"))
    val body = TextStyle(color = ColorProvider(Color.White), fontSize = WidgetText.BODY_SP.sp)
    val word = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.72f)), fontSize = WidgetText.BODY_SP.sp)
    val secondary = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.72f)), fontSize = WidgetText.SECONDARY_SP.sp)
    val secondaryShown = secondary.copy(color = ColorProvider(Color.White))
    val caption = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.50f)), fontSize = WidgetText.CAPTION_SP.sp, fontWeight = FontWeight.Medium)
    val captionShown = caption.copy(color = ColorProvider(Color.White))
}

/**
 * The top row: the title, with its note on the right. Step 6's strap status line takes this row's place when the
 * strap needs something ([strapStatus]).
 */
@Composable
internal fun TitleRow(title: String, note: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = WidgetType.title, maxLines = 1)
        if (note != null) {
            Spacer(GlanceModifier.defaultWeight())
            Text(note, style = WidgetType.secondary, maxLines = 1)
        }
    }
}

/** [text] in white dots [dot] dp across, read out as [description]: the widget's one dotted number (spec 3). */
@Composable
internal fun DotNumber(text: String, dot: Float, density: Float, description: String?) {
    val layout = dotLayout(text, dot, 1f)
    Image(
        ImageProvider(renderDots(text, dot, density)),
        contentDescription = description,
        modifier = GlanceModifier.size(layout.width.dp, layout.height.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * A week's bars ([bars], drawn [WeekBarsLook.width] by [WeekBarsLook.HEIGHT_DP] dp for columns [column] dp wide),
 * read out as [description], with each day's letter under its column as a Caption, white where it's lit.
 */
@Composable
internal fun WeekChart(bars: ImageProvider, description: String, letters: List<Pair<String, Boolean>>, column: Float) {
    val width = WeekBarsLook.width(letters.size, column)
    Column {
        Image(
            bars,
            contentDescription = description,
            modifier = GlanceModifier.size(width.dp, WeekBarsLook.HEIGHT_DP.dp),
            contentScale = ContentScale.FillBounds,
        )
        Spacer(GlanceModifier.height(4.dp))
        // A letter a column, the gap as padding rather than a Spacer: Glance drops a row's children past ten.
        Row {
            letters.forEachIndexed { i, (letter, lit) ->
                val gap = if (i < letters.lastIndex) WeekBarsLook.GAP_DP else 0f
                Box(GlanceModifier.width((column + gap).dp).padding(end = gap.dp), contentAlignment = Alignment.Center) {
                    Text(letter, style = (if (lit) WidgetType.captionShown else WidgetType.caption).copy(textAlign = TextAlign.Center), maxLines = 1)
                }
            }
        }
    }
}
