package app.aircons.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aircons.core.PluginManager
import app.aircons.data.AppSettings
import app.aircons.data.PluginInfo
import app.aircons.ui.components.GhostAction
import app.aircons.ui.components.Hairline
import app.aircons.ui.components.Kicker
import app.aircons.ui.components.PageHeader
import app.aircons.ui.components.PaperBackground
import app.aircons.ui.components.PrimaryAction
import app.aircons.ui.components.SectionLabel
import app.aircons.ui.components.TopBar
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Mono
import app.aircons.ui.theme.Moss
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface
import app.aircons.ui.theme.Vermilion

@Composable
fun LabScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val available by PluginManager.available.collectAsState()
    val syncing by PluginManager.syncing.collectAsState()
    val status by PluginManager.status.collectAsState()
    val logs by PluginManager.logs.collectAsState()
    val activeName by PluginManager.activeName.collectAsState()
    val manifestVersion by PluginManager.manifestVersion.collectAsState()

    var editor by remember { mutableStateOf(PluginManager.readScript(settings.activePluginId)) }
    var localMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.activePluginId) {
        editor = PluginManager.readScript(settings.activePluginId)
    }

    PaperBackground {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 10.dp, bottom = 38.dp)
        ) {
            TopBar(onBack = onBack, title = "LIVE CODE")
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 900.dp)) {
                PageHeader(
                    kicker = "Dynamic source",
                    title = "Change the mapping\nwithout reinstalling.",
                    body = "AirCons fetches JavaScript from the repository, caches it on device, and runs it inside the controller loop. Sync a new profile, press apply, keep playing."
                )
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryAction(
                    text = if (syncing) "Syncing…" else "Sync from GitHub",
                    onClick = { PluginManager.syncNow() },
                    enabled = !syncing,
                    color = Cobalt
                )
                GhostAction(
                    "Open repo",
                    onClick = { uriHandler.openUri(PluginManager.REPO_URL) }
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                MiniMeta("ACTIVE", activeName)
                MiniMeta("MANIFEST", if (manifestVersion == 0) "local" else "v$manifestVersion")
                MiniMeta("RAW", "main / plugins")
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Available scripts")
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                available.forEach { info ->
                    ScriptRow(
                        info = info,
                        active = if (!settings.scriptsEnabled) {
                            info.id == "builtin"
                        } else {
                            info.id == settings.activePluginId
                        },
                        onApply = {
                            val applied = PluginManager.activate(info.id)
                            if (applied) {
                                if (info.id == "builtin") {
                                    onUpdateSettings {
                                        it.copy(scriptsEnabled = false, activePluginId = "builtin")
                                    }
                                } else {
                                    onUpdateSettings {
                                        it.copy(scriptsEnabled = true, activePluginId = info.id)
                                    }
                                    editor = PluginManager.readScript(info.id)
                                }
                                localMessage = "Applied ${info.name}."
                            } else {
                                localMessage = PluginManager.status.value
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("Editor")
            Spacer(Modifier.height(10.dp))
            Text(
                "Define function map(input). Return lookX, lookY, steer, throttle and fire. The script sees mode, yaw, pitch, roll, accelH, accelV and trigger.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = editor,
                onValueChange = { editor = it },
                textStyle = TextStyle(
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = Ink
                ),
                cursorBrush = SolidColor(Cobalt),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Paper)
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PrimaryAction(
                    text = "Run & apply",
                    onClick = {
                        val ok = PluginManager.activateSource(editor)
                        localMessage = if (ok) {
                            onUpdateSettings {
                                it.copy(scriptsEnabled = true, activePluginId = "custom")
                            }
                            "Custom script is live."
                        } else {
                            PluginManager.status.value
                        }
                    },
                    color = Verdant
                )
                GhostAction(
                    "Reload active",
                    onClick = {
                        editor = PluginManager.readScript(settings.activePluginId)
                        localMessage = "Reloaded ${settings.activePluginId}."
                    }
                )
                GhostAction(
                    "Reset built-in",
                    onClick = {
                        PluginManager.activate("builtin")
                        onUpdateSettings {
                            it.copy(scriptsEnabled = false, activePluginId = "builtin")
                        }
                        editor = PluginManager.readScript("builtin")
                        localMessage = "Built-in mapping restored."
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface)
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Runtime", color = Verdant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (localMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(localMessage!!, style = MaterialTheme.typography.bodySmall, color = Ink)
                    }
                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        logs.takeLast(8).joinToString("\n").ifBlank { "No runtime messages yet." },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, lineHeight = 18.sp),
                        color = Muted
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Scripts run with the app's permissions. Only load code you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vermilion
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMeta(label: String, value: String) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScriptRow(info: PluginInfo, active: Boolean, onApply: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Cobalt.copy(alpha = 0.05f) else Surface)
            .border(1.dp, if (active) Cobalt.copy(alpha = 0.32f) else Line, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.width(8.dp))
                Text(
                    info.version,
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted
                )
            }
            if (info.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (active) {
            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Cobalt)
        } else {
            Text(
                "APPLY ↗",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Line, RoundedCornerShape(8.dp))
                    .clickable(onClick = onApply)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Ink
            )
        }
    }
}

private val Verdant = Color(0xFF1F6F5C)
