package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.theme.MetricColors

val SLEEP_COLORS = mapOf(
    SleepStage.AWAKE to MetricColors.sleepAwake,
    SleepStage.REM to MetricColors.sleepRem,
    SleepStage.LIGHT to MetricColors.sleepLight,
    SleepStage.DEEP to MetricColors.sleepDeep,
)

val SLEEP_NAMES = mapOf(
    SleepStage.AWAKE to "Awake",
    SleepStage.REM to "REM",
    SleepStage.LIGHT to "Light",
    SleepStage.DEEP to "Deep",
)

/** Counts taps on the tab that's showing; each new count scrolls the [ScreenList]s on that tab to the top. */
val LocalScrollToTop = androidx.compose.runtime.compositionLocalOf { 0 }

/** Shared scrolling list with consistent gutters for all dashboard tabs. */
@Composable
fun ScreenList(
    contentPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    /** Space between the screen edge and the cards. */
    gutter: Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
    // A tap on the tab that's showing scrolls whatever screen is open on it back to the top (spec 3.3).
    val toTop = LocalScrollToTop.current
    var handled by remember { mutableIntStateOf(toTop) }
    LaunchedEffect(toTop) { if (toTop != handled) { handled = toTop; state.animateScrollToItem(0) } }
    // The status bar gets an opaque strip so cards don't scroll underneath the clock.
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        state = state,
        contentPadding = PaddingValues(
            start = gutter, end = gutter,
            top = 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * Whether Charge is the Today hero: only for today, once Charge has a value, and once the waking up moment is over
 * ([org.openstrap.app.data.MomentResolver.wakingOver], or from noon when no sleep was recorded). Past days lead
 * with their Readiness, the day's verdict.
 */
internal fun chargeLeads(state: DayUiState, rec: RecoveryResult?): Boolean {
    if (state.date != LocalDate.now() || rec?.charge?.latest == null) return false
    val now = java.time.Instant.now().epochSecond
    val wake = state.sleep?.end
    return if (wake != null) org.openstrap.app.data.MomentResolver.wakingOver(now, wake)
    else java.time.LocalTime.now().hour >= 12
}

fun List<ChartPoint>.avg(): Int? = if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()
