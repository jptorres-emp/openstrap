package org.openstrap.app.ui.glow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.components.PinnedFade
import org.openstrap.app.ui.components.pinnedFade
import org.openstrap.app.ui.dashboard.LocalScrollToTop
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.IconSize
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space

/*
 * The page frame (docs/design/halo/components-audit.md, 3.1): every page is a column 16 from the screen's edges with
 * one gap between its blocks, set by the family it belongs to, under a header with the same grid on every page.
 */

/** The frame's and the header's measurements. */
internal object PageSpec {
    /** Screen edge to content, on every page at every phone width. */
    val Gutter = Space.S16

    /** Above the first block, under the status bar, and past the last one, over the tab bar. */
    val Top = Space.S4
    val Bottom = Space.S24

    /** A workout page starts a little lower, and ends this far above the screen's foot without pinned buttons. */
    val WorkoutTop = Space.S8
    val WorkoutEnd = Space.S16

    /** The header: 48 high (60 on a tab, which carries the strap pill and the day's peeks), 96 / 1fr / 96 columns. */
    val HeaderHeight = 48.dp
    val TabHeaderHeight = 60.dp
    val HeaderSide = 96.dp

    /** The header's side buttons, 44 across. */
    val HeaderButton = IconSize.TileM

    /** The caret after a title that switches the page, and its gap. */
    val Caret = 12.dp
    val CaretGap = Space.S8
}

/** Which family a page is in: it sets the gap between its blocks. */
internal enum class PageVariant(val gap: Dp) {
    /** A tab and the pages like it: cards 12 apart. */
    Tab(Space.S12),

    /** A detail: sections 16 apart. */
    Detail(Space.S16),

    /** A settings page: groups 24 apart. */
    Settings(Space.S24),

    /** A workout page (Ready, Choose, Live, a summary): blocks 12 apart. */
    Workout(Space.S12),
}

/**
 * A scrolling page: its blocks [variant]'s gap apart, [PageSpec.Gutter] from the edges (more on a wide screen, where the
 * page keeps to one centred column, [LocalPageColumn]), clear of the status bar and of the tab bar in [contentPadding].
 * A tap on the tab that's showing scrolls it to the top (spec 3.3). [gutter] is a dashboard's own, on two columns.
 */
@Composable
internal fun HaloPageList(
    contentPadding: PaddingValues,
    variant: PageVariant = PageVariant.Tab,
    state: LazyListState = rememberLazyListState(),
    gutter: Dp = PageSpec.Gutter,
    content: LazyListScope.() -> Unit,
) {
    val toTop = LocalScrollToTop.current
    var handled by remember { mutableIntStateOf(toTop) }
    LaunchedEffect(toTop) { if (toTop != handled) { handled = toTop; state.animateScrollToItem(0) } }
    val column = LocalPageColumn.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = GlowLayout.pageSide(maxWidth, column, gutter)
        // The status bar gets an opaque strip so cards don't scroll underneath the clock.
        LazyColumn(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding(),
            state = state,
            contentPadding = PaddingValues(
                start = side, end = side,
                top = PageSpec.Top,
                bottom = contentPadding.calculateBottomPadding() + PageSpec.Bottom,
            ),
            verticalArrangement = Arrangement.spacedBy(variant.gap),
            content = content,
        )
    }
}

/**
 * A page that isn't a lazy list: black, filling the height so the controls sit at the bottom, scrolling if it must
 * ([scroll]). [pinned] (Start, Done) stays on the bottom edge while the rest scrolls under it, behind the pinned
 * buttons' fade ([pinnedFade], spec 6.3), and the content ends clear of it. The workout pages use it.
 */
@Composable
internal fun HaloPageColumn(
    contentPadding: PaddingValues,
    variant: PageVariant = PageVariant.Workout,
    scroll: Boolean = true,
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        var pinnedHeight by remember { mutableStateOf(0.dp) }
        // On a wide screen the page keeps to one centred column (spec 2.4 Unfolded).
        val side = GlowLayout.pageSide(maxWidth, LocalPageColumn.current, PageSpec.Gutter)
        // The content ends [PinnedFade.Ramp] above the pinned buttons (their box already counts the bottom padding),
        // or a little above the bottom padding without them.
        val padTop =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + contentPadding.calculateTopPadding() + PageSpec.WorkoutTop
        val padBottom = contentPadding.calculateBottomPadding()
        val end =
            if (pinned != null) (PinnedFade.listClearance(pinnedHeight) - padBottom).coerceAtLeast(PageSpec.WorkoutEnd)
            else PageSpec.WorkoutEnd
        // The live screen fits the screen instead (scroll = false): its controls never move. A scrolling page ends at
        // the keyboard, so what's typed can scroll above it.
        Column(
            // The status bar's inset sits outside the scroll, so the page's content is clipped at it and never draws
            // under the system clock.
            Modifier.fillMaxSize().statusBarsPadding()
                .then(if (scroll) Modifier.imePadding().verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding).padding(start = side, end = side, top = PageSpec.WorkoutTop, bottom = end)
                // At least the screen's height, so the controls sit at the bottom without the page scrolling.
                .then(if (scroll) Modifier.heightIn(min = (maxHeight - padTop - padBottom - end).coerceAtLeast(0.dp)) else Modifier),
            verticalArrangement = Arrangement.spacedBy(variant.gap),
            content = content,
        )
        // Black from the bottom of the screen up to the buttons, fading out above them.
        if (pinned != null) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().pinnedFade()
                .padding(contentPadding).padding(start = side, end = side, bottom = Space.S16)
                .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } },
            verticalArrangement = Arrangement.spacedBy(Space.S4),
            content = pinned,
        )
    }
}

/** A header's look: [button] is its side buttons' surface. */
internal enum class HeaderVariant(val button: HaloIconVariant) {
    /** A page over a tab: back, the title, a trailing action. */
    Page(HaloIconVariant.Control),

    /** A detail, over its coloured wash: back and (i) on translucent black. */
    Detail(HaloIconVariant.OverWash),

    /** A workout page: close or minimise, the sport, the strap's lines. Its side columns grow when their lines need it. */
    Workout(HaloIconVariant.Control),
}

/**
 * The one header: [start], [centre] and [end] in a 96 / 1fr / 96 grid, [PageSpec.HeaderHeight] high, so the centre
 * stays centred whatever sits at the sides. The side buttons are 44 [HaloIconButton]s on [variant]'s surface.
 */
@Composable
internal fun HaloPageHeader(
    start: @Composable BoxScope.() -> Unit,
    centre: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    variant: HeaderVariant = HeaderVariant.Page,
    end: @Composable BoxScope.() -> Unit = {},
) {
    val side =
        if (variant == HeaderVariant.Workout) Modifier.widthIn(min = PageSpec.HeaderSide) else Modifier.width(PageSpec.HeaderSide)
    Row(modifier.fillMaxWidth().height(PageSpec.HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        Box(side, contentAlignment = Alignment.CenterStart, content = start)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center, content = centre)
        Box(side, contentAlignment = Alignment.CenterEnd, content = end)
    }
}

/**
 * The header of a page or a detail (HaloProfile, HaloAlarms and HaloDetailRecovery boards): back at the left, the
 * [title] centred on the screen in Page title type whatever the caret or the trailing action (one line, ending in an
 * ellipsis when it is too long), over [subtitle] in Secondary. [onTitle] makes the title a button with a caret (a
 * detail's switcher). [titleSuffix] follows the title on its line (a workout's Indoor tag). [trailing] is last, so a
 * trailing lambda at a call site lands at the right edge: a detail's (i), Copy, or the Save pill while there are edits.
 */
@Composable
internal fun HaloPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HeaderVariant = HeaderVariant.Page,
    backEnabled: Boolean = true,
    subtitle: String? = null,
    onTitle: (() -> Unit)? = null,
    titleSuffix: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val switchLabel = stringResource(R.string.glow_page_switch)
    HaloPageHeader(
        modifier = modifier, variant = variant,
        start = {
            HaloIconButton(
                AppIcons.ArrowLeft, stringResource(R.string.glow_back), onBack, variant = variant.button, enabled = backEnabled,
            )
        },
        centre = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.clip(Corner.Chip)
                        .let {
                            if (onTitle != null) it.clickable(onClickLabel = switchLabel, role = Role.Button, onClick = onTitle) else it
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The caret's room is kept at both sides, so the words sit in the middle with or without it.
                    if (onTitle != null) Spacer(Modifier.width(PageSpec.Caret + PageSpec.CaretGap))
                    Text(
                        title, color = Color.White, style = GlowType.pageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (onTitle != null) {
                        Spacer(Modifier.width(PageSpec.CaretGap))
                        Icon(AppIcons.CaretDown, null, modifier = Modifier.size(PageSpec.Caret), tint = Ink.Secondary)
                    }
                    if (titleSuffix != null) {
                        Spacer(Modifier.width(TagSpec.Gap))
                        titleSuffix()
                    }
                }
                if (subtitle != null) Text(
                    subtitle, color = Ink.Secondary, style = GlowType.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        },
        end = { trailing?.invoke() },
    )
}
