package org.openstrap.app.ui.glow.live

import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.Space
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.device.WorkoutState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.ui.glow.*
import org.openstrap.app.ui.dashboard.LocalHeartZones
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R

/**
 * A distance workout's map page (board Map view): the map from the top of the screen, with the frame's top bar (minimize,
 * sport, time, strap and GPS) and the heart rate over it, route only and follow me on the right; under it a black panel with the
 * distance, the moving time and the pace (a ride's speed) and the latest kilometre, above the bar shared with the
 * pace page ([LiveBottomBar]). A drag on the map pans it and stops following; follow me fits the route again.
 */
// The GPS lines are built once per screen from the recording, so the list never changes under a recomposition.
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun LiveMapScreen(
    r: WorkoutState.Recording,
    route: List<Route.Point>,
    readings: List<Pair<Long, Int>>,
    bpm: Int?,
    connected: Boolean,
    contentPadding: PaddingValues,
    controls: LiveControls,
    figures: List<Pair<String?, String>>,
    /** The total time, as on every live screen's top bar. */
    time: () -> Long,
    battery: Int?,
    /** The GPS line under the strap's in the top bar. */
    status: List<Pair<String, Color>>,
    /** The height of the bar shared with the other view ([LiveBottomBar]): the panel leaves this much room for it. */
    bottomRoom: Dp,
    /** False when the heart rate is on the other half of a folded phone. */
    heartPill: Boolean = true,
) {
    val heartZones = LocalHeartZones.current
    BackHandler(onBack = controls.onMinimize)
    val density = LocalDensity.current
    var panel by remember { mutableStateOf(344.dp) }
    var routeOnly by rememberSaveable { mutableStateOf(false) }
    var follow by rememberSaveable { mutableStateOf(true) }
    // A reading comes every second: the zone colours follow every tenth, the line every fix.
    val stretches = remember(route.size, readings.size / 10, heartZones) { Route.byZone(route, readings, r.maxHeartRate, heartZones) }
    val here = route.lastOrNull { it.accuracy <= Route.MAX_ACCURACY_M }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + contentPadding.calculateTopPadding() + 8.dp
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The map runs under the panel's rounded top, so no black shows at its corners.
        Box(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = (panel - PANEL_OVERLAP).coerceAtLeast(0.dp))) {
            WorkoutMap(
                stretches, routeOnly, Modifier.fillMaxSize(), here,
                fitPadding = PaddingValues(start = Space.S24, top = top + 112.dp, end = 76.dp, bottom = PANEL_OVERLAP + Space.S32),
                creditBottom = PANEL_OVERLAP + 14.dp,
                hereColor = liveColor(r),
                follow = follow, onPanned = { follow = false },
            )
            // The frame's own top bar over the map (sport, time, strap and GPS), on a scrim so it reads on any map, and
            // the heart rate under it.
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Ink.ScrimMap, Color.Transparent)))
                    .padding(top = top, start = Space.S16, end = Space.S16, bottom = Space.S12),
                verticalArrangement = Arrangement.spacedBy(Space.S8),
            ) {
                LiveTopBar(r, connected, controls, null, time, status, battery, quiet = false)
                if (heartPill) LitHeartPill(bpm, r.maxHeartRate, r.paused, 36.dp)
            }
            // On its side the map is short, so the buttons hang under the GPS pill instead of rising into it.
            Column(
                if (LocalLiveLandscape.current) Modifier.align(Alignment.TopEnd).padding(end = Space.S16, top = top + 54.dp)
                else Modifier.align(Alignment.BottomEnd).padding(end = Space.S16, bottom = PANEL_OVERLAP + 44.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S12),
            ) {
                // Pause is the page's one white tap, so these stay glass and show what is on by the sport's colour.
                val on = liveColor(r)
                HaloIconButton(
                    AppIcons.MapFold,
                    stringResource(if (routeOnly) R.string.workout_live_map_show_map else R.string.workout_live_map_route_only),
                    { routeOnly = !routeOnly },
                    variant = HaloIconVariant.Glass, tint = if (routeOnly) on else Color.White,
                )
                HaloIconButton(
                    AppIcons.NavigationArrow, stringResource(R.string.workout_live_map_follow), { follow = true },
                    variant = HaloIconVariant.Glass, tint = if (follow) on else Color.White,
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { panel = with(density) { it.height.toDp() } }
                .clip(Corner.Sheet).background(Color.Black)
                .padding(start = Space.S16, end = Space.S16, top = Space.S16, bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            // Board HaloLiveMap: three plain figures, then the latest kilometre on a plain panel.
            LiveFigures(figures)
            LastSplit(route, readings, r.workout.pauseRanges)
            Spacer(Modifier.height(bottomRoom))
        }
    }
}

/** How far the map runs under the panel's rounded top. */
private val PANEL_OVERLAP = 28.dp


/** The latest whole kilometre on a dark row with how long ago it ended, until the next; nothing before the first. */
@Composable
private fun LastSplit(route: List<Route.Point>, readings: List<Pair<Long, Int>>, pauses: List<LongRange>) {
    val splits = remember(route.size, pauses) { Route.splits(route, pauses) }
    val now = nowTicking()
    val (line, ago) = WorkoutCopy.lastSplit(splits, readings, route.firstOrNull()?.t, now) ?: return
    // A strip, kept 48 high over the map: one line, its sides from the card rule.
    HaloCard(Modifier.height(48.dp), size = HaloCardSize.Strip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line, color = Color.White, style = GlowType.secondary.copy(fontFeatureSettings = "tnum"),
                maxLines = 1, modifier = Modifier.weight(1f))
            Text(ago, color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1)
        }
    }
}
