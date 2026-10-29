package org.openstrap.app.ui.glow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.openstrap.app.data.Healthspan
import org.openstrap.app.ui.theme.MetricColors
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.l10n.tr

/** The Healthspan card's orchid, used for the glow: warm enough to stay clear of Sleep's blue. */
private val Orchid = MetricColors.habitAge

/** The same recipe as the Heart rate and Activity cards, in orchid. */
internal val HealthspanGlow = GlowStyle.chart(Orchid)

/** "3 years younger", "1 year older" or "Right on your age" for an [estimate] against [age], in whole years. */
internal fun healthspanVerdict(age: Int, estimate: Float): String {
    val years = age - estimate.roundToInt()
    return when {
        years > 0 -> tr(
            "$years ${if (years == 1) "year" else "years"} younger",
            "$years ${if (years == 1) "ano" else "anos"} mais novo",
        )")} younger"
        years < 0 -> tr(
            "${-years} ${if (-years == 1) "year" else "years"} older",
            "${-years} ${if (-years == 1) "ano" else "anos"} mais velho",
        )os")} older"
        else -> tr("Right on your age", "Mesmo na tua idade")
    }
}

/** "Steady" at 1x, "Slower" below, "Faster" above; "Learning" until there's enough history. */
internal fun paceLabel(pace: Float?): String = when {
    pace == null -> tr("Learning", "A aprender")
    pace < 1f -> tr("Slower", "Mais lento")
    pace > 1f -> tr("Faster", "Mais rápido")
    else -> tr("Steady", "Estável")
}

/** "1x", "0.5x", "-1x". */
internal fun paceText(pace: Float): String = if (pace % 1f == 0f) "${pace.toInt()}x" else "${pace}x"

/** "takes off about 1 year", "adds about ½ year", "takes off a few months" or "no change". */
internal fun effectText(years: Float): String {
    val y = abs(years)
    val verb = if (years < 0) tr("takes off", "tira") else tr("adds", "junta")
    return when {
        y < 0.05f -> tr("no change", "sem mudança")
        y < 0.375f -> tr("$verb a few months", "$verb alguns meses")
        y < 0.75f -> tr("$verb about ½ year", "$verb cerca de ½ ano")
        else -> y.roundToInt().let { tr(
            "$verb about $it ${if (it == 1) "year" else "years"}",
            "$verb cerca de $it ${if (it == 1) "ano" else "anos"}",
        )", "anos")}" }
    }
}

/** The line under a habit: your value, the typical one it's scored against, and a target where there is one. */
internal fun termDetail(t: Healthspan.Term, age: Int): String {
    val v = t.value
    val ref = t.reference
    if (v == null) return if (t.habit == Healthspan.Habit.Vo2max) tr("No VO2 max from workouts yet, counted as typical", "Ainda sem VO2 máx dos treinos, contado como típico") else tr("Not enough data yet, counted as typical", "Ainda sem dados suficientes, contado como típico")
    return when (t.habit) {
        Healthspan.Habit.Strength -> {
            val m = v.roundToInt()
            (if (m == 0) tr("None recorded", "Nada registado") else tr("$m min a week", "$m min por semana")) + tr(", most people your age do none", ", a maioria das pessoas da tua idade não faz") +
                (if (m < Healthspan.STRENGTH_TARGET_MINUTES) tr(
                    ", aim for ${Healthspan.STRENGTH_TARGET_MINUTES}",
                    ", aponta para ${Healthspan.STRENGTH_TARGET_MINUTES}",
                ) else "")
        }
        Healthspan.Habit.Vo2max -> tr(
            "${v.roundToInt()} ml/kg/min, typical at $age: ${ref?.roundToInt()}",
            "${v.roundToInt()} ml/kg/min, típico aos $age: ${ref?.roundToInt()}",
        )
        Healthspan.Habit.Steps -> tr(
            "${"%,d".format(v.roundToInt())} a day, typical: " +
                "${"%,d".format(((ref ?: 0f) / 100).roundToInt() * 100)}",
            "${"%,d".format(v.roundToInt())} por dia, típico: " +
                "${"%,d".format(((ref ?: 0f) / 100).roundToInt() * 100)}",
        ) +
            (if (v < 8000f) tr(", aim for 8,000–10,000", ", aponta para 8 000–10 000") else "")
        Healthspan.Habit.Regularity -> tr("${v.roundToInt()}, typical: ${ref?.roundToInt()}", "${v.roundToInt()}, típico: ${ref?.roundToInt()}")
        Healthspan.Habit.RestingHr -> tr(
            "${v.roundToInt()} bpm asleep, typical: about ${ref?.roundToInt()}",
            "${v.roundToInt()} bpm a dormir, típico: cerca de ${ref?.roundToInt()}",
        )
        Healthspan.Habit.TimeAsleep -> v.roundToInt().let { m -> tr(
            "${m / 60} h ${m % 60} m, counts only below 6 h 30 m",
            "${m / 60} h ${m % 60} min, só conta abaixo de 6 h 30 min",
        ) }
    }
}

/** "Sunday 22 Nov" for local midnight [day] (epoch seconds), with no-break spaces so it never wraps inside the date. */
internal fun sundayText(day: Long): String = java.time.Instant.ofEpochSecond(day).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM")).replace(' ', '\u00A0')

/** What to add to the profile: "Add your birth year and sex in your profile to see your Habit Age." */
internal fun profileReminder(missing: List<String>): String = tr(
    "Add your ${missing.joinToString(" and ")} in your profile to see your Habit Age.",
    "Junta o teu ${missing.joinToString(" e ") { PROFILE_FIELDS_PT[it] ?: it }} no perfil para veres a tua Idade dos hábitos.",
)

/** The profile's fields as HealthspanEngine names them, in Portuguese. */
private val PROFILE_FIELDS_PT = mapOf("birth year" to "ano de nascimento", "sex" to "sexo")

/** The starting state's line under your age: when a first estimate is due, or what it's still waiting for. */
internal fun learningText(o: Healthspan.Outcome): String = when {
    // The date's line is kept whole, so "First estimate:" never ends up apart from its date.
    o.firstEstimate != null -> tr(
        "Starts at your age.\nFirst\u00A0estimate:\u00A0${sundayText(o.firstEstimate)}.",
        "Começa na tua idade.\nPrimeira\u00A0estimativa:\u00A0${sundayText(o.firstEstimate)}.",
    )
    o.withheld == Healthspan.Withheld.Learning -> tr(
        "Starts at your age. Wear the strap for 30 days for a first estimate.",
        "Começa na tua idade. Usa a strap durante 30 dias para a primeira estimativa.",
    )
    else -> tr("Starts at your age. ${o.withheld?.reason.orEmpty()}", "Começa na tua idade. ${o.withheld?.reason.orEmpty()}")
}

/** The local date of [epoch] (a local midnight). */
private fun dateOf(epoch: Long) = java.time.Instant.ofEpochSecond(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

/**
 * Habit Age, one design wherever it shows (Recovery's foot, Today when there's a new estimate, the top of its page):
 * an edge card lit from the left, since it's about the weeks behind you. The title with its caret when it opens
 * something and (i) at the right of the title row (spec 5.4), "Updated Sun 18 Oct" under it, the age in dots, one
 * status line against last week (the range until there's a last week, orchid only when the number moved) and the age
 * scale. The method, the pace of aging and what moves it are in its info sheet, behind the (i). Before the first
 * estimate it says when that is, with no number; with no birth year or sex it says what's needed and opens the profile
 * ([onOpenProfile]) instead of the Habit Age page ([onClick], null on the page itself). Nothing shows until it has been
 * worked out.
 */
@Composable
fun HabitAgeEdgeCard(state: Healthspan.Outcome?, onClick: (() -> Unit)?, onOpenProfile: (() -> Unit)?, modifier: Modifier = Modifier) {
    state ?: return
    val r = state.result
    val withheld = state.withheld
    val needsProfile = r == null && state.missing.isNotEmpty() && withheld?.profile == true
    var info by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    EdgeCard(
        // Without an estimate it's one line, so it's only as high as that line.
        Orchid, modifier.fillMaxWidth().let { if (r != null) it.gridSize(CardSize.Wide) else it }, kind = EdgeKind.Earlier,
        onClick = if (needsProfile) onOpenProfile else onClick,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Habit Age", "Idade dos hábitos"), Modifier.weight(1f))
            InfoButton(tr("About Habit Age", "Sobre a Idade dos hábitos"), { info = true })
        }
        r?.let {
            Text(
                org.openstrap.app.ui.dashboard.TabCopy.habitAgeUpdated(dateOf(it.asOf)), color = Color.White.copy(alpha = GlowType.SECONDARY_ALPHA),
                style = GlowType.secondary, maxLines = 1,
            )
        }
        when {
            r != null -> {
                val line = org.openstrap.app.ui.dashboard.TabCopy.habitAgeLine(r.estimate, state.lastWeek, r.low, r.high)
                EdgeValue(r.estimate.roundToInt().toString(), tr("years", "anos"), line.text, if (line.moved) Orchid else Color.White.copy(alpha = GlowType.SECONDARY_ALPHA))
                Spacer(Modifier.height(10.dp))
                AgeScale(r.age, r.low, r.high, r.estimate)
            }
            needsProfile -> EdgeValue(null, null, profileReminder(state.missing), waiting = false)
            state.firstEstimate != null -> EdgeValue(null, null, org.openstrap.app.ui.dashboard.TabCopy.firstEstimate(dateOf(state.firstEstimate)), waiting = false)
            else -> EdgeValue(null, null, withheld?.reason, waiting = false)
        }
    }
    if (info) {
        val here = r?.let { org.openstrap.app.ui.dashboard.InfoCopy.habitAgeBar(it.age - it.estimate.roundToInt()) }
        InfoSheet(
            org.openstrap.app.ui.dashboard.InfoCopy.HABIT_AGE, Orchid, here, here?.let { org.openstrap.app.ui.dashboard.InfoCopy.HERE },
            onDismiss = { info = false },
        )
    }
}

/**
 * The age scale (chart kit): a line of ages, seven years either side of yours, the estimate's range as an orchid band
 * with a white dot at the [estimate], and a hairline at your [age]. Labelled with the range under the band and you at
 * the hairline. An early estimate's range can run past the ends; it's drawn to them.
 */
@Composable
internal fun AgeScale(age: Int, low: Int, high: Int, estimate: Float) {
    val from = age - 7
    val to = age + 7
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val pad = 8.dp
        fun f(a: Float) = (a.coerceIn(from.toFloat(), to.toFloat()) - from) / (to - from)
        fun x(a: Float) = pad + (w - pad * 2) * f(a)
        val label = Color.White.copy(alpha = 0.6f)
        Column {
            // Your age over its hairline, the range under its band, as in the kit, so the two never overlap.
            Box(Modifier.fillMaxWidth().height(16.dp)) { ScaleLabel(org.openstrap.app.ui.dashboard.TabCopy.yourAge(age), x(age.toFloat()), w, label) }
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val y = size.height / 2
                fun px(a: Float) = x(a).toPx()
                drawLine(Color.White.copy(alpha = 0.15f), Offset(px(from.toFloat()), y), Offset(px(to.toFloat()), y), 6.dp.toPx(), StrokeCap.Round)
                drawLine(Orchid.copy(alpha = 0.5f), Offset(px(low.toFloat()), y), Offset(px(high.toFloat()), y), 12.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(px(age.toFloat()), 0f), Offset(px(age.toFloat()), size.height), 1.dp.toPx())
                drawCircle(Color.White, 7.dp.toPx(), Offset(px(estimate), y))
            }
            Box(Modifier.fillMaxWidth().height(16.dp)) { ScaleLabel(org.openstrap.app.ui.dashboard.TabCopy.ageRange(low, high), x((low + high) / 2f), w, label) }
        }
    }
}

/** A label centred on [at] across a scale [width] wide, kept inside it. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ScaleLabel(text: String, at: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp, color: Color) {
    // Centred on its point by aligning a share of the way across, as BiasAlignment does.
    val bias = ((at / width) * 2f - 1f).coerceIn(-1f, 1f)
    Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(androidx.compose.ui.BiasAlignment(bias, 0f)))
}
