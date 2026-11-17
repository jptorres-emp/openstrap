package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Greys
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * How a token is drawn (boards HaloRewardsSettingsRules A1, HaloGoalsBests, HaloBestMoment), on a frame [FRAME] units
 * across. Earned: the shape filled with a radial from the top, ink at 0.95 down to 0.55, a 1.2 unit rim at 0.3, the
 * picture in black. Not yet earned: a #121317 fill, a #24262C rim and the picture in #4A4E57. A badge is a medal (a
 * squircle tile with a ribbon notch at the foot), a best a rosette of 12 points, the weekly goal a ring of arcs.
 */

/** A token's outline: a badge's medal tile, or a best's rosette. */
internal enum class MedalShape {
    /** A badge: a tile with a ribbon notch at the foot. */
    Tile,

    /** A personal best: a 12 point scalloped circle. */
    Rosette,
}

/** Where a token's picture and label sit on the frame, in frame units: the picture's box and the label's band. */
internal class MedalLayout(val emblemAlone: Rect, val emblemOver: Rect, val labelAlone: Rect, val labelUnder: Rect)

internal val MedalShape.layout: MedalLayout
    get() = when (this) {
        // Boards: the picture is 36 across (24 scaled 1.5) at (32, 28) on a medal, (32, 32) on a rosette.
        MedalShape.Tile -> MedalLayout(
            emblemAlone = Rect(32f, 28f, 68f, 64f), emblemOver = Rect(38f, 16f, 62f, 40f),
            labelAlone = Rect(24f, 12f, 76f, 70f), labelUnder = Rect(24f, 42f, 76f, 70f),
        )
        MedalShape.Rosette -> MedalLayout(
            emblemAlone = Rect(32f, 32f, 68f, 68f), emblemOver = Rect(38f, 22f, 62f, 46f),
            labelAlone = Rect(26f, 24f, 74f, 76f), labelUnder = Rect(28f, 48f, 72f, 74f),
        )
    }

/** The token on the frame: its outline, filled and rimmed, earned or quiet. [lit] (0..1) is how far an earned token's fill has risen. */
internal fun DrawScope.drawToken(shape: MedalShape, ink: Color, locked: Boolean, lit: Float) {
    val face = faceOf(shape)
    if (locked) {
        drawPath(face, Greys.Raised)
        drawPath(face, LockedRim, style = Stroke(RIM_LINE))
        return
    }
    val k = lerp(RISE_FLOOR, 1f, lit.coerceIn(0f, 1f))
    val box = face.getBounds()
    drawPath(
        face,
        Brush.radialGradient(
            listOf(ink.copy(alpha = FILL_TOP * k), ink.copy(alpha = FILL_BOTTOM * k)),
            center = Offset(MID, box.top + box.height * FILL_CENTRE_Y),
            radius = box.height * FILL_RADIUS,
        ),
    )
    drawPath(face, ink.copy(alpha = RIM_ALPHA), style = Stroke(RIM_LINE))
}

/**
 * [e] in [box] (frame units), black on an earned token and #4A4E57 on a quiet one: its filled parts, then its lines, 2
 * units wide on the picture's own 24 unit grid, as the boards draw them.
 */
internal fun DrawScope.drawEmblemIn(e: Emblem, box: Rect, color: Color) {
    withTransform({
        translate(box.left, box.top)
        scale(box.width / EMBLEM_FRAME, box.width / EMBLEM_FRAME, pivot = Offset.Zero)
    }) {
        e.fill?.let { drawPath(it, color) }
        e.stroke?.let { drawPath(it, color, style = Stroke(EMBLEM_LINE, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

/**
 * The weekly goal's ring: [goal] solid arcs round the frame, the first [count] of them in [ink] and the rest quiet, each
 * arc 9 units thick with round ends, the gaps about a unit wide (boards HaloGoalMoment, HaloGoalsBests).
 */
internal fun DrawScope.drawGoalRing(count: Int, goal: Int, ink: Color) {
    val n = goal.coerceAtLeast(1)
    val ring = Offset(MID - RING_RADIUS, MID - RING_RADIUS)
    val across = Size(RING_RADIUS * 2, RING_RADIUS * 2)
    val line = Stroke(RING_LINE, cap = StrokeCap.Round)
    if (n == 1) {
        drawArc(if (count >= 1) ink else RingQuiet, 0f, FULL_TURN, false, ring, across, style = line)
        return
    }
    val step = FULL_TURN / n
    val span = step - RING_GAP
    repeat(n) { i ->
        val from = -QUARTER_TURN + RING_GAP / 2 + i * step
        drawArc(if (i < count) ink else RingQuiet, from, span, false, ring, across, style = line)
    }
}

/** SVG path data as a path. */
internal fun parsePath(d: String): Path = PathParser().parsePathString(d).toPath()

private fun faceOf(shape: MedalShape): Path = when (shape) {
    MedalShape.Tile -> TileFace
    MedalShape.Rosette -> RosetteFace
}

/** The medal tile (board HaloHistoryBadges): rounded corners, straight sides and a notch like a ribbon's end at the foot. */
private val TileFace: Path by lazy {
    parsePath("M14 10 Q14 4 20 4 H80 Q86 4 86 10 V70 Q86 76 80 78 L50 96 L20 78 Q14 76 14 70 Z")
}

/** The rosette (board HaloBestMoment): 12 points, its valleys [ROSETTE_VALLEY] from the middle and its tips [ROSETTE_TIP]. */
private val RosetteFace: Path by lazy {
    Path().apply {
        for (i in 0 until ROSETTE_VERTICES) {
            val r = if (i % 2 == 0) ROSETTE_TIP else ROSETTE_VALLEY
            val t = -PI / 2 + 2 * PI * i / ROSETTE_VERTICES
            val x = MID + (r * cos(t)).toFloat()
            val y = MID + (r * sin(t)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private val LockedRim = Greys.MedalQuiet

/** The quiet arcs of the weekly goal's ring. */
private val RingQuiet = Greys.MedalQuiet

/** The frame a token is drawn on, units across, and its middle. */
internal const val FRAME = 100f
private const val MID = 50f
private const val EMBLEM_FRAME = 24f
private const val EMBLEM_LINE = 2f
private const val RIM_LINE = 1.2f
private const val RIM_ALPHA = 0.3f

/** The earned fill: a radial from the top at 0.95 to 0.55, centred 15% down and 90% across (boards, radialGradient). */
private const val FILL_TOP = 0.95f
private const val FILL_BOTTOM = 0.55f
private const val FILL_CENTRE_Y = 0.15f
private const val FILL_RADIUS = 0.9f

/** An earned token's fill starts this share of its strength as its sheet opens. */
private const val RISE_FLOOR = 0.2f

private const val ROSETTE_VERTICES = 24
private const val ROSETTE_TIP = 48.0
private const val ROSETTE_VALLEY = 42.2

private const val RING_RADIUS = 42f
private const val RING_LINE = 9f
private const val RING_GAP = 13.6f
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
