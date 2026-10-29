package org.openstrap.app.data

import org.openstrap.app.l10n.tr

/**
 * Words for the body-signal alert, written to the FDA's General Wellness guidance as revised on 6 January 2026
 * (docs/algorithms/evidence-review.md, "Wording"). Never name a disease, never call a value abnormal, never show a
 * diagnostic threshold, and never prompt clinical action beyond saying professional evaluation "may be helpful".
 */
object BodySignalsCopy {
    val TITLE: String get() = tr("Some body signals are off your normal", "Alguns sinais do corpo estão fora do teu normal")
    val SHORT: String get() = tr("Some signals are off your normal", "Alguns sinais estão fora do teu normal")

    /** The tail every alert text ends with: likely everyday causes, a gentle suggestion, and the wellness disclaimer. */
    val CAUSES: String
        get() = tr(
            "This can happen with heavy training, alcohol, travel, poor sleep, or when your body is fighting something off.",
            "Isto pode acontecer com treino pesado, álcool, viagens, sono fraco, ou quando o corpo está a combater alguma coisa.",
        )
    val ADVICE: String
        get() = tr(
            "An easier day may help. If you feel unwell, checking in with a healthcare professional may be helpful.",
            "Um dia mais leve pode ajudar. Se não te sentires bem, falar com um profissional de saúde pode ser útil.",
        )
    val DISCLAIMER: String get() = tr("This is a wellness estimate, not a medical assessment.", "Isto é uma estimativa de bem-estar, não uma avaliação médica.")

    /**
     * The push text, as the evidence review gives it: "Several of your overnight signals (resting heart rate, skin
     * temperature) are outside your usual range for the second night. …". The bracket names the signals that are off;
     * "for the second night" is said only when they have been off for two nights or more, else "last night".
     */
    fun pushText(signals: List<String>, nights: Int): String {
        val names = signals.ifEmpty { listOf(BodySignals.Family.Cardiac.label) }.joinToString(", ")
        if (org.openstrap.app.l10n.L10n.portuguese) {
            // Every signal's name is feminine (frequência, temperatura), so one sentence fits them all.
            val lead = if (signals.size > 1) "Vários sinais da noite ($names) estiveram" else "A tua $names da noite esteve"
            val time = if (nights >= 2) "pela segunda noite seguida" else "na noite passada"
            return "$lead fora do intervalo habitual $time. $CAUSES $ADVICE $DISCLAIMER"
        }
        val lead = if (signals.size > 1) "Several of your overnight signals ($names) are" else "Your overnight $names is"
        val time = if (nights >= 2) "for the second night" else "last night"
        return "$lead outside your usual range $time. $CAUSES $ADVICE $DISCLAIMER"
    }
}
