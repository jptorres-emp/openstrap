package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.PhosphorIcons

/** Line icons for the sports people start most, on a 24-unit grid with a 2-unit stroke; Icon() tints them. */
internal object SportIcons {
    val Run: ImageVector by lazy { stroke("Run", circle(14f, 4f, 2f), "m6 21 3-6 3 2v5", "m8 11 3-4 4 3 3 1", "M11 7 9 12l3 2") }
    val Walk: ImageVector by lazy { stroke("Walk", circle(13f, 4f, 2f), "M10 21l2-6-2-3 1-4 3 3h3", "m8 12-2 3") }
    val Strength: ImageVector by lazy { stroke("Strength", "M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12") }

    private val RUN = setOf(1, 2, 11)
    private val WALK = setOf(3, 15, 40, 215, 224, 225)
    private val STRENGTH = setOf(24, 50, 52, 222, 226)

    fun of(sportType: Int): ImageVector = when (sportType) {
        in RUN -> Run
        in WALK -> Walk
        in STRENGTH -> Strength
        else -> PhosphorIcons.Heartbeat
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder("Sport.$name", 24.dp, 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            for (d in paths) addPath(
                PathParser().parsePathString(d).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
