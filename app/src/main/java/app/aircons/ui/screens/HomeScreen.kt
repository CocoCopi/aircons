package app.aircons.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.aircons.BuildConfig
import app.aircons.data.AppSettings
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.LinkState
import app.aircons.data.PodBattery
import app.aircons.data.SessionBus
import app.aircons.data.UpdateInfo
import app.aircons.ui.components.BatteryCluster
import app.aircons.ui.components.GhostAction
import app.aircons.ui.components.Hairline
import app.aircons.ui.components.Kicker
import app.aircons.ui.components.LiveOrb
import app.aircons.ui.components.ModeCard
import app.aircons.ui.components.PaperBackground
import app.aircons.ui.components.PrimaryAction
import app.aircons.ui.components.SectionLabel
import app.aircons.ui.components.StatusPill
import app.aircons.ui.components.Wordmark
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.DisplaySerif
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface
import app.aircons.util.BondedDevice
import app.aircons.util.hasBluetoothConnect
import app.aircons.util.loadBondedDevices
import app.aircons.util.openBluetoothSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    settings: AppSettings,
    link: LinkState,
    sessionActive: Boolean,
    battery: PodBattery,
    deviceName: String?,
    frame: ControlFrame,
    pps: Int,
    updateInfo: UpdateInfo?,
    onSelectDevice: (BondedDevice) -> Unit,
    onOpenMode: (ControlMode) -> Unit,
    onStart: (ControlMode) -> Unit,
    onOpenLab: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var devices by remember { mutableStateOf<List<BondedDevice>>(emptyList()) }
    var bluetoothGranted by remember { mutableStateOf(hasBluetoothConnect(context)) }
    var refresh by remember { mutableIntStateOf(0) }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    LaunchedEffect(refresh) {
        bluetoothGranted = hasBluetoothConnect(context)
        if (bluetoothGranted) {
            devices = withContext(Dispatchers.IO) { loadBondedDevices(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedMode = ControlMode.fromKey(settings.modeKey)

    PaperBackground {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 780.dp
            val displaySize = if (maxWidth < 430.dp) 50.sp else 68.sp
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 1180.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (wide) 40.dp else 22.dp)
                    .padding(top = 14.dp, bottom = 34.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wordmark()
                    Spacer(Modifier.weight(1f))
                    StatusPill(link, sessionActive)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, Line, CircleShape)
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text("SET", style = MaterialTheme.typography.labelSmall, color = Ink)
                    }
                }

                Spacer(Modifier.height(if (wide) 42.dp else 28.dp))

                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                        Column(Modifier.weight(1.15f)) {
                            Hero(displaySize)
                        }
                        Column(Modifier.weight(1f)) {
                            ConnectionCard(
                                settings = settings,
                                link = link,
                                sessionActive = sessionActive,
                                battery = battery,
                                deviceName = deviceName,
                                frame = frame,
                                pps = pps,
                                devices = devices,
                                bluetoothGranted = bluetoothGranted,
                                selectedMode = selectedMode,
                                onSelectDevice = onSelectDevice,
                                onStart = onStart,
                                onOpenMode = onOpenMode,
                                onOpenLab = onOpenLab,
                                onRequestBluetooth = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        bluetoothLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.BLUETOOTH_SCAN,
                                                Manifest.permission.BLUETOOTH_CONNECT
                                            )
                                        )
                                    } else {
                                        openBluetoothSettings(context)
                                    }
                                },
                                onOpenBluetoothSettings = { openBluetoothSettings(context) }
                            )
                        }
                    }
                } else {
                    Hero(displaySize)
                    Spacer(Modifier.height(26.dp))
                    ConnectionCard(
                        settings = settings,
                        link = link,
                        sessionActive = sessionActive,
                        battery = battery,
                        deviceName = deviceName,
                        frame = frame,
                        pps = pps,
                        devices = devices,
                        bluetoothGranted = bluetoothGranted,
                        selectedMode = selectedMode,
                        onSelectDevice = onSelectDevice,
                        onStart = onStart,
                        onOpenMode = onOpenMode,
                        onOpenLab = onOpenLab,
                        onRequestBluetooth = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                bluetoothLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_SCAN,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                )
                            } else {
                                openBluetoothSettings(context)
                            }
                        },
                        onOpenBluetoothSettings = { openBluetoothSettings(context) }
                    )
                }

                Spacer(Modifier.height(38.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Three ways to play")
                    Spacer(Modifier.weight(1f))
                    Text("03 MODES", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Spacer(Modifier.height(14.dp))

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ControlMode.entries.forEach { mode ->
                            ModeCard(
                                mode = mode,
                                onClick = { onOpenMode(mode) },
                                active = mode == selectedMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ControlMode.entries.forEach { mode ->
                            ModeCard(
                                mode = mode,
                                onClick = { onOpenMode(mode) },
                                active = mode == selectedMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AirCons ${BuildConfig.VERSION_NAME}  ·  GPLv3  ·  light only",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "CODE LAB ↗",
                        modifier = Modifier.clickable(onClick = onOpenLab),
                        style = MaterialTheme.typography.labelSmall,
                        color = Cobalt
                    )
                }
                if (updateInfo != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Cobalt.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .background(Cobalt.copy(alpha = 0.05f))
                            .clickable(onClick = onOpenSettings)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "New build available · ${updateInfo.tag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink
                        )
                        Spacer(Modifier.weight(1f))
                        Text("GET ↗", style = MaterialTheme.typography.labelSmall, color = Cobalt)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Scripts live: ${SessionBus.script.value}  ·  pack ${pps} Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun Hero(displaySize: androidx.compose.ui.unit.TextUnit) {
    Column {
        Kicker("AirPods → game controller")
        Spacer(Modifier.height(14.dp))
        Text(
            "Your ears,\nin play.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = DisplaySerif,
                fontSize = displaySize,
                lineHeight = displaySize * 0.92f
            ),
            color = Ink
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Head aim. A one-pod sidearm. Two-handed steering. " +
                "No dongle, no extra hardware — just the pods already in your ears.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            modifier = Modifier.widthIn(max = 520.dp)
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            MiniStat("SENSOR", "25–100 Hz")
            MiniStat("LINK", "L2CAP 4097")
            MiniStat("MODE", "LIGHT")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

@Composable
private fun ConnectionCard(
    settings: AppSettings,
    link: LinkState,
    sessionActive: Boolean,
    battery: PodBattery,
    deviceName: String?,
    frame: ControlFrame,
    pps: Int,
    devices: List<BondedDevice>,
    bluetoothGranted: Boolean,
    selectedMode: ControlMode,
    onSelectDevice: (BondedDevice) -> Unit,
    onStart: (ControlMode) -> Unit,
    onOpenMode: (ControlMode) -> Unit,
    onOpenLab: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Line, shape)
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Your pods", color = Cobalt)
                Spacer(Modifier.weight(1f))
                Text(
                    if (sessionActive) "SESSION ON" else "STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sessionActive) Cobalt else Muted
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                deviceName ?: settings.targetName ?: "No pods selected",
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 86.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (link) {
                    LinkState.STREAMING -> "Streaming head motion at $pps Hz."
                    LinkState.CONNECTED -> "Control link open. Waiting for motion."
                    LinkState.CONNECTING -> "Opening the AirPods control channel…"
                    LinkState.ERROR -> "Link error. Reconnect the pods and try again."
                    LinkState.IDLE -> "Pair once in Android settings, then choose the pods here."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(end = 78.dp)
            )

            Spacer(Modifier.height(18.dp))
            BatteryCluster(battery)
            Spacer(Modifier.height(16.dp))

            if (!bluetoothGranted) {
                GhostAction(
                    "Grant Nearby devices",
                    onClick = onRequestBluetooth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (devices.isEmpty()) {
                GhostAction(
                    "Open Bluetooth settings",
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.forEach { device ->
                        DeviceChip(
                            device = device,
                            selected = device.address == settings.targetAddress,
                            onClick = { onSelectDevice(device) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Motion control needs AirPods Pro, AirPods 3 or newer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryAction(
                    text = if (sessionActive) "Open ${selectedMode.title}" else "Start ${selectedMode.title}",
                    onClick = {
                        if (sessionActive) onOpenMode(selectedMode) else onStart(selectedMode)
                    },
                    modifier = Modifier.weight(1f),
                    color = Ink
                )
                GhostAction("Tune", onClick = { onOpenMode(selectedMode) })
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostAction("Live code", onClick = onOpenLab, modifier = Modifier.weight(1f))
                GhostAction("Bluetooth", onClick = onOpenBluetoothSettings, modifier = Modifier.weight(1f))
            }
        }
        LiveOrb(
            frame = frame,
            mode = selectedMode,
            active = sessionActive,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(96.dp)
        )
    }
}

@Composable
private fun DeviceChip(device: BondedDevice, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Ink else Paper)
            .border(1.dp, if (selected) Ink else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (selected) Paper else Cobalt)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            device.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Paper else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
