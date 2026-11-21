package org.openstrap.app.ui.glow

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openstrap.app.R
import org.openstrap.app.data.Route
import org.openstrap.app.data.WorkoutRecorder
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.WorkoutCopy
import org.openstrap.app.ui.workout.HeartZonesCopy
import org.openstrap.app.export.GpxShare
import org.openstrap.app.export.GpxWriter
import org.openstrap.app.maps.RouteSnapper
import org.openstrap.app.presentation.Units
import org.openstrap.app.ui.dashboard.LocalHeartZones
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Corner
import org.openstrap.app.ui.theme.Greys
import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.workout.PhoneSummary
import org.openstrap.app.ui.workout.SplitRow

/*
 * A finished GPS workout's route map (boards HaloRouteMap to HaloRouteMapNotOnRoads), opened from the route card in the
 * summary's place: the map over the screen with Close, the title and GPX on top and Route only on the right, and under
 * it a black panel with the kilometres to pick from and the Roads or GPS line.
 */

/** How far the map runs under the panel's rounded top. */
private val PANEL_OVERLAP = 28.dp

/** A kilometre's chip: name over time, as tall as two lines want. */
private val SplitChipHeight = 52.dp

/** The route map page for [summary]'s workout; [model] says what is open and picked, close goes back to the summary. */
@Composable
internal fun RouteMapPage(summary: PhoneSummary, model: RouteMapModel, contentPadding: PaddingValues) {
    BackHandler(onBack = model::close)
    val hideTabBar = LocalHideTabBar.current
    DisposableEffect(Unit) {
        hideTabBar(true)
        onDispose { hideTabBar(false) }
    }
    val line = rememberRouteLine(summary)
    val rows = remember(summary, line.legs) { routeSplitRows(summary, line.legs) }
    val selected = model.selected?.takeIf { it in line.legs.indices && it in rows.indices }
    val density = LocalDensity.current
    var panel by remember { mutableStateOf(240.dp) }
    var follow by remember { mutableStateOf(true) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + contentPadding.calculateTopPadding() + 8.dp
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The map runs under the panel's rounded top, so no black shows at its corners.
        Box(Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = (panel - PANEL_OVERLAP).coerceAtLeast(0.dp))) {
            WorkoutMap(
                line.stretches, model.routeOnly, Modifier.fillMaxSize(),
                fitPadding = PaddingValues(start = Space.S24, top = top + 64.dp, end = 76.dp, bottom = PANEL_OVERLAP + Space.S32),
                creditBottom = PANEL_OVERLAP + 14.dp,
                follow = follow, onPanned = { follow = false },
                kmMarkers = line.kmMarkers,
                highlight = selected?.let { line.legs[it].points }.orEmpty(),
            )
            TopBar(summary, model::close, Modifier.padding(top = top, start = Space.S16, end = Space.S16))
            if (!follow) {
                HaloChip(
                    stringResource(R.string.glow_route_map_fit), { follow = true },
                    Modifier.align(Alignment.TopCenter).padding(top = top + 60.dp), icon = AppIcons.Route01,
                )
            }
            // White while it is on, translucent near black over the map otherwise, as on the live map.
            HaloIconButton(
                AppIcons.MapFold,
                stringResource(if (model.routeOnly) R.string.workout_live_map_show_map else R.string.workout_live_map_route_only),
                { model.routeOnly = !model.routeOnly },
                Modifier.align(Alignment.BottomEnd).padding(end = Space.S16, bottom = PANEL_OVERLAP + 44.dp),
                variant = if (model.routeOnly) HaloIconVariant.Primary else HaloIconVariant.Glass,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { panel = with(density) { it.height.toDp() } }
                .clip(Corner.Sheet).background(Color.Black)
                .padding(start = Space.S16, end = Space.S16, top = Space.S20, bottom = contentPadding.calculateBottomPadding() + Space.S16),
            verticalArrangement = Arrangement.spacedBy(Space.S16),
        ) {
            if (rows.isNotEmpty()) {
                Splits(rows, selected, summary.analysis.maxHeartRate) { i ->
                    model.pick(i)
                    follow = true
                }
            }
            if (selected != null) PickedSplit(rows[selected], summary.analysis.maxHeartRate)
            else LineChoice(line)
        }
    }
}

/** Close on the left, Route with the sport and distance in the middle, GPX on the right (board HaloRouteMap). */
@Composable
private fun TopBar(summary: PhoneSummary, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(48.dp)) {
        HaloIconButton(
            AppIcons.X, stringResource(R.string.glow_close), onClose,
            Modifier.align(Alignment.CenterStart), variant = HaloIconVariant.Glass,
        )
        val a = summary.analysis
        val distance = summary.distanceMeters?.let { Units.distance(it, Units.Precision.Workout) }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 104.dp).clip(Corner.Pill).background(Greys.OverMap)
                .padding(horizontal = Space.S12, vertical = Space.S4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.glow_summary_route), color = Color.White, style = GlowType.cardTitle, maxLines = 1)
            Text(
                listOfNotNull(a.sportName, distance).joinToString(", "), color = GlowType.noteColor, style = GlowType.noteSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        GpxButton(summary, Modifier.align(Alignment.CenterEnd))
    }
}

/** Hands the recorded route to another app as GPX ([GpxWriter]); a toast when the file can't be written. */
@Composable
private fun GpxButton(summary: PhoneSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.glow_route_map_gpx_failed)
    val a = summary.analysis
    HaloChip(
        stringResource(R.string.glow_route_map_gpx),
        {
            scope.launch {
                @Suppress("InjectDispatcher") // a one-off file write from a tap, nothing to test it with
                val uri = withContext(Dispatchers.IO) {
                    val gpx = GpxWriter.write(a.sportName, summary.route, summary.history, summary.pauses)
                    GpxShare.forSharing(context, GpxShare.fileName(a.workout.start), gpx)
                }
                if (uri != null) GpxShare.send(context, uri) else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
        },
        modifier, icon = AppIcons.UploadSimple,
    )
}

/** Splits and a hint, then the kilometres as chips, the [selected] one white; a tap picks one or clears it. */
@Suppress("UnstableCollections", "ComposeUnstableCollections") // the split rows are built once per summary and never change
@Composable
private fun Splits(rows: List<SplitRow>, selected: Int?, maxHr: Int, onPick: (Int) -> Unit) {
    val heartZones = LocalHeartZones.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.glow_summary_splits), color = Color.White, style = GlowType.cardTitle,
                maxLines = 1,
            )
            Spacer(Modifier.width(Space.S12))
            Text(
                stringResource(if (selected == null) R.string.glow_route_map_pick_hint else R.string.glow_route_map_unpick_hint),
                color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f),
            )
        }
        val list = rememberLazyListState()
        LaunchedEffect(selected) { selected?.let { list.animateScrollToItem(it) } }
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            itemsIndexed(rows) { i, row ->
                val on = i == selected
                val ink = if (on) Color.Black else Color.White
                Column(
                    Modifier.height(SplitChipHeight).clip(CircleShape).background(if (on) Color.White else Greys.Control)
                        .selectable(on, role = Role.Button) { onPick(i) }
                        .padding(horizontal = Space.S16),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        row.averageBpm?.let {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(zoneColor(it, maxHr, heartZones)))
                            Spacer(Modifier.width(Space.S4))
                        }
                        Text(row.name, color = ink, style = GlowType.buttonSmall, maxLines = 1)
                    }
                    Text(
                        row.time, color = if (on) ink.copy(alpha = 0.7f) else GlowType.noteColor,
                        style = GlowType.noteSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The picked kilometre in a line, "Km 3 in 6:02", with its average heart rate and the zone's name, the dot in its colour. */
@Composable
private fun PickedSplit(row: SplitRow, maxHr: Int) {
    val heartZones = LocalHeartZones.current
    HaloCard(Modifier.heightIn(min = 48.dp), size = HaloCardSize.Strip) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.glow_route_map_split_in, row.name, row.time), color = Color.White,
                style = GlowType.secondary.copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            row.averageBpm?.let {
                val zone = WorkoutRecorder.liveZoneOf(it, maxHr, heartZones)
                Box(Modifier.size(8.dp).clip(CircleShape).background(zoneColor(it, maxHr, heartZones)))
                Spacer(Modifier.width(Space.S8))
                Text(
                    stringResource(R.string.glow_route_map_split_heart, it, HeartZonesCopy.zoneName(zone)), color = GlowType.noteColor,
                    style = GlowType.noteSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1,
                )
            }
        }
    }
}

/**
 * [splitRowsOf], with the last part of the route always a row: the summary leaves out a part under 100 m, but the map
 * has a leg for it (a kilometre's chip picks the leg of the same index), so the chips end where the line does.
 */
private fun routeSplitRows(summary: PhoneSummary, legs: List<Route.Leg>): List<SplitRow> {
    val rows = splitRowsOf(summary, emptyList())
    val last = legs.lastOrNull()?.split ?: return rows
    if (legs.size == rows.size || last.meters >= 1000.0 || last.meters <= 0.0) return rows
    val i = legs.lastIndex
    val firstT = summary.route.firstOrNull()?.t
    val from = legs.getOrNull(i - 1)?.split?.endT ?: firstT ?: last.endT
    val meters = summary.distanceMeters?.takeIf { !summary.distanceFromSteps }?.let { it - 1000f * i }?.takeIf { it > 0f } ?: last.meters.toFloat()
    val distance = Units.distance(meters, Units.Precision.Workout) ?: Strings.app.format(R.string.workout_summary_meters, meters.toInt())
    return rows + SplitRow(
        Strings.app.format(R.string.workout_summary_split_last, distance), Units.workoutClock(last.seconds),
        WorkoutCopy.averageOver(summary.history, from, last.endT), last.seconds, SplitRow.Kind.Last,
    )
}

/** Line: Roads or GPS, and a note on what is drawn. Nothing to choose without the app (a preview). */
@Composable
private fun LineChoice(line: RouteLine) {
    val roads = line.roads ?: return
    val choose = line.choose ?: return
    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.glow_route_map_line), color = GlowType.noteColor, style = GlowType.body,
                maxLines = 1, modifier = Modifier.width(56.dp),
            )
            GlowSegments(
                listOf(stringResource(R.string.glow_route_map_roads), stringResource(R.string.glow_route_map_gps)),
                if (line.drawnAsRoads) 0 else 1, { choose(it == 0) }, Modifier.weight(1f),
            )
        }
        val note = when {
            !roads -> R.string.glow_route_map_note_gps
            line.matching -> R.string.glow_route_map_note_matching
            line.outcome is RouteSnapper.Outcome.Snapped -> R.string.glow_route_map_note_roads
            line.outcome == RouteSnapper.Outcome.NotOnRoads -> R.string.glow_route_map_note_not_on_roads
            else -> R.string.glow_route_map_note_offline
        }
        Text(
            stringResource(note), color = GlowType.noteColor, style = GlowType.noteSmall,
            modifier = Modifier.padding(start = 56.dp),
        )
    }
}
