package org.openstrap.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.SleepStage
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.SectionLabel
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.MuseoModerno
import org.openstrap.app.ui.theme.PhosphorIcons

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

/** Shared scrolling list with consistent gutters for all dashboard tabs. */
@Composable
fun ScreenList(
    contentPadding: PaddingValues,
    state: LazyListState = rememberLazyListState(),
    /** Space between the screen edge and the cards. */
    gutter: Dp = 16.dp,
    content: LazyListScope.() -> Unit,
) {
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

/** Opens "Your strap" from the header chip on every dashboard tab; provided by the app root. */
val LocalOpenStrap = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** The red full stop after the OpenStrap wordmark. */
private val WordmarkDot = Color(0xFFFF4D5E)

/**
 * Top bar: OpenStrap wordmark and strap battery chip, then ‹ TODAY › centered on its own row. Tapping the
 * day opens a calendar of the days that have data.
 */
@Composable
fun DateHeader(
    date: LocalDate,
    battery: Int?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    charging: Boolean = false,
    picker: DayPicker = DayPicker(),
) {
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    if (calendarOpen) DayCalendarSheet(date, picker.available, picker.onPick, onDismiss = { calendarOpen = false })
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("OpenStrap")
                    withStyle(SpanStyle(color = WordmarkDot)) { append(".") }
                },
                Modifier.weight(1f),
                fontFamily = MuseoModerno,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            // WHOOP's strap chip: always there, and it opens "Your strap".
            val openStrap = LocalOpenStrap.current
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .let { if (openStrap != null) it.clickable(onClickLabel = "Open your strap", onClick = openStrap) else it }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (battery != null) {
                    Text(
                        if (charging) "⚡$battery%" else "$battery%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (battery <= 15 || charging) MetricColors.battery(battery) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        PhosphorIcons.Watch, if (battery != null) "Strap connected" else "Strap not connected", Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (battery != null) Box(Modifier.size(6.dp).background(MetricColors.battery(battery), CircleShape))
                }
            }
        }
        // Pull to sync opens here, under the wordmark, and pushes the day and everything below it down.
        org.openstrap.app.ui.components.PullSyncReveal()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(PhosphorIcons.CaretLeft, "Previous day") }
            Row(
                Modifier.clip(RoundedCornerShape(50)).clickable(onClickLabel = "Pick a day") { calendarOpen = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(
                    when (date) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(PhosphorIcons.CaretDown, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext, enabled = date < LocalDate.now()) { Icon(PhosphorIcons.CaretRight, "Next day") }
        }
    }
}

/** How long after waking Readiness stays the headline before Charge takes over. */
private const val READINESS_HOURS_AFTER_WAKING = 3

/**
 * Whether Charge is the Today hero: only for today, once Charge has a value, and once you've been awake
 * [READINESS_HOURS_AFTER_WAKING] hours (or from noon when no sleep was recorded). Past days lead with
 * their Readiness, the day's verdict.
 */
internal fun chargeLeads(state: DayUiState, rec: RecoveryResult?): Boolean {
    if (state.date != LocalDate.now() || rec?.charge?.latest == null) return false
    val now = java.time.Instant.now().epochSecond
    val wake = state.sleep?.end
    return if (wake != null) now - wake >= READINESS_HOURS_AFTER_WAKING * 3600L
    else java.time.LocalTime.now().hour >= 12
}

fun List<ChartPoint>.avg(): Int? = if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()
