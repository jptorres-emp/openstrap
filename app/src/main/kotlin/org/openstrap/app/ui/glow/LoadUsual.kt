package org.openstrap.app.ui.glow

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
import org.openstrap.app.ui.components.pressClickable
import org.openstrap.app.presentation.Ranges
import org.openstrap.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.workout.SummaryCopy
import org.openstrap.app.ui.dashboard.OffWords
import org.openstrap.app.ui.dashboard.loadOffStatus
import org.openstrap.app.ui.dashboard.offTag
import androidx.compose.ui.text.style.TextOverflow

/*
 * The Load panel of a summary and a past workout (boards HaloSummaryRun, HaloPastWorkout): Load against your usual, time to
 * recover and the bar.
 */

/**
 * What a workout's usual Load is, to print under its Load and draw as the band on the bar. Today develop has one usual
 * value, so [low] equals [high] and it reads "usual 100" with a tick; the boards print "usual 80 to 120", which is a
 * [low] under [high]: build the range here and the label and the band follow, nothing else changes (plan, Open calls 4).
 */
internal class LoadUsual(val low: Float, val high: Float) {
    /** The usual as words: "100", or "80 to 120" for a range. */
    fun words(): String =
        if (Math.round(low) == Math.round(high)) "${Math.round(low)}" else Ranges.numbers(Math.round(low), Math.round(high))

    companion object {
        /** The single usual value develop has, as a range of no width. */
        fun of(usual: Float?): LoadUsual? = usual?.let { LoadUsual(it, it) }
    }
}

/** The plain panel's fill (workout rules, Tokens: #0C0D10). */
private val PlainPanel = Color(0xFF0C0D10)

/** The track and band of the Load bar (boards: #1E2025 and #3A3D45). */
private val LoadTrack = Color(0xFF1E2025)
private val LoadBand = Color(0xFF3A3D45)

/**
 * Load from this workout against your usual (board HaloSummaryRun), a plain panel: "From this run" with its tag (above,
 * about or below usual), Load over "Load, usual 100" and time to recover over its label, then a bar with your usual on
 * it and this Load as a white dot. What earlier workouts left to recover from reads under them, and the heart rate
 * [drop] line when there is one. [onInfo] opens the (i) sheet: training effect and EPOC.
 */
@Composable
internal fun WorkoutLoadCard(a: WorkoutAnalysis, usual: Float?, drop: String? = null, onInfo: (() -> Unit)? = null) {
    val load = a.workout.trainingLoad
    val status = loadOffStatus(load?.toFloat(), usual, endOfDay = true)
    val range = LoadUsual.of(usual)
    val usualTag = stringResource(R.string.glow_summary_load_tag_usual).takeIf { load != null && usual != null }
    val tag = offTag(OffWords.Usual, status)?.text ?: usualTag
    Column(
        Modifier.fillMaxWidth().clip(GlowLayout.TileShape).background(PlainPanel)
            .let { if (onInfo != null) it.pressClickable(onInfo, GlowLayout.TileShape) else it }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SummaryCopy.loadTitle(a.workout.sportType), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onInfo != null) Icon(
                    AppIcons.TapCaret, stringResource(R.string.glow_summary_load_about), Modifier.padding(start = 6.dp).size(14.dp),
                    tint = Color.White.copy(alpha = CARD_CARET_ALPHA),
                )
            }
            if (tag != null) Text(
                tag, Modifier.padding(start = 12.dp).background(HaloButtonSurface, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                color = SoftInk, style = GlowType.caption, maxLines = 1,
            )
        }
        if (load != null) {
            val recover = SummaryCopy.recoverSpan(a.recoveryMinutes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadFigure(
                    "$load", null,
                    range?.let { stringResource(R.string.glow_summary_load_usual, it.words()) }
                        ?: stringResource(R.string.workout_summary_load_label),
                    Modifier.weight(1f),
                )
                if (recover != null) {
                    val (number, unit) = recover.split(" ", limit = 2).let { it[0] to it.getOrNull(1) }
                    LoadFigure(number, unit, stringResource(R.string.glow_summary_recover_label), Modifier.weight(1f), rule = true)
                }
            }
            LoadBar(load.toFloat(), range)
        } else Text(stringResource(R.string.glow_summary_load_none), color = GlowType.noteColor, style = GlowType.secondary)
        SummaryCopy.carriedOver(a.recoveryMinutes, a.ownRecoveryMinutes, a.workout.sportType)?.let {
            Text(it, color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        drop?.let { Text(it, color = GlowType.noteColor, style = GlowType.noteSmall) }
    }
}

/** A figure of the Load panel: the value in Funnel Display Light 28 with its small [unit], the label under it in 12 px grey. */
@Composable
private fun LoadFigure(value: String, unit: String?, label: String, modifier: Modifier = Modifier, rule: Boolean = false) {
    Column(
        modifier.let { m ->
            if (!rule) m else m.drawBehind {
                drawLine(FigureRuleInk, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
            }.padding(start = 16.dp)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            buildAnnotatedString {
                append(value)
                if (unit != null) withStyle(GlowType.noteSmall.copy(fontSize = 13.sp).toSpanStyle()) { append(" $unit") }
            },
            color = Color.White, style = GlowType.figure, maxLines = 1,
        )
        Text(label, color = GlowType.noteColor, style = GlowType.noteSmall, maxLines = 2)
    }
}

private val FigureRuleInk = Color(0xFF24262C)

/** The scale the Load bar runs to when nothing is higher (board HaloSummaryRun: usual 80 to 120 sits at 40 to 60 per cent). */
private const val LOAD_SCALE = 200f

/** The bar under the Load panel: the usual as a band (a tick while it is one value) and [load] as a white dot. */
@Composable
private fun LoadBar(load: Float, usual: LoadUsual?) {
    val top = maxOf(LOAD_SCALE, load * 1.25f, (usual?.high ?: 0f) * 1.7f)
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = 5.dp.toPx()
        val w = size.width - 2 * r
        fun x(v: Float) = r + (v / top).coerceIn(0f, 1f) * w
        val round = CornerRadius(2.dp.toPx())
        val y = 3.dp.toPx()
        val thick = 4.dp.toPx()
        drawRoundRect(LoadTrack, Offset(0f, y), Size(size.width, thick), round)
        if (usual != null) {
            val from = x(usual.low)
            val to = maxOf(x(usual.high), from + 3.dp.toPx())
            drawRoundRect(LoadBand, Offset(from, y), Size(to - from, thick), round)
        }
        drawCircle(Color.White, r, Offset(x(load), size.height / 2))
    }
}

