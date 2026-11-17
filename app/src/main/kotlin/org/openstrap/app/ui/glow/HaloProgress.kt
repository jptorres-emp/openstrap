package org.openstrap.app.ui.glow

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.OnSurface
import org.openstrap.app.ui.theme.Space

/*
 * How far along, and the round marks (docs/design/halo/components-audit.md, 3.17, 3.19 and 3.20): a bar, a row of
 * segments or a ring is [HaloProgress], [HaloProgressSegments] or [HaloProgressRing]; an icon on a circle is [HaloTile];
 * a medal's size is a [TokenSize]. Charts stay in the chart kit.
 */

/** The progress marks' measurements. */
internal object ProgressSpec {
    /** A bar: 6 high, a pill. */
    val BarHeight = 6.dp

    /** A row of segments: 6 high, 4 apart, corners 3. */
    val SegmentHeight = 6.dp
    val SegmentGap = Space.S4
    val SegmentCorner = 3.dp

    /** A ring in a row's lead (2.5 stroke), and a ring on its own. */
    val RowRingStroke = 2.5.dp
    val RingStroke = 8.dp

    /** An unknown wait: a third of the track slides across in this long. */
    const val SLIDE_MS = 1200
    const val SLIDE_SHARE = 3f
}

/**
 * A bar [share] of the way along (0..1) in [color], over a track one step above the surface ([OnSurface.line]), so it
 * shows on a sheet as on a card. With no [share] (how long isn't known) a third of the track slides across.
 */
@Composable
internal fun HaloProgress(share: Float?, modifier: Modifier = Modifier, color: Color = Color.White) {
    val track = OnSurface.line
    val slide = if (share == null) {
        rememberInfiniteTransition(label = "progress")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(ProgressSpec.SLIDE_MS, easing = LinearEasing)), label = "progress slide")
    } else null
    Canvas(modifier.fillMaxWidth().height(ProgressSpec.BarHeight).clip(Corner.Pill)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        if (share != null) {
            drawRoundRect(color, size = Size(size.width * share.coerceIn(0f, 1f), size.height), cornerRadius = r)
        } else if (slide != null) {
            val w = size.width / ProgressSpec.SLIDE_SHARE
            drawRoundRect(color, topLeft = Offset(-w + (size.width + w) * slide.value, 0f), size = Size(w, size.height), cornerRadius = r)
        }
    }
}

/**
 * A row of [count] solid segments: the first [done] in [doneColor], the one under way white, the rest faint (workout
 * rules, shared pieces). Drawn with the chart kit's [SegmentBar].
 */
@Composable
internal fun HaloProgressSegments(count: Int, done: Int, modifier: Modifier = Modifier, doneColor: Color = Ink.Tertiary) {
    SegmentBar(
        List(count) { i -> Segment(1f, if (i < done) doneColor else if (i == done) Ink.Primary else Ink.Line) },
        modifier, height = ProgressSpec.SegmentHeight, gap = ProgressSpec.SegmentGap, corner = ProgressSpec.SegmentCorner,
    )
}

/** A ring [share] of the way round in [color], [stroke] wide: 2.5 in a row's lead. */
@Composable
internal fun HaloProgressRing(
    share: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = ProgressSpec.RowRingStroke,
    track: Color = Ink.Edge,
) = ProgressRing(share, color, modifier, stroke, track)

/* ------------------------------------------------- Tiles ------------------------------------------------- */

/** The tile's measurements. */
internal object TileSpec {
    /** An [TileTone.Ink] tile's fill: its colour at this share. */
    const val INK_TINT = 0.16f

    /** A picked ink tile's fill (a sport you are on), ringed white. */
    const val PICKED_TINT = 0.3f

    /** A dim glyph on a waiting row. */
    const val WAITING_GLYPH = 0.3f

    /** The effort glow: its strength at full effort, how far it reaches, and how low its centre sits. */
    const val EFFORT_GLOW = 0.7f
    const val EFFORT_REACH = 0.56f
    const val EFFORT_LOW = 0.6f
}

/** A tile's size across, and its glyph: 32 and 16, 44 and 20, 64 and 24. */
internal enum class TileSize(val size: Dp, val glyph: Dp) {
    Small(IconSize.TileS, IconSize.GlyphS),
    Medium(IconSize.TileM, IconSize.GlyphM),
    Large(IconSize.TileL, IconSize.GlyphL),
}

/** What a tile's circle is filled with. */
@Immutable
internal sealed interface TileTone {
    /** The control grey one step above the surface ([OnSurface.control]), a strong white glyph: settings. */
    data object Neutral : TileTone

    /** [color] at 16% with the glyph in it ([dim] for a waiting row), or at 30% with the white ring when [picked]. */
    data class Ink(val color: Color, val dim: Boolean = false, val picked: Boolean = false) : TileTone

    /** White with a black glyph: the chosen one. */
    data object Selected : TileTone

    /** A one-step-up grey with a heart-red glow from below, as strong as [effort] (0..1): how hard a workout was. */
    data class Effort(val effort: Float) : TileTone
}

/**
 * An icon on a circle, always a circle: [size] across, filled by [tone], the [icon] in its glyph size. [glyph] overrides
 * the glyph's colour. [badge] sits over the tile (a sport done outdoors).
 */
@Composable
internal fun HaloTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: TileSize = TileSize.Small,
    tone: TileTone = TileTone.Neutral,
    glyph: Color? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val fill = when (tone) {
        TileTone.Neutral -> OnSurface.control
        is TileTone.Ink -> tone.color.copy(alpha = tone.color.alpha * if (tone.picked) TileSpec.PICKED_TINT else TileSpec.INK_TINT)
        TileTone.Selected -> Color.White
        is TileTone.Effort -> OnSurface.panel
    }
    val ink = glyph ?: when (tone) {
        TileTone.Neutral, is TileTone.Effort -> Ink.Strong
        is TileTone.Ink -> tone.color.copy(alpha = tone.color.alpha * if (tone.dim) TileSpec.WAITING_GLYPH else 1f)
        TileTone.Selected -> Color.Black
    }
    Box(modifier.size(size.size)) {
        Box(
            Modifier.matchParentSize().clip(Corner.Pill).background(fill)
                .let { if (tone is TileTone.Ink && tone.picked) it.border(ChipSpec.Ring, Color.White, Corner.Pill) else it }
                .let { if (tone is TileTone.Effort) it.effortGlow(tone.effort) else it },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(size.glyph), tint = ink) }
        badge?.invoke(this)
    }
}

/** The effort light: a soft heart-red glow behind the glyph, centred a little low as light from below would be. */
private fun Modifier.effortGlow(effort: Float): Modifier = drawBehind {
    val centre = Offset(size.width / 2, size.height * TileSpec.EFFORT_LOW)
    drawRect(
        Brush.radialGradient(
            0f to MetricColors.heart.copy(alpha = TileSpec.EFFORT_GLOW * effort.coerceIn(0f, 1f)), 1f to Color.Transparent,
            center = centre, radius = size.minDimension * TileSpec.EFFORT_REACH,
        ),
    )
}

/* ------------------------------------------------- Tokens ------------------------------------------------- */

/** A medal's sizes (3.19): the next badge, a grid's token, a sheet's, a moment's. */
internal enum class TokenSize(val size: Dp) {
    Next(56.dp),
    Grid(72.dp),
    Sheet(96.dp),
    Moment(160.dp),
}
