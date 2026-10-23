package org.openstrap.app

import android.Manifest
import org.openstrap.app.ui.firmware.FirmwareScreen
import org.openstrap.app.ui.firmware.FirmwareViewModel
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.components.FloatingNavBar
import dev.chrisbanes.haze.hazeSource
import org.openstrap.app.ui.components.NavItem
import org.openstrap.app.ui.device.DeviceScreen
import org.openstrap.app.ui.device.DeviceViewModel
import org.openstrap.app.ui.dashboard.MomentHeader
import org.openstrap.app.ui.dashboard.Reselect
import org.openstrap.app.ui.dashboard.TabNav
import org.openstrap.app.ui.dashboard.dayTitle
import org.openstrap.app.ui.dashboard.strapChip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.tween
import org.openstrap.app.ui.components.Motion
import org.openstrap.app.ui.components.ListDetail
import org.openstrap.app.ui.components.SideBySideWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.openstrap.app.ui.theme.PhosphorIcons

class MainActivity : ComponentActivity() {
    /** The latest `open_metric` link, until the app root has opened it. */
    private val metricRequests = kotlinx.coroutines.flow.MutableStateFlow<MetricRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars (light icons) whatever the system mode, to match the always-dark theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as OpenStrapApp).container
        setContent { OpenStrapTheme { AppRoot(container, metricRequests) } }
    }

    override fun onStart() {
        super.onStart()
        (application as OpenStrapApp).container.strap.setAppVisible(true)
    }

    override fun onStop() {
        (application as OpenStrapApp).container.strap.setAppVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Reconnect to a paired strap whenever the app comes to the front (sync follows on connect).
        val granted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) (application as OpenStrapApp).container.strap.autoConnect()
        // The association or battery setting may have changed in system settings meanwhile.
        (application as OpenStrapApp).container.companion.refresh()
        handleDebugIntent()
        showWorkoutFromIntent()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent()
        showWorkoutFromIntent()
    }

    /**
     * The workout notification opens the live screen, even if it was tucked away; a shortcut or the tile, the ready
     * screen; an auto-detected workout's notification, that workout; a health alert or body-signal notification, its
     * metric's screen for its day.
     */
    private fun showWorkoutFromIntent() {
        intent.getStringExtra(EXTRA_OPEN_METRIC)?.let { route ->
            val day = intent.getStringExtra(EXTRA_METRIC_DAY)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            intent.removeExtra(EXTRA_OPEN_METRIC)
            intent.removeExtra(EXTRA_METRIC_DAY)
            metricRequests.value = MetricRequest(route, day)
        }
        val open = intent.getLongExtra(EXTRA_OPEN_WORKOUT, -1)
        if (open >= 0) {
            intent.removeExtra(EXTRA_OPEN_WORKOUT)
            (application as OpenStrapApp).container.workout.openRequest.value = open
        }
        val ready = intent.getStringExtra(EXTRA_READY_SPORT)?.toIntOrNull() ?: intent.getIntExtra(EXTRA_READY_SPORT, -1)
        if (ready >= 0) {
            intent.removeExtra(EXTRA_READY_SPORT)
            (application as OpenStrapApp).container.workout.readyRequest.value = ready
        }
        if (!intent.getBooleanExtra(EXTRA_SHOW_WORKOUT, false)) return
        intent.removeExtra(EXTRA_SHOW_WORKOUT)
        (application as OpenStrapApp).container.workout.minimized.value = false
    }

    companion object {
        const val EXTRA_SHOW_WORKOUT = "show_workout"
        const val EXTRA_READY_SPORT = "ready_sport"
        const val EXTRA_OPEN_WORKOUT = "open_workout"

        /** A metric's detail by [org.openstrap.app.ui.dashboard.Metric] name ("Hrv"), or "Recovery" or "Sleep". */
        const val EXTRA_OPEN_METRIC = "open_metric"

        /** The day for [EXTRA_OPEN_METRIC], as an ISO date ("2026-10-23"); without it the day showing stays. */
        const val EXTRA_METRIC_DAY = "metric_day"
    }

    /** Debug: `am start -n org.openstrap.app/.MainActivity --ei dump_raw_days 7` dumps raw history to files/raw. */
    private fun handleDebugIntent() {
        // Debug: `--ez demo_data true` fills an empty database with sample data (for the emulator).
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo_data", false)) {
            intent.removeExtra("demo_data")
            val dao = (application as OpenStrapApp).container.database.healthDao()
            kotlinx.coroutines.MainScope().launch { DemoData.fill(dao) }
        }
        // Debug: `--ei probe_endpoint 73 --es probe_hex 00` sends raw bytes to a channel endpoint and logs replies.
        val probeEndpoint = intent.getIntExtra("probe_endpoint", -1)
        if (BuildConfig.DEBUG && probeEndpoint >= 0) {
            val hex = intent.getStringExtra("probe_hex").orEmpty()
            intent.removeExtra("probe_endpoint")
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            ((application as OpenStrapApp).container.strap as? org.openstrap.app.device.StrapManager)?.probe(probeEndpoint, bytes)
        }
        // Debug: `--ez strap_sessions true` makes phone workouts also run a session on the strap (0x0019, unverified).
        if (BuildConfig.DEBUG && intent.hasExtra("strap_sessions")) {
            val on = intent.getBooleanExtra("strap_sessions", false)
            intent.removeExtra("strap_sessions")
            kotlinx.coroutines.MainScope().launch { (application as OpenStrapApp).container.settings.setStrapSessions(on) }
            Toast.makeText(this, "Strap sessions ${if (on) "on" else "off"}", Toast.LENGTH_SHORT).show()
        }
        // Debug: `--es buzz AboveTarget` plays a cue's pattern on the strap (a Cue name, Stop or Offer), to feel it.
        val buzz = intent.getStringExtra("buzz")
        if (BuildConfig.DEBUG && buzz != null) {
            intent.removeExtra("buzz")
            val pattern = when (buzz) {
                "Stop" -> org.openstrap.app.device.WorkoutSession.STOP_BUZZ
                "Offer" -> org.openstrap.app.device.WorkoutSession.OFFER_BUZZ
                else -> runCatching { org.openstrap.app.data.Cue.valueOf(buzz).pattern }.getOrNull()
            }
            val strap = (application as OpenStrapApp).container.strap
            if (pattern != null) kotlinx.coroutines.MainScope().launch {
                runCatching { strap.buzz(pattern) }.onFailure { Toast.makeText(this@MainActivity, "Buzz failed: ${it.message}", Toast.LENGTH_SHORT).show() }
            }
        }
        // Debug: `--es simulate_hr 140x120,80x600 --ei simulate_steps 0 --ei simulate_battery 80` plays heart rate as a connected strap (emulator).
        val simulate = intent.getStringExtra("simulate_hr")
        if (BuildConfig.DEBUG && simulate != null) {
            intent.removeExtra("simulate_hr")
            val script = simulate.split(',').mapNotNull { p -> p.split('x').takeIf { it.size == 2 }?.let { (b, s) -> b.toIntOrNull()?.let { bpm -> s.toIntOrNull()?.let { bpm to it } } } }
            ((application as OpenStrapApp).container.strap as? org.openstrap.app.device.StrapManager)?.simulate(script, intent.getIntExtra("simulate_steps", 0), intent.getIntExtra("simulate_battery", 80))
        }
        val days = intent.getIntExtra("dump_raw_days", 0)
        if (!BuildConfig.DEBUG || days <= 0) return
        intent.removeExtra("dump_raw_days")
        // Debug: `--es dump_types 5,6,70` limits the dump to these GDSP codes (even ones the strap doesn't advertise).
        val types = intent.getStringExtra("dump_types")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
        intent.removeExtra("dump_types")
        android.util.Log.i("OpenStrap", "dump: requested $days days, types ${types ?: "all"}")
        ((application as OpenStrapApp).container.strap as? org.openstrap.app.device.StrapManager)?.dumpRaw(days, types)
    }
}

private enum class Tab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    /** The capsule's colour while this tab shows (spec 3.2). */
    val tint: androidx.compose.ui.graphics.Color,
) {
    Today("Today", PhosphorIcons.HouseSimple, PhosphorIcons.HouseSimpleFill, androidx.compose.ui.graphics.Color.White),
    Recovery("Recovery", PhosphorIcons.Plant, PhosphorIcons.Plant, org.openstrap.app.ui.theme.MetricColors.readiness),
    Sleep("Sleep", PhosphorIcons.Moon, PhosphorIcons.MoonFill, org.openstrap.app.ui.theme.MetricColors.sleepDeep),
    Activity("Activity", PhosphorIcons.SneakerMove, PhosphorIcons.SneakerMoveFill, org.openstrap.app.ui.theme.MetricColors.steps),
}

/**
 * The pages that open over whichever tab is showing and close back to it (spec 3.3): the Strap page and what it opens,
 * and the pages the You sheet opens. Shallowest first.
 */
private enum class Page { Strap, Alarms, PhoneAlerts, HealthAlerts, Firmware, ZeppAccount, Profile, Diagnostics }

/** Keeps the open pages across process death, as their names ("Strap,Alarms"). */
private val PagesSaver: androidx.compose.runtime.saveable.Saver<List<Page>, String> = androidx.compose.runtime.saveable.Saver(
    save = { pages -> pages.joinToString(",") { it.name } },
    restore = { s -> s.split(',').mapNotNull { n -> Page.entries.firstOrNull { it.name == n } } },
)

/** What an `open_metric` link asked for: the screen and the day to show it for. */
internal data class MetricRequest(val route: String, val day: java.time.LocalDate?)

private val BLUETOOTH_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

private fun epochNow() = System.currentTimeMillis() / 1000

@Composable
private fun AppRoot(container: AppContainer, metricRequests: kotlinx.coroutines.flow.MutableStateFlow<MetricRequest?>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dayVm: DayViewModel = viewModel(factory = viewModelFactory { initializer { DayViewModel(container.health, container.strap.todaySteps, container.settings.profile) } })
    val deviceVm: DeviceViewModel = viewModel(factory = viewModelFactory { initializer { DeviceViewModel(container) } })

    // Which tab shows and the one before it, for back; every tab keeps its own stack of details.
    var nav by rememberSaveable(stateSaver = TabNav.Saver) { mutableStateOf(TabNav()) }
    val tab = nav.current
    val liveEnabled by container.strap.liveHeartRateEnabled.collectAsStateWithLifecycle()
    val firmwareVm: FirmwareViewModel = viewModel(factory = viewModelFactory { initializer { FirmwareViewModel(container) } })
    // Pages over the tab showing (the Strap page and what it opens, Profile, Diagnostics), deepest last.
    var pages by rememberSaveable(stateSaver = PagesSaver) { mutableStateOf(emptyList<Page>()) }
    var youOpen by rememberSaveable { mutableStateOf(false) }
    // The All metrics sheet, from the circle beside the tab bar: over the tab, like the You sheet, never a place.
    var allMetricsOpen by rememberSaveable { mutableStateOf(false) }
    val workoutsVm: org.openstrap.app.ui.workout.WorkoutsViewModel = viewModel(factory = viewModelFactory {
        initializer { org.openstrap.app.ui.workout.WorkoutsViewModel(container.database.healthDao(), container.settings.profile) }
    })
    val workouts by workoutsVm.workouts.collectAsStateWithLifecycle()
    val openWorkout by workoutsVm.open.collectAsStateWithLifecycle()
    val sportEffects by workoutsVm.effects.collectAsStateWithLifecycle()
    // Each tab's stack of details, deepest last; back pops one. Opening a detail never moves you to another tab.
    var todayStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var activityStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var recoveryStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var sleepStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    fun stackOf(t: Int): List<Detail> = when (Tab.entries[t]) {
        Tab.Today -> todayStack; Tab.Recovery -> recoveryStack; Tab.Sleep -> sleepStack; Tab.Activity -> activityStack
    }
    fun setStack(t: Int, stack: List<Detail>) = when (Tab.entries[t]) {
        Tab.Today -> todayStack = stack; Tab.Recovery -> recoveryStack = stack; Tab.Sleep -> sleepStack = stack; Tab.Activity -> activityStack = stack
    }
    // The All metrics Charge row asks the Recovery tab to scroll to its Charge card, once.
    var showCharge by remember { mutableStateOf(false) }
    // Each tap on the tab showing bumps its count, and its lists scroll to the top.
    val scrollToTop = remember { androidx.compose.runtime.mutableStateListOf(*Array(Tab.entries.size) { 0 }) }
    val history: org.openstrap.app.ui.dashboard.DailyHistory = { m, from, to -> container.health.daily(m, from, to) }
    var editGoals by rememberSaveable { mutableStateOf(false) }
    val goals by container.settings.goals.collectAsStateWithLifecycle(org.openstrap.app.data.settings.Goals())
    val todayTileNames by container.settings.todayTileNames.collectAsStateWithLifecycle(null)
    // Unknown names (a metric since renamed) drop out; nothing chosen yet means all of them. Remembered, like the
    // picker below: a new object each time the root recomposes (on every tab change) would recompose every tab.
    val todayTiles = remember(todayTileNames) {
        todayTileNames?.mapNotNull { n -> org.openstrap.app.ui.dashboard.Metric.entries.firstOrNull { it.name == n } }
            ?.takeIf { it.isNotEmpty() } ?: org.openstrap.app.ui.glow.TODAY_TILES
    }
    val recovery by dayVm.recovery.collectAsStateWithLifecycle()
    val healthspan by dayVm.healthspan.collectAsStateWithLifecycle()
    val profile by deviceVm.profile.collectAsStateWithLifecycle()
    val paired by deviceVm.paired.collectAsStateWithLifecycle()
    val day by dayVm.state.collectAsStateWithLifecycle()
    val strapReadiness by remember(day.dayStart) { container.database.healthDao().strapReadiness(day.dayStart) }.collectAsStateWithLifecycle(null)
    val checkIn by remember(day.date) { container.health.checkIn(day.date) }.collectAsStateWithLifecycle(null)
    val daysWithData by dayVm.daysWithData.collectAsStateWithLifecycle()
    val picker = remember(daysWithData, dayVm) { DayPicker(daysWithData, dayVm::select) }
    val live by container.strap.liveHeartRate.collectAsStateWithLifecycle()
    val connection by container.strap.connection.collectAsStateWithLifecycle()
    val sync by container.strap.sync.collectAsStateWithLifecycle()
    val message by deviceVm.message.collectAsStateWithLifecycle()
    val workoutState by container.workout.state.collectAsStateWithLifecycle()
    val workoutMinimized by container.workout.minimized.collectAsStateWithLifecycle()
    // The ready screen's sport while it is open, and whether it opened on the sport list ("All sports").
    var readySport by rememberSaveable { mutableStateOf<Int?>(null) }
    var readyPicking by rememberSaveable { mutableStateOf(false) }
    val suggestions by workoutsVm.suggestions.collectAsStateWithLifecycle()
    // Hidden while a workout records: the strap's history may show the one under way.
    val suggestion = suggestions.firstOrNull().takeIf { workoutState !is org.openstrap.app.device.WorkoutState.Recording }
    val offer by container.workout.offer.collectAsStateWithLifecycle()
    // A stretch being saved as a workout waits here while its sport is chosen.
    var savingSuggestion by remember { mutableStateOf<org.openstrap.app.data.WorkoutSuggestions.Suggestion?>(null) }
    val firmwareInstalling: () -> Boolean = { Page.Firmware in pages && firmwareVm.step.value is org.openstrap.app.ui.firmware.FirmwareStep.Installing }
    // Closes the pages over the tab, unless an update is being installed there; true if any closed.
    val closePages: () -> Boolean = {
        if (pages.isEmpty() || firmwareInstalling()) false
        else { if (Page.Firmware in pages) firmwareVm.reset(); pages = emptyList(); true }
    }
    val openPage: (Page) -> Unit = { p -> if (pages.lastOrNull() != p) pages = pages + p }
    val closePage: () -> Unit = { pages = pages.dropLast(1) }
    // A launcher shortcut or the Quick Settings tile asked for the ready screen.
    val readyRequest by container.workout.readyRequest.collectAsStateWithLifecycle()
    LaunchedEffect(readyRequest) {
        readyRequest?.let { sport ->
            if (workoutState !is org.openstrap.app.device.WorkoutState.Recording) { readySport = sport; readyPicking = false }
            container.workout.readyRequest.value = null
        }
    }
    // An auto-detected workout's notification: that workout, on the Activity tab, its home.
    val openRequest by container.workout.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        openRequest?.let { start ->
            closePages()
            activityStack = listOf(Detail.Workout(start))
            nav = nav.select(Tab.Activity.ordinal)
            container.workout.openRequest.value = null
        }
    }
    // A health alert or body-signal notification: its metric's screen, for the alert's day, over the tab showing.
    val metricRequest by metricRequests.collectAsStateWithLifecycle()
    LaunchedEffect(metricRequest) {
        metricRequest?.let { r ->
            Detail.fromRoute(r.route)?.let { detail ->
                closePages()
                youOpen = false
                allMetricsOpen = false
                r.day?.let(dayVm::select)
                if (stackOf(tab).lastOrNull() != detail) setStack(tab, stackOf(tab) + detail)
            }
            metricRequests.value = null
        }
    }
    // The day a workout is being added to after the fact.
    var addingOn by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    // While a workout records, the circle beside the tab bar shows its heart rate in the zone colour.
    val workoutAccent = (workoutState as? org.openstrap.app.device.WorkoutState.Recording)
        ?.let { org.openstrap.app.ui.glow.workoutColor(it, live, connection is ConnectionState.Connected) }

    // When the strap was last heard from, for the header chip: kept fresh while connected, then left where it stopped.
    // Without that in this run, the last synced reading or the last sync stand in.
    var seenConnected by rememberSaveable { mutableStateOf<Long?>(null) }
    val connected = connection is ConnectionState.Connected
    LaunchedEffect(connected) {
        if (connected) while (true) { seenConnected = epochNow(); kotlinx.coroutines.delay(30_000) }
    }
    val lastSeen = listOfNotNull(
        seenConnected,
        (sync as? org.openstrap.app.device.SyncState.Done)?.atEpochSeconds,
        day.heartRate.lastOrNull()?.x?.takeIf { day.date == java.time.LocalDate.now() },
    ).maxOrNull()
    // The chip's "18 min ago" moves on by itself.
    val now by androidx.compose.runtime.produceState(epochNow()) { while (true) { kotlinx.coroutines.delay(30_000); value = epochNow() } }
    // Right after the process starts the service is still reconnecting, so the chip waits before saying "18 min ago".
    val sinceStart = (android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()) / 1000
    val chip = strapChip(paired != null, connection, sync, lastSeen, now, sinceStart)

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); deviceVm.clearMessage() }
    }

    // Bluetooth permission gate: remember what to run once granted.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = result[Manifest.permission.BLUETOOTH_SCAN] == true && result[Manifest.permission.BLUETOOTH_CONNECT] == true
        if (ok) pending?.invoke() else Toast.makeText(context, "Bluetooth permission is required.", Toast.LENGTH_LONG).show()
        pending = null
    }
    val requestBluetooth: (() -> Unit) -> Unit = { then -> pending = then; btLauncher.launch(BLUETOOTH_PERMISSIONS) }
    // Location for a GPS workout: asked only when GPS is turned on; the workout starts either way (steps without it).
    var afterLocation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.none { it }) Toast.makeText(context, "Without location, distance comes from the strap's steps.", Toast.LENGTH_LONG).show()
        afterLocation?.invoke()
        afterLocation = null
    }
    // GPX of a kept route: the system's save dialog picks where; the start and end are already left out.
    var pendingGpx by remember { mutableStateOf<String?>(null) }
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val text = pendingGpx
        pendingGpx = null
        if (uri != null && text != null) scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
        }
    }
    val exportGpx: (Long) -> Unit = { start ->
        scope.launch {
            val file = workoutsVm.gpx(start)
            if (file == null) Toast.makeText(context, "This route is too short to share once its start and end are hidden.", Toast.LENGTH_LONG).show()
            else { pendingGpx = file.second; gpxLauncher.launch(file.first) }
        }
    }
    val requestLocation: (() -> Unit) -> Unit = { then ->
        afterLocation = then
        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(deviceVm::exportCsv)
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(container.healthConnect.permissions)) deviceVm.exportHealthConnect()
        else Toast.makeText(context, "Health Connect permissions were not granted.", Toast.LENGTH_LONG).show()
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(deviceVm::importCsv)
    }
    val hcReadLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.any { it in container.healthConnect.readPermissions }) deviceVm.importHealthConnect()
        else Toast.makeText(context, "Health Connect permissions were not granted.", Toast.LENGTH_LONG).show()
    }
    val importHealthConnect: () -> Unit = {
        if (!container.healthConnect.isAvailable()) {
            Toast.makeText(context, "Health Connect isn't available on this phone.", Toast.LENGTH_LONG).show()
        } else scope.launch {
            if (container.healthConnect.hasReadPermissions()) deviceVm.importHealthConnect()
            else hcReadLauncher.launch(container.healthConnect.readPermissions)
        }
    }
    val exportHealthConnect: () -> Unit = {
        if (!container.healthConnect.isAvailable()) {
            Toast.makeText(context, "Health Connect isn't available on this phone.", Toast.LENGTH_LONG).show()
        } else scope.launch {
            if (container.healthConnect.hasPermissions()) deviceVm.exportHealthConnect()
            else hcLauncher.launch(container.healthConnect.permissions)
        }
    }

    // Swiping left and right moves between tabs. [nav] owns the tab and the pager follows it: a tap animates the
    // pager there, and only a swipe the user made, once it settles, is another way to pick the tab. The pager
    // settling from its own animation (or one cut short by the next tap) never writes back.
    val pager = rememberPagerState(initialPage = tab) { Tab.entries.size }
    val swiped = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    LaunchedEffect(tab) {
        if (pager.currentPage != tab) {
            swiped.set(false)
            pager.animateScrollToPage(tab, animationSpec = tween(Motion.SWIFT, easing = Motion.easing))
        }
    }
    LaunchedEffect(pager) {
        launch { pager.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) swiped.set(true) } }
        snapshotFlow { pager.settledPage }.collect { nav = nav.settled(it, swiped.get()) }
    }
    // A detail screen open on this tab keeps the swipe for itself (and for its back gesture).
    val currentDetail = stackOf(tab).lastOrNull()
    LaunchedEffect(currentDetail) {
        workoutsVm.open((currentDetail as? Detail.Workout)?.start)
        // A screen opened or closed: the next tap on the tab scrolls before it pops.
        nav = nav.moved()
    }
    val subScreenOpen = currentDetail != null || pages.isNotEmpty()
    // Back closes what's open first (those screens handle it, registered later so they win). From a tab's root it
    // goes to the tab before, then to Today; on Today looking at a past day, back first returns to today. Only
    // Today's back leaves the app.
    val today = java.time.LocalDate.now()
    androidx.activity.compose.BackHandler(enabled = !subScreenOpen && (tab != Tab.Today.ordinal || day.date != today)) {
        if (tab == Tab.Today.ordinal) dayVm.select(today) else nav.back()?.let { nav = it }
    }
    // Back handlers on tabs that aren't showing (they stay composed, stacks and all) answer to a dispatcher nobody
    // presses, so back only ever closes what's on screen.
    val idleBack = remember {
        object : androidx.navigationevent.NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = androidx.navigationevent.NavigationEventDispatcher()
        }
    }
    // The pages are what the glass nav bar blurs as they scroll under it.
    val navGlass = dev.chrisbanes.haze.rememberHazeState()
    // Popups are drawn in the app's own window over a blur of the pages, like the nav bar.
    val glassHost = remember { org.openstrap.app.ui.components.GlassHost() }
    // Half-open on a table, the fold level: heart rate fills the standing top half, the app carries on below the fold.
    // During a workout the top half is the workout's heart card instead, and the controls stay below.
    val activity = androidx.activity.compose.LocalActivity.current
    val windowLayout by remember(activity) {
        activity?.let { androidx.window.layout.WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: kotlinx.coroutines.flow.emptyFlow()
    }.collectAsStateWithLifecycle(null)
    val halfOpen = windowLayout?.displayFeatures?.filterIsInstance<androidx.window.layout.FoldingFeature>()
        ?.filter { it.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED }.orEmpty()
    val hinge = halfOpen.firstOrNull { it.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL }
    // Stood up like a book, the fold upright: only the live workout splits, heart card left, controls right.
    val bookHinge = halfOpen.firstOrNull { it.orientation == androidx.window.layout.FoldingFeature.Orientation.VERTICAL }
    val workoutOpen = workoutState as? org.openstrap.app.device.WorkoutState.Recording
    val workoutOnTop = workoutOpen?.takeIf { hinge != null && !workoutMinimized }
    CompositionLocalProvider(
        org.openstrap.app.ui.dashboard.LocalGoals provides goals,
        org.openstrap.app.ui.components.LocalGlassHost provides glassHost,
    ) {
      androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      if (hinge != null) {
          val density = androidx.compose.ui.platform.LocalDensity.current
          val top = androidx.compose.ui.Modifier.height(with(density) { hinge.bounds.top.toDp() })
          if (workoutOnTop != null) {
              val recent by container.workout.recent.collectAsStateWithLifecycle()
              val zoneSeconds by container.workout.zoneSeconds.collectAsStateWithLifecycle()
              org.openstrap.app.ui.glow.GlowWorkoutGlance(workoutOnTop, live, recent, zoneSeconds, connection, top)
          } else org.openstrap.app.ui.glow.GlowTabletopHeart(
              day, live, liveEnabled.takeIf { connection is ConnectionState.Connected }, { container.strap.setLiveHeartRate(it) }, top,
          )
          androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(with(density) { hinge.bounds.height().toDp() }))
      }
      // Below the fold the status bar is far away, so the pages don't make room for it.
      androidx.compose.foundation.layout.BoxWithConstraints(
          androidx.compose.ui.Modifier.weight(1f).fillMaxWidth()
              .let { if (hinge != null) it.consumeWindowInsets(androidx.compose.foundation.layout.WindowInsets.statusBars) else it },
      ) {
        // Wider than tall (a phone on its side, an unfolded foldable turned sideways): the tabs stand down the
        // left edge, so the pages keep their height.
        val rail = maxWidth > maxHeight && maxWidth >= 600.dp
        val navItems = Tab.entries.map { NavItem(it.label, it.icon, it.selectedIcon, it.tint) }
        // Another tab keeps every stack. The tab showing scrolls to the top, then pops to its root (spec 3.3). A tap
        // that closes pages over the tab does only that.
        val selectTab: (Int) -> Unit = { t ->
            youOpen = false
            val closed = closePages()
            when {
                firmwareInstalling() -> Unit
                t != nav.current -> nav = nav.select(t)
                closed -> Unit
                else -> {
                    val (next, action) = nav.reselect(stackOf(t).size)
                    nav = next
                    when (action) {
                        Reselect.ScrollToTop -> scrollToTop[t] = scrollToTop[t] + 1
                        Reselect.PopToRoot -> setStack(t, emptyList())
                    }
                }
            }
        }
        // The circle only ever does an action (spec 3.2): it opens the sheet over whatever shows, and no tab hears of it.
        val openAllMetrics: () -> Unit = { allMetricsOpen = true }
        val action: @Composable () -> Unit = {
            // The moments (step 3) pass the workout window's start here.
            TabAction(
                workoutOpen, live, workoutAccent, workoutWindow = null, navGlass,
                onOpenWorkout = { container.workout.minimized.value = false }, onAllMetrics = openAllMetrics,
            )
        }
        Scaffold(
            bottomBar = {
                if (!rail) FloatingNavBar(navItems, tab, selectTab, glass = navGlass, action = action)
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            if (editGoals) {
                org.openstrap.app.ui.device.GoalsDialog(goals, onSave = { g -> scope.launch { container.settings.saveGoals(g) }; editGoals = false }, onDismiss = { editGoals = false })
            }
            // The header on every tab: Today's title for the day (step 3 makes it the moment), the tab's name elsewhere.
            val header: @Composable (Tab) -> Unit = { t ->
                MomentHeader(
                    if (t == Tab.Today) dayTitle(day.date) else t.label, day.date, chip, dayVm::previousDay, dayVm::nextDay, picker,
                    onOpenYou = { youOpen = true }, onOpenStrap = { openPage(Page.Strap) },
                )
            }
            // What a tab's stack shows on top: the same screens on every tab, each pushing onto the stack it's on.
            val detailPage: @Composable (Int, List<Detail>) -> Unit = { t, stack ->
                val pop: () -> Unit = { setStack(t, stack.dropLast(1)) }
                val push: (Detail) -> Unit = { setStack(t, stack + it) }
                when (val detail = stack.last()) {
                    is Detail.OfMetric -> MetricDetail(
                        detail.metric, day, history = history,
                        liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                        onLiveToggle = { container.strap.setLiveHeartRate(it) },
                        onOpenMetric = { push(Detail.OfMetric(it)) },
                        contentPadding = padding, onBack = pop,
                    )
                    is Detail.Workout -> WorkoutDetail(
                        openWorkout?.takeIf { it.workout.start == detail.start }, padding, onBack = pop,
                        onDelete = { container.workout.discard(detail.start); pop() },
                        workoutsVm = workoutsVm, session = container.workout, exportGpx = exportGpx,
                    )
                    Detail.AllWorkouts -> org.openstrap.app.ui.glow.GlowWorkoutsScreen(
                        workouts, padding, onOpen = { push(Detail.Workout(it)) }, onBack = pop,
                    )
                    Detail.Healthspan -> org.openstrap.app.ui.glow.GlowHealthspanScreen(healthspan, padding, onBack = pop)
                    // The Recovery and Sleep tabs' screens, opened over another tab from a card about them.
                    Detail.Recovery -> {
                        androidx.activity.compose.BackHandler(onBack = pop)
                        org.openstrap.app.ui.glow.GlowRecoveryScreen(
                            day, recovery, strapReadiness, history,
                            header = { org.openstrap.app.ui.glow.PageHeader("Recovery", pop, subtitle = org.openstrap.app.ui.glow.dayName(day.date)) },
                            contentPadding = padding, onOpenMetric = { push(Detail.OfMetric(it)) },
                        )
                    }
                    Detail.Sleep -> {
                        androidx.activity.compose.BackHandler(onBack = pop)
                        org.openstrap.app.ui.glow.GlowSleepScreen(
                            day, header = { org.openstrap.app.ui.glow.PageHeader("Sleep", pop, subtitle = org.openstrap.app.ui.glow.dayName(day.date)) },
                            contentPadding = padding, onOpenMetric = { push(Detail.OfMetric(it)) }, recovery = recovery,
                        )
                    }
                }
            }
            // Every tab stays composed, so a swipe never builds a whole dashboard mid-gesture (with only the neighbours
            // kept, crossing halfway built the next one); glows on tabs off screen, or while the pager moves, hold still.
            HorizontalPager(
                pager, androidx.compose.ui.Modifier.padding(start = if (rail) org.openstrap.app.ui.components.NavRailWidth else 0.dp).hazeSource(navGlass),
                userScrollEnabled = !subScreenOpen, key = { it }, beyondViewportPageCount = Tab.entries.size - 1,
            ) { page -> androidx.compose.runtime.CompositionLocalProvider(
                // Remembered: a new lambda each time would be a new static local, recomposing the whole tab under it.
                org.openstrap.app.ui.glow.LocalGlowPagePaused provides remember(pager, page) { { pager.isScrollInProgress || pager.settledPage != page } },
                org.openstrap.app.ui.dashboard.LocalScrollToTop provides scrollToTop[page],
                *(if (page == tab) emptyArray() else arrayOf(androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides idleBack)),
            ) {
                val pullToSync: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit = { screen ->
                    org.openstrap.app.ui.components.PullToSync(sync, connection is ConnectionState.Connected, container.strap::syncNow) { screen() }
                }
                val t = Tab.entries[page]
                // A tab's root with its stack opening over it (or beside it, when wide).
                ListDetail(
                    stackOf(page), isOpen = { it.isNotEmpty() }, depth = { it.size }, minWidth = SideBySideWidth,
                    detail = { stack -> if (stack.isNotEmpty()) detailPage(page, stack) },
                ) {
                    pullToSync {
                        when (t) {
                            Tab.Today -> org.openstrap.app.ui.glow.GlowTodayScreen(
                                day, live, header = { header(Tab.Today) }, contentPadding = padding,
                                // Cards about other tabs open those screens here, on Today; none switches tab.
                                onOpenSleep = { todayStack = listOf(Detail.Sleep) },
                                onOpenMetric = { todayStack = listOf(Detail.OfMetric(it)) },
                                recovery = recovery,
                                strapReadiness = strapReadiness,
                                checkIn = checkIn?.feeling,
                                onCheckIn = { v -> scope.launch { container.health.saveCheckIn(day.date, v) } },
                                onOpenRecovery = { todayStack = listOf(Detail.Recovery) },
                                workouts = workouts,
                                onOpenWorkout = { todayStack = listOf(Detail.Workout(it)) },
                                liveEnabled = liveEnabled.takeIf { connection is ConnectionState.Connected },
                                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                onOpenHealthspan = { todayStack = listOf(Detail.Healthspan) },
                                healthspan = healthspan,
                                onOpenProfile = { openPage(Page.Profile) },
                                history = history,
                                tiles = todayTiles,
                                onTilesChange = { m -> scope.launch { container.settings.saveTodayTiles(m.map { it.name }) } },
                                suggestion = suggestion,
                                onSaveSuggestion = { savingSuggestion = suggestion },
                                onDismissSuggestion = { suggestion?.let { container.workout.dismissSuggestion(it.start, it.end) } },
                                onStartWorkout = { readySport = org.openstrap.app.ui.glow.topSports(workouts).first().sportType; readyPicking = false },
                                offer = offer,
                                onRecordOffer = { container.workout.recordOffer() },
                                onDeclineOffer = { container.workout.declineOffer() },
                                running = workoutState as? org.openstrap.app.device.WorkoutState.Recording,
                                onOpenRunning = { container.workout.minimized.value = false },
                                onResumeRunning = container.workout::resume,
                            )
                            Tab.Recovery -> org.openstrap.app.ui.glow.GlowRecoveryScreen(
                                day, recovery, strapReadiness, history, header = { header(Tab.Recovery) }, contentPadding = padding,
                                onOpenMetric = { recoveryStack = listOf(Detail.OfMetric(it)) },
                                showCharge = showCharge, onChargeShown = { showCharge = false },
                            )
                            Tab.Sleep -> org.openstrap.app.ui.glow.GlowSleepScreen(
                                day, header = { header(Tab.Sleep) }, contentPadding = padding,
                                onOpenMetric = { sleepStack = listOf(Detail.OfMetric(it)) },
                                recovery = recovery,
                            )
                            Tab.Activity -> org.openstrap.app.ui.glow.GlowActivityScreen(
                                day, recovery, workouts, sportEffects, { from, to -> container.health.dailyPai(from, to) },
                                header = { header(Tab.Activity) }, contentPadding = padding,
                                onOpenMetric = { activityStack = listOf(Detail.OfMetric(it)) },
                                onOpenWorkout = { activityStack = listOf(Detail.Workout(it)) },
                                onAllWorkouts = { activityStack = listOf(Detail.AllWorkouts) },
                                onEditProfile = { openPage(Page.Profile) },
                                onStartWorkout = { readySport = it; readyPicking = false },
                                suggestion = suggestion,
                                onSaveSuggestion = { savingSuggestion = suggestion },
                                onDismissSuggestion = { suggestion?.let { container.workout.dismissSuggestion(it.start, it.end) } },
                                onAllSports = { readySport = readySport ?: org.openstrap.app.ui.glow.topSports(workouts).first().sportType; readyPicking = true },
                                onAddWorkout = { addingOn = day.date },
                                offer = offer,
                                onRecordOffer = { container.workout.recordOffer() },
                                onDeclineOffer = { container.workout.declineOffer() },
                                paiSex = profile.sex,
                            )
                        }
                    }
                }
            } }
            // The pages over the tab: the Strap page and what it opens, and the pages the You sheet opens.
            if (pages.isNotEmpty()) androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxSize().padding(start = if (rail) org.openstrap.app.ui.components.NavRailWidth else 0.dp)
                    .background(MaterialTheme.colorScheme.background).hazeSource(navGlass, zIndex = 1f),
            ) {
                org.openstrap.app.ui.components.OledSurfaces {
                    OverPages(
                        pages, container, deviceVm, firmwareVm, profile, padding, requestBluetooth, lastSeen,
                        open = openPage, close = closePage,
                    )
                }
            }
        }
        if (rail) org.openstrap.app.ui.components.FloatingNavRail(
            navItems, tab, selectTab, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterStart),
            glass = navGlass, action = action,
        )
        if (youOpen) {
            val log by container.strap.log.collectAsStateWithLifecycle()
            org.openstrap.app.ui.device.YouSheet(
                profile, goals, org.openstrap.app.data.Recovery.sleepNeed(profile.age(), profile.sleepNeedMinutes), log.size,
                onDismiss = { youOpen = false },
                onProfile = { youOpen = false; openPage(Page.Profile) },
                onGoals = { editGoals = true },
                onSaveCopy = { csvLauncher.launch("openstrap-export.zip") },
                onRestoreCopy = { csvImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                onHealthConnect = exportHealthConnect,
                onRestoreHealthConnect = importHealthConnect,
                onDiagnostics = { youOpen = false; openPage(Page.Diagnostics) },
                onSampleData = if (org.openstrap.app.data.SampleData.AVAILABLE) ({ deviceVm.loadSampleData() }) else null,
            )
        }
        // A map, not a card: rows whose home is a tab go there, at its root and top (Charge to its card); the rest open
        // their detail on the tab showing. The day is shared, so the tab shows the same one. The sheet closes first.
        if (allMetricsOpen) org.openstrap.app.ui.glow.AllMetricsSheet(
            org.openstrap.app.ui.glow.metricIndex(day, recovery, healthspan, workouts), day.date,
            onDismiss = { allMetricsOpen = false },
            onOpen = { target ->
                allMetricsOpen = false
                closePages()
                when (target) {
                    is org.openstrap.app.ui.glow.IndexTarget.Open ->
                        if (stackOf(tab).lastOrNull() != target.detail) setStack(tab, stackOf(tab) + target.detail)
                    is org.openstrap.app.ui.glow.IndexTarget.Home -> {
                        val home = when (target.tab) {
                            org.openstrap.app.ui.glow.HomeTab.Recovery -> Tab.Recovery
                            org.openstrap.app.ui.glow.HomeTab.Sleep -> Tab.Sleep
                        }.ordinal
                        setStack(home, emptyList())
                        nav = nav.select(home)
                        if (target.charge) showCharge = true else scrollToTop[home] = scrollToTop[home] + 1
                    }
                }
            },
        )
        addingOn?.let { date ->
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val series by androidx.compose.runtime.produceState<List<Pair<Long, Int>>?>(null, date) { value = workoutsVm.dayMinuteHeartRate(date) }
                val maxHr by workoutsVm.maxHeartRate.collectAsStateWithLifecycle()
                org.openstrap.app.ui.glow.GlowAddWorkoutScreen(
                    date, series, maxHr ?: 190, androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues(),
                    onSave = { from, to, sport -> container.workout.saveFromHistory(from, to, sport); addingOn = null },
                    onBack = { addingOn = null },
                )
            }
        }
        savingSuggestion?.let { s ->
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                org.openstrap.app.ui.glow.GlowSportPickerScreen(
                    androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues(),
                    onPick = { sport -> container.workout.saveFromHistory(s.start, s.end, sport); savingSuggestion = null },
                    onBack = { savingSuggestion = null },
                )
            }
        }
        WorkoutLayer(
            container, workoutState, workoutMinimized, readySport, readyPicking, live, connection, workoutsVm, requestBluetooth, requestLocation, exportGpx,
            onReadySport = { readySport = it },
            onCloseReady = { readySport = null; readyPicking = false },
            capsuleBottom = if (rail) 16.dp else 104.dp,
            tabletop = hinge != null, bookHinge = bookHinge,
        )
        org.openstrap.app.ui.components.GlassOverlay(glassHost, navGlass)
      }
      }
    }
}

/**
 * The circle beside the tab bar (spec 3.2): during a recording, the live heart rate in the zone [accent], opening the
 * live screen; in the workout window, Start workout; otherwise the All metrics sheet. Only ever an action, never a
 * place: it is never lit like a tab.
 */
@Composable
private fun TabAction(
    recording: org.openstrap.app.device.WorkoutState.Recording?,
    live: Int?,
    accent: androidx.compose.ui.graphics.Color?,
    /** Starts a workout, while it's the usual time for one; set by the moments (step 3), null until then. */
    workoutWindow: (() -> Unit)?,
    glass: dev.chrisbanes.haze.HazeState,
    onOpenWorkout: () -> Unit,
    onAllMetrics: () -> Unit,
) {
    val white = androidx.compose.ui.graphics.Color.White
    when {
        recording != null -> org.openstrap.app.ui.components.NavActionCircle(
            live?.let { "Heart rate $it. Open the workout" } ?: "Open the workout", onOpenWorkout, glass,
        ) {
            val color = accent ?: white
            if (live != null) org.openstrap.app.ui.glow.DotMatrixText("$live", dot = 2.6.dp, gap = 1.dp, color = color)
            else androidx.compose.material3.Icon(PhosphorIcons.HeartFill, null, androidx.compose.ui.Modifier.size(22.dp), tint = color)
        }
        workoutWindow != null -> org.openstrap.app.ui.components.NavActionCircle("Start workout", workoutWindow, glass, filled = true) {
            androidx.compose.material3.Icon(PhosphorIcons.PlayFill, null, androidx.compose.ui.Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color.Black)
        }
        else -> org.openstrap.app.ui.components.NavActionCircle("All metrics", onAllMetrics, glass) {
            androidx.compose.material3.Icon(PhosphorIcons.SquaresFour, null, androidx.compose.ui.Modifier.size(22.dp), tint = white)
        }
    }
}

/** The top page over the tab, sliding in and out like a stack; each closes back to the one under it. */
@Composable
private fun OverPages(
    pages: List<Page>,
    container: AppContainer,
    deviceVm: DeviceViewModel,
    firmwareVm: FirmwareViewModel,
    profile: org.openstrap.app.data.settings.UserProfile,
    padding: androidx.compose.foundation.layout.PaddingValues,
    requestBluetooth: (() -> Unit) -> Unit,
    lastSeen: Long?,
    open: (Page) -> Unit,
    close: () -> Unit,
) {
    // Read from the strap when first needed (the Strap page's rows show what it holds), and again on each page that edits it.
    val needsAlarms = pages.any { it == Page.Strap || it == Page.Alarms || it == Page.HealthAlerts }
    val alarmsVm: org.openstrap.app.ui.alarms.AlarmsViewModel? = if (needsAlarms) viewModel(
        key = "alarms", factory = viewModelFactory { initializer { org.openstrap.app.ui.alarms.AlarmsViewModel(container.strap) } },
    ) else null
    org.openstrap.app.ui.components.PushContent(pages, depth = { it.size }) { stack ->
        when (stack.lastOrNull()) {
            null -> Unit
            Page.Strap -> {
                val alarms = alarmsVm ?: return@PushContent
                val alarmsState by alarms.state.collectAsStateWithLifecycle()
                val account by firmwareVm.account.collectAsStateWithLifecycle()
                val connection by container.strap.connection.collectAsStateWithLifecycle()
                // Nothing read yet (the strap wasn't there when it was asked): ask again once it's connected.
                LaunchedEffect(connection is ConnectionState.Connected) {
                    if (connection is ConnectionState.Connected && alarmsState.caps == null && !alarmsState.busy) alarms.refresh()
                }
                DeviceScreen(
                    deviceVm, padding, requestBluetooth, container.settings, alarmsState, account != null, lastSeen,
                    onAlarms = { open(Page.Alarms) },
                    onPhoneAlerts = { open(Page.PhoneAlerts) },
                    onHealthAlerts = { open(Page.HealthAlerts) },
                    onFirmware = { open(Page.Firmware) },
                    onZeppAccount = { open(Page.ZeppAccount) },
                    onBack = close,
                )
            }
            Page.Alarms -> {
                val alarms = alarmsVm ?: return@PushContent
                LaunchedEffect(Unit) { alarms.refresh() }
                val alarmsState by alarms.state.collectAsStateWithLifecycle()
                org.openstrap.app.ui.alarms.AlarmsScreen(alarms, alarmsState, padding, onBack = close)
            }
            Page.HealthAlerts -> {
                val alarms = alarmsVm ?: return@PushContent
                LaunchedEffect(Unit) { alarms.refresh() }
                val alarmsState by alarms.state.collectAsStateWithLifecycle()
                org.openstrap.app.ui.alarms.HealthAlertsScreen(alarms, alarmsState, padding, onBack = close)
            }
            Page.PhoneAlerts -> org.openstrap.app.ui.alarms.PhoneAlertsScreen(container.phoneAlerts, container.settings, padding, onBack = close)
            Page.Firmware -> FirmwareScreen(firmwareVm, padding, onBack = close, onZeppAccount = { open(Page.ZeppAccount) })
            Page.ZeppAccount -> org.openstrap.app.ui.firmware.ZeppAccountScreen(firmwareVm, padding, onBack = close)
            Page.Profile -> org.openstrap.app.ui.device.ProfileScreen(profile, padding, onSave = { deviceVm.saveProfile(it); close() }, onBack = close)
            Page.Diagnostics -> {
                val log by container.strap.log.collectAsStateWithLifecycle()
                org.openstrap.app.ui.device.DiagnosticsScreen(log, padding, onBack = close)
            }
        }
    }
}

/**
 * The workout screens over the whole app: the ready screen, the live screen (or, tucked away, a capsule above the tab
 * bar) and the summary once it ends.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.WorkoutLayer(
    container: AppContainer,
    state: org.openstrap.app.device.WorkoutState,
    minimized: Boolean,
    readySport: Int?,
    readyPicking: Boolean,
    live: Int?,
    connection: ConnectionState,
    workoutsVm: org.openstrap.app.ui.workout.WorkoutsViewModel,
    requestBluetooth: (() -> Unit) -> Unit,
    requestLocation: (() -> Unit) -> Unit,
    exportGpx: (Long) -> Unit,
    onReadySport: (Int) -> Unit,
    onCloseReady: () -> Unit,
    capsuleBottom: androidx.compose.ui.unit.Dp,
    tabletop: Boolean = false,
    bookHinge: androidx.window.layout.FoldingFeature? = null,
) {
    val session = container.workout
    val padding = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues()
    val full = androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    when {
        // Minimizing morphs the live screen into the capsule above the tab bar, and tapping the capsule back.
        state is org.openstrap.app.device.WorkoutState.Recording -> org.openstrap.app.ui.glow.WorkoutMorph(
            minimized, capsuleBottom + padding.calculateBottomPadding(),
            full = {
                val recent by session.recent.collectAsStateWithLifecycle()
                val stats by session.stats.collectAsStateWithLifecycle()
                val zoneSeconds by session.zoneSeconds.collectAsStateWithLifecycle()
                val route by session.route.collectAsStateWithLifecycle()
                val history by session.history.collectAsStateWithLifecycle()
                // Folded, the heart card has the standing half: above the fold (drawn with the tabletop heart) or,
                // stood up like a book, the left half here.
                val liveScreen: @Composable (androidx.compose.ui.Modifier) -> Unit = { modifier ->
                    androidx.compose.foundation.layout.Box(modifier) {
                        org.openstrap.app.ui.glow.GlowWorkoutLiveScreen(
                            state, live, recent, stats, zoneSeconds, connection, padding,
                            onPause = session::pause, onResume = session::resume, onEnd = session::end,
                            onMinimize = { session.minimized.value = true },
                            onEndAtPrompt = session::endAtPrompt, onKeepGoing = session::keepGoing,
                            route = route, history = history,
                            onStartSet = session::startSet, onSetDone = session::setDone, onExtendRest = session::extendRest,
                            onLap = session::lap,
                            split = tabletop || bookHinge != null,
                        )
                    }
                }
                if (bookHinge != null) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize()) {
                        org.openstrap.app.ui.glow.GlowWorkoutGlance(
                            state, live, recent, zoneSeconds, connection,
                            androidx.compose.ui.Modifier.width(with(density) { bookHinge.bounds.left.toDp() }).fillMaxHeight(),
                        )
                        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(with(density) { bookHinge.bounds.width().toDp() }))
                        liveScreen(androidx.compose.ui.Modifier.weight(1f).fillMaxHeight())
                    }
                } else liveScreen(androidx.compose.ui.Modifier.fillMaxSize())
            },
            capsule = { place ->
                org.openstrap.app.ui.glow.WorkoutCapsule(
                    state, live, connection is ConnectionState.Connected, onClick = { session.minimized.value = false },
                    onPause = session::pause, onResume = session::resume, modifier = place,
                )
            },
        )
        state is org.openstrap.app.device.WorkoutState.Ended -> androidx.compose.foundation.layout.Box(full) {
            val summary by remember(state.start) { workoutsVm.phoneSummary(state.start) }.collectAsStateWithLifecycle(null)
            org.openstrap.app.ui.glow.GlowPhoneWorkoutScreen(
                summary, padding, onBack = session::dismissSummary, onDelete = { session.discard(state.start) },
                onTrim = { from, to -> session.trim(state.start, from, to) }, onUndoTrim = { session.undoTrim(state.start) }, justEnded = true,
                onExportGpx = { exportGpx(state.start) },
            )
        }
    }
    // The ready screen comes and goes like any other screen; a workout starting replaces it at once.
    val recording = state is org.openstrap.app.device.WorkoutState.Recording || state is org.openstrap.app.device.WorkoutState.Ended
    org.openstrap.app.ui.glow.PushOverlay(readySport.takeIf { !recording }, cut = recording) { sport ->
        androidx.compose.foundation.layout.Box(full) {
            org.openstrap.app.ui.glow.GlowWorkoutReadyScreen(
                sport, readyPicking, connection, live, padding,
                onSport = onReadySport,
                onStart = { sport, askToEnd, target, useGps, keepRoute, rest, setsTarget ->
                    val start = { session.start(sport, askToEnd, target, useGps, keepRoute, rest, setsTarget); onCloseReady() }
                    val granted = container.app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val withBluetooth = { if (granted) start() else requestBluetooth(start) }
                    if (useGps && !org.openstrap.app.device.WorkoutLocation.granted(container.app)) requestLocation(withBluetooth) else withBluetooth()
                },
                onBack = onCloseReady,
            )
        }
    }
}

/** A metric's detail. */
@androidx.compose.runtime.Composable
private fun MetricDetail(
    metric: org.openstrap.app.ui.dashboard.Metric,
    state: org.openstrap.app.ui.dashboard.DayUiState,
    history: org.openstrap.app.ui.dashboard.DailyHistory,
    liveHeartRate: Int?,
    liveEnabled: Boolean,
    canUseLive: Boolean,
    onLiveToggle: (Boolean) -> Unit,
    onOpenMetric: (org.openstrap.app.ui.dashboard.Metric) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onBack: () -> Unit,
) = org.openstrap.app.ui.glow.GlowMetricDetailScreen(metric, state, history, liveHeartRate, liveEnabled, canUseLive, onLiveToggle, onOpenMetric, contentPadding, onBack)

/** A workout's detail. */
@androidx.compose.runtime.Composable
private fun WorkoutDetail(
    a: org.openstrap.app.data.WorkoutAnalysis?,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    workoutsVm: org.openstrap.app.ui.workout.WorkoutsViewModel,
    session: org.openstrap.app.device.WorkoutSession,
    exportGpx: (Long) -> Unit,
) {
    // OpenStrap's own recordings get the workout-mode summary, trim and delete; the strap's come back with the next sync.
    if (a?.fromPhone == true) {
        val start = a.workout.start
        val summary by remember(start) { workoutsVm.phoneSummary(start) }.collectAsStateWithLifecycle(null)
        org.openstrap.app.ui.glow.GlowPhoneWorkoutScreen(
            summary, contentPadding, onBack, onDelete,
            onTrim = { from, to -> session.trim(start, from, to) }, onUndoTrim = { session.undoTrim(start) }, justEnded = false,
            onExportGpx = { exportGpx(start) },
        )
    } else org.openstrap.app.ui.glow.GlowWorkoutScreen(a, contentPadding, onBack)
}
