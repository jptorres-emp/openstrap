package org.openstrap.app.screenshots

import android.app.Application
import android.content.Context
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.glance.LocalGlanceId
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import org.openstrap.app.widget.LoadingWidget
import org.openstrap.app.widget.LocalStatusLine
import androidx.glance.appwidget.composeForPreview
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.widget.MomentWidget
import org.openstrap.app.widget.RecoveryWidget
import org.openstrap.app.widget.SleepWidget
import org.openstrap.app.widget.StartWidget
import org.openstrap.app.widget.StepsWidget
import org.openstrap.app.widget.WidgetSamples
import org.openstrap.app.widget.WidgetSizes
import org.openstrap.app.ui.glow.draw.GlowMood
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pins the five home screen widgets (Recovery, Steps, Sleep, Start, Moment) at their three sizes (Small, Wide, Large,
 * [WidgetSizes]) and the three font scales, as the widget picker's own preview draws them: each widget's sample day
 * (WidgetSamples) composed by Glance into RemoteViews ([composeForPreview]) and inflated in a host at exactly the
 * size's cell. Pictures are named `<widget>_<size>_<scale>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetShots {
    @Test fun recovery() = widget("recovery", RecoveryWidget(), RecoveryWidget.Shown(WidgetSamples.recovery(DAY, LOCALE), GlowMood.Day))

    @Test fun steps() = widget("steps", StepsWidget(), StepsWidget.Shown(WidgetSamples.steps(DAY, LOCALE), GlowMood.Day))

    @Test fun sleep() = widget("sleep", SleepWidget(), WidgetSamples.sleep(DAY, ZONE))

    @Test fun start() = widget("start", StartWidget(), StartWidget.Shown(WidgetSamples.start(NOW, ZONE, LOCALE), GlowMood.Day))

    @Test fun moment() = widget("moment", MomentWidget(), MomentWidget.Shown(WidgetSamples.moment(DAY, ZONE), null))

    private fun widget(name: String, inner: LoadingWidget<*>, sample: Any) {
        val widget = SamplePreview(inner, sample)
        val context = ApplicationProvider.getApplicationContext<Application>()
        SIZES.forEach { (sizeName, size) ->
            ScreenshotTest.FONT_SCALES.forEach { scale ->
                RuntimeEnvironment.setFontScale(scale)
                val info = AppWidgetProviderInfo().apply {
                    minWidth = size.width.value.toInt()
                    minHeight = size.height.value.toInt()
                }
                val views = runBlocking { widget.composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, info) }
                val dm = context.resources.displayMetrics
                val w = (size.width.value * dm.density).toInt()
                val h = (size.height.value * dm.density).toInt()
                val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
                // The size picks which of the widget's three layouts is inflated, as the launcher's host does.
                // (The public method is hidden from the compile classpath, so it is called through reflection.)
                val apply = RemoteViews::class.java.getMethod(
                    "apply", Context::class.java, ViewGroup::class.java, Class.forName("android.widget.RemoteViews\$InteractionHandler"), SizeF::class.java,
                )
                val view = apply.invoke(views, context, root, null, SizeF(size.width.value, size.height.value)) as View
                root.addView(view, FrameLayout.LayoutParams(w, h))
                root.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                // Drawn to a bitmap by hand: a view's own capture wants an activity to host it.
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                bitmap.captureRoboImage(File(ScreenshotTest.OUTPUT_DIR, "widgets/${name}_${sizeName}_$scale.png").path, ScreenshotTest.SMOOTHED)
            }
        }
    }

    /**
     * The widget's own layout with [sample], as [LoadingWidget.providePreview] draws its own, but inside a Glance id:
     * the layouts read one for the launcher's cell sizes, which the picker's preview (no widget, so no id) doesn't
     * give. A widget's `Content` is protected, so it is called through reflection.
     */
    private class SamplePreview(private val inner: LoadingWidget<*>, private val sample: Any) : GlanceAppWidget() {
        override val sizeMode = inner.sizeMode
        override val previewSizeMode = inner.previewSizeMode

        override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) = Unit

        override suspend fun providePreview(context: Context, widgetCategory: Int) {
            val content = inner.javaClass.declaredMethods.first { it.name == "Content" && it.parameterCount == 3 }
                .apply { isAccessible = true }
            provideContent {
                CompositionLocalProvider(LocalGlanceId provides AppWidgetId(1), LocalStatusLine provides null) {
                    Reflected(content, sample)
                }
            }
        }

        // Lint sees only currentComposer here, but the reflected Content draws the whole widget, so this composable
        // emits and must not be @ReadOnlyComposable.
        @Suppress("ComposeReadOnlyComposable")
        @Composable
        private fun Reflected(content: java.lang.reflect.Method, sample: Any?) {
            content.invoke(inner, sample, currentComposer, 0)
        }
    }

    private companion object {
        /** The sample day is fixed (the widgets' own preview uses today's date, which would move the week's pictures). */
        val DAY: LocalDate = LocalDate.of(2026, 11, 13)
        val ZONE: ZoneId = ZoneOffset.UTC
        val NOW: Instant = DAY.atTime(13, 0).toInstant(ZoneOffset.UTC)
        val LOCALE: Locale = Locale.US
        val SIZES = listOf("small" to WidgetSizes.Small, "wide" to WidgetSizes.Wide, "large" to WidgetSizes.Large)
    }
}
