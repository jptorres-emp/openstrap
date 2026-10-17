package org.openstrap.app.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/** Whether the glow design is on; screens shared with the classic design check it to pick their look. */
val LocalGlowDesign = staticCompositionLocalOf { false }

/** The nav bar's glass: near-black, see-through, with a lit top edge. Popups use it over a blurred screen. */
val GlassTint = Color(0xFF0B0B0D)
val GlassEdge = BorderStroke(
    1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.2f), 0.5f to Color.White.copy(alpha = 0.05f), 1f to Color.Black.copy(alpha = 0.4f)),
)

/** How see-through a glass popup is: clearer when the screen behind is blurred, nearly solid when it can't be. */
private fun glassAlpha(blurred: Boolean) = if (blurred) 0.62f else 0.94f

/**
 * Blurs everything behind the popup this is placed in (Android 12+, when the phone allows cross-window blur),
 * and dims it a little. Returns whether the blur is on, so the popup can be more or less see-through.
 */
@Composable
fun blurBehindPopup(radius: Int = 60): Boolean {
    val view = LocalView.current
    val window = generateSequence(view as View?) { it.parent as? View }
        .firstNotNullOfOrNull { it as? DialogWindowProvider }?.window
        ?: (view.parent as? DialogWindowProvider)?.window
    val blurred = window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window.windowManager.isCrossWindowBlurEnabled
    DisposableEffect(window, blurred) {
        if (window != null) {
            if (blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = radius }
            }
            window.setDimAmount(if (blurred) 0.25f else 0.6f)
        }
        onDispose { }
    }
    return blurred
}

/** The glass colour for a popup's surface. */
@Composable
fun glassColor(blurred: Boolean) = GlassTint.copy(alpha = glassAlpha(blurred))

/**
 * AlertDialog with the same slots. With the glow design it's a dark glass panel over the blurred screen, like the
 * nav bar; otherwise it's the standard Material dialog.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    if (!LocalGlowDesign.current) {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, icon = icon, title = title, text = text)
        return
    }
    val panel: @Composable (Color) -> Unit = { color ->
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = color,
            border = GlassEdge,
            contentColor = Color.White,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) { icon() }
                    }
                }
                if (title != null) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = if (icon != null) Alignment.Center else Alignment.CenterStart) { title() }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.8f)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Box(Modifier.weight(1f, fill = false)) { text() }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
    val host = LocalGlassHost.current
    if (host != null) GlassPortal(host, sheet = false, onDismiss = onDismissRequest) { panel(GlassTint.copy(alpha = 0.72f)) }
    else Dialog(onDismissRequest = onDismissRequest) { panel(glassColor(blurBehindPopup())) }
}

/**
 * With the glow design, the screen's cards go OLED black with a faint hairline instead of grey fills, matching
 * the glow tabs; otherwise the theme is left as it is.
 */
@Composable
fun OledSurfaces(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val c = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = c.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color(0xFF101012),
            surfaceContainerHighest = Color(0xFF1A1A1D),
            outline = Color.White.copy(alpha = 0.16f),
            outlineVariant = Color.White.copy(alpha = 0.12f),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/* ------------------------------------------- In-app overlay ----------------------------------------- */

/**
 * Popups drawn inside the app's own window, over a blur of the page, like the nav bar. A popup in its own window
 * can only blur what's behind it when the phone allows cross-window blur (many don't); drawn here, the blur
 * always works. Popups declared anywhere register here and the root draws them on top of everything.
 */
class GlassHost {
    internal val layers = androidx.compose.runtime.mutableStateListOf<GlassLayer>()
}

internal class GlassLayer(
    val sheet: Boolean,
    val onDismiss: androidx.compose.runtime.State<() -> Unit>,
    val content: androidx.compose.runtime.State<@Composable () -> Unit>,
)

/** The overlay host, when the glow design is on; null otherwise. */
val LocalGlassHost = staticCompositionLocalOf<GlassHost?> { null }

/** Shows [content] in the overlay for as long as this is in the composition. */
@Composable
private fun GlassPortal(host: GlassHost, sheet: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val d = androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val c = androidx.compose.runtime.rememberUpdatedState(content)
    DisposableEffect(host) {
        val layer = GlassLayer(sheet, d, c)
        host.layers += layer
        onDispose { host.layers -= layer }
    }
}

/** The page behind a popup: blurred and darkened, the nav bar's glass spread across the screen. */
private val BackdropBlur = dev.chrisbanes.haze.blur.HazeBlurStyle {
    blurRadius(28.dp)
    backgroundColor(Color.Black)
    colorEffects(listOf(dev.chrisbanes.haze.blur.HazeColorEffect.tint(Color.Black.copy(alpha = 0.45f))))
    fallbackColorEffect(dev.chrisbanes.haze.blur.HazeColorEffect.tint(Color.Black.copy(alpha = 0.8f)))
}

/** Draws the registered popups; place it last at the root, over everything, with the pages' [haze]. */
@Composable
fun GlassOverlay(host: GlassHost, haze: dev.chrisbanes.haze.HazeState) {
    host.layers.forEach { layer ->
        androidx.compose.runtime.key(layer) {
            androidx.activity.compose.BackHandler { layer.onDismiss.value() }
            val shown = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) { shown.animateTo(1f, androidx.compose.animation.core.tween(220)) }
            val tap = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = shown.value }
                    .hazeBlur(HazeInput.Sources(haze), BackdropBlur)
                    .clickable(tap, indication = null) { layer.onDismiss.value() },
            )
            Box(
                Modifier.fillMaxSize().imePadding().let { if (layer.sheet) it else it.systemBarsPadding().padding(24.dp) }
                    .graphicsLayer {
                        alpha = shown.value
                        translationY = (1f - shown.value) * (if (layer.sheet) 120.dp.toPx() else 16.dp.toPx())
                    },
                contentAlignment = if (layer.sheet) Alignment.BottomCenter else Alignment.Center,
            ) {
                // The panel takes its own taps, so tapping it doesn't dismiss.
                Box(Modifier.clickable(androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
                    layer.content.value()
                }
            }
        }
    }
}

/** The glass panel a popup sits on: see-through dark over the blurred page, with a lit top edge. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(28.dp), content: @Composable () -> Unit) {
    Surface(shape = shape, color = GlassTint.copy(alpha = 0.72f), border = GlassEdge, contentColor = Color.White, modifier = modifier, content = content)
}

/** A bottom sheet as glass in the overlay with the glow design, else Material's sheet (supplied by the caller). */
@Composable
fun GlassSheet(onDismiss: () -> Unit, fallback: @Composable (@Composable () -> Unit) -> Unit, content: @Composable () -> Unit) {
    val host = LocalGlassHost.current
    if (host == null) { fallback(content); return }
    GlassPortal(host, sheet = true, onDismiss = onDismiss) {
        GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                }
                content()
            }
        }
    }
}
