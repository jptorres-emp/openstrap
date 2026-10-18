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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import org.openstrap.app.ui.dashboard.DateHeader
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.Detail
import org.openstrap.app.ui.dashboard.MetricDetailScreen
import org.openstrap.app.ui.dashboard.SleepScreen
import org.openstrap.app.ui.components.FloatingNavBar
import dev.chrisbanes.haze.hazeSource
import org.openstrap.app.ui.components.NavItem
import org.openstrap.app.ui.dashboard.TodayScreen
import org.openstrap.app.ui.dashboard.RecoveryScreen
import org.openstrap.app.ui.device.DeviceScreen
import org.openstrap.app.ui.device.DeviceViewModel
import org.openstrap.app.ui.device.YouScreen
import org.openstrap.app.ui.dashboard.LocalOpenStrap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.tween
import org.openstrap.app.ui.components.Motion
import org.openstrap.app.ui.components.PushContent
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
private enum class YouPage { Hub, Strap, Alarms, Firmware, DesignLab }

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
    val liveEnabled by container.strap.liveHeartRateEnabled.collectAsStateWithLifecycle()
    val firmwareVm: FirmwareViewModel = viewModel(factory = viewModelFactory { initializer { FirmwareViewModel(container) } })
    var showFirmware by rememberSaveable { mutableStateOf(false) }
    var showAlarms by rememberSaveable { mutableStateOf(false) }
    var showStrap by rememberSaveable { mutableStateOf(false) }
    var showDesignLab by rememberSaveable { mutableStateOf(false) }
    val glowToday by container.settings.glowTodayEnabled.collectAsStateWithLifecycle(false)
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
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var editGoals by rememberSaveable { mutableStateOf(false) }
    val goals by container.settings.goals.collectAsStateWithLifecycle(org.openstrap.app.data.settings.Goals())
    val todayTileNames by container.settings.todayTileNames.collectAsStateWithLifecycle(null)
    // Unknown names (a metric since renamed) drop out; nothing chosen yet means all of them.
    val todayTiles = todayTileNames?.mapNotNull { n -> org.openstrap.app.ui.dashboard.Metric.entries.firstOrNull { it.name == n } }
        ?.takeIf { it.isNotEmpty() } ?: org.openstrap.app.ui.glow.TODAY_TILES
    val recovery by dayVm.recovery.collectAsStateWithLifecycle()
    val profile by deviceVm.profile.collectAsStateWithLifecycle()
    val day by dayVm.state.collectAsStateWithLifecycle()
    val strapReadiness by remember(day.dayStart) { container.database.healthDao().strapReadiness(day.dayStart) }.collectAsStateWithLifecycle(null)
    val daysWithData by dayVm.daysWithData.collectAsStateWithLifecycle()
    val picker = DayPicker(daysWithData, dayVm::select)
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
    val subScreenOpen = currentDetail != null || (Tab.entries[tab] == Tab.You && (showStrap || showAlarms || showFirmware || showDesignLab))
    // Back closes what's open on a tab first (those screens handle it, registered later so they win);
    // with nothing left it goes home to Today. Only Today's back leaves the app.
    androidx.activity.compose.BackHandler(enabled = tab != Tab.Today.ordinal && !subScreenOpen) { tab = Tab.Today.ordinal }
    // On Today looking at a past day, back first returns to today; the next back leaves.
    val today = java.time.LocalDate.now()
    androidx.activity.compose.BackHandler(enabled = tab == Tab.Today.ordinal && !subScreenOpen && day.date != today) { dayVm.select(today) }
    val openStrap: () -> Unit = { tab = Tab.You.ordinal; showStrap = true }
    // The pages are what the glass nav bar blurs as they scroll under it.
    val navGlass = dev.chrisbanes.haze.rememberHazeState()
    // Glow popups are drawn in the app's own window over a blur of the pages, like the nav bar.
    val glassHost = remember { org.openstrap.app.ui.components.GlassHost() }
    val glow = BuildConfig.DEBUG && glowToday
    CompositionLocalProvider(
        LocalOpenStrap provides openStrap,
        org.openstrap.app.ui.dashboard.LocalGoals provides goals,
        org.openstrap.app.ui.components.LocalGlowDesign provides glow,
        org.openstrap.app.ui.components.LocalGlassHost provides glassHost.takeIf { glow },
    ) {
      androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                FloatingNavBar(Tab.entries.map { NavItem(it.label, it.icon, it.selectedIcon) }, tab, {
                    tab = it; todayStack = emptyList(); activityStack = emptyList(); recoveryStack = emptyList(); sleepStack = emptyList()
                    showAlarms = false; showStrap = false; showDesignLab = false
                    // An update being installed stays open, so coming back to You shows its progress.
                    if (firmwareVm.step.value !is org.openstrap.app.ui.firmware.FirmwareStep.Installing) { firmwareVm.reset(); showFirmware = false }
                }, glass = if (BuildConfig.DEBUG && glowToday) navGlass else null)
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            if (editProfile) {
                org.openstrap.app.ui.device.ProfileDialog(profile, onSave = { deviceVm.saveProfile(it); editProfile = false }, onDismiss = { editProfile = false })
            }
            if (editGoals) {
                org.openstrap.app.ui.device.GoalsDialog(goals, onSave = { g -> scope.launch { container.settings.saveGoals(g) }; editGoals = false }, onDismiss = { editGoals = false })
            }
            HorizontalPager(pager, androidx.compose.ui.Modifier.hazeSource(navGlass), userScrollEnabled = !subScreenOpen, key = { it }) { page ->
                val pullToSync: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit = { screen ->
                    org.openstrap.app.ui.components.PullToSync(sync, connection is ConnectionState.Connected, container.strap::syncNow) { screen() }
                }
                when (Tab.entries[page]) {
                    Tab.Today -> PushContent(todayStack, depth = { it.size }) { stack ->
                        when (val detail = stack.lastOrNull()) {
                            is Detail.OfMetric -> MetricDetail(BuildConfig.DEBUG && glowToday, 
                                detail.metric, day, history = history,
                                liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                onOpenMetric = { todayStack = todayStack + Detail.OfMetric(it) },
                                contentPadding = padding, onBack = { todayStack = todayStack.dropLast(1) },
                            )
                            is Detail.Workout -> WorkoutDetail(BuildConfig.DEBUG && glowToday, openWorkout?.takeIf { it.workout.start == detail.start }, padding, onBack = { todayStack = todayStack.dropLast(1) })
                            // Today never opens the full workout list; it would only land here from a stale saved stack.
                            null, Detail.AllWorkouts -> pullToSync {
                                if (BuildConfig.DEBUG && glowToday) org.openstrap.app.ui.glow.GlowTodayScreen(
                                    day, live, battery, charging, dayVm::previousDay, dayVm::nextDay, padding,
                                    picker = picker,
                                    onOpenSleep = { tab = Tab.Sleep.ordinal },
                                    onOpenMetric = { todayStack = listOf(Detail.OfMetric(it)) },
                                    recovery = recovery,
                                    onOpenRecovery = { tab = Tab.Recovery.ordinal },
                                    workouts = workouts,
                                    onOpenWorkout = { todayStack = listOf(Detail.Workout(it)) },
                                    liveEnabled = liveEnabled.takeIf { connection is ConnectionState.Connected },
                                    onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                    onOpenActivity = { tab = Tab.Activity.ordinal },
                                    history = history,
                                    tiles = todayTiles,
                                    onTilesChange = { m -> scope.launch { container.settings.saveTodayTiles(m.map { it.name }) } },
                                ) else TodayScreen(
                                    day, live, battery, charging, dayVm::previousDay, dayVm::nextDay, padding,
                                    picker = picker,
                                    onOpenSleep = { tab = Tab.Sleep.ordinal },
                                    onOpenMetric = { todayStack = listOf(Detail.OfMetric(it)) },
                                    recovery = recovery,
                                    onOpenRecovery = { tab = Tab.Recovery.ordinal },
                                    workouts = workouts,
                                    onOpenWorkout = { todayStack = listOf(Detail.Workout(it)) },
                                )
                            }
                        }
                    }
                    Tab.Recovery -> PushContent(recoveryStack, depth = { it.size }) { stack ->
                        val detail = stack.lastOrNull()
                        if (detail is Detail.OfMetric) MetricDetail(BuildConfig.DEBUG && glowToday, 
                            detail.metric, day, history = history,
                            liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                            onLiveToggle = { container.strap.setLiveHeartRate(it) },
                            onOpenMetric = { recoveryStack = recoveryStack + Detail.OfMetric(it) },
                            contentPadding = padding, onBack = { recoveryStack = recoveryStack.dropLast(1) },
                        ) else pullToSync {
                            if (BuildConfig.DEBUG && glowToday) org.openstrap.app.ui.glow.GlowRecoveryScreen(
                                day, recovery, strapReadiness, history, battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                onOpenMetric = { recoveryStack = listOf(Detail.OfMetric(it)) },
                            ) else RecoveryScreen(
                                recovery, day.date, day.dayStart..day.dayEnd, day.sleep?.let { it.start..it.end }, padding,
                                onEditProfile = { editProfile = true },
                                strapReadiness = strapReadiness,
                            ) { DateHeader(day.date, battery, dayVm::previousDay, dayVm::nextDay, charging, picker) }
                        }
                    }
                    Tab.Sleep -> PushContent(sleepStack, depth = { it.size }) { stack ->
                        val detail = stack.lastOrNull()
                        if (detail is Detail.OfMetric) MetricDetail(BuildConfig.DEBUG && glowToday, 
                            detail.metric, day, history = history,
                            liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                            onLiveToggle = { container.strap.setLiveHeartRate(it) },
                            onOpenMetric = { sleepStack = sleepStack + Detail.OfMetric(it) },
                            contentPadding = padding, onBack = { sleepStack = sleepStack.dropLast(1) },
                        ) else pullToSync {
                            if (BuildConfig.DEBUG && glowToday) org.openstrap.app.ui.glow.GlowSleepScreen(
                                day, battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                onOpenMetric = { sleepStack = listOf(Detail.OfMetric(it)) },
                            ) else SleepScreen(
                                day, battery, charging, dayVm::previousDay, dayVm::nextDay, padding, picker = picker,
                                onOpenMetric = { sleepStack = listOf(Detail.OfMetric(it)) },
                            )
                        }
                    }
                    Tab.Activity -> PushContent(activityStack, depth = { it.size }) { stack ->
                        val detail = stack.lastOrNull()
                        when {
                            detail is Detail.Workout ->
                                WorkoutDetail(BuildConfig.DEBUG && glowToday, openWorkout?.takeIf { it.workout.start == detail.start }, padding, onBack = { activityStack = activityStack.dropLast(1) })
                            detail is Detail.OfMetric -> MetricDetail(BuildConfig.DEBUG && glowToday, 
                                detail.metric, day, history = history,
                                liveHeartRate = live, liveEnabled = liveEnabled, canUseLive = connection is ConnectionState.Connected,
                                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                                onOpenMetric = { activityStack = activityStack + Detail.OfMetric(it) },
                                contentPadding = padding, onBack = { activityStack = activityStack.dropLast(1) },
                            )
                            // The full list: its own page on glow, the tab itself on classic.
                            detail is Detail.AllWorkouts || !(BuildConfig.DEBUG && glowToday) -> {
                                if (detail != null && BuildConfig.DEBUG && glowToday) {
                                    org.openstrap.app.ui.glow.GlowWorkoutsScreen(
                                        workouts, padding, onOpen = { activityStack = activityStack + Detail.Workout(it) },
                                        onBack = { activityStack = activityStack.dropLast(1) },
                                    )
                                } else {
                                    if (detail != null) androidx.activity.compose.BackHandler { activityStack = activityStack.dropLast(1) }
                                    org.openstrap.app.ui.workout.WorkoutsScreen(workouts, padding, onOpen = { activityStack = activityStack + Detail.Workout(it) })
                                }
                            }
                            else -> pullToSync {
                                org.openstrap.app.ui.glow.GlowActivityScreen(
                                    day, recovery, workouts, sportEffects, { from, to -> container.health.dailyPai(from, to) },
                                    battery, charging, dayVm::previousDay, dayVm::nextDay, picker, padding,
                                    onOpenMetric = { activityStack = listOf(Detail.OfMetric(it)) },
                                    onOpenWorkout = { activityStack = listOf(Detail.Workout(it)) },
                                    onAllWorkouts = { activityStack = listOf(Detail.AllWorkouts) },
                                    onEditProfile = { editProfile = true },
                                )
                            }
                        }
                    }
                    Tab.You -> org.openstrap.app.ui.components.OledSurfaces(BuildConfig.DEBUG && glowToday) { PushContent(
                        when { showDesignLab -> YouPage.DesignLab; showFirmware -> YouPage.Firmware; showAlarms -> YouPage.Alarms; showStrap -> YouPage.Strap; else -> YouPage.Hub },
                        depth = { if (it == YouPage.DesignLab) 1 else it.ordinal.coerceAtMost(2) },
                    ) { youPage -> when (youPage) {
                    YouPage.DesignLab -> {
                        org.openstrap.app.ui.glow.GlowLabScreen(padding, onBack = { showDesignLab = false })
                    }
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
                            onEditProfile = { editProfile = true },
                            goals = goals,
                            onEditGoals = { editGoals = true },
                            onOpenStrap = { showStrap = true },
                            onExportCsv = { csvLauncher.launch("openstrap-export.zip") },
                            onExportHealthConnect = exportHealthConnect,
                            onOpenDesignLab = if (BuildConfig.DEBUG) ({ showDesignLab = true }) else null,
                            glowToday = if (BuildConfig.DEBUG) glowToday else null,
                            onGlowToday = { on -> scope.launch { container.settings.setGlowToday(on) } },
                        )
                    }
                    } } }
                }
            }
        }
        if (glow) org.openstrap.app.ui.components.GlassOverlay(glassHost, navGlass)
      }
    }
}

/** A metric's detail in the glow language or the classic one. */
@androidx.compose.runtime.Composable
private fun MetricDetail(
    glow: Boolean,
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
) = if (glow) org.openstrap.app.ui.glow.GlowMetricDetailScreen(metric, state, history, liveHeartRate, liveEnabled, canUseLive, onLiveToggle, onOpenMetric, contentPadding, onBack)
else MetricDetailScreen(metric, state, history, liveHeartRate, liveEnabled, canUseLive, onLiveToggle, onOpenMetric, contentPadding, onBack)

/** A workout's detail in the glow language or the classic one. */
@androidx.compose.runtime.Composable
private fun WorkoutDetail(glow: Boolean, a: org.openstrap.app.data.WorkoutAnalysis?, contentPadding: androidx.compose.foundation.layout.PaddingValues, onBack: () -> Unit) =
    if (glow) org.openstrap.app.ui.glow.GlowWorkoutScreen(a, contentPadding, onBack)
    else org.openstrap.app.ui.workout.WorkoutScreen(a, contentPadding, onBack)
