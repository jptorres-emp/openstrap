package org.openstrap.app.l10n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The launcher shows the phone's language, not the app's setting: the shortcuts, the tile and the widget picker's names,
 * descriptions and previews are Android resources. Every English one has its Portuguese, the app's name aside.
 */
class ResourcesTest {
    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun names(dir: String): Set<String> =
        Regex("<string name=\"([a-z_]+)\"").findAll(File(root, "res/$dir/strings.xml").readText()).map { it.groupValues[1] }.toSet()

    @Test fun `every launcher string has its Portuguese`() {
        assertEquals(names("values") - "app_name", names("values-pt"))
    }
}
