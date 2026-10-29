package org.openstrap.app.l10n

import android.content.Context
import java.util.Locale

/** The language chosen on the You page: the phone's own, English, or Portuguese as it's spoken in Portugal. */
enum class AppLanguage(val key: String) {
    System("system"),
    English("en"),
    Portuguese("pt"),
    ;

    companion object {
        /** The choice saved as [key], the phone's own for anything else. */
        fun of(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: System
    }
}

/**
 * The language the app speaks. Every word is written in English and in Portuguese side by side ([tr]), so the two
 * can't drift apart; this says which one shows. It's set as the app starts, from the saved choice ([load]), and when
 * the choice changes ([choose]), after which the screen is drawn again. Dates and numbers follow it too: the default
 * locale is Portugal's in Portuguese and the UK's in English, or the phone's own when the app follows the phone.
 */
object L10n {
    /** True while the app speaks Portuguese (Portugal). */
    @Volatile
    var portuguese: Boolean = false
        private set

    /** The phone's own locale, as it was before the app set one. */
    private var device: Locale? = null

    private const val PREFS = "language"
    private const val KEY = "app_language"

    /** Whether [choice] speaks Portuguese on a phone set to [device]. */
    fun speaksPortuguese(choice: AppLanguage, device: Locale): Boolean = when (choice) {
        AppLanguage.System -> device.language == "pt"
        AppLanguage.English -> false
        AppLanguage.Portuguese -> true
    }

    /** The saved choice; the phone's own until one is made. */
    fun chosen(context: Context): AppLanguage =
        AppLanguage.of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    /** Speaks the saved choice; called as the app starts, before anything is drawn. */
    fun load(context: Context) = apply(chosen(context))

    /** Saves [choice] and speaks it from now on. The caller draws the screen again. */
    fun choose(context: Context, choice: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.key).apply()
        apply(choice)
    }

    private fun apply(choice: AppLanguage) {
        val phone = device ?: Locale.getDefault().also { device = it }
        portuguese = speaksPortuguese(choice, phone)
        Locale.setDefault(
            when (choice) {
                AppLanguage.System -> phone
                AppLanguage.English -> if (phone.language == "en") phone else Locale.UK
                AppLanguage.Portuguese -> Locale.forLanguageTag("pt-PT")
            },
        )
    }

    /** For tests: speak Portuguese or English without a saved choice, dates and numbers left as they are. */
    fun speak(portuguese: Boolean) {
        this.portuguese = portuguese
    }
}

/** [en] in English, [pt] in Portuguese (Portugal): whichever the app speaks now ([L10n]). */
fun tr(en: String, pt: String): String = if (L10n.portuguese) pt else en
