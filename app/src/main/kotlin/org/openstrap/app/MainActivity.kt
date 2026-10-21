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
import androidx.compose.runtime.mutableIntStateOf
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
import org.openstrap.app.ui.device.YouScreen
import org.openstrap.app.ui.dashboard.LocalOpenStrap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars (light icons) whatever the system mode, to match the always-dark theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as OpenStrapApp).container
        setContent { OpenStrapTheme { AppRoot(container) } }
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
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent()
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

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    Today("Today", PhosphorIcons.House, PhosphorIcons.HouseFill),
    Recovery("Recovery", PhosphorIcons.Plant, PhosphorIcons.Plant),
    Sleep("Sleep", PhosphorIcons.Moon, PhosphorIcons.MoonFill),
    Activity("Activity", PhosphorIcons.Footprints, PhosphorIcons.Footprints),
    You("You", PhosphorIcons.User, PhosphorIcons.User),
}

/** The screens stacked under the You tab, shallowest first. */
private enum class YouPage { Hub, Profile, Strap, Alarms, Firmware }

private val BLUETOOTH_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)

@Composable
private fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dayVm: DayViewModel = viewModel(factory = viewModelFactory { initializer { DayViewModel(container.health, container.strap.todaySteps, container.settings.profile) } })
    val deviceVm: DeviceViewModel = viewModel(factory = viewModelFactory { initializer { DeviceViewModel(container) } })

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The Recovery tab section Today's top card asked for; cleared once the tab has scrolled to it.
    var recoveryFocus by remember { mutableStateOf<org.openstrap.app.ui.glow.RecoverySection?>(null) }
    val liveEnabled by container.strap.liveHeartRateEnabled.collectAsStateWithLifecycle()
    val firmwareVm: FirmwareViewModel = viewModel(factory = viewModelFactory { initializer { FirmwareViewModel(container) } })
    var showFirmware by rememberSaveable { mutableStateOf(false) }
    var showAlarms by rememberSaveable { mutableStateOf(false) }
    var showStrap by rememberSaveable { mutableStateOf(false) }
    val workoutsVm: org.openstrap.app.ui.workout.WorkoutsViewModel = viewModel(factory = viewModelFactory {
        initializer { org.openstrap.app.ui.workout.WorkoutsViewModel(container.database.healthDao(), container.settings.profile) }
    })
    val workouts by workoutsVm.workouts.collectAsStateWithLifecycle()
    val openWorkout by workoutsVm.open.collectAsStateWithLifecycle()
    val sportEffects by workoutsVm.effects.collectAsStateWithLifecycle()
    // What each tab has pushed on top of itself; opening a detail never moves you to another tab.
    // Each tab's stack of details, deepest last; back pops one.
    var todayStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var activityStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var recoveryStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    var sleepStack by rememberSaveable(stateSaver = Detail.StackSaver) { mutableStateOf(emptyList<Detail>()) }
    val history: org.openstrap.app.ui.dashboard.DailyHistory = { m, from, to -> container.health.daily(m, from, to) }
    // The tab whose profile page is open (You, or Activity's energy and load rows), or -1.
    var profileTab by rememberSaveable { mutableIntStateOf(-1) }
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
    val day by dayVm.state.collectAsStateWithLifecycle()
    val strapReadiness by remember(day.dayStart) { container.database.healthDao().strapReadiness(day.dayStart) }.collectAsStateWithLifecycle(null)
    val checkIn by remember(day.date) { container.health.checkIn(day.date) }.collectAsStateWithLifecycle(null)
    val daysWithData by dayVm.daysWithData.collectAsStateWithLifecycle()
    val picker = remember(daysWithData, dayVm) { DayPicker(daysWithData, dayVm::select) }
    val live by container.strap.liveHeartRate.collectAsStateWithLifecycle()
    val connection by container.strap.connection.collectAsStateWithLifecycle()
    val sync by container.strap.sync.collectAsStateWithLifecycle()
    val message by deviceVm.message.collectAsStateWithLifecycle()
    val battery = (connection as? ConnectionState.Connected)?.batteryPercent
    val charging = (connection as? ConnectionState.Connected)?.charging == true

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

    // Swiping left and right moves between tabs. The bar and the swipe drive the same page: a tap
    // animates the pager there, and a finished swipe updates the tab.
    val pager = rememberPagerState(initialPage = tab) { Tab.entries.size }
    LaunchedEffect(tab) {
        if (pager.currentPage != tab) pager.animateScrollToPage(tab, animationSpec = tween(Motion.SWIFT, easing = Motion.easing))
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { tab = it } }
    // A detail screen open on this tab keeps the swipe for itself (and for its back gesture).
    val currentDetail = when (Tab.entries[tab]) {
        Tab.Today -> todayStack.lastOrNull()
        Tab.Activity -> activityStack.lastOrNull()
        Tab.Recovery -> recoveryStack.lastOrNull()
        Tab.Sleep -> sleepStack.lastOrNull()
        else -> null
    }
    LaunchedEffect(currentDetail) { workoutsVm.open((currentDetail as? Detail.Workout)?.start) }
    val subScreenOpen = currentDetail != null || profileTab == tab || (Tab.entries[tab] == Tab.You && (showStrap || showAlarms || showFirmware))
    // Back closes what's open on a tab first (those screens handle it, registered later so they win);
    // with nothing left it goes home to Today. Only Today's back leaves the app.
    androidx.activity.compose.BackHandler(enabled = tab != Tab.Today.ordinal && !subScreenOpen) { tab = Tab.Today.ordinal }
    // On Today looking at a past day, back first returns to today; the next back leaves.
    val today = java.time.LocalDate.now()
    androidx.activity.compose.BackHandler(enabled = tab == Tab.Today.ordinal && !subScreenOpen && day.date != today) { dayVm.select(today) }
    val openStrap: () -> Unit = { tab = Tab.You.ordinal; showStrap = true }
    // The pages are what the glass nav bar blurs as they scroll under it.
    val navGlass = dev.chrisbanes.haze.rememberHazeState()
    // Popups are drawn in the app's own window over a blur of the pages, like the nav bar.
    val glassHost = remember { org.openstrap.app.ui.components.GlassHost() }
    // Half-open on a table, the fold level: heart rate fills the standing top half, the app carries on below the fold.
    val activity = androidx.activity.compose.LocalActivity.current
    val windowLayout by remember(activity) {
        activity?.let { androidx.window.layout.WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: kotlinx.coroutines.flow.emptyFlow()
    }.collectAsStateWithLifecycle(null)
    val hinge = windowLayout?.displayFeatures?.filterIsInstance<androidx.window.layout.FoldingFeature>()?.firstOrNull {
        it.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED && it.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL
    }
    CompositionLocalProvider(
        LocalOpenStrap provides openStrap,
        org.openstrap.app.ui.dashboard.LocalGoals provides goals,
        org.openstrap.app.ui.components.LocalGlassHost provides glassHost,
    ) {
      androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
      if (hinge != null) {
          val density = androidx.compose.ui.platform.LocalDensity.current
          org.openstrap.app.ui.glow.GlowTabletopHeart(
              day, live, liveEnabled.takeIf { connection is ConnectionState.Connected }, { container.strap.setLiveHeartRate(it) },
              androidx.compose.ui.Modifier.height(with(density) { hinge.bounds.top.toDp() }),
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
        val navItems = Tab.entries.map { NavItem(it.label, it.icon, it.selectedIcon) }
        val selectTab: (Int) -> Unit = {
            tab = it; todayStack = emptyList(); activityStack = emptyList(); recoveryStack = emptyList(); sleepStack = emptyList()
            showAlarms = false; showStrap = false; profileTab = -1
            // An update being installed stays open, so coming back to You shows its progress.
            if (firmwareVm.step.value !is org.openstrap.app.ui.firmware.FirmwareStep.Installing) { firmwareVm.reset(); showFirmware = false }
        }
        Scaffold(
            bottomBar = {
                if (!rail) FloatingNavBar(navItems, tab, selectTab, glass = navGlass)
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            val profilePage: @Composable () -> Unit = {
                org.openstrap.app.ui.device.ProfileScreen(profile, padding, onSave = { deviceVm.saveProfile(it); profileTab = -1 }, onBack = { profileTab = -1 })
            }
            if (editGoals) {
                org.openstrap.app.ui.device.GoalsDialog(goals, onSave = { g -> scope.launch { container.settings.saveGoals(g) }; editGoals = false }, onDismiss = { editGoals = false })
            }
            // The You list and the pages it opens; [ListDetail] puts them side by side when there's room.
            val youPage: @Composable (YouPage) -> Unit = { youPage -> when (youPage) {
                YouPage.Profile -> profilePage()
                YouPage.Firmware -> {
                    FirmwareScreen(firmwareVm, padding, onBack = { showFirmware = false })
                }
                YouPage.Alarms -> {
                    // Created here so it reads from the strap each time the screen opens.
                    val alarmsVm: org.openstrap.app.ui.alarms.AlarmsViewModel = viewModel(
                        key = "alarms", factory = viewModelFactory { initializer { org.openstrap.app.ui.alarms.AlarmsViewModel(container.strap) } },
                    )
                    LaunchedEffect(Unit) { alarmsVm.refresh() }
                    val alarmsState by alarmsVm.state.collectAsStateWithLifecycle()
                    org.openstrap.app.ui.alarms.AlarmsScreen(alarmsVm, alarmsState, padding, onBack = { showAlarms = false })
                }
                YouPage.Strap -> {
                    DeviceScreen(
                        deviceVm, padding, requestBluetooth,
                        onFirmware = { showFirmware = true },
                        onAlarms = { showAlarms = true },
                        onBack = { showStrap = false },
                    )
                }
                YouPage.Hub -> {
                    YouScreen(
                        deviceVm, padding,
                        onEditProfile = { profileTab = Tab.You.ordinal },
                        goals = goals,
                        onEditGoals = { editGoals = true },
                        onOpenStrap = { showStrap = true },
                        onExportCsv = { csvLauncher.launch("openstrap-export.zip") },
                        onExportHealthConnect = exportHealthConnect,
                        onImportCsv = { csvImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        onImportHealthConnect = importHealthConnect,
                    )
                }
            } }
            // Every tab stays composed, so a swipe never builds a whole dashboard mid-gesture (with only the neighbours
            // kept, crossing halfway built the next one); glows on tabs off screen, or while the pager moves, hold still.
            HorizontalPager(
                pager, androidx.compose.ui.Modifier.padding(start = if (rail) org.openstrap.app.ui.components.NavRailWidth else 0.dp).hazeSource(navGlass),
                userScrollEnabled = !subScreenOpen, key = { it }, beyondViewportPageCount = Tab.entries.size - 1,
            ) { page -> androidx.compose.runtime.CompositionLocalProvider(
                // Remembered: a new lambda each time would be a new static local, recomposing the whole tab under it.
                org.openstrap.app.ui.glow.LocalGlowPagePaused provides remember(pager, page) { { pager.isScrollInProgress || pager.settledPage != page } },
            ) {
                val pullToSync: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit = { screen ->
                    org.openstrap.app.ui.components.PullToSync(sync, connection is ConnectionState.Connected, container.strap::syncNow) { screen() }
                }
                when (Tab.entries[page]) {
                    // Today never opens the full workout list; it would only be on the stack from a stale saved one.
                    // The profile (from the Healthspan card's reminder) opens over everything else on the tab.
                    Tab.Today -> ListDetail(
                        todayStack to (profileTab == Tab.Today.ordinal),
                        isOpen = { (s, profileOpen) -> profileOpen || s.lastOrNull().let { it != null && it !is Detail.AllWorkouts } },
                        depth = { (s, profileOpen) -> s.size + if (profileOpen) 1 else 0 }, minWidth = SideBySideWidth,
                        detail = { (stack, profileOpen) ->
                            if (profileOpen) profilePage() else when (val detail = stack.lastOrNull()) {
                                is Detail.OfMetric -> MetricDetail(
                                    detail.metric, day, history = history,
                                    liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                    onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                    onOpenMetric = { todayStack = todayStack + Detail.OfMetric(it) },
                                    contentPadding = padding, onBack = { todayStack = todayStack.dropLast(1) },
                                )
                                is Detail.Workout -> WorkoutDetail(openWorkout?.takeIf { it.workout.start == detail.start }, padding, onBack = { todayStack = todayStack.dropLast(1) })
                                Detail.Healthspan -> org.openstrap.app.ui.glow.GlowHealthspanScreen(healthspan, padding, onBack = { todayStack = todayStack.dropLast(1) })
                                else -> Unit
                            }
                        },
                    ) {
                            pullToSync {
                                org.openstrap.app.ui.glow.GlowTodayScreen(
                                    day, live, battery, charging, dayVm::previousDay, dayVm::nextDay, padding,
                                    picker = picker,
                                    onOpenSleep = { tab = Tab.Sleep.ordinal },
                                    onOpenMetric = { todayStack = listOf(Detail.OfMetric(it)) },
                                    recovery = recovery,
                                    strapReadiness = strapReadiness,
                                    checkIn = checkIn?.feeling,
                                    onCheckIn = { v -> scope.launch { container.health.saveCheckIn(day.date, v) } },
                                    onOpenRecovery = { section -> recoveryStack = emptyList(); recoveryFocus = section; tab = Tab.Recovery.ordinal },
                                    workouts = workouts,
                                    onOpenWorkout = { todayStack = listOf(Detail.Workout(it)) },
                                    liveEnabled = liveEnabled.takeIf { connection is ConnectionState.Connected },
                                    onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                    onOpenActivity = { tab = Tab.Activity.ordinal },
                                    onOpenHealthspan = { todayStack = listOf(Detail.Healthspan) },
                                    healthspan = healthspan,
                                    onOpenProfile = { profileTab = Tab.Today.ordinal },
                                    history = history,
                                    tiles = todayTiles,
                                    onTilesChange = { m -> scope.launch { container.settings.saveTodayTiles(m.map { it.name }) } },
                                )
                            }
                    }
                    Tab.Recovery -> ListDetail(
                        recoveryStack, isOpen = { it.lastOrNull() is Detail.OfMetric }, depth = { it.size }, minWidth = SideBySideWidth,
                        detail = { stack ->
                            val detail = stack.last() as Detail.OfMetric
                            MetricDetail(
                                detail.metric, day, history = history,
                                liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                onOpenMetric = { recoveryStack = recoveryStack + Detail.OfMetric(it) },
                                contentPadding = padding, onBack = { recoveryStack = recoveryStack.dropLast(1) },
                            )
                        },
                    ) {
                        pullToSync {
                            org.openstrap.app.ui.glow.GlowRecoveryScreen(
                                day, recovery, strapReadiness, history, battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                onOpenMetric = { recoveryStack = listOf(Detail.OfMetric(it)) },
                                focus = recoveryFocus, onFocused = { recoveryFocus = null },
                            )
                        }
                    }
                    Tab.Sleep -> ListDetail(
                        sleepStack, isOpen = { it.lastOrNull() is Detail.OfMetric }, depth = { it.size }, minWidth = SideBySideWidth,
                        detail = { stack ->
                            val detail = stack.last() as Detail.OfMetric
                            MetricDetail(
                                detail.metric, day, history = history,
                                liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                onOpenMetric = { sleepStack = sleepStack + Detail.OfMetric(it) },
                                contentPadding = padding, onBack = { sleepStack = sleepStack.dropLast(1) },
                            )
                        },
                    ) {
                        pullToSync {
                            org.openstrap.app.ui.glow.GlowSleepScreen(
                                day, battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                onOpenMetric = { sleepStack = listOf(Detail.OfMetric(it)) },
                                recovery = recovery,
                            )
                        }
                    }
                    // The activity dashboard, with workouts and metrics opening over it (or beside it, when wide).
                    // The profile page (from the energy and load rows) opens over everything else on the tab.
                    Tab.Activity -> ListDetail(
                        activityStack to (profileTab == Tab.Activity.ordinal),
                        isOpen = { (stack, profileOpen) -> profileOpen || stack.isNotEmpty() },
                        depth = { (stack, profileOpen) -> stack.size + if (profileOpen) 1 else 0 },
                        minWidth = SideBySideWidth,
                        detail = { (stack, profileOpen) ->
                            if (profileOpen) profilePage() else when (val detail = stack.last()) {
                                is Detail.Workout -> WorkoutDetail(openWorkout?.takeIf { it.workout.start == detail.start }, padding, onBack = { activityStack = activityStack.dropLast(1) })
                                is Detail.OfMetric -> MetricDetail(
                                    detail.metric, day, history = history,
                                    liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                    onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                    onOpenMetric = { activityStack = activityStack + Detail.OfMetric(it) },
                                    contentPadding = padding, onBack = { activityStack = activityStack.dropLast(1) },
                                )
                                Detail.AllWorkouts -> org.openstrap.app.ui.glow.GlowWorkoutsScreen(
                                    workouts, padding, onOpen = { activityStack = activityStack + Detail.Workout(it) },
                                    onBack = { activityStack = activityStack.dropLast(1) },
                                )
                                // Opened only from Today.
                                Detail.Healthspan -> Unit
                            }
                        },
                    ) {
                        pullToSync {
                            org.openstrap.app.ui.glow.GlowActivityScreen(
                                day, recovery, workouts, sportEffects, { from, to -> container.health.dailyPai(from, to) },
                                battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                onOpenMetric = { activityStack = listOf(Detail.OfMetric(it)) },
                                onOpenWorkout = { activityStack = listOf(Detail.Workout(it)) },
                                onAllWorkouts = { activityStack = listOf(Detail.AllWorkouts) },
                                onEditProfile = { profileTab = Tab.Activity.ordinal },
                            )
                        }
                    }
                    // On a wide screen the You list stays on the left and its pages open on the right.
                    Tab.You -> org.openstrap.app.ui.components.OledSurfaces { ListDetail(
                        when {
                            showFirmware -> YouPage.Firmware; showAlarms -> YouPage.Alarms; showStrap -> YouPage.Strap
                            profileTab == Tab.You.ordinal -> YouPage.Profile; else -> YouPage.Hub
                        },
                        isOpen = { it != YouPage.Hub },
                        depth = { when (it) { YouPage.Hub -> 0; YouPage.Profile, YouPage.Strap -> 1; else -> 2 } },
                        closedMaxWidth = 600.dp,
                        detail = { page -> youPage(page) },
                    ) { youPage(YouPage.Hub) } }
                }
            } }
        }
        if (rail) org.openstrap.app.ui.components.FloatingNavRail(
            navItems, tab, selectTab, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterStart),
            glass = navGlass,
        )
        org.openstrap.app.ui.components.GlassOverlay(glassHost, navGlass)
      }
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
private fun WorkoutDetail(a: org.openstrap.app.data.WorkoutAnalysis?, contentPadding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) =
    org.openstrap.app.ui.glow.GlowWorkoutScreen(a, contentPadding, onBack)
