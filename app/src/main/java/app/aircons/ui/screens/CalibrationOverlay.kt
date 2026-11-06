package app.aircons.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aircons.data.CalibrationState
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.ui.components.GhostAction
import app.aircons.ui.components.Kicker
import app.aircons.ui.components.ModeGlyph
import app.aircons.ui.components.PrimaryAction
import app.aircons.ui.components.ReticlePreview
import app.aircons.ui.components.SectionLabel
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Moss
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.Surface

@Composable
fun CalibrationOverlay(
    mode: ControlMode,
    frame: ControlFrame,
    sessionActive: Boolean,
    calibration: CalibrationState,
    onRecenter: () -> Unit,
    onClose: () -> Unit
) {
    val accent = Color(mode.accent)
    LaunchedEffect(Unit) {
        if (sessionActive) onRecenter()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        ModeGlyph(
            mode = mode,
            color = accent.copy(alpha = 0.07f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(420.dp)
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Zero · ${mode.title}", accent)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .border(1.dp, Line, CircleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("CLOSE", style = MaterialTheme.typography.labelSmall, color = Ink)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Surface)
                        .border(1.dp, Line, RoundedCornerShape(30.dp))
                        .padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SectionLabel("Calibration")
                    Spacer(Modifier.height(14.dp))

                    when {
                        !sessionActive -> {
                            Text(
                                "Start a session first.",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Ink,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The pods need to be streaming before AirCons can learn a neutral pose.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(22.dp))
                            GhostAction("Back to modes", onClose, Modifier.fillMaxWidth())
                        }

                        calibration == CalibrationState.COLLECTING -> {
                            CircularProgressIndicator(
                                color = accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                "Hold still.",
                                style = MaterialTheme.typography.displaySmall,
                                color = Ink,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                mode.hold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Collecting 18 motion samples. Keep the pose steady for about a second.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }

                        else -> {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Moss.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", style = MaterialTheme.typography.titleLarge, color = Moss)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Zeroed.",
                                style = MaterialTheme.typography.displaySmall,
                                color = Ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Move around and watch the dot. Re-zero any time from this screen or with a double stem press.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            ReticlePreview(
                                frame = frame,
                                mode = mode,
                                active = true,
                                modifier = Modifier.size(190.dp)
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                GhostAction("Zero again", onRecenter, Modifier.weight(1f))
                                PrimaryAction(
                                    "Done",
                                    onClose,
                                    Modifier.weight(1f),
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
