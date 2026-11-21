package org.openstrap.app.ui.glow.draw

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.openstrap.app.ui.glow.CardTuning
import org.openstrap.app.ui.glow.PercentSquircle
import org.openstrap.app.ui.glow.pictures.drawHourDots
import org.openstrap.app.ui.glow.pictures.drawWeekRingDots

/*
 * The app's pictures the home screen widgets draw too (board HaloWidgets), here where the widget layer may reach them.
 */

/**
 * The app's card squircle ([CardTuning.shape]) [w] by [h] px at [density] px per dp: its corner is the cards' share of
 * the shorter side, never past their cap, so a widget's corners match the cards on Today.
 */
fun cardSquirclePath(w: Float, h: Float, density: Float): android.graphics.Path {
    val shape = PercentSquircle(CardTuning.CORNER, CardTuning.SMOOTHING, CardTuning.CORNER_CAP)
    return when (val outline = shape.createOutline(Size(w, h), LayoutDirection.Ltr, Density(density))) {
        is Outline.Generic -> outline.path.asAndroidPath()
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }.asAndroidPath()
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }.asAndroidPath()
    }
}

/** Today's Steps card's hours as dots ([drawHourDots]) across the draw area in [ink]. */
fun DrawScope.drawStepsHourDots(hourly: List<Float>, ink: Color) = drawHourDots(hourly, null, ink)

/** Today's Your week rings ([drawWeekRingDots]) across the draw area, [stroke] px, [today]'s ring in [ink]. */
fun DrawScope.drawWeekRingsDotted(shares: List<Float?>, ink: Color, stroke: Float, today: Int) =
    drawWeekRingDots(shares, ink, stroke, today)
