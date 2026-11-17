package org.openstrap.app.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.openstrap.app.testing.FileStrings
import java.io.File

/**
 * The text rules (docs/design/halo/text-rules.md, board HaloTextRules) that a string's own text can show, checked over
 * every English string in res/values. CopyPunctuationTest already bans the interpunct, semicolon and em dash.
 *
 * - T4: no ellipsis, no doubled full stop, no exclamation mark, no "Tap to" (say what the tap does).
 * - T5: counts say "5 of 7", never "5/7".
 * - T6: "Done", never "OK". The app doesn't name itself in the files that hold statuses, notes and score lines.
 * - T2: figure labels fit a third of a row (12 characters) and end with nothing.
 */
class TextRulesTest {
    private val values = FileStrings(folders = listOf(FileStrings.MAIN_VALUES))

    /** Every string and plural item, by name. */
    private val texts: List<Pair<String, String>> =
        values.strings.map { (name, text) -> name to text } +
            values.plurals.flatMap { (name, items) -> items.map { (quantity, text) -> "$name/$quantity" to text } }

    private fun failIfAny(rule: String, found: List<String>) {
        if (found.isNotEmpty()) fail("$rule (docs/design/halo/text-rules.md):\n" + found.joinToString("\n"))
    }

    @Test fun `no ellipsis, doubled stop, exclamation mark or tap instruction`() {
        assertTrue("found no strings in res/values", texts.size > 1000)
        val found = texts.filter { (_, t) ->
            '…' in t || "..." in t || DOUBLE_STOP.containsMatchIn(t) || '!' in t || t.contains("Tap to", ignoreCase = true)
        }.filter { (n, _) -> n !in ALLOWED }.map { (n, t) -> "$n  $t" }
        failIfAny("T4: no ellipsis, doubled stop, exclamation mark or \"Tap to\"", found)
    }

    @Test fun `counts say of, never a slash`() {
        val found = texts.filter { (n, t) -> n !in ALLOWED && SLASH_COUNT.containsMatchIn(t) }.map { (n, t) -> "$n  $t" }
        failIfAny("T5: counts say \"5 of 7\", never \"5/7\"", found)
    }

    @Test fun `buttons say Done, never OK`() {
        val found = texts.filter { (n, t) -> n !in ALLOWED && t.trim() == "OK" }.map { (n, _) -> n }
        failIfAny("T6: \"Done\", never \"OK\"", found)
    }

    @Test fun `the app doesn't name itself in statuses, notes and score lines`() {
        val found = STATUS_FILES.flatMap { file ->
            names(file).mapNotNull { n -> values.strings[n]?.takeIf { APP_NAME.containsMatchIn(it) }?.let { "$file: $n  $it" } }
        }
        failIfAny("T6: no \"unstrap\" in a status, note or score line", found)
    }

    @Test fun `figure labels fit a third of a row and end with nothing`() {
        val found = texts.filter { (n, _) -> FIGURE_LABEL.containsMatchIn(n) }
            .filter { (_, t) -> t.length > FIGURE_LABEL_BUDGET || t.trimEnd().endsWith('.') }
            .map { (n, t) -> "$n  \"$t\" (${t.length})" }
        failIfAny("T2: a figure label is at most $FIGURE_LABEL_BUDGET characters, with no full stop", found)
    }

    /** The string names declared in one res/values file. */
    private fun names(file: String): List<String> {
        val f = File(FileStrings.MAIN_VALUES, file)
        assertTrue("missing $f", f.isFile)
        return NAME.findAll(f.readText()).map { it.groupValues[1] }.toList()
    }

    private companion object {
        val DOUBLE_STOP = Regex("""(?<!\.)\.\.(?!\.)""")
        val SLASH_COUNT = Regex("""\d\s*/\s*\d""")
        val APP_NAME = Regex("""\bunstrap\b""", RegexOption.IGNORE_CASE)
        /** The labels under a live figure, from workout-rules.md's list. */
        val FIGURE_LABEL = Regex("""^workout_live_figure_""")
        val NAME = Regex("""<(?:string|plurals) name="([^"]+)"""")
        const val FIGURE_LABEL_BUDGET = 12

        /** Strings a rule would catch that are right as they are, each with why. Prefer fixing the string. */
        val ALLOWED = mapOf(
            "workout_start_preset_forty_twenty" to "40 s of work, 20 s of rest: a HIIT preset's name, not a count",
            "workout_start_tap_to_type" to "Under a goal's number, says the number itself can be typed into: there's no button to name",
            "core_checkin_ok" to "The middle of the check-in's five-point scale (how you feel), not a button",
        ).keys

        /** The files whose strings are statuses, notes and score lines: where the app never names itself. */
        val STATUS_FILES = listOf(
            "strings_core_today.xml", "strings_glow_today.xml", "strings_day_today.xml", "strings_core_tabs.xml",
            "strings_glow_metrics.xml", "strings_glow_recovery.xml", "strings_glow_sleep.xml", "strings_glow_activity.xml",
            "strings_core_recovery.xml", "strings_day_sleep.xml",
        )
    }
}
