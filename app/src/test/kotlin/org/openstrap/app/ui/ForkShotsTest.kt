package org.openstrap.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.data.ForkHabits
import org.openstrap.app.data.ForkPlan
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkTally
import org.openstrap.app.data.ForkTarget
import org.openstrap.app.data.ForkTraining
import org.openstrap.app.data.JournalHabit
import org.openstrap.app.data.db.WeightEntry
import org.openstrap.app.R
import org.openstrap.app.testing.ComposeRules
import org.openstrap.app.ui.device.JournalContent
import org.openstrap.app.ui.device.JournalTicks
import org.openstrap.app.ui.device.WeightContent
import org.openstrap.app.ui.glow.DotMatrixText
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.ReportsPage
import org.openstrap.app.ui.glow.draw.DotSize
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * The fork's own pieces drawn to PNGs under build/shots, for a look without a phone: the numbers in Barlow Condensed
 * at every size, the weight sheet's body, the Reports page's week, month and Training view (from 60 made-up days run
 * through the reports' own sums) and the journal, in Portuguese (the fork's language) and English. Off unless
 * OPENSTRAP_SHOTS is set (the wip check sets it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1200dp-xxhdpi")
class ForkShotsTest {
    @get:Rule val rules = ComposeRules()

    @Before fun only() = assumeTrue(System.getenv("OPENSTRAP_SHOTS") != null)

    private fun shot(name: String, then: (() -> Unit)? = null, content: @Composable () -> Unit) {
        rules.compose.setContent { OpenStrapTheme { Box(Modifier.background(Color.Black).padding(16.dp)) { content() } } }
        rules.compose.waitForIdle()
        if (then != null) {
            then()
            rules.compose.waitForIdle()
        }
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

    /** 60 made-up days, as the source would read them, through the reports' own sums. */
    private val report: ForkReports.Report by lazy {
        val today = LocalDate.of(2026, 11, 21)
        val random = Random(3)
        val raw = (ForkReports.DAYS - 1L downTo 0L).map { back ->
            val date = today.minusDays(back)
            val soccer = date.dayOfWeek == DayOfWeek.TUESDAY || date.dayOfWeek == DayOfWeek.THURSDAY
            val cross = date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
            val alcohol = date.dayOfWeek == DayOfWeek.FRIDAY
            val asleep = 400 + random.nextInt(90)
            ForkReports.Day(
                date, recovery = 58 + random.nextInt(30), hrv = 46f + random.nextInt(14), restingHr = 52f + random.nextInt(6),
                asleepMinutes = asleep, bedtime = 610 + random.nextInt(80), wakeTime = 410 + random.nextInt(50),
                inBedMinutes = asleep + 40, deepRem = 120 + random.nextInt(50), load = 20f + random.nextInt(90),
                steps = 6000 + random.nextInt(6000), kcal = 2200f + random.nextInt(600),
                sessions = listOfNotNull(
                    ForkReports.Session(SOCCER, 5400, late = true).takeIf { soccer },
                    ForkReports.Session(CROSS_TRAINING, 3600).takeIf { cross },
                ),
                partial = back == 0L,
                journal = if (back % 9 == 4L) null else if (alcohol) setOf(JournalHabit.Alcohol) else emptySet(),
            )
        }
        // The mornings after soccer and a drink, a little lower, so the habits have something to show.
        val days = raw.mapIndexed { i, d ->
            val before = raw.getOrNull(i - 1)
            val after = before != null && (before.sessions.any { it.sport == SOCCER } || before.journal.orEmpty().isNotEmpty())
            if (after) d.copy(hrv = d.hrv!! * 0.85f, recovery = d.recovery!! - 9) else d
        }
        val loads = days.filter { !it.partial }.associate { it.date to it.load!! }
        val usual = days.associate { it.date to 55f }
        val training = days.filter { it.sessions.isNotEmpty() }.map { it.date }.toSet()
        val marked = ForkTarget.mark(days, loads, training, usual)
        val target = ForkTarget.target(marked.last(), loads, training, 55f)
        ForkReports.Report(
            today, ForkReports.week(marked, today, usual), ForkReports.month(marked, today, usual), ForkReports.normal(marked),
            ForkTally.weekdays(marked), ForkPlan.plan(marked, 480, target, 32f), ForkHabits.effects(marked, 55f), yesterday = null,
            training = ForkTraining.training(marked, today.minusDays(1), fatigue = 68f, usual = 55f),
        )
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The page, on the segment named [segment] (the week when null). */
    private fun page(name: String, segment: Int? = null) = shot(name, then = if (segment == null) null else ({ select(segment) })) {
        Box(Modifier.width(380.dp)) { ReportsPage(report, PaddingValues(0.dp), {}, {}) }
    }

    private fun select(segment: Int) {
        rules.compose.onNodeWithText(context.getString(segment)).performClick()
    }

    @Test @Config(qualifiers = "+pt-rPT-h3200dp") fun reportsPortuguese() = page("fork_reports_pt")

    @Test @Config(qualifiers = "+pt-rPT-h3200dp") fun reportsMonthPortuguese() = page("fork_reports_month_pt", R.string.fork_rep_month)

    @Test @Config(qualifiers = "+pt-rPT-h3200dp") fun trainingPortuguese() = page("fork_training_pt", R.string.fork_rep_training)

    @Test @Config(qualifiers = "+h3200dp") fun reportsEnglish() = page("fork_reports_en")

    @Test @Config(qualifiers = "+h3200dp") fun trainingEnglish() = page("fork_training_en", R.string.fork_rep_training)

    @Test @Config(qualifiers = "+pt-rPT") fun journalPortuguese() = shot("fork_journal_pt") {
        Box(Modifier.width(380.dp)) { JournalContent(JournalTicks(setOf(JournalHabit.Alcohol, JournalHabit.Stretching)), { _, _ -> }) }
    }

    @Test @Config(qualifiers = "+pt-rPT") fun weightPortuguese() = weight("fork_weight_pt")

    private companion object {
        const val SOCCER = 191
        const val CROSS_TRAINING = 130
    }
}
