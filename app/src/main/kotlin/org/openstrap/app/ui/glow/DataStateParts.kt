package org.openstrap.app.ui.glow

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.dashboard.PARTIAL_DAY_INFO
import org.openstrap.app.ui.dashboard.PARTIAL_DAY_ROWS
import org.openstrap.app.ui.dashboard.PairStep
import org.openstrap.app.ui.dashboard.Wear
import org.openstrap.app.ui.dashboard.partialLine
import org.openstrap.app.ui.theme.MetricColors

/*
 * The pieces of the data states (spec 4.3): the pairing stage on Today's hero, and the partial day sheet a card's
 * "Partial day" tag opens.
 */

/**
 * The pairing stage (E3): the strap, lit and still, a line across to the phone, and the phone. Only the line changes
 * with the [step], always a solid 1 dp hairline (spec 2.7): faint while it waits, brighter once a strap is in reach,
 * pulsing while it connects, in the strap's light when connected, amber when it couldn't.
 */
@Composable
internal fun PairingStage(step: PairStep, modifier: Modifier = Modifier) {
    val pulse = if (step == PairStep.Connecting) {
        val t = rememberInfiniteTransition(label = "pairing line")
        t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pairing pulse").value
    } else 1f
    val line = when (step) {
        PairStep.Ready -> Color.White.copy(alpha = 0.22f)
        PairStep.Searching -> Color.White.copy(alpha = 0.45f)
        PairStep.Found -> StrapLight.copy(alpha = 0.6f)
        PairStep.Connecting -> StrapLight.copy(alpha = pulse)
        PairStep.Connected -> StrapLight
        PairStep.Failed, PairStep.KeyRejected -> MetricColors.warn.copy(alpha = 0.75f)
    }
    Row(modifier.fillMaxWidth().height(StageHeight), verticalAlignment = Alignment.CenterVertically) {
        StrapDrawing(StrapLight, lit = true, syncing = false, Modifier.size(width = StageHeight * 0.8f, height = StageHeight))
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp)) {
            val y = size.height / 2
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), StrokeCap.Round)
        }
        PhoneDrawing(Modifier.size(width = 30.dp, height = 52.dp))
    }
}

/** The pairing stage's height: the strap drawing's, which sets the hero's. */
private val StageHeight = 92.dp

/** This phone, drawn in line like the strap: its outline and the speaker slot. */
@Composable
private fun PhoneDrawing(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            Color.White.copy(alpha = 0.8f), Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke),
            CornerRadius(6.dp.toPx()), style = Stroke(stroke),
        )
        val slot = size.width * 0.3f
        drawLine(
            Color.White.copy(alpha = 0.45f), Offset((size.width - slot) / 2, 5.dp.toPx()), Offset((size.width + slot) / 2, 5.dp.toPx()),
            1.2.dp.toPx(), StrokeCap.Round,
        )
    }
}

/** What a "Partial day" tag opens: what it means, how much wear the figures cover, what it lowers and the rule. */
@Composable
internal fun PartialDaySheet(wear: Wear, isToday: Boolean, onDismiss: () -> Unit) =
    InfoSheet(PARTIAL_DAY_INFO, EdgeNeutral, null, partialLine(wear, isToday), onDismiss = onDismiss, rows = PARTIAL_DAY_ROWS)
