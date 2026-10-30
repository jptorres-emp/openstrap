package org.openstrap.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openstrap.app.DemoData
import org.openstrap.app.data.HealthRepository
import org.openstrap.app.data.JournalDay
import org.openstrap.app.data.Moment
import org.openstrap.app.data.RecoveryResult
import org.openstrap.app.data.db.OpenStrapDatabase
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.l10n.L10n
import org.openstrap.app.ui.dashboard.DayUiState
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.ReportOutcome
import org.openstrap.app.ui.glow.DotMatrixText
import org.openstrap.app.ui.glow.DotSize
import org.openstrap.app.ui.glow.GlowRecoveryScreen
import org.openstrap.app.ui.glow.GlowSleepScreen
import org.openstrap.app.ui.glow.GlowTodayScreen
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.TrainingScreen
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens as the app draws them, from the debug build's demo data, saved as PNGs under build/shots for a look at the
 * layout and type without a phone. In Portuguese, as the app is used. Off unless OPENSTRAP_SHOTS is set (the wip
 * check sets it), so the release build never waits on them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var db: OpenStrapDatabase
    private lateinit var repo: HealthRepository
    private val profile = UserProfile(birthYear = 1993, sex = UserProfile.Sex.Male, weightKg = 78f, heightCm = 178)
    private val today = LocalDate.now()

    @Before fun setUp() {
        assumeTrue(System.getenv("OPENSTRAP_SHOTS") != null)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        L10n.speak(true)
        Locale.setDefault(Locale.forLanguageTag("pt-PT"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, OpenStrapDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { DemoData.fill(db.healthDao()) }
        repo = HealthRepository(db.healthDao())
    }

    @After fun tearDown() {
        if (::db.isInitialized) db.close()
        Dispatchers.resetMain()
        L10n.speak(false)
    }

    private fun day(): Pair<DayUiState, RecoveryResult> = runBlocking {
        val vm = DayViewModel(repo, flowOf(null), flowOf(profile))
        val state = withTimeout(60_000) { vm.state.first { it.loaded && it.date == today } }
        state to repo.recovery(today, profile)
    }

    private fun report(): ReportOutcome = runBlocking { ReportOutcome.Ready(repo.reports(today, profile, emptyList())) }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { OpenStrapTheme { Box(Modifier.fillMaxSize().background(Color.Black)) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val header: @Composable () -> Unit = { Text("Hoje", style = GlowType.pageTitle, color = Color.White, modifier = Modifier.padding(vertical = 12.dp)) }
    private val padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

    @Test fun numbers() = shot("numbers") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DotSize.entries.forEach { size ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DotMatrixText("72", dot = size)
                    DotMatrixText("8,1", dot = size)
                    DotMatrixText("3 878", dot = size)
                    DotMatrixText("7:20", dot = size)
                    Text(size.name, style = GlowType.secondary, color = Color.White)
                }
            }
        }
    }

    @Test fun todayWaking() {
        val (state, recovery) = day()
        val report = report()
        shot("today_waking") {
            GlowTodayScreen(
                state, liveHeartRate = null, header = header, contentPadding = padding, moment = Moment.Waking,
                recovery = recovery, report = report, journal = JournalDay(today.minusDays(1), null),
            )
        }
    }

    @Test fun todayEvening() {
        val (state, recovery) = day()
        shot("today_evening") {
            GlowTodayScreen(state, liveHeartRate = null, header = header, contentPadding = padding, moment = Moment.Evening, recovery = recovery, report = report())
        }
    }

    @Test fun sleep() {
        val (state, recovery) = day()
        shot("sleep") { GlowSleepScreen(state, header = header, contentPadding = padding, onOpen = {}, recovery = recovery) }
    }

    @Test fun recovery() {
        val (state, recovery) = day()
        shot("recovery") {
            GlowRecoveryScreen(state, recovery, { m, a, b -> repo.daily(m, a, b) }, header = header, contentPadding = padding, onOpenMetric = {})
        }
    }

    @Test fun training() {
        val report = report()
        shot("training") {
            TrainingScreen(
                today, report, hasBody = true, weights = emptyList(), imperial = false, header = header, contentPadding = padding,
                onOpen = {}, onEditProfile = {}, onLogWeight = {},
            )
        }
    }
}
