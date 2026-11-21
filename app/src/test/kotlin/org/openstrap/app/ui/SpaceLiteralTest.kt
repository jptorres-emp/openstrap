package org.openstrap.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openstrap.app.ui.theme.Space

/**
 * The spacing scale lives in ui/theme/Scale.kt (`Space`): no other file writes a `.dp` literal inside `padding`,
 * `PaddingValues`, `spacedBy` or a `Spacer`. Eight steps (2, 4, 8, 12, 16, 20, 24, 32) and a card's own padding
 * (`Inset.card`). See docs/design/halo/components-audit.md, section 2.3.
 */
class SpaceLiteralTest {
    private val spacingCalls = Regex("padding|PaddingValues|spacedBy|Spacer")
    private val dpLiteral = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")

    /** Hairlines and borders: 0, half a dp and 1 dp are not spacing steps and stay literal. */
    private val hairlines = setOf("0", "0.5", "1")

    /** Files that are not UI layout: the path from ui/ (a folder ends in a slash) and why. */
    private val skippedFiles = mapOf(
        "theme/" to "the tokens themselves",
        "glow/draw/" to "the glow, light and chart drawing: geometry, not layout",
        "glow/live/LiveMind.kt" to "the mind map: drawn node by node, already allowlisted for ink",
        "glow/ShareCards.kt" to "the share pictures are bitmaps with their own sizes",
    )

    /**
     * Literals that must stay off the scale: the file's path from ui/, the literal, and why. Each is a measured
     * alignment (the line sits under a label column or clear of a map's chrome), not a gap between parts.
     */
    private val measured = mapOf<Pair<String, String>, String>(
        ("glow/DetailPicture.kt" to "46") to "clears the chart's top readout, which is 46 dp tall",
        ("glow/live/LiveMap.kt" to "64") to "fits the route below the top bar",
        ("glow/live/LiveMap.kt" to "76") to "fits the route clear of the side actions",
        ("glow/live/LiveMap.kt" to "54") to "the map button sits clear of the top bar",
        ("glow/live/LiveMap.kt" to "44") to "the map button sits above the panel's overlap",
        ("glow/RouteMapPage.kt" to "64") to "fits the route below the top bar, as on the live map",
        ("glow/RouteMapPage.kt" to "76") to "fits the route clear of the side actions, as on the live map",
        ("glow/RouteMapPage.kt" to "60") to "the Fit chip sits just under the 48 dp top bar",
        ("glow/RouteMapPage.kt" to "44") to "the map button sits above the panel's overlap, as on the live map",
        ("glow/RouteMapPage.kt" to "104") to "the title pill clears the close button and the GPX chip",
        ("glow/RouteMapPage.kt" to "56") to "the note lines up after the 56 dp Line label",
    )

    @Test fun `no spacing is written as a dp literal outside the theme`() {
        val found = UiSources().calls(spacingCalls).flatMap { (path, line, args) ->
            if (skippedFiles.keys.any { path.startsWith(it) }) return@flatMap emptyList()
            // A Spacer with a lambda is a canvas (drawWithCache, onSizeChanged), not a gap: its dp are drawing geometry.
            if ('{' in args) return@flatMap emptyList()
            dpLiteral.findAll(args).map { it.groupValues[1] }
                .filter { it !in hairlines && (path to it) !in measured }
                .map { "$path:$line  $it.dp" }.toList()
        }
        assertTrue(
            "Use Space.S2 to S32 (ui/theme/Scale.kt), or Inset.card for a card's own padding. A literal that must stay " +
                "off the scale goes in this test's `measured` list with its reason (docs/design/halo/components-audit.md 2.3):\n" +
                found.joinToString("\n"),
            found.isEmpty(),
        )
    }

    @Test fun `the scale is exactly the eight steps`() {
        assertEquals(listOf(2f, 4f, 8f, 12f, 16f, 20f, 24f, 32f), Space.Steps.map { it.value })
        val named = Space::class.java.declaredFields.map { it.name }.filter { Regex("S\\d+").matches(it) }
        assertEquals(listOf("S2", "S4", "S8", "S12", "S16", "S20", "S24", "S32").sorted(), named.sorted())
    }

    @Test fun `the allowlist keeps no entry that is gone`() {
        val used = UiSources().calls(spacingCalls).filter { '{' !in it.third }.flatMap { (path, _, args) ->
            dpLiteral.findAll(args).map { path to it.groupValues[1] }.toList()
        }.toSet()
        val stale = measured.keys.filter { it !in used }
        assertTrue("Remove from `measured`: $stale", stale.isEmpty())
        assertTrue("A literal in `hairlines` is not a step", hairlines.none { it in Space.Steps.map { s -> s.value.toInt().toString() } })
    }
}
