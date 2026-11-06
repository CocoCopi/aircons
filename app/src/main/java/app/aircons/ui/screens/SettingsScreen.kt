package app.aircons.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.aircons.core.PluginManager
import app.aircons.core.UpdateManager
import app.aircons.data.AppSettings
import app.aircons.data.UpdateInfo
import app.aircons.ui.components.AnchorMap
import app.aircons.ui.components.GhostAction
import app.aircons.ui.components.Hairline
import app.aircons.ui.components.Kicker
import app.aircons.ui.components.PageHeader
import app.aircons.ui.components.PaperBackground
import app.aircons.ui.components.PrimaryAction
import app.aircons.ui.components.SectionLabel
import app.aircons.ui.components.ToggleLine
import app.aircons.ui.components.TopBar
import app.aircons.ui.components.ValueSlider
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Moss
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface
import app.aircons.ui.theme.Vermilion
import app.aircons.util.hasBluetoothConnect
import app.aircons.util.hasNotificationPermission
import app.aircons.util.isAccessibilityEnabled
import app.aircons.util.openAccessibilitySettings
import app.aircons.util.openNotificationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var refresh by remember { mutableIntStateOf(0) }
    var bluetoothGranted by remember { mutableStateOf(hasBluetoothConnect(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var installAllowed by remember { mutableStateOf(UpdateManager.canInstall(context)) }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(refresh) {
        bluetoothGranted = hasBluetoothConnect(context)
        notificationGranted = hasNotificationPermission(context)
        accessibilityOn = isAccessibilityEnabled(context)
        installAllowed = UpdateManager.canInstall(context)
    }

    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    PaperBackground {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 10.dp, bottom = 40.dp)
        ) {
            TopBar(onBack = onBack, title = "SETTINGS")
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 900.dp)) {
                PageHeader(
                    kicker = "The quiet controls",
                    title = "Make it yours.",
                    body = "Nothing here is hidden behind an account. Permissions, injection anchors, updates and the fine feel of the controller."
                )
            }

            Spacer(Modifier.height(26.dp))
            SettingsCard("Permissions", "Required for a live session") {
                PermissionRow(
                    title = "Nearby devices",
                    detail = "Find and talk to your AirPods over Bluetooth.",
                    granted = bluetoothGranted,
                    action = "GRANT",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                            )
                        }
                    }
                )
                Hairline()
                PermissionRow(
                    title = "Notifications",
                    detail = "Keep the controller alive in the background.",
                    granted = notificationGranted,
                    action = "GRANT",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    }
                )
                Hairline()
                PermissionRow(
                    title = "Touch injection",
                    detail = "Accessibility service that turns motion into swipes and taps.",
                    granted = accessibilityOn,
                    action = "OPEN",
                    onAction = { openAccessibilitySettings(context) }
                )
                Hairline()
                PermissionRow(
                    title = "Install updates",
                    detail = "Let AirCons open APK updates from the repo.",
                    granted = installAllowed,
                    action = "ALLOW",
                    onAction = { UpdateManager.requestInstallPermission(context) }
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsCard("Session", "How the controller behaves") {
                ToggleLine(
                    "In-game HUD",
                    "A minimal reticle drawn over the game.",
                    settings.hudEnabled
                ) { value -> onUpdateSettings { it.copy(hudEnabled = value) } }
                Hairline()
                ToggleLine(
                    "Haptics",
                    "A short tick when the trigger fires.",
                    settings.haptics
                ) { value -> onUpdateSettings { it.copy(haptics = value) } }
                Hairline()
                ToggleLine(
                    "Auto-fire",
                    "Repeat taps during a trigger pulse.",
                    settings.autoFire
                ) { value -> onUpdateSettings { it.copy(autoFire = value) } }
                Hairline()
                ToggleLine(
                    "Alternate tracking packets",
                    "Use the newer AirPods packet flavor. Turn off if motion is silent.",
                    settings.alternatePackets
                ) { value -> onUpdateSettings { it.copy(alternatePackets = value) } }
                Hairline()
                ToggleLine(
                    "Check updates on launch",
                    "Show a quiet update pill when a newer build exists.",
                    settings.checkUpdatesOnLaunch
                ) { value -> onUpdateSettings { it.copy(checkUpdatesOnLaunch = value) } }
            }

            Spacer(Modifier.height(16.dp))
            SettingsCard("Injection map", "Where virtual touches land") {
                AnchorMap(
                    lookX = settings.lookAnchorX,
                    lookY = settings.lookAnchorY,
                    steerX = settings.steerAnchorX,
                    steerY = settings.steerAnchorY,
                    fireX = settings.fireAnchorX,
                    fireY = settings.fireAnchorY
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Normalized screen coordinates. Move the anchors to match your game's look area, steering joystick and fire button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(8.dp))
                AnchorSliders(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsCard("Update", "Built by GitHub Actions") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AirCons ${UpdateManager.currentVersion}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink
                        )
                        Text(
                            "Rolling APK release from the repository.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted
                        )
                    }
                    if (checking || downloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Cobalt
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GhostAction(
                        if (checking) "Checking…" else "Check for updates",
                        onClick = {
                            if (!checking && !downloading) {
                                checking = true
                                updateMessage = null
                                scope.launch {
                                    val result = UpdateManager.check()
                                    checking = false
                                    val info = result.getOrNull()
                                    val newer = info != null && UpdateManager.isNewer(info)
                                    updateInfo = info.takeIf { newer }
                                    updateMessage = result.exceptionOrNull()?.localizedMessage
                                        ?: when {
                                            info == null -> "No APK asset found."
                                            !newer -> "You're on the latest build."
                                            else -> null
                                        }
                                }
                            }
                        },
                        enabled = !checking && !downloading
                    )
                    if (updateInfo != null) {
                        PrimaryAction(
                            if (downloading) "Downloading $progress%" else "Download & install",
                            onClick = {
                                val info = updateInfo
                                if (info != null && !downloading) {
                                    downloading = true
                                    progress = 0
                                    scope.launch {
                                        val result = UpdateManager.download(context, info) { value ->
                                            scope.launch(Dispatchers.Main) { progress = value }
                                        }
                                        downloading = false
                                        result.onSuccess { file ->
                                            val opened = UpdateManager.install(context, file)
                                            updateMessage = if (opened) {
                                                "Installer opened."
                                            } else {
                                                "Allow installs from AirCons, then try again."
                                            }
                                        }.onFailure { error ->
                                            updateMessage = error.localizedMessage
                                        }
                                    }
                                }
                            },
                            enabled = !downloading,
                            color = Cobalt
                        )
                    }
                }
                if (updateInfo != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        updateInfo!!.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink
                    )
                    Text(
                        updateInfo!!.tag + " · " + formatSize(updateInfo!!.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    if (updateInfo!!.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            updateInfo!!.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (updateMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        updateMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Vermilion
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsCard("About", "Free software, no account") {
                Text(
                    "AirCons turns the AirPods already connected to your Android phone into a motion controller. Light mode only, by design.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Protocol research builds on LibrePods and the public Apple Accessory Protocol work. AirCons is licensed GPLv3. AirPods is a trademark of Apple Inc.; AirCons is not affiliated with Apple.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GhostAction(
                        "Repository ↗",
                        onClick = { uriHandler.openUri(PluginManager.REPO_URL) }
                    )
                    GhostAction(
                        "Scripts ↗",
                        onClick = { uriHandler.openUri(PluginManager.RAW_BASE + "plugins/") }
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            Text(
                "AirCons · ${UpdateManager.currentVersion} · light only",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Line, shape)
            .padding(20.dp)
    ) {
        Kicker(title, color = Cobalt)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (granted) Moss else Vermilion)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) {
            Text("ON", style = MaterialTheme.typography.labelSmall, color = Moss)
        } else {
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Ink
            )
        }
    }
}

@Composable
private fun AnchorSliders(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    ValueSlider(
        "Look anchor · X",
        percent(settings.lookAnchorX),
        settings.lookAnchorX,
        0.05f..0.95f,
        accent = Cobalt
    ) { value -> onUpdateSettings { it.copy(lookAnchorX = value) } }
    ValueSlider(
        "Look anchor · Y",
        percent(settings.lookAnchorY),
        settings.lookAnchorY,
        0.05f..0.95f,
        accent = Cobalt
    ) { value -> onUpdateSettings { it.copy(lookAnchorY = value) } }
    ValueSlider(
        "Steer anchor · X",
        percent(settings.steerAnchorX),
        settings.steerAnchorX,
        0.05f..0.95f,
        accent = Moss
    ) { value -> onUpdateSettings { it.copy(steerAnchorX = value) } }
    ValueSlider(
        "Steer anchor · Y",
        percent(settings.steerAnchorY),
        settings.steerAnchorY,
        0.05f..0.95f,
        accent = Moss
    ) { value -> onUpdateSettings { it.copy(steerAnchorY = value) } }
    ValueSlider(
        "Fire anchor · X",
        percent(settings.fireAnchorX),
        settings.fireAnchorX,
        0.05f..0.95f,
        accent = Vermilion
    ) { value -> onUpdateSettings { it.copy(fireAnchorX = value) } }
    ValueSlider(
        "Fire anchor · Y",
        percent(settings.fireAnchorY),
        settings.fireAnchorY,
        0.05f..0.95f,
        accent = Vermilion
    ) { value -> onUpdateSettings { it.copy(fireAnchorY = value) } }
}

private fun percent(value: Float): String = String.format(Locale.US, "%.0f%%", value * 100f)

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "APK"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
