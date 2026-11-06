package app.aircons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.aircons.data.AppSettings
import app.aircons.data.CalibrationState
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.LinkState
import app.aircons.data.MotionSample
import app.aircons.data.PodBattery
import app.aircons.ui.components.GhostAction
import app.aircons.ui.components.Hairline
import app.aircons.ui.components.Kicker
import app.aircons.ui.components.ModeGlyph
import app.aircons.ui.components.PaperBackground
import app.aircons.ui.components.PrimaryAction
import app.aircons.ui.components.ReticlePreview
import app.aircons.ui.components.SectionLabel
import app.aircons.ui.components.StatusPill
import app.aircons.ui.components.ToggleLine
import app.aircons.ui.components.TopBar
import app.aircons.ui.components.ValueSlider
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Mono
import app.aircons.ui.theme.Moss
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface
import app.aircons.ui.theme.Vermilion
import app.aircons.util.isAccessibilityEnabled
import app.aircons.util.openAccessibilitySettings
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ModeScreen(
    mode: ControlMode,
    settings: AppSettings,
    link: LinkState,
    sessionActive: Boolean,
    motion: MotionSample,
    frame: ControlFrame,
    battery: PodBattery,
    pps: Int,
    calibration: CalibrationState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRecenter: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    val accent = Color(mode.accent)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PaperBackground {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 820.dp
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 1180.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (wide) 40.dp else 22.dp)
                    .padding(top = 10.dp, bottom = 36.dp)
            ) {
                TopBar(
                    onBack = onBack,
                    title = "MODE ${mode.number}",
                    trailing = { StatusPill(link, sessionActive) }
                )
                Spacer(Modifier.height(22.dp))

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1.05f)) {
                            ModeHero(mode, accent, wide = true)
                            Spacer(Modifier.height(20.dp))
                            LivePanel(
                                mode = mode,
                                accent = accent,
                                motion = motion,
                                frame = frame,
                                battery = battery,
                                pps = pps,
                                active = sessionActive,
                                calibration = calibration
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            SessionPanel(
                                mode = mode,
                                accent = accent,
                                settings = settings,
                                sessionActive = sessionActive,
                                accessibilityOn = accessibilityOn,
                                onStart = onStart,
                                onStop = onStop,
                                onRecenter = onRecenter,
                                onOpenCalibration = onOpenCalibration,
                                onOpenSettings = onOpenSettings,
                                onOpenAccessibility = { openAccessibilitySettings(context) }
                            )
                            Spacer(Modifier.height(16.dp))
                            TuningPanel(settings, accent, onUpdateSettings)
                            Spacer(Modifier.height(16.dp))
                            GestureCard(mode, accent)
                        }
                    }
                } else {
                    ModeHero(mode, accent, wide = false)
                    Spacer(Modifier.height(18.dp))
                    LivePanel(
                        mode = mode,
                        accent = accent,
                        motion = motion,
                        frame = frame,
                        battery = battery,
                        pps = pps,
                        active = sessionActive,
                        calibration = calibration
                    )
                    Spacer(Modifier.height(16.dp))
                    SessionPanel(
                        mode = mode,
                        accent = accent,
                        settings = settings,
                        sessionActive = sessionActive,
                        accessibilityOn = accessibilityOn,
                        onStart = onStart,
                        onStop = onStop,
                        onRecenter = onRecenter,
                        onOpenCalibration = onOpenCalibration,
                        onOpenSettings = onOpenSettings,
                        onOpenAccessibility = { openAccessibilitySettings(context) }
                    )
                    Spacer(Modifier.height(16.dp))
                    TuningPanel(settings, accent, onUpdateSettings)
                    Spacer(Modifier.height(16.dp))
                    GestureCard(mode, accent)
                }

                Spacer(Modifier.height(26.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Touch injection uses Android Accessibility. Games see normal swipes and taps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun ModeHero(mode: ControlMode, accent: Color, wide: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Kicker(mode.kicker, accent)
            Spacer(Modifier.height(8.dp))
            Text(
                mode.title,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = if (wide) MaterialTheme.typography.displayMedium.fontSize else
                        MaterialTheme.typography.displaySmall.fontSize
                ),
                color = Ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                mode.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = Muted,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
        ModeGlyph(
            mode = mode,
            color = accent,
            modifier = Modifier.size(if (wide) 142.dp else 104.dp)
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(
            mode.hold,
            style = MaterialTheme.typography.bodySmall,
            color = Ink
        )
    }
}

@Composable
private fun LivePanel(
    mode: ControlMode,
    accent: Color,
    motion: MotionSample,
    frame: ControlFrame,
    battery: PodBattery,
    pps: Int,
    active: Boolean,
    calibration: CalibrationState
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Line, shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Live telemetry", accent)
            Spacer(Modifier.weight(1f))
            Text(
                when (calibration) {
                    CalibrationState.COLLECTING -> "ZEROING"
                    CalibrationState.READY -> "ZEROED"
                    CalibrationState.IDLE -> if (active) "WAITING" else "STANDBY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (calibration == CalibrationState.READY) Moss else Muted
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReticlePreview(
                frame = frame,
                mode = mode,
                active = active,
                modifier = Modifier.size(142.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                TelemetryRow("YAW", formatDegrees(motion.yaw), accent)
                TelemetryRow("PITCH", formatDegrees(motion.pitch), accent)
                TelemetryRow("ROLL", formatDegrees(motion.roll), accent)
                TelemetryRow("PACKETS", "$pps Hz", Ink)
                TelemetryRow(
                    "BATTERY",
                    battery.minimum?.let { "$it%" } ?: "—",
                    if ((battery.minimum ?: 100) < 20) Vermilion else Ink
                )
            }
        }
        if (!active) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Start a session to stream motion and inject input.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
            color = color
        )
    }
}

@Composable
private fun SessionPanel(
    mode: ControlMode,
    accent: Color,
    settings: AppSettings,
    sessionActive: Boolean,
    accessibilityOn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRecenter: () -> Unit,
    onOpenCalibration: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Line, shape)
            .padding(20.dp)
    ) {
        SectionLabel("Session")
        Spacer(Modifier.height(10.dp))
        if (sessionActive) {
            PrimaryAction(
                text = "End session",
                onClick = onStop,
                color = Vermilion,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostAction("Set neutral", onClick = onRecenter, modifier = Modifier.weight(1f))
                GhostAction(
                    "Calibrate",
                    onClick = onOpenCalibration,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            PrimaryAction(
                text = "Start ${mode.title}",
                onClick = onStart,
                color = accent,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostAction(
                    "Calibrate",
                    onClick = onOpenCalibration,
                    enabled = false,
                    modifier = Modifier.weight(1f)
                )
                GhostAction("Anchors", onClick = onOpenSettings, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (accessibilityOn) Moss else Vermilion)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (accessibilityOn) "Touch injection ready" else "Touch injection disabled",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.weight(1f)
            )
            if (!accessibilityOn) {
                Text(
                    "ENABLE ↗",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Line, RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenAccessibility)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Vermilion
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Anchors decide where swipes and shots land in-game. Tune them in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        if (settings.autoFire) {
            Spacer(Modifier.height(4.dp))
            Text("Auto-fire is on · hold the stem trigger for repeat taps.", style = MaterialTheme.typography.bodySmall, color = accent)
        }
    }
}

@Composable
private fun TuningPanel(
    settings: AppSettings,
    accent: Color,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Line, shape)
            .padding(20.dp)
    ) {
        SectionLabel("Feel")
        Spacer(Modifier.height(4.dp))
        ValueSlider(
            label = "Sensitivity",
            valueLabel = String.format(Locale.US, "%.2f×", settings.sensitivity),
            value = settings.sensitivity,
            range = 0.3f..2.5f,
            accent = accent,
            onValueChange = { value -> onUpdateSettings { it.copy(sensitivity = value) } }
        )
        ValueSlider(
            label = "Smoothing",
            valueLabel = String.format(Locale.US, "%.2f", settings.smoothing),
            value = settings.smoothing,
            range = 0f..0.9f,
            accent = accent,
            onValueChange = { value -> onUpdateSettings { it.copy(smoothing = value) } }
        )
        ValueSlider(
            label = "Deadzone",
            valueLabel = String.format(Locale.US, "%.2f", settings.deadzone),
            value = settings.deadzone,
            range = 0f..0.25f,
            accent = accent,
            onValueChange = { value -> onUpdateSettings { it.copy(deadzone = value) } }
        )
        ValueSlider(
            label = "Injection rate",
            valueLabel = "${settings.injectionHz} Hz",
            value = settings.injectionHz.toFloat(),
            range = 8f..30f,
            steps = 21,
            accent = accent,
            onValueChange = { value ->
                onUpdateSettings { it.copy(injectionHz = value.roundToInt().coerceIn(8, 30)) }
            }
        )
        Spacer(Modifier.height(4.dp))
        Hairline()
        ToggleLine(
            label = "Invert pitch",
            description = "Flip the vertical axis.",
            checked = settings.invertPitch,
            onCheckedChange = { value -> onUpdateSettings { it.copy(invertPitch = value) } }
        )
        ToggleLine(
            label = "Auto-fire",
            description = "Repeat taps while the trigger pulse is held.",
            checked = settings.autoFire,
            onCheckedChange = { value -> onUpdateSettings { it.copy(autoFire = value) } }
        )
        ToggleLine(
            label = "In-game HUD",
            description = "Minimal reticle and telemetry over the game.",
            checked = settings.hudEnabled,
            onCheckedChange = { value -> onUpdateSettings { it.copy(hudEnabled = value) } }
        )
        if (ControlMode.fromKey(settings.modeKey) == ControlMode.WHEEL) {
            ToggleLine(
                label = "Swap wheel axes",
                description = "Use yaw for steering and roll for throttle.",
                checked = settings.swapWheelAxes,
                onCheckedChange = { value -> onUpdateSettings { it.copy(swapWheelAxes = value) } }
            )
        }
    }
}

@Composable
private fun GestureCard(mode: ControlMode, accent: Color) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Paper)
            .border(1.dp, Line, shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Stem map")
            Spacer(Modifier.weight(1f))
            Text(mode.kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Spacer(Modifier.height(12.dp))
        GestureRow("Single press", "Trigger / fire", accent)
        GestureRow("Double press", "Re-zero the controller", accent)
        GestureRow("Triple press", "Cycle to the next mode", accent)
        GestureRow("Long press", "Re-zero the controller", accent)
        Spacer(Modifier.height(10.dp))
        Text(
            "AirCons customizes the stems while a session is active, so media keys may behave differently.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }
}

@Composable
private fun GestureRow(label: String, value: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = accent)
    }
}

private fun formatDegrees(value: Float): String =
    String.format(Locale.US, "%+.1f°", value)
