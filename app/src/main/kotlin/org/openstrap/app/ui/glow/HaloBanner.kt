package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.Space

/*
 * The one banner (docs/design/halo/components-audit.md, 3.15): news with at most one action, only when there is news (an
 * update ready, a backup that failed, the strap away, a workout not saved). A grey card with a lead tile in the kind's
 * colour (the designer chose the circle over a left bar), a title, a note, and an action pill.
 */

/** The banner's measurements. */
internal object BannerSpec {
    /** The lead tile: a 44 circle in the kind's colour at 16%, its glyph 20. */
    val Tile = TileSize.Medium.size
    val Glyph = TileSize.Medium.glyph
    const val TINT = TileSpec.INK_TINT

    /** Between the tile, the words and the action; between the title and the note. */
    val Gap = Space.S12
    val LineGap = Space.S2

    /** The title 15, the note 13, the action a 36 pill. */
    val Title = GlowType.cardTitle
    val Note = GlowType.secondary
    val Action = HaloButtonSize.Small
}

/** What a banner says: its colour and its icon. */
internal enum class BannerKind(val color: Color, val icon: ImageVector) {
    Good(MetricColors.good, AppIcons.CheckCircle),
    Warn(MetricColors.warn, AppIcons.Warning),
    Problem(DestructiveInk, AppIcons.WarningCircle),
    Info(EdgeNeutral, AppIcons.Info),
}

/**
 * A banner of [kind]: the [icon] on its tile, [title], an optional [note], and one action pill on the right when [action]
 * and [onAction] are given. [content] goes under the words, for the rare banner that carries more (a button across).
 * Inside a card that is already there (a step of the firmware page), [framed] false leaves the card out.
 */
@Composable
internal fun HaloBanner(
    kind: BannerKind,
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = kind.icon,
    framed: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BannerSpec.Gap)) {
            HaloTile(icon, size = TileSize.Medium, tone = TileTone.Ink(kind.color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BannerSpec.LineGap)) {
                Text(title, color = Color.White, style = BannerSpec.Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (note != null) Text(note, color = GlowType.noteColor, style = BannerSpec.Note)
            }
            if (action != null && onAction != null) {
                HaloButton(action, onAction, variant = HaloButtonVariant.Secondary, size = BannerSpec.Action)
            }
        }
        content?.invoke(this)
    }
    if (framed) HaloCard(modifier, content = body)
    else Column(modifier, verticalArrangement = Arrangement.spacedBy(CardSpec.Gap), content = body)
}
