package org.openstrap.app.ui.alarms

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.presentation.ClockSetting
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import java.text.DateFormatSymbols
import kotlin.math.abs

/*
 * The time as two wheels (HaloAlarmSheet and HaloQuietHoursSheet boards): Funnel Display Light, the chosen number at 48
 * on a #17181C pill 64 tall, its neighbours at 32 and 40% white, the next ones at 24 and 18%. The hour and the minute
 * wheel sit either side of a colon; a phone on a 12 hour clock gets a third, short wheel for AM and PM.
 */

private val RowHeight = 56.dp
private const val VISIBLE_PAD = 2
private const val HOURS_A_DAY = 24
private const val HALF_DAY = 12
private const val MINUTES_AN_HOUR = 60
private const val MINUTE_STEP = 5
private val HourWheelWidth = 96.dp
private val MeridiemWheelWidth = 84.dp

/** A wheel's number as two digits. */
private fun two(n: Int) = n.toString().padStart(2, '0')

/**
 * The time of day in minutes after midnight, [initial] to start with, [onChange] on every change. Minutes step by 5, or by 1
 * when [initial] isn't on a 5 (an alarm Zepp set to 06:42 keeps its minute).
 */
@Composable
internal fun TimeWheels(initial: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, use24Hour: Boolean = ClockSetting.use24Hour) {
    var hour by rememberSaveable { mutableIntStateOf(initial / MINUTES_AN_HOUR) }
    var minute by rememberSaveable { mutableIntStateOf(initial % MINUTES_AN_HOUR) }
    val minuteStep = remember { if (initial % MINUTES_AN_HOUR % MINUTE_STEP == 0) MINUTE_STEP else 1 }
    SideEffect { onChange(hour * MINUTES_AN_HOUR + minute) }
    Box(modifier.fillMaxWidth().padding(vertical = Space.S4), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(GlowLayout.TileShape).background(Greys.Control))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (use24Hour) {
                Wheel((0 until HOURS_A_DAY).toList(), hour, { hour = it }, ::two, Modifier.width(HourWheelWidth))
            } else {
                // 12 first, then 1 to 11: the hour within its half of the day.
                val twelve = listOf(HALF_DAY) + (1 until HALF_DAY)
                Wheel(
                    twelve, if (hour % HALF_DAY == 0) HALF_DAY else hour % HALF_DAY,
                    { hour = it % HALF_DAY + hour / HALF_DAY * HALF_DAY }, ::two, Modifier.width(HourWheelWidth),
                )
            }
            Text(":", Modifier.padding(horizontal = Space.S8), color = Color.White, style = GlowType.figureLarge)
            Wheel((0 until MINUTES_AN_HOUR step minuteStep).toList(), minute, { minute = it }, ::two, Modifier.width(HourWheelWidth))
            if (!use24Hour) {
                val names = remember { DateFormatSymbols.getInstance().amPmStrings }
                Wheel(
                    listOf(0, 1), hour / HALF_DAY, { hour = hour % HALF_DAY + it * HALF_DAY }, { names[it] },
                    Modifier.width(MeridiemWheelWidth),
                )
            }
        }
    }
}

/** One wheel: [values] with the [chosen] one centred, snapping to each. Scrolling reports the centred value to [onPick]. */
@Suppress("UnstableCollections", "ComposeUnstableCollections")
@Composable
internal fun Wheel(values: List<Int>, chosen: Int, onPick: (Int) -> Unit, label: (Int) -> String, modifier: Modifier = Modifier) {
    val start = remember { values.indexOf(chosen).coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // Two blank rows lead and trail, so the first and last value can reach the middle.
    val centre by remember {
        derivedStateOf {
            val px = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset * 2 >= px) 1 else 0).coerceIn(0, values.lastIndex)
        }
    }
    val pick by rememberUpdatedState(onPick)
    SideEffect { values.getOrNull(centre)?.let(pick) }
    LazyColumn(
        modifier.height(RowHeight * (2 * VISIBLE_PAD + 1)),
        state = state, flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.size + 2 * VISIBLE_PAD) { i ->
            val v = i - VISIBLE_PAD
            Box(Modifier.height(RowHeight), contentAlignment = Alignment.Center) {
                if (v in values.indices) {
                    val (style, alpha) = when (abs(v - centre)) {
                        0 -> GlowType.wheelChosen to 1f
                        1 -> GlowType.wheelNear to GlowType.WHEEL_NEAR_ALPHA
                        else -> GlowType.wheelFar to GlowType.WHEEL_FAR_ALPHA
                    }
                    Text(label(values[v]), Modifier.alpha(alpha), color = Color.White, style = style, maxLines = 1)
                }
            }
        }
    }
}
