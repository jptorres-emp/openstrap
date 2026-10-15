package org.openstrap.app

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.openstrap.app.device.ConnectionState
import org.openstrap.app.ui.dashboard.DayViewModel
import org.openstrap.app.ui.dashboard.HeartScreen
import org.openstrap.app.ui.dashboard.SleepScreen
import org.openstrap.app.ui.dashboard.TodayScreen
import org.openstrap.app.ui.device.DeviceScreen
import org.openstrap.app.ui.device.DeviceViewModel
import org.openstrap.app.ui.theme.OpenStrapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenStrapApp).container
        setContent { OpenStrapTheme { AppRoot(container) } }
    }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Today("Today", Icons.Default.Home),
    Heart("Heart", Icons.Default.Favorite),
    Sleep("Sleep", Icons.Default.Bedtime),
    Device("Device", Icons.Default.Watch),
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
    val dayVm: DayViewModel = viewModel(factory = viewModelFactory { initializer { DayViewModel(container.health) } })
    val deviceVm: DeviceViewModel = viewModel(factory = viewModelFactory { initializer { DeviceViewModel(container) } })

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var liveEnabled by rememberSaveable { mutableStateOf(false) }
    val day by dayVm.state.collectAsStateWithLifecycle()
    val live by container.strap.liveHeartRate.collectAsStateWithLifecycle()
    val connection by container.strap.connection.collectAsStateWithLifecycle()
    val message by deviceVm.message.collectAsStateWithLifecycle()
    val battery = (connection as? ConnectionState.Connected)?.batteryPercent

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
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (Tab.entries[tab]) {
            Tab.Today -> TodayScreen(day, live, battery, dayVm::previousDay, dayVm::nextDay, padding)
            Tab.Heart -> HeartScreen(
                day, live, liveEnabled,
                canUseLive = connection is ConnectionState.Connected,
                battery = battery,
                onLiveToggle = { liveEnabled = it; container.strap.setLiveHeartRate(it) },
                onPrev = dayVm::previousDay, onNext = dayVm::nextDay, contentPadding = padding,
            )
            Tab.Sleep -> SleepScreen(day, battery, dayVm::previousDay, dayVm::nextDay, padding)
            Tab.Device -> DeviceScreen(
                deviceVm, padding, requestBluetooth,
                onExportCsv = { csvLauncher.launch("openstrap-export.zip") },
                onExportHealthConnect = exportHealthConnect,
                onFirmware = { Toast.makeText(context, "Firmware update is coming next.", Toast.LENGTH_SHORT).show() },
            )
        }
    }
}
