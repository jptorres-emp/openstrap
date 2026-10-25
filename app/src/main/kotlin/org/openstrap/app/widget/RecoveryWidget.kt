package org.openstrap.app.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.openstrap.app.OpenStrapApp
import org.openstrap.app.data.DayWindow
import org.openstrap.app.ui.glow.DotSize
import org.openstrap.app.ui.glow.EdgeKind
import org.openstrap.app.ui.glow.EdgeStatus
import org.openstrap.app.ui.glow.GlowMood
import org.openstrap.app.ui.glow.draw.WeekBarsLook
import org.openstrap.app.ui.glow.draw.dotLayout
import org.openstrap.app.ui.glow.draw.drawWeekBars
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
class RecoveryWidget : LoadingWidget<RecoveryWidget.Shown>(WidgetKind.Recovery) {
    override suspend fun loadShown(context: Context): Shown = load(context)

    @Composable
    override fun Content(data: Shown) = RecoveryContent(data)

    /** What the widget shows and the light it's lit in. */
    data class Shown(val model: RecoveryWidgetModel, val mood: GlowMood)

    companion object {
        /**
         * Today's Recovery, worked out as the app does ([org.openstrap.app.data.HealthRepository.recovery]), and
         * yesterday's while this morning's isn't in; the mood from the moment now ([widgetMood]).
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
                Log.w(WIDGET_TAG, "widgets: Recovery for $date not worked out: $e")
                null
            }
            val today = now.atZone(zone).toLocalDate()
            val result = day(today)
            val yesterday = if (result?.second?.shown?.value == null) day(today.minusDays(1))?.second else null
            val model = RecoveryWidgetModel.from(result?.second, yesterday)
            return Shown(model, widgetMood(container, result?.first?.usualBedtime, now, zone))
        }
    }
}

/** The Recovery widget's receiver. The first one placed stops Moment leading with Recovery (spec 3.1). */
class RecoveryWidgetReceiver : PlacedWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecoveryWidget()
}

/* ------------------------------------------------------------------------------------------------ */
/* Layout                                                                                            */
/* ------------------------------------------------------------------------------------------------ */

/** Recovery's ink. */
private val Ink = MetricColors.readiness

/**
 * The widget at the size Glance lays out: the edge light behind, drawn at the cell's real size, then the size's
 * content. Any size the launcher gives lands on one of the three ([WidgetSizes.bucket]).
 */
@Composable
private fun RecoveryContent(data: RecoveryWidget.Shown) {
    val model = data.model
    val open = openMetric(LocalContext.current, RECOVERY_ROUTE, model.date)
    EdgeWidget(edgeLight(EdgeStatus.Usual, EdgeKind.Earlier), Ink, data.mood, open, BITMAP_EXTRA) { frame ->
        when {
            model.score == null -> WaitingContent(model.title, model.waiting)
            frame.bucket == WidgetSizes.Small -> SmallContent(model, frame.density)
            frame.bucket == WidgetSizes.Wide -> WideContent(model, frame.density)
            else -> LargeContent(model, frame.density, frame.contentWidth, frame.fontScale)
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

/** The score in dots, read out as "78, Ready". */
@Composable
private fun Score(m: RecoveryWidgetModel, dot: DotSize, density: Float) {
    val text = m.score ?: return
    DotNumber(text, dot.dot.value, density, m.scoreDescription)
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
    WeekChart(ImageProvider(bitmap), "Recovery each day for the last seven days", week.map { it.letter to it.shown }, column)
}

/* ------------------------------------------------------------------------------------------------ */
/* Bitmaps                                                                                           */
/* ------------------------------------------------------------------------------------------------ */

/** The route `open_metric` takes for the Recovery screen ([org.openstrap.app.ui.dashboard.Detail.fromRoute]). */
internal const val RECOVERY_ROUTE = "Recovery"

/** Room for the dots and the week beside the backgrounds, in the bitmap budget. */
private const val BITMAP_EXTRA = 1.25
