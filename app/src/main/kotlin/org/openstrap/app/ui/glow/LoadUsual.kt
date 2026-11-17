package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import org.openstrap.app.data.WorkoutAnalysis
import org.openstrap.app.data.WorkoutLoadBand
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
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
 * What a workout's usual Load is, to print under its Load and draw as the band on the bar: "usual 80 to 120" as the
 * boards print it, [low] under [high] (plan, Open calls 4). Edges that round to one number read as that number.
 */
internal class LoadUsual(val low: Float, val high: Float) {
    /** The usual as words: "100", or "80 to 120" for a range. */
    fun words(): String =
        if (Math.round(low) == Math.round(high)) "${Math.round(low)}" else Ranges.numbers(Math.round(low), Math.round(high))

    companion object {
        /** The usual workout Load as the two edges the boards print, "80 to 120" ([WorkoutLoadBand]). */
        fun of(usual: Float?): LoadUsual? = WorkoutLoadBand.edges(usual)?.let { (low, high) -> LoadUsual(low.toFloat(), high.toFloat()) }
    }
}

private val LoadBand = Greys.Line

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
    HaloCard(onClick = onInfo) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SummaryCopy.loadTitle(a.workout.sportType), color = Color.White, style = GlowType.cardTitle, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onInfo != null) Icon(
                    AppIcons.TapCaret, stringResource(R.string.glow_summary_load_about), Modifier.padding(start = Space.S8).size(14.dp),
                    tint = Ink.Tertiary,
                )
            }
            if (tag != null) Text(
                tag, Modifier.padding(start = Space.S12).background(Greys.Control, CircleShape).padding(horizontal = Space.S8,
                    vertical = Space.S2),
                color = SoftInk, style = GlowType.caption, maxLines = 1,
            )
        }
        if (load != null) {
            val recover = SummaryCopy.recoverSpan(a.recoveryMinutes)
            val (number, unit) = recover?.split(" ", limit = 2)?.let { it[0] to it.getOrNull(1) } ?: (null to null)
            HaloFigures(
                listOfNotNull(
                    "$load" to (range?.let { stringResource(R.string.glow_summary_load_usual, it.words()) }
                        ?: stringResource(R.string.workout_summary_load_label)),
                    if (recover != null) number to stringResource(R.string.glow_summary_recover_label) else null,
                ),
                units = listOf(null, unit),
            )
            LoadBar(load.toFloat(), range)
        } else Text(stringResource(R.string.glow_summary_load_none), color = GlowType.noteColor, style = GlowType.secondary)
        SummaryCopy.carriedOver(a.recoveryMinutes, a.ownRecoveryMinutes, a.workout.sportType)?.let {
            Text(it, color = GlowType.noteColor, style = GlowType.noteSmall)
        }
        drop?.let { Text(it, color = GlowType.noteColor, style = GlowType.noteSmall) }
    }
}

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
        drawRoundRect(Greys.Raised, Offset(0f, y), Size(size.width, thick), round)
        if (usual != null) {
            val from = x(usual.low)
            val to = maxOf(x(usual.high), from + 3.dp.toPx())
            drawRoundRect(LoadBand, Offset(from, y), Size(to - from, thick), round)
        }
        drawCircle(Color.White, r, Offset(x(load), size.height / 2))
    }
}

