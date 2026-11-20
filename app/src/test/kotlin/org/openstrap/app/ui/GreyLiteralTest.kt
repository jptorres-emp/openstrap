package org.openstrap.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The grey ladder lives in ui/theme/Surfaces.kt: no other file writes a dark neutral grey as `Color(0xFFxxxxxx)`. */
class GreyLiteralTest {
    private val root: File = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).first { it.isDirectory }
    private val grey = Regex("""Color\(0x[Ff][Ff]([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})""")

    @Test fun `no dark neutral grey is written outside the theme`() {
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { !it.invariantSeparatorsPath.contains("/ui/theme/") }
            .flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val hit = grey.findAll(line).any { m ->
                        val (r, g, b) = m.destructured
                        r.equals(g, true) && g.equals(b, true) && r.toInt(16) in 0x01..0x5F
                    }
                    if (hit) "${f.name}:${i + 1}  ${line.trim()}" else null
                }
            }.toList()
        assertTrue("Write greys in ui/theme/Surfaces.kt (Greys) only:\n" + found.joinToString("\n"), found.isEmpty())
    }
}
