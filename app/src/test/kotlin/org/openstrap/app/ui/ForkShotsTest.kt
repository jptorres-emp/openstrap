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
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.data.db.WeightEntry
import org.openstrap.app.ui.device.JournalContent
import org.openstrap.app.ui.device.JournalTicks
import org.openstrap.app.ui.glow.ReportsPage
import androidx.compose.foundation.layout.PaddingValues
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
 * at every size, the weight sheet's body, and the Reports page and the journal in Portuguese (the fork's language) and
 * English. Off unless OPENSTRAP_SHOTS is set (the wip check sets it).
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

    @Test fun weight() = weight("fork_weight")

    private fun weight(name: String) {
        val today = LocalDate.of(2026, 11, 21)
        val kg = listOf(93.6f, 93.4f, 93.5f, 93.1f, 93.0f, 92.9f, 92.6f, 92.8f, 92.4f, 92.5f, 92.1f, 92.0f)
        val entries = kg.flatMapIndexed { week, w ->
            listOf(0L, 3L).map { d -> WeightEntry(today.minusDays(7L * (kg.size - 1 - week) + d), w + d * 0.1f, 1) }
        }
        shot(name) {
            Box(Modifier.width(360.dp)) { WeightContent(org.openstrap.app.ui.device.WeightEntries(entries), today, false, "92.0", {}, 92.0f) }
        }
    }

    private val report = run {
        val week = ForkReports.Figures(64f, 3f, 1f, 52f, 54f, 412f, 48f, 4f, 8432f)
        val before = ForkReports.Figures(58f, 2.3f, 1.3f, 49f, 56f, 398f, 41f, 3.3f, 7900f)
        ForkReports.Report(
            LocalDate.of(2026, 11, 21),
            ForkReports.Compare(week, before),
            ForkReports.Compare(week.copy(ready = 14f, low = 4f, workouts = 15f), before.copy(ready = 11f, low = 6f, workouts = 12f)),
            ForkTarget.Target(ForkTarget.DayKind.Usual, 40..60, personal = true, fromStrap = false), 32f, 45f,
            listOf(
                ForkHabits.Effect(ForkHabits.Habit.Logged(JournalHabit.Alcohol), 6, 20, 52f, 66f, -14f, ForkHabits.Verdict.Clear),
                ForkHabits.Effect(ForkHabits.Habit.ShortNight, 5, 21, 55f, 64f, -9f, ForkHabits.Verdict.Likely),
                ForkHabits.Effect(ForkHabits.Habit.Logged(JournalHabit.Meditation), 2, 24, 70f, 63f, null, ForkHabits.Verdict.TooFew),
            ),
            yesterday = null,
        )
    }

    @Test @Config(qualifiers = "+pt-rPT") fun reportsPortuguese() = shot("fork_reports_pt") {
        Box(Modifier.width(380.dp)) { ReportsPage(report, PaddingValues(0.dp), {}, {}) }
    }

    @Test fun reportsEnglish() = shot("fork_reports_en") {
        Box(Modifier.width(380.dp)) { ReportsPage(report, PaddingValues(0.dp), {}, {}) }
    }

    @Test @Config(qualifiers = "+pt-rPT") fun journalPortuguese() = shot("fork_journal_pt") {
        Box(Modifier.width(380.dp)) { JournalContent(JournalTicks(setOf(JournalHabit.Alcohol, JournalHabit.Stretching)), { _, _ -> }) }
    }

    @Test @Config(qualifiers = "+pt-rPT") fun weightPortuguese() = weight("fork_weight_pt")
}
