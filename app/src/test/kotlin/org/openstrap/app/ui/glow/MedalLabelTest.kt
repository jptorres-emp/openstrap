package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.Rewards
import org.openstrap.app.testing.ComposeRules
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A token's number or distance ("1000", "14/14", "HM") keeps inside its face at every size it's drawn, the small cells,
 * a sheet's 96 and a moment's 160 alike, rather than running into the edge (boards HaloGoalsBests, HaloBestMoment).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedalLabelTest {
    @get:Rule val rules = ComposeRules()

    /** The medals with the longest labels in each shape, each label once. */
    private val looks: List<Pair<String, MedalLook>> = listOf(
        "1000" to badgeLook(badge("km_1000"), "1000"),
        "500" to badgeLook(badge("workouts_500"), "500"),
        "1M" to badgeLook(badge("steps_1m"), "1M"),
        "10K" to badgeLook(badge("first_10k"), "10K"),
        "52" to badgeLook(badge("goal_52"), "52"),
        "14/14" to badgeLook(badge("goal_first"), "14/14"),
        "HM" to bestLook(Rewards.Kind.fastest(HALF_MARATHON)),
    )

    private fun badge(id: String) = Rewards.BADGES.first { it.id == id }

    @Test fun `labels keep inside the face on a cell's token`() = assertInside(72.dp)

    @Test fun `labels keep inside the face on a sheet's token`() = assertInside(96.dp)

    @Test fun `labels keep inside the face on a moment's token`() = assertInside(160.dp)

    private fun assertInside(diameter: Dp) {
        rules.compose.setContent {
            Column {
                looks.forEachIndexed { i, (_, look) -> Box(Modifier.testTag("medal$i")) { Medal(look, diameter) } }
            }
        }
        val out = looks.mapIndexedNotNull { i, (label, look) ->
            val medal = unclipped(rules.compose.onNodeWithTag("medal$i").fetchSemanticsNode())
            val text = unclipped(rules.compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode())
            val unit = medal.width / FRAME_UNITS
            val corners = listOf(text.topLeft, text.topRight, text.bottomLeft, text.bottomRight)
            // Each corner in frame units from the token's top left.
            val inside = corners.all { c -> within(look.shape, (c.x - medal.left) / unit, (c.y - medal.top) / unit) }
            "$label on ${look.shape} at $diameter: a corner of $text is outside the face, token $medal".takeIf { !inside }
        }
        assertTrue(out.joinToString("\n"), out.isEmpty())
    }

    /** A node's bounds where it's laid out, even off the screen's edge, as its size scaled by any layer. */
    private fun unclipped(node: SemanticsNode): Rect {
        val box = node.layoutInfo.coordinates
        return Rect(box.localToRoot(Offset.Zero), box.localToRoot(Offset(box.size.width.toFloat(), box.size.height.toFloat())))
    }

    /**
     * Whether the point ([x], [y], in frame units) is inside the face of [shape]: the medal's straight sides and its
     * slanted foot, or the rosette's inner circle (its valleys are 42.2 from the middle).
     */
    private fun within(shape: MedalShape, x: Float, y: Float): Boolean = when (shape) {
        MedalShape.Tile -> x in 14f..86f && y in 4f..78f
        MedalShape.Rosette -> hypot(x - MID, y - MID) <= 42f
    }

    private companion object {
        const val HALF_MARATHON = 21097
        const val FRAME_UNITS = 100f
        const val MID = 50f
    }
}
