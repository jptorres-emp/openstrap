package org.openstrap.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.db.WeightEntry
import org.openstrap.app.testing.ComposeRules
import org.openstrap.app.ui.device.WeightContent
import org.openstrap.app.ui.glow.DotMatrixText
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The fork's own pieces drawn to PNGs under build/shots, for a look without a phone: the numbers in Barlow Condensed
 * at every size, and the weight sheet's body. Off unless OPENSTRAP_SHOTS is set (the wip check sets it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1200dp-xxhdpi")
class ForkShotsTest {
    @get:Rule val rules = ComposeRules()

    @Before fun only() = assumeTrue(System.getenv("OPENSTRAP_SHOTS") != null)

    private fun shot(name: String, content: @Composable () -> Unit) {
        rules.compose.setContent { OpenStrapTheme { Box(Modifier.background(Color.Black).padding(16.dp)) { content() } } }
        rules.compose.waitForIdle()
        val bitmap = rules.compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun numbers() = shot("fork_numbers") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DotSize.entries.forEach { size ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DotMatrixText("72", dot = size)
                    DotMatrixText("8.1", dot = size)
                    DotMatrixText("3,878", dot = size)
                    DotMatrixText("7:20", dot = size)
                    Text(size.name, style = GlowType.secondary, color = Color.White)
                }
            }
        }
    }

    @Test fun weight() {
        val today = LocalDate.of(2026, 11, 21)
        val kg = listOf(93.6f, 93.4f, 93.5f, 93.1f, 93.0f, 92.9f, 92.6f, 92.8f, 92.4f, 92.5f, 92.1f, 92.0f)
        val entries = kg.flatMapIndexed { week, w ->
            listOf(0L, 3L).map { d -> WeightEntry(today.minusDays(7L * (kg.size - 1 - week) + d), w + d * 0.1f, 1) }
        }
        shot("fork_weight") {
            Box(Modifier.width(360.dp)) { WeightContent(entries, today, false, "92.0", {}, 92.0f) }
        }
    }
}
