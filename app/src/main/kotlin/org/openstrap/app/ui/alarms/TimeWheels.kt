package org.openstrap.app.ui.alarms

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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

/**
 * The time of day in minutes after midnight, [initial] to start with, [onChange] on every change. Minutes step by 5, or by 1
 * when [initial] isn't on a 5 (an alarm Zepp set to 06:42 keeps its minute).
 */
@Composable
internal fun TimeWheels(initial: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, use24Hour: Boolean = ClockSetting.use24Hour) {
    var hour by rememberSaveable { mutableIntStateOf(initial / 60) }
    var minute by rememberSaveable { mutableIntStateOf(initial % 60) }
    val minuteStep = remember { if (initial % 60 % 5 == 0) 5 else 1 }
    LaunchedEffect(hour, minute) { onChange(hour * 60 + minute) }
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(GlowLayout.TileShape).background(Color(0xFF17181C)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (use24Hour) {
                Wheel((0..23).toList(), hour, { hour = it }, { "%02d".format(it) }, Modifier.width(96.dp))
            } else {
                // 1 to 12 on the wheel, 12 standing for 0 of its half of the day.
                val twelve = (listOf(12) + (1..11)).toList()
                Wheel(twelve, if (hour % 12 == 0) 12 else hour % 12, { hour = (it % 12) + (hour / 12) * 12 }, { "%02d".format(it) }, Modifier.width(96.dp))
            }
            Text(":", Modifier.padding(horizontal = 6.dp), color = Color.White, style = GlowType.figureLarge)
            Wheel((0..59 step minuteStep).toList(), minute, { minute = it }, { "%02d".format(it) }, Modifier.width(96.dp))
            if (!use24Hour) {
                val names = remember { DateFormatSymbols.getInstance().amPmStrings }
                Wheel(listOf(0, 1), hour / 12, { hour = hour % 12 + it * 12 }, { names[it] }, Modifier.width(84.dp))
            }
        }
    }
}

/** One wheel: [values] with the [chosen] one centred, snapping to each. Scrolling reports the centred value to [onPick]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(values: List<Int>, chosen: Int, onPick: (Int) -> Unit, label: (Int) -> String, modifier: Modifier = Modifier) {
    val start = remember { values.indexOf(chosen).coerceAtLeast(0) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // Two blank rows lead and trail, so the first and last value can reach the middle.
    val centre by remember {
        derivedStateOf {
            val px = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset * 2 >= px) 1 else 0).coerceIn(0, values.lastIndex)
        }
    }
    LaunchedEffect(centre) { values.getOrNull(centre)?.let(onPick) }
    LazyColumn(
        modifier.height(RowHeight * (2 * VISIBLE_PAD + 1)),
        state = state, flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.size + 2 * VISIBLE_PAD) { i ->
            val v = i - VISIBLE_PAD
            Box(Modifier.height(RowHeight), contentAlignment = Alignment.Center) {
                if (v in values.indices) {
                    val d = abs(v - centre)
                    val (style, alpha) = when (d) {
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
