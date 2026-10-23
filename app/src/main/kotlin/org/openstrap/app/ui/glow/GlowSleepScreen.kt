package org.openstrap.app.ui.glow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import org.openstrap.app.data.SleepHistory
import org.openstrap.app.ui.components.ChartPoint
import org.openstrap.app.ui.components.StageSpan
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.LocalGoals
import org.openstrap.app.ui.dashboard.Metric
import org.openstrap.app.ui.dashboard.SLEEP_NAMES
import org.openstrap.app.ui.dashboard.SleepCard
import org.openstrap.app.ui.dashboard.SleepFacts
import org.openstrap.app.ui.dashboard.SleepWindows
import org.openstrap.app.ui.dashboard.TabCopy
import org.openstrap.app.ui.dashboard.TodayCopy
import org.openstrap.app.ui.dashboard.nightVitals
import org.openstrap.app.ui.dashboard.sleepCards
import org.openstrap.app.ui.dashboard.sleepWeek
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.MetricColors

/*
 * The Sleep tab (spec 5.2): "how did I sleep, and is it trending right". The night glows with its stages, the
 * vitals while asleep as a list, then the week's regularity, breathing disturbances and naps as edge cards
 * ([sleepCards]). Always in the night mood. What shaped the score and the night's charts belong in the Sleep
 * detail (step 6).
 */
@Composable
fun GlowSleepScreen(
    state: DayUiState,
    /** The tab's header, or a back header when the screen is opened from a card on another tab. */
    header: @Composable () -> Unit,
    contentPadding: PaddingValues,
    onOpenMetric: (Metric) -> Unit,
    /** The nightly model, for regularity and breathing disturbances. */
    recovery: org.openstrap.app.data.RecoveryResult? = null,
) {
    val list = rememberLazyListState()
    val rec = recovery?.takeIf { it.date == state.date }
    val breathing = rec?.breathingDisturbances
    val slots = sleepCards(SleepFacts(breathing = breathing != null, naps = state.naps.isNotEmpty(), hasNight = state.sleep != null))
    // The Sleep tab is always at night (spec 2.6): calm glow, light from below only.
    CompositionLocalProvider(LocalGlowPaused provides { list.isScrollInProgress }, LocalGlowMood provides GlowMood.Night) {
        GlowScreenList(contentPadding, list) {
            full { header() }
            // With naps and a breathing summary the two are a Small pair; alone, either is a Wide.
            val pair = breathing != null && state.naps.isNotEmpty()
            tabCards(slots) { card, m ->
                when (card) {
                    SleepCard.Hero -> NightHero(state)
                    // TODO(step 6): each row opens its detail scoped to the night, with the night's chart there.
                    SleepCard.Vitals -> VitalsList(state, onOpenMetric)
                    SleepCard.Regularity -> RegularityCard(state, rec?.regularity)
                    SleepCard.Breathing -> breathing?.let { if (pair) BreathingSmall(it, m) else BreathingCard(it) }
                    SleepCard.Naps -> NapsCard(state, if (pair) m else Modifier.fillMaxWidth().height(GlowGrid.UnitHeight))
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------------ */

/** Sleep glows midnight blue. */
internal val SleepGlow = GlowStyle.Midnight.copy(
    color = MetricColors.sleepDeep,
    coreX = 0.5f, coreY = 0.6f, coreWidth = 1.1f, coreHeight = 0.9f,
)

private fun clock(epoch: Long) = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** The stages in the order the hero adds them up: deepest first, awake last. */
private val STAGE_ORDER = listOf(
    org.openstrap.app.data.db.SleepStage.DEEP, org.openstrap.app.data.db.SleepStage.REM,
    org.openstrap.app.data.db.SleepStage.LIGHT, org.openstrap.app.data.db.SleepStage.AWAKE,
)

/**
 * The night: how long, against the rest of the week, then the score and its word, how often you woke, the stages to
 * scrub and their totals. As high as its content. TODO(step 6): tapping opens the Sleep detail with what shaped the score.
 */
@Composable
private fun NightHero(state: DayUiState) {
    val session = state.sleep
    val isToday = state.date == LocalDate.now()
    GlowCard(SleepGlow, Modifier.fillMaxWidth().gridSize(CardSize.Wide)) {
        Column {
            GlowHeader("Last night", trailing = session?.let { "${clock(it.start)}–${clock(it.end)}" })
            if (session == null) {
                HeroSentence(TabCopy.noNight(state.naps.isNotEmpty(), isToday))
                return@Column
            }
            val asleep = state.asleepSeconds ?: (session.end - session.start)
            val others = state.nights.takeLast(SleepHistory.WEEK_NIGHTS).filter { it.date != state.date }.mapNotNull { it.asleepSeconds }
            HeroSentence(TabCopy.night(asleep, others))
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                val score = session.score
                // The horizon arc (chart kit): the score rising from the night's horizon.
                if (score != null) GlowHorizon(score / 100f, score.toString())
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        score?.let { "Sleep score, ${TabCopy.sleepScoreWord(it).lowercase()}" } ?: "No score from the strap",
                        color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary,
                    )
                    TabCopy.woke(session.wakeCount)?.let { Text(it, color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary) }
                }
            }
            if (state.sleepStages.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                GlowSleepStages(state.sleepStages, 96.dp)
            }
            state.sleepBreakdown?.let { b ->
                Spacer(Modifier.height(16.dp))
                FigureRow(STAGE_ORDER.map { st -> clockDuration(b.byStage[st] ?: 0L) to (SLEEP_NAMES[st] ?: "") })
            }
        }
    }
}

/** Stages as the glow hypnogram, rows Awake to Deep, with start and end times underneath. Shared with Today, where [compact] leaves the names and times out. */
@Composable
internal fun GlowSleepStages(spans: List<StageSpan>, height: Dp, compact: Boolean = false) {
    val start = spans.minOf { it.start }
    val end = spans.maxOf { it.end }.coerceAtLeast(start + 1)
    val rows = DayViewModel.SLEEP_STAGE_ROWS
    val byRow = rows.entries.associate { it.value to (SLEEP_NAMES[it.key] ?: "") }
    val names = (0 until rows.size).map { byRow[it] ?: "" }
    val stages = remember(spans) {
        spans.map { GlowStage((it.start - start).toFloat() / (end - start), (it.end - start).toFloat() / (end - start), rows[it.stage] ?: 0) }
    }
    fun at(f: Float) = clock(start + ((end - start) * f).toLong())
    // Compact, as on Today's Last night card, the rows are too close for their names and the card's title has the times.
    GlowHypnogram(stages, names, height = height, labels = if (compact) emptyList() else listOf(clock(start), clock(end)), showNames = !compact) { st ->
        val mins = (((end - start) * (st.to - st.from)) / 60).toInt().coerceAtLeast(1)
        "${names[st.row]}, ${at(st.from)}–${at(st.to)}, $mins min"
    }
}

/** The vitals while asleep against your previous nights, each opening its metric. */
@Composable
private fun VitalsList(state: DayUiState, onOpen: (Metric) -> Unit) {
    ListPanel(title = "Night vitals", note = "Against your nights") {
        nightVitals(state).forEachIndexed { i, v ->
            ListRow(
                v.label, v.color, v.value, v.status,
                offNormal = v.direction != null && v.direction != 0, onClick = { onOpen(v.metric) }, divider = i > 0,
            )
        }
    }
}

/**
 * Sleep regularity, its one home: how alike the week's sleep and wake times were, the usual times, and each night's
 * bed and wake as bars. Lit from the top like a night sky. TODO(step 6): tapping a night, and the method in the info sheet.
 */
@Composable
private fun RegularityCard(state: DayUiState, reg: org.openstrap.app.data.SleepRegularity.Result?) {
    val zone = ZoneId.systemDefault()
    val w = sleepWeek(state, LocalGoals.current.sleepSeconds, reg, zone)
    EdgeCard(MetricColors.sleepDeep, Modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Sleep) {
        EdgeTitle("Sleep regularity", "Last 7 nights")
        EdgeValue(reg?.sri?.roundToInt()?.toString(), null, if (w == null) "No sleep recorded in the last 7 nights" else TabCopy.regularity(reg), waiting = false)
        if (w != null) {
            w.usual?.let { u ->
                Text(
                    TabCopy.usualTimes(SleepHistory.clockMinutes(u.bedtime), SleepHistory.clockMinutes(u.wake)),
                    color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA), style = GlowType.secondary, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SleepWindows(w.nights, w.usual, zone)
        }
    }
}

/**
 * Breathing disturbances: the strap's count of dips in blood oxygen per hour asleep, against your own nights. No
 * severity words: those are apnea bands, and this is a wellness estimate, marked early until checked on hardware.
 * A value against your usual, so lit from below.
 */
@Composable
private fun BreathingCard(b: org.openstrap.app.data.Recovery.BreathingDisturbances) {
    EdgeCard(MetricColors.sleepLight, Modifier.fillMaxWidth().gridSize(CardSize.Wide), kind = EdgeKind.Value) {
        EdgeTitle("Breathing disturbances", "Early estimate")
        EdgeValue("%.1f".format(b.perHour), "an hour", TabCopy.breathing(b))
    }
}

/** Breathing disturbances as a Small beside the naps: the count an hour and your usual. Lit from below, a value. */
@Composable
private fun BreathingSmall(b: org.openstrap.app.data.Recovery.BreathingDisturbances, modifier: Modifier) =
    SmallCard(MetricColors.sleepLight, "Breathing disturbances", "%.1f".format(b.perHour), "/ h", TabCopy.breathingShort(b), modifier)

/**
 * The day's naps as a Small (a Wide when alone): the time napped, a strip of the latest nap's stages and when it was.
 * Earlier in the day, so lit from the left. TODO(step 6): tapping opens the naps in the Sleep detail, with each nap's
 * heart rate.
 */
@Composable
private fun NapsCard(state: DayUiState, modifier: Modifier) {
    val naps = state.naps.sortedBy { it.start }
    if (naps.isEmpty()) return
    val stages = state.napStages[naps.last().start].orEmpty()
    SmallCard(
        MetricColors.sleepRem, TabCopy.napsTitle(naps.size), clockDuration(naps.sumOf { it.end - it.start }), null,
        TabCopy.naps(naps.size, clock(naps.first().start)), modifier, kind = EdgeKind.Earlier,
        extra = if (stages.isEmpty()) null else ({ StageStrip(stages, Modifier.padding(top = 8.dp)) }),
    )
}
