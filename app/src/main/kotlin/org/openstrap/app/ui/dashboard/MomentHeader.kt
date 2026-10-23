package org.openstrap.app.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.PhosphorIcons

/** How far a swipe on the header has to travel to change the day. */
private val SwipeDistance = 56.dp

/**
 * The header on every tab (spec 3.1): the avatar on the left opens You, the [title] over the day in the middle, the
 * All metrics button and the strap chip on the right open the sheet and the Strap page. Swipe it sideways to change
 * the day (on every tab at once), or tap the day for the calendar. The day before peeks in dimmed on the left, the day after on the right while there is
 * one. Pull to sync opens its gap underneath.
 */
@Composable
fun MomentHeader(
    title: String,
    date: LocalDate,
    chip: StrapChip,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    picker: DayPicker,
    onOpenYou: () -> Unit,
    onOpenStrap: () -> Unit,
    onOpenAllMetrics: () -> Unit,
) {
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    if (calendarOpen) DayCalendarSheet(date, picker.available, picker.onPick, onDismiss = { calendarOpen = false })
    val today = LocalDate.now()
    val hasNext = date < today
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(PhosphorIcons.User, "You", "Open You", onOpenYou)
            DaySwitcher(title, date, hasNext, onPrev, onNext, { calendarOpen = true }, Modifier.weight(1f))
            RoundButton(PhosphorIcons.SquaresFour, "All metrics", "Open all metrics", onOpenAllMetrics)
            Spacer(Modifier.width(8.dp))
            StrapChipButton(chip, onOpenStrap)
        }
        // Pull to sync opens here and pushes everything below it down.
        org.openstrap.app.ui.components.PullSyncReveal()
    }
}

/** A round button at either end of the header: the avatar, and All metrics in its likeness. */
@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, clickLabel: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Avatar).clickable(onClickLabel = clickLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(20.dp), tint = Color.White) }
}

/** The title over the day, with its neighbours peeking in either side; it follows a sideways drag and springs back. */
@Composable
private fun DaySwitcher(
    title: String, date: LocalDate, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit, modifier: Modifier,
) {
    val threshold = with(LocalDensity.current) { SwipeDistance.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shift by animateFloatAsState(drag, if (dragging) snap() else spring(dampingRatio = 1f), label = "header drag")
    val prev by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)
    val canNext by rememberUpdatedState(hasNext)
    Row(
        modifier.height(60.dp)
            .pointerInput(threshold) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        // Pulling the day to the right brings the day before, as a page would.
                        if (drag > threshold) prev() else if (drag < -threshold && canNext) next()
                        dragging = false; drag = 0f
                    },
                    onDragCancel = { dragging = false; drag = 0f },
                ) { change, amount ->
                    change.consume()
                    // No day after today: the drag that way only gives a little.
                    drag = (drag + amount).let { if (it < 0 && !canNext) it.coerceAtLeast(-threshold / 3) else it }
                }
            }
            .graphicsLayer { translationX = shift * 0.4f },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Peek(peekDay(date.minusDays(1)))
        Column(
            Modifier.padding(horizontal = 16.dp).widthIn(max = 200.dp).clip(CircleShape)
                .clickable(onClickLabel = "Pick a day", onClick = onPick)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { contentDescription = "$title, ${dateLine(date)}" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                title, color = Color.White.copy(alpha = 0.56f), style = GlowType.secondary.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
            Text(
                dateLine(date), color = Color.White, style = GlowType.label.copy(fontSize = 17.sp, lineHeight = 22.sp),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasNext) Peek(peekDay(date.plusDays(1))) else Spacer(Modifier.width(PeekWidth))
    }
}

@Composable
private fun Peek(text: String) {
    Text(text, Modifier.width(PeekWidth), color = Color.White.copy(alpha = 0.22f), style = GlowType.label.copy(fontWeight = FontWeight.Normal), maxLines = 1, softWrap = false)
}

/** The strap chip: a dot, and words only when they matter. */
@Composable
private fun StrapChipButton(chip: StrapChip, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        Modifier.clip(shape).background(ChipSurface).border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .clickable(onClickLabel = "Open your strap", onClick = onClick)
            .semantics { contentDescription = chip.description }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(chipDotColor(chip.dot), CircleShape))
        if (chip.text.isNotEmpty()) {
            Spacer(Modifier.width(7.dp))
            Text(
                chip.text, maxLines = 1, softWrap = false,
                color = if (chip.dot == StrapChip.Dot.Warn) MetricColors.warn else Color.White,
                style = GlowType.secondary.copy(fontSize = 13.sp),
            )
        }
    }
}

private fun chipDotColor(dot: StrapChip.Dot): Color = when (dot) {
    StrapChip.Dot.Fine -> MetricColors.good
    StrapChip.Dot.Low -> MetricColors.battery(StrapChip.LOW_BATTERY)
    StrapChip.Dot.Charging -> MetricColors.good
    StrapChip.Dot.Warn -> MetricColors.warn
    StrapChip.Dot.Quiet -> Color.White.copy(alpha = 0.3f)
}

private val Avatar = Color(0xFF1C1C1E)
private val ChipSurface = Color(0xFF16161A)
private val PeekWidth = 34.dp
