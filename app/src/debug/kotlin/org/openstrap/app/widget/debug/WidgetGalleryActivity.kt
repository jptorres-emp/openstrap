package org.openstrap.app.widget.debug

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.openstrap.app.ui.glow.GlowMood
import org.openstrap.app.device.WidgetUpdater
import org.openstrap.app.widget.WidgetKind
import org.openstrap.app.widget.WidgetRefresh
import org.openstrap.app.widget.WidgetSizes

/**
 * Debug builds only: a real widget hosted at exactly its three sizes (Small, Wide, Large) on black, for checking on
 * the phone over adb. Binding needs the permission first:
 *
 *     adb shell appwidget grantbind --package org.openstrap.app --user 0
 *     adb shell am start -n org.openstrap.app/.widget.debug.WidgetGalleryActivity --es widget steps --es mood night
 *
 * `widget` is the kind to host (recovery, steps, sleep, start), Recovery without it. `mood` is day, evening, night or auto
 * (the moment's). Sleep is always night and ignores it. `--es pin sleep` asks the launcher to pin a widget of that
 * kind to the home screen. `--es cells oneui` hosts at One UI's real cells (2 × 2 about 180 × 220, 4 × 2 about
 * 353 × 220, 4 × 3 about 353 × 330) instead of the three minimums, to see what people see; `--es cells min` goes
 * back. The ids it binds are deleted when it closes or hosts another kind.
 */
class WidgetGalleryActivity : ComponentActivity() {
    private lateinit var host: AppWidgetHost
    private val bound = mutableListOf<Int>()
    private lateinit var column: LinearLayout
    private lateinit var hosted: LinearLayout
    private var kind = WidgetKind.Recovery
    private var sizes = SIZES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        applyMood(intent.getStringExtra(EXTRA_MOOD))
        applyCells(intent.getStringExtra(EXTRA_CELLS))
        kind = kindOf(intent.getStringExtra(EXTRA_WIDGET)) ?: WidgetKind.Recovery
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(48))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(column)
        })
        column.addView(controls())
        hosted = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(hosted)
        bindAll()
        kindOf(intent.getStringExtra(EXTRA_PIN))?.let(::requestPin)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = kindOf(intent.getStringExtra(EXTRA_WIDGET))
        if (intent.hasExtra(EXTRA_MOOD)) applyMood(intent.getStringExtra(EXTRA_MOOD))
        val cellsChanged = intent.hasExtra(EXTRA_CELLS) && applyCells(intent.getStringExtra(EXTRA_CELLS))
        when {
            next != null && next != kind -> show(next)
            cellsChanged -> show(kind)
            intent.hasExtra(EXTRA_MOOD) -> refresh()
        }
        kindOf(intent.getStringExtra(EXTRA_PIN))?.let(::requestPin)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        unbindAll()
        // Placed widgets go back to the moment's mood.
        if (WidgetRefresh.debugMood != null) {
            WidgetRefresh.debugMood = null
            WidgetUpdater.changedAll(applicationContext)
        }
        super.onDestroy()
    }

    /** The kind named [name] ("steps", "recovery", "sleep", "start") that has a widget yet; null for none. */
    private fun kindOf(name: String?): WidgetKind? =
        WidgetKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.takeIf { WidgetUpdater.registration(it) != null }

    /** Hosts [next] in place of the kind shown. */
    private fun show(next: WidgetKind) {
        unbindAll()
        kind = next
        bindAll()
    }

    private fun unbindAll() {
        bound.forEach { host.deleteAppWidgetId(it) }
        bound.clear()
        hosted.removeAllViews()
    }

    /** Binds an id per size and hosts its view at that size exactly. */
    private fun bindAll() {
        val manager = AppWidgetManager.getInstance(this)
        val registration = WidgetUpdater.registration(kind) ?: run { note("No $kind widget yet."); return }
        val provider = ComponentName(this, registration.receiver)
        val info: AppWidgetProviderInfo? = manager.getInstalledProvidersForPackage(packageName, null).firstOrNull { it.provider == provider }
        if (info == null) { note("The $kind widget's provider isn't installed."); return }
        for ((name, size) in sizes) {
            val id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, provider)) {
                host.deleteAppWidgetId(id)
                note("Not allowed to bind. Run: adb shell appwidget grantbind --package $packageName --user 0")
                return
            }
            bound += id
            manager.updateAppWidgetOptions(id, sizeOptions(size))
            val view: AppWidgetHostView = host.createView(applicationContext, id, info).apply { setPadding(0, 0, 0, 0) }
            label("$kind $name ${size.width.value.toInt()} × ${size.height.value.toInt()}")
            hosted.addView(view, LinearLayout.LayoutParams(dp(size.width.value), dp(size.height.value)).apply { bottomMargin = dp(24) })
        }
        refresh()
    }

    /** The size bundle Glance reads to pick its layout: Android 12's size list, and the min and max before it. */
    private fun sizeOptions(size: DpSize): Bundle = Bundle().apply {
        val w = size.width.value.toInt()
        val h = size.height.value.toInt()
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w.toFloat(), h.toFloat())))
        }
    }

    /** Loads the widget again and renders every bound id. */
    private fun refresh() {
        val ids = bound.toList()
        val shown = kind
        val widget = WidgetUpdater.registration(shown)?.widget ?: return
        lifecycleScope.launch {
            try {
                WidgetRefresh.bump(shown)
                val glance = GlanceAppWidgetManager(applicationContext)
                ids.forEach { widget().update(applicationContext, glance.getGlanceIdBy(it)) }
            } catch (e: Exception) {
                Log.w(TAG, "widget gallery: update failed: $e")
            }
        }
    }

    /** "oneui" for One UI's cells, anything else for the minimums; true when that changed what's hosted. */
    private fun applyCells(name: String?): Boolean {
        val next = if (name.equals("oneui", ignoreCase = true)) ONE_UI_SIZES else SIZES
        val changed = next != sizes
        sizes = next
        return changed
    }

    private fun applyMood(name: String?) {
        WidgetRefresh.debugMood = when (name?.lowercase()) {
            null, "", "auto" -> null
            else -> GlowMood.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    /** Asks the launcher to pin a widget of [pin] to the home screen. */
    private fun requestPin(pin: WidgetKind) {
        val manager = AppWidgetManager.getInstance(this)
        val registration = WidgetUpdater.registration(pin) ?: return
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "This launcher can't pin widgets", Toast.LENGTH_SHORT).show()
            return
        }
        manager.requestPinAppWidget(ComponentName(this, registration.receiver), null, null)
    }

    /** A button per kind, mood buttons and Pin, for checking by hand. */
    private fun controls() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(this@WidgetGalleryActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            WidgetKind.entries.filter { WidgetUpdater.registration(it) != null }.forEach { k ->
                addView(Button(this@WidgetGalleryActivity).apply {
                    text = k.name
                    setOnClickListener { if (k != kind) show(k) }
                })
            }
        })
        addView(moodRow())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
    }

    private fun moodRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf("Auto" to "auto", "Day" to "day", "Evening" to "evening", "Night" to "night").forEach { (text, mood) ->
            addView(Button(this@WidgetGalleryActivity).apply {
                this.text = text
                setOnClickListener { applyMood(mood); refresh() }
            })
        }
        addView(Button(this@WidgetGalleryActivity).apply {
            text = "Pin"
            setOnClickListener { requestPin(kind) }
        })
    }

    private fun label(text: String) = hosted.addView(TextView(this).apply {
        this.text = text
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, 0, 0, dp(8))
    })

    private fun note(text: String) {
        Log.w(TAG, "widget gallery: $text")
        label(text)
    }

    /** Rounded up, so a hosted view is never a fraction of a dp short of its size and laid out as a smaller one. */
    private fun dp(v: Float): Int = kotlin.math.ceil(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)).toInt()
    private fun dp(v: Int): Int = dp(v.toFloat())

    private companion object {
        const val TAG = "OpenStrap"
        const val HOST_ID = 0x5753
        const val EXTRA_MOOD = "mood"
        const val EXTRA_PIN = "pin"
        const val EXTRA_WIDGET = "widget"
        const val EXTRA_CELLS = "cells"
        val SIZES = listOf("Small" to WidgetSizes.Small, "Wide" to WidgetSizes.Wide, "Large" to WidgetSizes.Large)

        /** One UI's cells on the phone, roughly (spec 2.1): what the three layouts stretch to there. */
        val ONE_UI_SIZES = listOf(
            "Small (One UI 2 × 2)" to DpSize(180.dp, 220.dp),
            "Wide (One UI 4 × 2)" to DpSize(353.dp, 220.dp),
            "Large (One UI 4 × 3)" to DpSize(353.dp, 330.dp),
        )
    }
}
