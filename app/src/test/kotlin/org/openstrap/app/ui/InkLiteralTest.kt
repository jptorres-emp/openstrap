package org.openstrap.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The ink ladder lives in ui/theme/Ink.kt and the data colours in ui/theme/Theme.kt: no other file writes a
 * `Color.White.copy(alpha = ...)`, a black scrim alpha, a light grey literal or a coloured literal. A file can sit in
 * the allowlists below only with its reason, and only for drawing (a picture, not text, an icon or a rule).
 */
class InkLiteralTest {
    private val root: File = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
    private val alphaInk = Regex("""Color\.(White|Black)\.copy\(\s*alpha\s*=""")
    private val literal = Regex("""Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})\)""")

    /** White and black alphas that are glow and light rendering, not an ink: the file's path from ui/ and why. */
    private val drawingAlphas = mapOf(
        "glow/draw/" to "the glow, light and chart drawing specs: gloss stops, card edges and bar dims are rendering maths",
        "glow/live/LiveMind.kt" to "the mind clock's veil: a black scaled by an animated fade",
        "glow/ShareCards.kt" to "the share picture's black gradients over the user's photo (bitmap art, not UI)",
    )

    /** Coloured literals that are not a metric's, a sport's or a surface's colour: none today. */
    private val drawingColours = emptyMap<String, String>()

    private fun hits(allow: Map<String, String>, test: (String) -> Boolean): List<String> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val path = f.invariantSeparatorsPath
                !path.contains("/ui/theme/") && allow.keys.none { path.contains("/ui/$it") }
            }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (test(line)) "${f.name}:${i + 1}  ${line.trim()}" else null } }
            .toList()

    private fun hex(m: MatchResult) = m.groupValues.drop(1).map { it.toInt(16) }

    @Test fun `no white or black alpha is written outside the theme`() {
        val found = hits(drawingAlphas) { alphaInk.containsMatchIn(it) }
        assertTrue("Use Ink.X (ui/theme/Ink.kt), or Ink.Primary for a drawing only:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun `no light grey literal is written outside the theme`() {
        val found = hits(emptyMap()) { line ->
            literal.findAll(line).any { m ->
                val (_, r, g, b) = hex(m)
                r == g && g == b && r in 0x60..0xFE
            }
        }
        assertTrue("Write light greys in ui/theme/Ink.kt only:\n" + found.joinToString("\n"), found.isEmpty())
    }

    @Test fun `no coloured literal is written outside the theme`() {
        val found = hits(drawingColours) { line ->
            literal.findAll(line).any { m ->
                val (a, r, g, b) = hex(m)
                a != 0 && !(r == g && g == b)
            }
        }
        assertTrue("Name the colour in ui/theme/Theme.kt (MetricColors, SportColors):\n" + found.joinToString("\n"), found.isEmpty())
    }
}
