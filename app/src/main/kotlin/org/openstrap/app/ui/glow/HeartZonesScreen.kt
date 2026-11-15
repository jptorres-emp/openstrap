package org.openstrap.app.ui.glow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.data.HeartZones
import org.openstrap.app.data.MaxHeartRate
import org.openstrap.app.data.Workouts
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.workout.HeartZonesCopy
import org.openstrap.app.ui.workout.ZONE_COLORS
import org.openstrap.app.ui.workout.ZONE_NAMES

/**
 * Heart rate zones (board SettingsHeartZones), from Ready's Your zones and the You page: your max heart rate as the
 * hero, lit in heart red from the left, with where it comes from and Set it myself; then the five zones as a plain
 * list with their heart rates. One set for every sport. A zone opens a sheet to move its edges; [onZones] stores the
 * zones and [onMax] your own max (null learns it again). [max] is null until it's worked out.
 */
@Composable
fun HeartZonesScreen(
    max: MaxHeartRate?,
    zones: HeartZones,
    contentPadding: PaddingValues,
    onMax: (Int?) -> Unit,
    onZones: (HeartZones) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var settingMax by rememberSaveable { mutableStateOf(false) }
    ScreenList(contentPadding, gutter = GlowLayout.WideGutter) {
        item { PageHeader(HeartZonesCopy.TITLE, onBack) }
        // Your max and where it comes from, then the zones, then the two ways to change it (board HaloHeartZones).
        item {
            PageRows {
                PageRow(
                    HeartZonesCopy.MAX_TITLE, max?.let { stringResource(R.string.glow_zones_bpm_value, it.bpm) },
                    onClick = { if (max != null) settingMax = true }, divider = false,
                )
                if (max != null) PageInfoRow(stringResource(R.string.glow_zones_worked_out_from), HeartZonesCopy.source(max))
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.glow_zones_heading), footnote = HeartZonesCopy.NOTE) {
                (0 until Workouts.ZONES).forEach { z ->
                    ZoneRow(z, if (max != null) HeartZonesCopy.zoneBpm(zones, z, max.bpm) else "", divider = z > 0) { if (max != null) editing = z }
                }
            }
        }
        item {
            PageRows {
                PageActionRow(
                    HeartZonesCopy.SET_IT, { settingMax = true }, icon = AppIcons.PencilEdit, enabled = max != null, divider = false,
                )
                if (max?.source == MaxHeartRate.Source.Set) {
                    PageActionRow(HeartZonesCopy.LEARN_IT, { onMax(null) }, icon = AppIcons.ArrowCounterClockwise)
                }
                if (!zones.standard) {
                    PageActionRow(HeartZonesCopy.RESET, { onZones(HeartZones.STANDARD) }, icon = AppIcons.ArrowCounterClockwise)
                }
            }
        }
    }
    val zone = editing
    if (zone != null && max != null) ZoneSheet(zone, zones, max.bpm, onZones) { editing = null }
    if (settingMax && max != null) MaxSheet(max, { onMax(it); settingMax = false }) { settingMax = false }
}

/** A zone's range under its sheet's title (board HaloZoneSheet). */
private val ZoneRangeInk = Color(0xFFC9CED6)

/** A zone on the list (board HaloHeartZones): its colour as a 10 square, "Zone 2, Aerobic", its heart rates and a chevron. */
@Composable
private fun ZoneRow(zone: Int, bpm: String, divider: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .topHairline(divider, DetailRowLine)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(ZONE_COLORS[zone]))
        Text(
            stringResource(R.string.glow_metrics_zone_title, HeartZonesCopy.zoneName(zone), ZONE_NAMES[zone]), Modifier.weight(1f),
            color = Color.White, style = GlowType.body, maxLines = 1,
        )
        Text(bpm, color = Color.White.copy(alpha = GlowType.ROW_VALUE_ALPHA), style = GlowType.rowValue)
        Spacer(Modifier.width(2.dp))
        RowChevron()
    }
}

/**
 * Moving a zone's edges (board HaloZoneSheet): where it starts and where it ends, a bpm at a time, on a draft that only
 * Save keeps; Cancel and a swipe down throw it away. The zone beside each edge moves too.
 */
@Composable
private fun ZoneSheet(zone: Int, zones: HeartZones, maxHr: Int, onZones: (HeartZones) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.glow_metrics_zone_title, HeartZonesCopy.zoneName(zone), ZONE_NAMES[zone])
    var draft by remember { mutableStateOf(zones) }
    PageSheet(onDismiss, title) {
        val start = draft.startBpm(zone, maxHr)
        // The zone's colour bar and its range under the title.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp, 26.dp).clip(RoundedCornerShape(3.dp)).background(ZONE_COLORS[zone]))
            Text(HeartZonesCopy.zoneBpm(draft, zone, maxHr), color = ZoneRangeInk, style = GlowType.body)
        }
        BpmStepper(
            HeartZonesCopy.STARTS_AT, start.toString(), { draft = draft.withStart(zone, start - 1, maxHr) },
            { draft = draft.withStart(zone, start + 1, maxHr) },
        )
        draft.endBpm(zone, maxHr)?.let { end ->
            BpmStepper(
                HeartZonesCopy.ENDS_AT, end.toString(), { draft = draft.withEnd(zone, end - 1, maxHr) },
                { draft = draft.withEnd(zone, end + 1, maxHr) },
            )
        }
        Text(HeartZonesCopy.edgesNote(zone), color = GlowType.noteColor, style = GlowType.secondary)
        SaveCancel(onDismiss, saveEnabled = true) { onZones(draft); onDismiss() }
    }
}

/** Cancel and Save side by side at a sheet's foot (settings rules: edits apply on Save, Cancel discards them). */
@Composable
private fun SaveCancel(onCancel: () -> Unit, saveEnabled: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageButton(stringResource(R.string.glow_cancel), onCancel, Modifier.weight(1f))
        PageButton(HeartZonesCopy.SAVE, onSave, Modifier.weight(1f), enabled = saveEnabled, primary = true)
    }
}

/**
 * A heart rate with minus and plus squircles either side of it (board HaloZoneSheet: 44 buttons, the number Light 44). With
 * [onType] the number is tapped to type it on the number keyboard (board HaloMaxHrSheet).
 */
@Composable
private fun BpmStepper(
    label: String?,
    bpm: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onType: ((String) -> Unit)? = null,
    enabled: Boolean = true,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    val name = label ?: HeartZonesCopy.SET_TITLE
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (label != null) Text(label, color = GlowType.noteColor, style = GlowType.secondary)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(
                AppIcons.Minus, stringResource(R.string.glow_metrics_zone_lower, name), onLess,
                size = 44.dp, enabled = enabled,
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                if (typing && onType != null) {
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.foundation.text.BasicTextField(
                        bpm, onType, Modifier.width(IntrinsicSize.Min).widthIn(min = 72.dp).focusRequester(focus),
                        textStyle = GlowType.goalValue.copy(color = Color.White, textAlign = TextAlign.Center),
                        singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    androidx.compose.runtime.SideEffect { focus.requestFocus() }
                } else {
                    Text(
                        bpm, color = Color.White, style = GlowType.goalValue,
                        modifier = if (onType != null) Modifier.clickable { typing = true } else Modifier,
                    )
                }
                Text(
                    " " + stringResource(R.string.glow_unit_bpm), color = GlowType.noteColor, style = GlowType.body,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            RoundIconButton(
                AppIcons.Plus, stringResource(R.string.glow_metrics_zone_higher, name), onMore,
                size = 44.dp, enabled = enabled,
            )
        }
    }
}

/** Setting your max yourself (board HaloMaxHrSheet): the stepper, or tap the number and type it; Save keeps it. */
@Composable
private fun MaxSheet(max: MaxHeartRate, onMax: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(max.bpm.toString()) }
    val value = HeartZonesCopy.parseMax(text)
    fun step(by: Int) { value?.let { text = (it + by).coerceIn(MAX_BPM_RANGE).toString() } }
    PageSheet(onDismiss, HeartZonesCopy.SET_TITLE) {
        BpmStepper(
            null, text, { step(-1) }, { step(1) }, onType = { v -> text = v.filter { it.isDigit() }.take(3) },
            enabled = value != null,
        )
        Text(HeartZonesCopy.SET_NOTE, color = GlowType.noteColor, style = GlowType.secondary)
        SaveCancel(onDismiss, saveEnabled = value != null) { onMax(value) }
    }
}

/** The heart rates a typed or stepped max may take (the profile's tested range). */
private val MAX_BPM_RANGE = org.openstrap.app.data.Recovery.TESTED_MAX_RANGE
