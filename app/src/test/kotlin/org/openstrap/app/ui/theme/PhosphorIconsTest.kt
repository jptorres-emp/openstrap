package org.openstrap.app.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhosphorIconsTest {
    /** Every icon getter on the object, found by reflection so a new icon is covered without listing it here. */
    private val getters = PhosphorIcons::class.java.declaredMethods
        .filter { it.parameterCount == 0 && it.returnType == ImageVector::class.java && it.name.startsWith("get") }

    @Test fun `every icon builds on the 24 grid with drawable paths`() {
        assertTrue("found only ${getters.size} icons", getters.size > 90)
        for (g in getters) {
            val icon = g.invoke(PhosphorIcons) as ImageVector
            val name = g.name.removePrefix("get")
            assertEquals(name, 24f, icon.viewportWidth)
            assertEquals(name, 24f, icon.viewportHeight)
            val paths = (0 until icon.root.size).map { icon.root[it] }.filterIsInstance<VectorPath>()
            assertTrue("$name has no paths", paths.isNotEmpty())
            for (p in paths) assertTrue("$name has an empty path", p.pathData.isNotEmpty())
        }
    }

    @Test fun `selected variants draw bolder than their outlines`() {
        fun width(icon: ImageVector) = (icon.root[0] as VectorPath).strokeLineWidth
        for ((plain, selected) in listOf(
            PhosphorIcons.HouseSimple to PhosphorIcons.HouseSimpleFill,
            PhosphorIcons.Plant to PhosphorIcons.PlantFill,
            PhosphorIcons.Moon to PhosphorIcons.MoonFill,
            PhosphorIcons.SneakerMove to PhosphorIcons.SneakerMoveFill,
        )) assertTrue(selected.name, width(selected) > width(plain))
    }

    @Test fun `play pause and stop are filled`() {
        for (icon in listOf(PhosphorIcons.PlayFill, PhosphorIcons.PauseFill, PhosphorIcons.StopFill)) {
            assertTrue(icon.name, (icon.root[0] as VectorPath).fill != null)
        }
    }
}
