package app.aircons.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.aircons.AirConsApp
import app.aircons.core.UpdateManager
import app.aircons.data.ControlMode
import app.aircons.data.SessionBus
import app.aircons.data.UpdateInfo
import app.aircons.service.AirConsService
import app.aircons.ui.screens.CalibrationOverlay
import app.aircons.ui.screens.HomeScreen
import app.aircons.ui.screens.LabScreen
import app.aircons.ui.screens.ModeScreen
import app.aircons.ui.screens.SettingsScreen
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface
import app.aircons.ui.theme.Vermilion
import app.aircons.util.hasBluetoothConnect
import app.aircons.util.hasNotificationPermission

private const val SCREEN_HOME = "home"
private const val SCREEN_MODE = "mode"
private const val SCREEN_LAB = "lab"
private const val SCREEN_SETTINGS = "settings"

@Composable
fun AirConsRoot() {
    val context = LocalContext.current
    val settingsStore = AirConsApp.instance.settings
    val settings by settingsStore.state.collectAsState()

    val link by SessionBus.link.collectAsState()
    val battery by SessionBus.battery.collectAsState()
    val deviceName by SessionBus.deviceName.collectAsState()
    val frame by SessionBus.frame.collectAsState()
    val motion by SessionBus.motion.collectAsState()
    val pps by SessionBus.pps.collectAsState()
    val sessionActive by SessionBus.sessionActive.collectAsState()
    val calibration by SessionBus.calibration.collectAsState()
    val error by SessionBus.error.collectAsState()

    var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var settingsReturn by rememberSaveable { mutableStateOf(SCREEN_HOME) }
    var selectedModeKey by rememberSaveable { mutableStateOf(settings.modeKey) }
    var showCalibration by rememberSaveable { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<ControlMode?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    val selectedMode = ControlMode.fromKey(selectedModeKey)
    val liveMode by SessionBus.mode.collectAsState()

    LaunchedEffect(liveMode, sessionActive) {
        if (sessionActive) selectedModeKey = liveMode.key
    }

    fun startNow(mode: ControlMode) {
        settingsStore.setMode(mode)
        selectedModeKey = mode.key
        AirConsService.start(context, mode)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val mode = pendingMode
        pendingMode = null
        if (mode != null) startNow(mode)
    }

    val bluetoothStartLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mode = pendingMode
        pendingMode = null
        val granted = result[Manifest.permission.BLUETOOTH_CONNECT] == true ||
            hasBluetoothConnect(context)
        if (!granted) {
            SessionBus.setError("Bluetooth permission is required to reach the AirPods.")
        } else if (mode != null) {
            if (!hasNotificationPermission(context) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                pendingMode = mode
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startNow(mode)
            }
        }
    }

    fun startSession(mode: ControlMode) {
        selectedModeKey = mode.key
        settingsStore.setMode(mode)
        if (!settingsStore.state.value.phoneMotion && !hasBluetoothConnect(context)) {
            pendingMode = mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothStartLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            } else {
                SessionBus.setError("Bluetooth is required to reach the AirPods.")
                pendingMode = null
            }
            return
        }
        if (!hasNotificationPermission(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            pendingMode = mode
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startNow(mode)
    }

    LaunchedEffect(Unit) {
        if (settings.checkUpdatesOnLaunch) {
            val info = UpdateManager.check().getOrNull()
            if (info != null && UpdateManager.isNewer(info)) {
                updateInfo = info
            }
        }
    }

    BackHandler(enabled = showCalibration || screen != SCREEN_HOME) {
        if (showCalibration) {
            showCalibration = false
        } else {
            screen = SCREEN_HOME
        }
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = screen, animationSpec = tween(220), label = "screen") { current ->
            when (current) {
                SCREEN_MODE -> {
                    ModeScreen(
                        mode = selectedMode,
                        settings = settings,
                        link = link,
                        sessionActive = sessionActive,
                        motion = motion,
                        frame = frame,
                        battery = battery,
                        pps = pps,
                        calibration = calibration,
                        onBack = { screen = SCREEN_HOME },
                        onStart = { startSession(selectedMode) },
                        onStop = { AirConsService.stop(context) },
                        onRecenter = { AirConsService.recenter(context) },
                        onOpenCalibration = { showCalibration = true },
                        onOpenSettings = {
                            settingsReturn = SCREEN_MODE
                            screen = SCREEN_SETTINGS
                        },
                        onUpdateSettings = { transform -> settingsStore.update(transform) }
                    )
                }

                SCREEN_LAB -> {
                    LabScreen(
                        settings = settings,
                        onBack = { screen = SCREEN_HOME },
                        onUpdateSettings = { transform -> settingsStore.update(transform) }
                    )
                }

                SCREEN_SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        onBack = { screen = settingsReturn },
                        onUpdateSettings = { transform -> settingsStore.update(transform) }
                    )
                }

                else -> {
                    HomeScreen(
                        settings = settings,
                        link = link,
                        sessionActive = sessionActive,
                        battery = battery,
                        deviceName = deviceName,
                        frame = frame,
                        pps = pps,
                        updateInfo = updateInfo,
                        onSelectDevice = { device ->
                            settingsStore.setTarget(device.address, device.name)
                        },
                        onOpenMode = { mode ->
                            selectedModeKey = mode.key
                            settingsStore.setMode(mode)
                            screen = SCREEN_MODE
                        },
                        onStart = { mode -> startSession(mode) },
                        onOpenLab = { screen = SCREEN_LAB },
                        onOpenSettings = {
                            settingsReturn = SCREEN_HOME
                            screen = SCREEN_SETTINGS
                        }
                    )
                }
            }
        }

        if (showCalibration) {
            CalibrationOverlay(
                mode = selectedMode,
                frame = frame,
                sessionActive = sessionActive,
                calibration = calibration,
                onRecenter = { AirConsService.recenter(context) },
                onClose = { showCalibration = false }
            )
        }

        if (error != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .border(1.dp, Vermilion.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                val canFallback = error?.let {
                    it.contains("AirPods control link", ignoreCase = true) ||
                        it.contains("Phone motion", ignoreCase = true) ||
                        it.contains("No AirPods", ignoreCase = true)
                } == true
                if (canFallback) {
                    Text(
                        "USE PHONE",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cobalt)
                            .clickable {
                                settingsStore.update { it.copy(phoneMotion = true) }
                                SessionBus.clearError()
                                startSession(ControlMode.fromKey(selectedModeKey))
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Paper
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "DISMISS",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Vermilion.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { SessionBus.clearError() }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Vermilion
                )
            }
        }
    }
}
