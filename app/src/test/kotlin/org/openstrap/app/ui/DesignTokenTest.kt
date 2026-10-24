package org.openstrap.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.openstrap.app.ui.glow.DotSize
import androidx.compose.ui.graphics.luminance
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowPalette
import org.openstrap.app.ui.glow.GlowStyle
import org.openstrap.app.ui.theme.MetricColors
import java.io.File

/**
 * The design system's tokens (spec 2.2 to 2.4, 8.2): the values match the spec, and the UI takes them from their one
 * place. Scans the code under ui/, comments and strings left out, and fails with file and line.
 */
class DesignTokenTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val uiRoot = File(root, "kotlin/org/openstrap/app/ui")

    /** Every Kotlin file under ui/, by its path from ui/, as code only. */
    private val sources: Map<String, List<String>> by lazy {
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }
            .associate { it.relativeTo(uiRoot).invariantSeparatorsPath to codeOnly(it.readText()).lines() }
    }

    /** Each line that matches [pattern], as "path:line  text", skipping the files in [except]. */
    private fun find(pattern: Regex, except: Set<String> = emptySet()): List<String> = sources.flatMap { (path, lines) ->
        if (path in except) emptyList()
        else lines.mapIndexedNotNull { i, line -> if (pattern.containsMatchIn(line)) "$path:${i + 1}  ${line.trim()}" else null }
    }

    @Test fun `the tokens hold the spec's values`() {
        assertEquals(listOf(2.4f, 3f, 3.6f, 4.5f, 6f, 9f), DotSize.entries.map { it.dot.value })
        DotSize.entries.forEach { assertEquals(it.name, it.dot.value * 0.38f, it.gap.value, 1e-4f) }
        assertEquals(28.dp, GlowLayout.CardCorner)
        assertEquals(16.dp, GlowLayout.TileCorner)
        assertEquals(GlowLayout.CardCorner, GlowStyle(Color.White).radius)
        val p = GlowLayout.CardPadding
        assertEquals(
            List(4) { 20.dp },
            listOf(p.calculateLeftPadding(LayoutDirection.Ltr), p.calculateTopPadding(), p.calculateRightPadding(LayoutDirection.Ltr), p.calculateBottomPadding()),
        )
        assertEquals(14.dp, GlowLayout.RowPadding)
        assertEquals(Color(0xFFF66C28), MetricColors.charge)
        assertEquals(Color(0xFF8B7BEA), MetricColors.evening)
    }

    @Test fun `glow bodies keep white text readable, and are no darker than they need be`() {
        assertEquals(0.18f, GlowPalette.MID_MAX_LUMINANCE, 1e-6f)
        assertTrue(GlowPalette.TONE_MAX_LUMINANCE < GlowPalette.MID_MAX_LUMINANCE)
        // White on the body: at least 4.5:1 (spec 2.1), for every metric's ink.
        val inks = listOf(
            MetricColors.readiness, MetricColors.charge, MetricColors.sleepDeep, MetricColors.heart, MetricColors.steps,
            MetricColors.hrv, MetricColors.spo2, MetricColors.stress, MetricColors.temperature, MetricColors.load,
            MetricColors.habitAge, MetricColors.evening,
        )
        inks.forEach { ink ->
            val body = GlowPalette(GlowStyle(ink)).mid.luminance()
            assertTrue("$ink body luminance $body", body <= GlowPalette.MID_MAX_LUMINANCE + 1e-3f)
            assertTrue("$ink white on body ${1.05f / (body + 0.05f)}", 1.05f / (body + 0.05f) >= 4.5f)
        }
        assertEquals(0.08f, GlowLayout.HairlineAlpha, 1e-6f)
        assertEquals(0.06f, GlowLayout.DividerAlpha, 1e-6f)
    }

    @Test fun `no metric has a second colour`() {
        val found = find(Regex("""\b(chargeScale|chargeAt|chargeDeep|readinessDeep)\b|MetricColors\.bad\b|\bval bad\b"""))
        if (found.isNotEmpty()) fail("Charge is one Ember, Recovery one teal, and bad is gone (spec 2.2):\n" + found.joinToString("\n"))
    }

    @Test fun `every dot-matrix number takes one of the six dot sizes`() {
        assertTrue("found no source under ${uiRoot.absolutePath}", sources.size > 50)
        val found = mutableListOf<String>()
        sources.forEach { (path, lines) ->
            val code = lines.joinToString("\n")
            Regex("""\bDotMatrixText\(""").findAll(code).forEach { m ->
                val args = balanced(code, m.range.last)
                val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                // The declaration takes a DotSize; calls pass one, never a size or gap of their own.
                if (Regex("""\bdot\s*=\s*[^,]*\d\.dp|\bgap\s*=""").containsMatchIn(args)) found += "$path:$line  DotMatrixText(${args.trim()})"
            }
        }
        if (found.isNotEmpty()) fail("Dot sizes come from DotSize (spec 2.3):\n" + found.joinToString("\n"))
    }

    /** Card-sized corners: anything this round is a card, a panel or a sheet, and takes [GlowLayout.CardShape]. */
    @Test fun `cards take their corners from GlowLayout`() {
        val found = mutableListOf<String>()
        sources.forEach { (path, lines) ->
            if (path == "glow/GlowLayout.kt") return@forEach
            val code = lines.joinToString("\n")
            Regex("""\b(RoundedCornerShape|CornerRadius)\(""").findAll(code).forEach { m ->
                val args = balanced(code, m.range.last)
                val round = Regex("""(\d+(?:\.\d+)?)\.dp""").findAll(args).any { it.groupValues[1].toFloat() >= CARD_SIZED }
                val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                if (round && (path to line) !in cornerAllowed) found += "$path:$line  ${m.value}${args.trim()})"
            }
        }
        // A style's own corner (GlowStyle(radius = 36.dp)); a circle's radius in a draw call is drawn in px.
        found += find(Regex("""\bradius\s*=\s*\d+(\.\d+)?\.dp\b(?!\.toPx)"""))
        if (found.isNotEmpty()) fail("Card corners are GlowLayout.CardCorner (spec 2.4):\n" + found.joinToString("\n"))
        val stale = cornerAllowed.filter { (path, line) ->
            sources[path]?.getOrNull(line - 1)?.let { Regex("""(RoundedCornerShape|CornerRadius)\(""").containsMatchIn(it) } != true
        }
        assertTrue("corner allowlist entries that no longer match: $stale", stale.isEmpty())
    }

    @Test fun `type sizes come from the type roles`() {
        val counts = sources.mapValues { (_, lines) -> lines.count { Regex("""\bfontSize\s*=""").containsMatchIn(it) } }
            .filterValues { it > 0 }
        val over = counts.filter { (path, n) -> path !in typeHomes && n > (typeNotMigrated[path] ?: 0) }
        if (over.isNotEmpty()) {
            fail(
                "Text takes its size from a GlowType role (spec 2.3), not a fontSize of its own:\n" +
                    over.keys.flatMap { p -> find(Regex("""\bfontSize\s*=""")).filter { it.startsWith("$p:") } }.joinToString("\n"),
            )
        }
        // The allowlist only shrinks: a migrated file comes off it.
        val stale = typeNotMigrated.filter { (path, n) -> (counts[path] ?: 0) < n }
        assertTrue("type allowlist is higher than the code, lower it: $stale (now ${stale.keys.associateWith { counts[it] ?: 0 }})", stale.isEmpty())
    }

    private companion object {
        /** From this radius up a corner is a card's. Tiles, chips, bars and pills stay under it. */
        const val CARD_SIZED = 20f

        /** Card-sized corners on things that aren't cards, by file and line, each with why. */
        val cornerAllowed = setOf(
            // The floating tab bar and its selected capsule: a pill, redone with the tab bar (step 10).
            "components/Insights.kt" to 118, "components/Insights.kt" to 122, "components/Insights.kt" to 133,
            "components/Insights.kt" to 134, "components/Insights.kt" to 140,
            // The page buttons: fully round in step 8 (spec 2.4, pills and buttons).
            "glow/PageParts.kt" to 49,
        )

        /** Where type sizes are defined. */
        val typeHomes = setOf("glow/GlowType.kt", "theme/Theme.kt")

        /**
         * Files still setting their own type sizes, with how many, until their step moves them to the GlowType roles
         * (audit section 4). The list shrinks with each step and never grows.
         */
        val typeNotMigrated = mapOf(
            "components/Charts.kt" to 2,
            "dashboard/SleepPanels.kt" to 1,
            "device/DeviceScreen.kt" to 2,
            "firmware/FirmwareScreen.kt" to 3,
            "glow/AllMetricsScreen.kt" to 1,
            "glow/GlowActivityScreen.kt" to 2,
            "glow/GlowCharts.kt" to 5,
            "glow/GlowMetricDetail.kt" to 10,
            "glow/GlowWorkoutMode.kt" to 4,
            "glow/GlowWorkoutSummary.kt" to 1,
            "glow/InfoSheet.kt" to 4,
            "glow/TabParts.kt" to 1,
            "glow/TodayCards.kt" to 1,
        )
    }
}

/** The text between the `(` at [open] and its matching `)`. */
private fun balanced(code: String, open: Int): String {
    var depth = 0
    for (i in open until code.length) {
        when (code[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) return code.substring(open + 1, i)
        }
    }
    return code.substring(open + 1)
}

/**
 * [src] with comments and the contents of string and char literals blanked, line breaks kept, so a scan sees only
 * code and line numbers still match. Template code inside strings is blanked too.
 */
private fun codeOnly(src: String): String {
    val out = StringBuilder(src.length)
    var i = 0
    fun blank(c: Char) = out.append(if (c == '\n') '\n' else ' ')
    while (i < src.length) {
        val c = src[i]
        when {
            src.startsWith("//", i) -> while (i < src.length && src[i] != '\n') { blank(src[i]); i++ }
            src.startsWith("/*", i) -> {
                var depth = 0
                do {
                    when {
                        src.startsWith("/*", i) -> { depth++; blank(src[i]); blank(src[i + 1]); i += 2 }
                        src.startsWith("*/", i) -> { depth--; blank(src[i]); blank(src[i + 1]); i += 2 }
                        else -> { blank(src[i]); i++ }
                    }
                } while (depth > 0 && i < src.length)
            }
            src.startsWith("\"\"\"", i) -> {
                out.append("\"\"\""); i += 3
                while (i < src.length && !src.startsWith("\"\"\"", i)) { blank(src[i]); i++ }
                out.append("\"\"\""); i += 3
            }
            c == '"' || c == '\'' -> {
                out.append(c); i++
                while (i < src.length && src[i] != c && src[i] != '\n') {
                    if (src[i] == '\\') { blank(src[i]); i++ }
                    if (i < src.length) { blank(src[i]); i++ }
                }
                if (i < src.length) { out.append(src[i]); i++ }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}
