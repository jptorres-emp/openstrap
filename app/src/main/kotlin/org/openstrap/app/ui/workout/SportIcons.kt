package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.AppIcons

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
        11 -> AppIcons.Mountains
        in RUN -> Run
        15, 224 -> AppIcons.PersonSimpleHike
        215 -> AppIcons.Dog
        225 -> AppIcons.Backpack
        in WALK -> Walk
        108, 57, 58 -> AppIcons.Stairs
        9 -> AppIcons.Steps
        4 -> AppIcons.PersonSimpleBike
        8, 143 -> AppIcons.Bicycle
        6 -> AppIcons.PersonSimpleSwim
        7 -> AppIcons.Waves
        23 -> AppIcons.Boat
        49 -> AppIcons.Timer
        130, 51, 109 -> AppIcons.Lightning
        222 -> AppIcons.FlagCheckered
        50 -> AppIcons.Barbell
        in STRENGTH -> Strength
        60 -> AppIcons.FlowerLotus
        61 -> AppIcons.PersonSimpleTaiChi
        53, 55 -> AppIcons.PersonArmsSpread
        76 -> AppIcons.MusicNotes
        17, 205 -> AppIcons.TennisBall
        89, 204 -> AppIcons.PingPong
        92 -> AppIcons.PersonSimpleThrow
        18 -> AppIcons.SoccerBall
        85 -> AppIcons.Basketball
        88 -> AppIcons.Volleyball
        83 -> AppIcons.Golf
        97 -> AppIcons.BoxingGlove
        12 -> AppIcons.PersonSimpleSki
        70, 10 -> AppIcons.Mountains
        else -> AppIcons.Heartbeat
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
