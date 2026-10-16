package org.openstrap.app

import org.openstrap.app.device.SyncState
import android.Manifest
import org.openstrap.app.ui.firmware.FirmwareScreen
import org.openstrap.app.ui.firmware.FirmwareViewModel
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import org.openstrap.app.ui.dashboard.DayPicker
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.HeartScreen
import org.openstrap.app.ui.dashboard.SleepScreen
import org.openstrap.app.ui.dashboard.FocusRequest
import org.openstrap.app.ui.dashboard.FocusTarget
import org.openstrap.app.ui.components.FloatingNavBar
import org.openstrap.app.ui.components.NavItem
import org.openstrap.app.ui.dashboard.TodayScreen
import org.openstrap.app.ui.dashboard.RecoveryScreen
import org.openstrap.app.ui.device.DeviceScreen
import org.openstrap.app.ui.device.DeviceViewModel
import org.openstrap.app.ui.theme.OpenStrapTheme
import org.openstrap.app.ui.theme.PhosphorIcons

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    Heart("Heart", PhosphorIcons.Heart, PhosphorIcons.HeartFill),
    Sleep("Sleep", PhosphorIcons.Moon, PhosphorIcons.MoonFill),
    Workouts("Workouts", PhosphorIcons.Barbell, PhosphorIcons.Barbell),
    Device("Device", PhosphorIcons.Watch, PhosphorIcons.WatchFill),
}

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
    var focus by remember { mutableStateOf<FocusRequest?>(null) }
    val liveEnabled by container.strap.liveHeartRateEnabled.collectAsStateWithLifecycle()
    val firmwareVm: FirmwareViewModel = viewModel(factory = viewModelFactory { initializer { FirmwareViewModel(container) } })
    var showFirmware by rememberSaveable { mutableStateOf(false) }
    var showAlarms by rememberSaveable { mutableStateOf(false) }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    val workoutsVm: org.openstrap.app.ui.workout.WorkoutsViewModel = viewModel(factory = viewModelFactory {
        initializer { org.openstrap.app.ui.workout.WorkoutsViewModel(container.database.healthDao(), container.settings.profile) }
    })
    val workouts by workoutsVm.workouts.collectAsStateWithLifecycle()
    val openWorkout by workoutsVm.open.collectAsStateWithLifecycle()
    var workoutStart by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(workoutStart) { workoutsVm.open(workoutStart) }
    var editProfile by rememberSaveable { mutableStateOf(false) }
    val recovery by dayVm.recovery.collectAsStateWithLifecycle()
    val profile by deviceVm.profile.collectAsStateWithLifecycle()
    val day by dayVm.state.collectAsStateWithLifecycle()
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

    Scaffold(
        bottomBar = {
            FloatingNavBar(Tab.entries.map { NavItem(it.label, it.icon, it.selectedIcon) }, tab, { tab = it; showRecovery = false; workoutStart = null; showAlarms = false })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (editProfile) {
            org.openstrap.app.ui.device.ProfileDialog(profile, onSave = { deviceVm.saveProfile(it); editProfile = false }, onDismiss = { editProfile = false })
        }
        if (showRecovery && Tab.entries[tab] == Tab.Today) {
            RecoveryScreen(
                recovery, day.date, day.dayStart..day.dayEnd, day.sleep?.let { it.start..it.end }, padding,
                onBack = { showRecovery = false }, onEditProfile = { editProfile = true },
            )
        } else when (Tab.entries[tab]) {
            Tab.Today -> TodayScreen(
                day, live, battery, charging, dayVm::previousDay, dayVm::nextDay, padding,
                picker = picker,
                onOpenSleep = { tab = Tab.Sleep.ordinal },
                onFocus = { target ->
                    focus = FocusRequest(target)
                    tab = (if (target == FocusTarget.Breathing) Tab.Sleep else Tab.Heart).ordinal
                },
                recovery = recovery,
                onOpenRecovery = { showRecovery = true },
                sync = sync as? SyncState.Running,
                workouts = workouts,
                onOpenWorkout = { tab = Tab.Workouts.ordinal; workoutStart = it },
            )
            Tab.Heart -> HeartScreen(
                day, live, liveEnabled,
                canUseLive = connection is ConnectionState.Connected,
                battery = battery,
                charging = charging,
                onLiveToggle = { container.strap.setLiveHeartRate(it) },
                onPrev = dayVm::previousDay, onNext = dayVm::nextDay, contentPadding = padding, picker = picker,
                focus = focus, onFocusDone = { focus = null },
            )
            Tab.Sleep -> SleepScreen(day, battery, charging, dayVm::previousDay, dayVm::nextDay, padding, focus, onFocusDone = { focus = null }, picker = picker)
            Tab.Workouts -> if (workoutStart != null) {
                org.openstrap.app.ui.workout.WorkoutScreen(openWorkout?.takeIf { it.workout.start == workoutStart }, padding, onBack = { workoutStart = null })
            } else {
                org.openstrap.app.ui.workout.WorkoutsScreen(workouts, padding, onOpen = { workoutStart = it })
            }
            Tab.Device -> if (showFirmware) {
                FirmwareScreen(firmwareVm, padding, onBack = { showFirmware = false })
            } else if (showAlarms) {
                // Created here so it reads from the strap each time the screen opens.
                val alarmsVm: org.openstrap.app.ui.alarms.AlarmsViewModel = viewModel(
                    key = "alarms", factory = viewModelFactory { initializer { org.openstrap.app.ui.alarms.AlarmsViewModel(container.strap) } },
                )
                LaunchedEffect(Unit) { alarmsVm.refresh() }
                val alarmsState by alarmsVm.state.collectAsStateWithLifecycle()
                org.openstrap.app.ui.alarms.AlarmsScreen(alarmsVm, alarmsState, padding, onBack = { showAlarms = false })
            } else {
                DeviceScreen(
                    deviceVm, padding, requestBluetooth,
                    onExportCsv = { csvLauncher.launch("openstrap-export.zip") },
                    onExportHealthConnect = exportHealthConnect,
                    onFirmware = { showFirmware = true },
                    onAlarms = { showAlarms = true },
                )
            }
        }
    }
}
