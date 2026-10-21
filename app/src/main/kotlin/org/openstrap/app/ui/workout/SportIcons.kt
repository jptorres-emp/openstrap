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
    /** A flag, for marking a lap. */
    val Lap: ImageVector by lazy { stroke("Lap", "M5 21V4", "M5 4h11l-2 4 2 4H5") }

    private val RUN = setOf(1, 2, 11)
    private val WALK = setOf(3, 15, 40, 215, 224, 225)
    private val STRENGTH = setOf(24, 50, 52, 222, 226)

    fun of(sportType: Int): ImageVector = when (sportType) {
        11 -> PhosphorIcons.Mountains
        in RUN -> Run
        15, 224 -> PhosphorIcons.PersonSimpleHike
        215 -> PhosphorIcons.Dog
        225 -> PhosphorIcons.Backpack
        in WALK -> Walk
        108, 57, 58 -> PhosphorIcons.Stairs
        9 -> PhosphorIcons.Steps
        4 -> PhosphorIcons.PersonSimpleBike
        8, 143 -> PhosphorIcons.Bicycle
        6 -> PhosphorIcons.PersonSimpleSwim
        7 -> PhosphorIcons.Waves
        23 -> PhosphorIcons.Boat
        49 -> PhosphorIcons.Timer
        130, 51, 109 -> PhosphorIcons.Lightning
        222 -> PhosphorIcons.FlagCheckered
        50 -> PhosphorIcons.Barbell
        in STRENGTH -> Strength
        60 -> PhosphorIcons.FlowerLotus
        61 -> PhosphorIcons.PersonSimpleTaiChi
        53, 55 -> PhosphorIcons.PersonArmsSpread
        76 -> PhosphorIcons.MusicNotes
        17, 205 -> PhosphorIcons.TennisBall
        89, 204 -> PhosphorIcons.PingPong
        92 -> PhosphorIcons.PersonSimpleThrow
        18 -> PhosphorIcons.SoccerBall
        85 -> PhosphorIcons.Basketball
        88 -> PhosphorIcons.Volleyball
        83 -> PhosphorIcons.Golf
        97 -> PhosphorIcons.BoxingGlove
        12 -> PhosphorIcons.PersonSimpleSki
        70, 10 -> PhosphorIcons.Mountains
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
