package org.openstrap.app.ui.glow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import org.openstrap.app.data.Route

/** The marker number's size, and a smaller one so three digits still sit inside the ring. */
private const val KM_TEXT_DP = 11f
private const val KM_TEXT_3_DIGITS_DP = 8f

/** Each marker a Point feature whose `icon` names its image, `km-<n>`, added to the style by [WorkoutMap]. */
internal fun kmMarkerFeatures(markers: List<Pair<Int, Route.Point>>): String =
    markers.joinToString(",", """{"type":"FeatureCollection","features":[""", "]}") { (km, p) ->
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${coord(p)}]},"properties":{"icon":"km-$km"}}"""
    }

/**
 * Kilometre [km]'s marker: a 22 dp dark circle, as the start dot, ringed in white, its number in bold white. A bitmap,
 * not a map text label, since a label needs a font from the tile host and Route only makes no requests.
 */
internal fun kmMarkerBitmap(km: Int, density: Float): Bitmap {
    val size = (22 * density).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val ring = 2 * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0xFF121316.toInt()
    canvas.drawCircle(c, c, c - ring / 2, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = ring
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, c - ring / 2, paint)
    paint.style = Paint.Style.FILL
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val label = km.toString()
    paint.textSize = (if (label.length > 2) KM_TEXT_3_DIGITS_DP else KM_TEXT_DP) * density
    canvas.drawText(label, c, c - (paint.descent() + paint.ascent()) / 2, paint)
    return bitmap
}

/** [points] as one LineString feature, for a picked kilometre's white casing, or none under two points. */
internal fun highlightGeoJson(points: List<Route.Point>): String = if (points.size < 2) {
    """{"type":"FeatureCollection","features":[]}"""
} else {
    """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""" +
        points.joinToString(",") { "[${coord(it)}]" } + "]}}]}"
}
