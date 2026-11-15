package org.openstrap.app.ui.glow

import org.openstrap.app.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import org.openstrap.app.data.Rewards
import org.openstrap.app.data.Weeks
import org.openstrap.app.presentation.RewardsCopy
import org.openstrap.app.presentation.dotFormat
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.FamilySummary

/*
 * The celebrations (rules A2, boards HaloBestMoment, HaloGoalMoment, HaloHistoryBadges): the full screen goal moment, once
 * a week, the full screen card for a personal best (never for a milestone), the badges past history earned, shown once
 * on one screen, and the two switches that turn the goal moment and the weekly recap off. No other pop ups. A moment is
 * black with one radial glow of its token's colour behind the token (the one place light spreads), a Close at the top
 * right, the token, one Doto figure, its name, a line in a tint of the colour and a grey line, then Share and Continue,
 * Continue the one white thing.
 */

/**
 * A moment's page: black, [content] centred under the Close at the top right, the [bottom] bar 24 dp from the foot.
 * [glow] (the token's colour) is drawn by [momentGlow] on the token.
 */
@Composable
private fun MomentPage(
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    bottom: @Composable RowScope.() -> Unit,
    topGap: Dp = MOMENT_TOP_GAP,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Scrolls under the bottom bar; the scroll clips to the screen, so the glow spreads across the full width.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = MOMENT_BAR_ROOM),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(AppIcons.X, stringResource(R.string.glow_close), onClose, size = 44.dp, iconSize = 18.dp)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = topGap), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(contentPadding)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), content = bottom,
        )
    }
}

/**
 * The glow behind a moment's token (rules A2): a radial of [ink] about [size] across at 0.45, 0.15 at 55% and nothing at
 * the edge, a little below the token's middle. It sits behind the token only and never reaches the edges.
 */
private fun Modifier.momentGlow(ink: Color, size: Dp = MOMENT_GLOW): Modifier = drawBehind {
    val radius = size.toPx() / 2
    drawCircle(
        Brush.radialGradient(
            0f to ink.copy(alpha = GLOW_CENTRE), GLOW_MID to ink.copy(alpha = GLOW_MID_ALPHA), 1f to Color.Transparent,
            center = Offset(this.size.width / 2, this.size.height / 2 + GLOW_DROP.toPx()), radius = radius,
        ),
        radius, Offset(this.size.width / 2, this.size.height / 2 + GLOW_DROP.toPx()),
    )
}

/** A moment's name: Funnel Display 26 semibold. */
@Composable
private fun MomentName(text: String) =
    Text(
        text, color = Color.White, style = GlowType.goalValueMinor.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

/** A moment's line of how it compares: 15 in a tint of the token's colour. */
@Composable
private fun MomentLine(text: String, ink: Color) =
    Text(
        text, color = lerp(ink, Color.White, LINE_TINT), style = GlowType.body, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

/**
 * The full screen card for a personal best (board HaloBestMoment): the rosette at 160 in the best's colour, its value in
 * Doto, what it is, how far it beat the last one and when it was set. Share (dark, half, when there is somewhere to share
 * it) and Continue (white, half).
 */
@Composable
fun BestCardScreen(
    best: Rewards.NewBest,
    sportName: String,
    contentPadding: PaddingValues,
    onContinue: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    BackHandler(onBack = onContinue)
    val now = System.currentTimeMillis() / 1000
    val ink = bestInk(best.best.kind)
    MomentPage(contentPadding, onClose = onContinue, bottom = { MomentButtons(onShare, onContinue) }) {
        Box(Modifier.momentGlow(ink)) { Medal(bestLook(best.best.kind), MOMENT_TOKEN, lit = rememberLightRise(true)) }
        DotFigure(RewardsCopy.bestValue(best.best.kind, best.best.value), BigNumber.Feature, Modifier.padding(top = 4.dp))
        MomentName(RewardsCopy.bestTitle(best.best.kind))
        MomentLine(RewardsCopy.beatLine(best, now), ink)
        Text(
            RewardsCopy.whenSet(best.best.date, sportName), color = GlowType.noteColor, style = GlowType.secondary,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The goal moment (board HaloGoalMoment), once a week after the summary of the workout that met the goal: the ring of the
 * week's workouts at 160, "4/4" in Doto, the weeks in a row, the week's days as solid pills, and Continue, with
 * Celebrations settings under the days.
 */
@Composable
fun GoalMomentScreen(
    count: Int,
    goal: Int,
    inARow: Int,
    weekStarts: List<Long>,
    contentPadding: PaddingValues,
    onSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    BackHandler(onBack = onContinue)
    val firstDay = remember { Weeks.firstDay() }
    // Only as the moment opens, like the summary's days: it is shown once, for the week just played.
    @Suppress("ForbiddenMethodCall/uiClock")
    val today = remember { LocalDate.now() }
    val days = remember(weekStarts) { FamilySummary.weekDays(weekStarts, today, firstDay) }
    MomentPage(contentPadding, onClose = onContinue, bottom = { MomentButtons(null, onContinue) }) {
        Box(Modifier.momentGlow(RewardLime)) {
            WeeklyGoalRing(count, goal, MOMENT_TOKEN, pluralStringResource(R.plurals.glow_goals_count_of_goal, goal, count, goal))
        }
        DotFigure(dotFormat("%d/%d", count, goal), BigNumber.Feature, Modifier.padding(top = 4.dp))
        MomentName(stringResource(R.string.glow_celebrate_goal_met))
        MomentLine(RewardsCopy.momentLine(inARow), RewardLime)
        val locale = LocalConfiguration.current.locales[0]
        DayPills(
            days.map { (d, on) -> d.dayOfWeek.getDisplayName(DayStyle.NARROW, locale) to on },
            today = days.indexOfFirst { it.first == today }.takeIf { it >= 0 },
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
        )
        PageTextButton(stringResource(R.string.glow_celebrate_settings), onSettings)
    }
}

/** A moment's bottom bar (rules A2): Share (dark) beside Continue (white) in halves, or Continue alone, full width. */
@Composable
private fun RowScope.MomentButtons(onShare: (() -> Unit)?, onContinue: () -> Unit) {
    if (onShare != null) PageButton(stringResource(R.string.glow_share), onShare, Modifier.weight(1f), size = ButtonSize.Main)
    PageButton(stringResource(R.string.glow_continue), onContinue, Modifier.weight(1f), primary = true, size = ButtonSize.Main)
}

/**
 * The celebration settings and the week the goal moment last showed for, with what changes them, handed to the
 * summary so it can follow itself with the goal moment.
 */
class Celebrations(
    val goalMoment: Boolean,
    val weeklyRecap: Boolean,
    /** The week (epoch day) the goal moment last showed for. */
    val momentWeek: Long?,
    val setGoalMoment: (Boolean) -> Unit,
    val setWeeklyRecap: (Boolean) -> Unit,
    val markMoment: (Long) -> Unit,
)

/** The two celebration switches (board HaloCelebrationsSheet, workouts spec 3.4), in one panel in a sheet, then Done. */
@Composable
fun CelebrationsSheet(
    goalMoment: Boolean,
    weeklyRecap: Boolean,
    onGoalMoment: (Boolean) -> Unit,
    onWeeklyRecap: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    PageSheet(onDismiss, stringResource(R.string.glow_celebrate_title)) {
        PageRows {
            PageSwitchRow(
                stringResource(R.string.glow_celebrate_goal_moment), stringResource(R.string.glow_celebrate_goal_moment_line), goalMoment,
                onGoalMoment, divider = false,
            )
            PageSwitchRow(
                stringResource(R.string.glow_celebrate_recap), stringResource(R.string.glow_celebrate_recap_line), weeklyRecap,
                onWeeklyRecap,
            )
        }
        PageButton(stringResource(R.string.glow_done), onDismiss, Modifier.fillMaxWidth(), primary = true)
    }
}

/**
 * The badges past history earned, shown once on one screen when the app first works them out (board HaloHistoryBadges,
 * requests spec 7.2), rather than one by one: their count in Doto, the line, the tokens in a grid of three at 72 and
 * Continue.
 */
@Composable
fun HistoryBadgesScreen(
    badges: List<Rewards.Badge>,
    contentPadding: PaddingValues,
    onContinue: () -> Unit,
    face: (Rewards.Badge) -> String = { it.short },
) {
    BackHandler(onBack = onContinue)
    MomentPage(contentPadding, onClose = onContinue, bottom = { MomentButtons(null, onContinue) }, topGap = HISTORY_TOP_GAP) {
        Box(Modifier.momentGlow(RewardLime, HISTORY_GLOW)) { BigNumberText("${badges.size}", BigNumber.Feature) }
        MomentName(stringResource(R.string.glow_celebrate_history_title))
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(HISTORY_GAP),
        ) {
            badges.chunked(HISTORY_PER_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HISTORY_GAP)) {
                    row.forEach { b ->
                        Column(
                            Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Medal(badgeLook(b, face(b)), 72.dp)
                            Text(b.title, color = Color.White, style = GlowType.footnote, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(HISTORY_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The badges history earned, three to a row, 16 apart. */
private const val HISTORY_PER_ROW = 3
private val HISTORY_GAP = 16.dp

/** Board HaloBestMoment: the token is 160 and the glow 300 across, the body 100 under the Close. History: 60 under, glow 360. */
private val MOMENT_TOKEN = 160.dp
private val MOMENT_GLOW = 300.dp
private val HISTORY_GLOW = 360.dp
private val MOMENT_TOP_GAP = 84.dp
private val HISTORY_TOP_GAP = 44.dp
private val GLOW_DROP = 58.dp

/** Room kept clear of the bottom bar: its 64, the 24 under it and a little air. */
private val MOMENT_BAR_ROOM = 104.dp

private const val GLOW_CENTRE = 0.45f
private const val GLOW_MID = 0.55f
private const val GLOW_MID_ALPHA = 0.15f

/** A moment's line is its colour mixed this far toward white (#C6F55A reads as #DEF99F). */
private const val LINE_TINT = 0.42f
