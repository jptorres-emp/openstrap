package org.openstrap.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.openstrap.app.ui.glow.PageSpec
import org.openstrap.app.ui.theme.ControlSurface
import java.io.File

/**
 * The details' rules (docs/design/halo/detail-rules.md sections 1 to 3, boards HaloDetailRecovery, HaloDetailSteps and
 * HaloDetailHabitAge): one dotted number a detail, one frame with one header on every page, and controls in the design
 * system's own style. Scans the code under ui/, comments left out. Dotted charts in the pictures are guarded by
 * HaloRulesTest.
 */
class DetailRulesTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val uiRoot = File(root, "kotlin/org/openstrap/app/ui")

    /** Every Kotlin file under ui/, by its path from ui/, with comments blanked. */
    private val sources: Map<String, String> by lazy {
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }
            .associate { it.relativeTo(uiRoot).invariantSeparatorsPath to withoutComments(it.readText()) }
    }

    private fun lineOf(code: String, at: Int) = code.substring(0, at).count { it == '\n' } + 1

    /** Detail rules section 1.3 (headline) and 1.8 (figures row are plain text): the header's number is the only dotted one. */
    @Test fun `a detail's one dotted number is its header`() {
        // Spec 2.3: times, ranges, totals and second figures are text (HaloFigures), never a row of dotted figures.
        val dotted = Regex("""\b(FigureRow|HeroFigure|HeroReading|EdgeValue)\(""")
        val found = DETAILS.flatMap { path ->
            val code = sources.getValue(path)
            dotted.findAll(code).map { "$path:${lineOf(code, it.range.first)}  ${it.value}" }.toList()
        }
        if (found.isNotEmpty()) fail("One dotted number a detail, its header's (spec 2.3):\n" + found.joinToString("\n"))
        // The header draws its number once; the score gauge takes its place, they don't add one.
        DETAILS.forEach { path ->
            val n = Regex("""\bDotMatrixText\(""").findAll(sources.getValue(path)).count()
            assertTrue("$path draws $n dotted numbers, the template's header is the only one", n <= 1)
        }
    }

    @Test fun `Recovery draws the arc gauge and Sleep score the horizon arc, both centred`() {
        // Board HaloDetailRecovery: the thin gauge (DetailGauge) holds the score on the score details, and the arcs of the chart
        // kit stay for their cards: nothing that glows, and neither sweeps in from zero.
        assertTrue("Recovery holds its score in DetailGauge", Regex("""\bDetailGauge\((score|shown) / 100f""").containsMatchIn(sources.getValue("glow/ScoreDetail.kt")))
        assertTrue("Sleep score holds its score in DetailGauge", Regex("""\bDetailGauge\(score / 100f""").containsMatchIn(sources.getValue("glow/SleepDetail.kt")))
        val home = emptyMap<String, String>()
        val found = sources.flatMap { (path, code) ->
            Regex("""\b(GlowHorizon|GlowArcGauge|ScoreGauge|ArcGauge|HorizonArc)\(""").findAll(code)
                .filter { m -> val name = m.groupValues[1]; path != "glow/GlowCharts.kt" && home[name] != path }
                .map { "$path:${lineOf(code, it.range.first)}  ${it.value}" }.toList()
        }
        if (found.isNotEmpty()) fail("The arc gauge is Recovery's, the horizon arc the Sleep score's, nothing else:\n" + found.joinToString("\n"))
        val charts = sources.getValue("glow/GlowCharts.kt")
        listOf("fun ArcGauge(", "fun HorizonArc(").forEach { head ->
            val body = charts.substringAfter(head).substringBefore("\n}\n")
            assertTrue("$head has round caps and no halo", "StrokeCap.Round" in body && "drawCircle" !in body && "Brush" !in body)
            assertTrue("$head fills from the value shown", "rememberGaugeProgress(" in body)
        }
        val progress = charts.substringAfter("fun rememberGaugeProgress(").substringBefore("\n}\n")
        assertTrue("a gauge starts at its value, not at zero", "Animatable(target)" in progress)
        val template = sources.getValue("glow/GlowMetricDetail.kt").substringAfter("fun DetailTemplate(").substringBefore("\n}\n")
        assertTrue("the gauge sits left of the status, centred on it", Regex("""if \(gauge != null\) Row\(\s*[^\n]*\s*verticalAlignment = Alignment.CenterVertically""").containsMatchIn(template))
    }

    /** Detail rules section 1.2: the header is back, title, (i), in a 96 / 1fr / 96 grid. */
    @Test fun `every detail's header ends in the (i)`() {
        // Spec 2.5 and 6.1: the (i) at the right of every detail's header, nothing else there.
        val nav = sources.getValue("glow/GlowMetricDetail.kt").substringAfter("fun DetailTemplate(").substringBefore("\n}\n")
        // HaloDetailRecovery board: the (i) is a 44 dp button over the wash, not the card's small InfoButton.
        assertTrue("the detail header's trailing is the (i)", Regex("""HaloPageHeader\([^{]*HeaderVariant\.Detail[^{]*\)\s*\{\s*HaloIconButton\(\s*AppIcons\.Info\b""").containsMatchIn(nav))
        DETAILS.forEach { path ->
            val code = sources.getValue(path)
            assertTrue("$path builds its own header", !Regex("""\bHaloPageHeader\(""").containsMatchIn(code) || path == "glow/GlowMetricDetail.kt")
        }
    }

    /** Detail rules section 1.2 and settings rules B1: back is the 44 squircle from PageHeader, never a page's own arrow. */
    @Test fun `every page has the one page header`() {
        // Spec 3.4: the round back button and the page title, from PageHeader. No bare arrow of a page's own.
        val found = sources.filterKeys { it != "glow/HaloPage.kt" && it !in HEADER_NOT_MIGRATED }.flatMap { (path, code) ->
            Regex("""\bAppIcons\.ArrowLeft\b""").findAll(code).map { "$path:${lineOf(code, it.range.first)}" }.toList()
        }
        if (found.isNotEmpty()) fail("A page's way back is PageHeader's back button (spec 3.4):\n" + found.joinToString("\n"))
        assertEquals(44.dp, PageSpec.HeaderButton)
        assertEquals(Color(0xFF282828), ControlSurface)
    }

    /** Detail rules section 3 and workout rules section 5: no coloured switch in a detail, no type of its own. */
    @Test fun `details use the design system's controls and type`() {
        // Audit step 7: no coloured switch in a detail header, and no type of a detail's own (spec 2.3, 6.2).
        val found = (DETAILS + "glow/InfoSheet.kt").flatMap { path ->
            val code = sources.getValue(path)
            Regex("""\bGlowSwitch\(|\bSwitch\(|\bfontSize\s*=|MaterialTheme\.typography\.(?!labelLarge)""").findAll(code)
                .map { "$path:${lineOf(code, it.range.first)}  ${it.value}" }.toList()
        }
        if (found.isNotEmpty()) fail("Details take their controls and type from the design system:\n" + found.joinToString("\n"))
    }

    private companion object {
        /** The detail screens in the metric detail template (spec 6.1). */
        val DETAILS = listOf("glow/GlowMetricDetail.kt", "glow/ScoreDetail.kt", "glow/SleepDetail.kt", "glow/ActivityDetail.kt")

        /** Workout pages that still draw their own back arrow, until step 9 moves them to PageHeader. */
        // The search page puts its back arrow beside the search field in place of a title, so it sets its own HaloIconButton.
        val HEADER_NOT_MIGRATED = setOf("glow/ChooseScreen.kt")
    }
}

/** [src] with its block and line comments blanked, line breaks kept so line numbers still match. */
private fun withoutComments(src: String): String =
    src.replace(Regex("""/\*[\s\S]*?\*/""")) { m -> m.value.replace(Regex("[^\n]"), " ") }
        .replace(Regex("""(?m)^\s*//.*$"""), "")
