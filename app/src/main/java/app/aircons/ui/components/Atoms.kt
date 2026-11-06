package app.aircons.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.LinkState
import app.aircons.data.PodBattery
import app.aircons.ui.theme.Cobalt
import app.aircons.ui.theme.DisplaySerif
import app.aircons.ui.theme.Ink
import app.aircons.ui.theme.Line
import app.aircons.ui.theme.Mono
import app.aircons.ui.theme.Moss
import app.aircons.ui.theme.Muted
import app.aircons.ui.theme.Paper
import app.aircons.ui.theme.PaperDeep
import app.aircons.ui.theme.Surface
import app.aircons.ui.theme.Vermilion
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PaperBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Paper, Color(0xFFF3EFE6)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            drawCircle(
                color = Cobalt.copy(alpha = 0.045f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.03f)
            )
            drawCircle(
                color = Vermilion.copy(alpha = 0.035f),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.05f, size.height * 0.94f)
            )
        }
        content()
    }
}

@Composable
fun Wordmark(size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = DisplaySerif, fontWeight = FontWeight.Medium)) {
                append("Air")
            }
            withStyle(
                SpanStyle(
                    fontFamily = DisplaySerif,
                    fontStyle = FontStyle.Italic,
                    color = Cobalt
                )
            ) {
                append("Cons")
            }
            withStyle(SpanStyle(color = Vermilion)) { append(".") }
        },
        fontSize = size,
        color = Ink
    )
}

@Composable
fun StatusPill(link: LinkState, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by transition.animateFloat(
        initialValue = if (active) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "statusAlpha"
    )
    val color = when (link) {
        LinkState.STREAMING -> Moss
        LinkState.CONNECTED -> Cobalt
        LinkState.CONNECTING -> Vermilion
        LinkState.ERROR -> MaterialTheme.colorScheme.error
        LinkState.IDLE -> Muted
    }
    val label = when (link) {
        LinkState.STREAMING -> "LIVE"
        LinkState.CONNECTED -> "LINKED"
        LinkState.CONNECTING -> "LINKING"
        LinkState.ERROR -> "ERROR"
        LinkState.IDLE -> "OFFLINE"
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, Line, CircleShape)
            .background(Surface.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (active) pulse else 1f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Ink
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Line) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Muted) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
fun PageHeader(
    kicker: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium
) {
    Column(modifier) {
        SectionLabel(kicker, color = Cobalt)
        Spacer(Modifier.height(12.dp))
        Text(title, style = titleStyle, color = Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            modifier = Modifier.widthIn(max = 540.dp)
        )
    }
}

@Composable
fun ModeGlyph(mode: ControlMode, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val r = min(w, h) / 2f
        val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)

        when (mode) {
            ControlMode.HEAD -> {
                drawArc(
                    color = color.copy(alpha = 0.35f),
                    startAngle = -58f,
                    sweepAngle = 116f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.72f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = stroke
                )
                drawArc(
                    color = color.copy(alpha = 0.6f),
                    startAngle = -44f,
                    sweepAngle = 88f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.5f),
                    size = Size(r * 1.1f, r * 1.1f),
                    style = stroke
                )
                val head = Path().apply {
                    moveTo(c.x - r * 0.18f, c.y - r * 0.36f)
                    cubicTo(
                        c.x + r * 0.26f, c.y - r * 0.46f,
                        c.x + r * 0.42f, c.y - r * 0.06f,
                        c.x + r * 0.23f, c.y + r * 0.2f
                    )
                    cubicTo(
                        c.x + r * 0.12f, c.y + r * 0.35f,
                        c.x - r * 0.16f, c.y + r * 0.36f,
                        c.x - r * 0.27f, c.y + r * 0.17f
                    )
                    cubicTo(
                        c.x - r * 0.4f, c.y - r * 0.05f,
                        c.x - r * 0.35f, c.y - r * 0.28f,
                        c.x - r * 0.18f, c.y - r * 0.36f
                    )
                }
                drawPath(head, color = color, style = stroke)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(c.x + r * 0.28f, c.y - r * 0.32f),
                    size = Size(r * 0.13f, r * 0.5f),
                    cornerRadius = CornerRadius(r * 0.065f, r * 0.065f),
                    style = Stroke(width = 1.1.dp.toPx())
                )
                drawCircle(color = color.copy(alpha = 0.4f), radius = r * 0.045f, center = Offset(c.x + r * 0.46f, c.y - r * 0.42f))
            }

            ControlMode.SIDEARM -> {
                drawCircle(
                    color = color.copy(alpha = 0.22f),
                    radius = r * 0.52f,
                    center = c,
                    style = stroke
                )
                drawCircle(
                    color = color.copy(alpha = 0.45f),
                    radius = r * 0.24f,
                    center = c,
                    style = stroke
                )
                drawLine(color.copy(alpha = 0.4f), Offset(c.x - r * 0.72f, c.y), Offset(c.x - r * 0.4f, c.y), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.4f), Offset(c.x + r * 0.4f, c.y), Offset(c.x + r * 0.72f, c.y), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.4f), Offset(c.x, c.y - r * 0.72f), Offset(c.x, c.y - r * 0.4f), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.4f), Offset(c.x, c.y + r * 0.4f), Offset(c.x, c.y + r * 0.72f), strokeWidth = 1.dp.toPx())
                rotate(-34f, c) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(c.x - r * 0.15f, c.y - r * 0.58f),
                        size = Size(r * 0.3f, r * 1.12f),
                        cornerRadius = CornerRadius(r * 0.15f, r * 0.15f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                    drawRoundRect(
                        color = color.copy(alpha = 0.18f),
                        topLeft = Offset(c.x - r * 0.15f, c.y - r * 0.58f),
                        size = Size(r * 0.3f, r * 1.12f),
                        cornerRadius = CornerRadius(r * 0.15f, r * 0.15f)
                    )
                }
                drawCircle(color = Vermilion, radius = r * 0.05f, center = c)
            }

            ControlMode.WHEEL -> {
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = r * 0.62f,
                    center = c,
                    style = stroke
                )
                drawCircle(
                    color = color.copy(alpha = 0.5f),
                    radius = r * 0.2f,
                    center = c,
                    style = stroke
                )
                drawLine(color.copy(alpha = 0.3f), Offset(c.x - r * 0.58f, c.y), Offset(c.x - r * 0.18f, c.y), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.3f), Offset(c.x + r * 0.18f, c.y), Offset(c.x + r * 0.58f, c.y), strokeWidth = 1.dp.toPx())
                drawLine(color.copy(alpha = 0.3f), Offset(c.x, c.y - r * 0.58f), Offset(c.x, c.y - r * 0.18f), strokeWidth = 1.dp.toPx())
                rotate(-28f, Offset(c.x - r * 0.58f, c.y)) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.12f),
                        size = Size(r * 0.16f, r * 0.42f),
                        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
                    )
                }
                rotate(28f, Offset(c.x + r * 0.58f, c.y)) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(c.x + r * 0.56f, c.y - r * 0.3f),
                        size = Size(r * 0.16f, r * 0.42f),
                        cornerRadius = CornerRadius(r * 0.08f, r * 0.08f)
                    )
                }
                drawCircle(color = Vermilion, radius = r * 0.045f, center = Offset(c.x, c.y - r * 0.62f))
            }
        }
    }
}

@Composable
fun ModeCard(
    mode: ControlMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val accent = Color(mode.accent)
    val shape = RoundedCornerShape(26.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "modePress")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (active) accent.copy(alpha = 0.06f) else Surface)
            .border(1.dp, if (active) accent.copy(alpha = 0.4f) else Line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(20.dp)
            .heightIn(min = 178.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mode.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(22.dp).height(1.dp).background(accent.copy(alpha = 0.4f)))
                Spacer(Modifier.width(10.dp))
                Text(
                    mode.kicker.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(mode.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                mode.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.widthIn(max = 210.dp)
            )
        }
        ModeGlyph(
            mode = mode,
            color = accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(92.dp)
        )
        Text(
            "↗",
            modifier = Modifier.align(Alignment.TopEnd),
            style = MaterialTheme.typography.titleLarge,
            color = accent
        )
    }
}

@Composable
fun BatteryPill(
    label: String,
    level: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Ink
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Line, shape)
            .background(Paper.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.weight(1f))
            Text(
                text = level?.let { "$it" } ?: "—",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                color = accent
            )
            if (charging) {
                Text("↯", style = MaterialTheme.typography.labelSmall, color = Moss)
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Line.copy(alpha = 0.6f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth((level ?: 0).coerceIn(0, 100) / 100f)
                    .height(2.dp)
                    .background(if ((level ?: 0) < 20) Vermilion else accent)
            )
        }
    }
}

@Composable
fun BatteryCluster(battery: PodBattery, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BatteryPill("L", battery.left, battery.leftCharging, Modifier.weight(1f), Cobalt)
        BatteryPill("R", battery.right, battery.rightCharging, Modifier.weight(1f), Vermilion)
        BatteryPill("CASE", battery.case, battery.caseCharging, Modifier.weight(1f), Moss)
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "actionPress")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (enabled) color else Line)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 22.dp, vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Paper else Muted
            )
            Spacer(Modifier.width(12.dp))
            Text("→", style = MaterialTheme.typography.titleMedium, color = if (enabled) Paper else Muted)
        }
    }
}

@Composable
fun GhostAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, if (enabled) Line else Line.copy(alpha = 0.5f), CircleShape)
            .background(Surface.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) color else Muted
        )
    }
}

@Composable
fun ToggleLine(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Paper,
                checkedTrackColor = Ink,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = PaperDeep,
                uncheckedBorderColor = Line
            )
        )
    }
}

@Composable
fun ValueSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color = Cobalt,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono),
                color = accent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Line.copy(alpha = 0.65f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

@Composable
fun Stat(label: String, value: String, accent: Color = Ink) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono),
            color = accent
        )
    }
}

@Composable
fun LiveOrb(
    frame: ControlFrame,
    mode: ControlMode,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(mode.accent)
    val transition = rememberInfiniteTransition(label = "orb")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(14000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "orbDrift"
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                center = c,
                radius = r
            ),
            radius = r,
            center = c
        )
        drawCircle(
            color = Line,
            radius = r * 0.86f,
            center = c,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = accent.copy(alpha = 0.32f),
            radius = r * 0.58f,
            center = c,
            style = Stroke(width = 1.dp.toPx())
        )
        for (i in 0 until 3) {
            val angle = drift + i * 2.094f
            val radius = r * (0.66f + i * 0.07f)
            val point = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius)
            drawCircle(accent.copy(alpha = 0.45f - i * 0.1f), radius = 2.2.dp.toPx(), center = point)
        }
        val dotX = c.x + frame.lookX * r * 0.5f + frame.steer * r * 0.5f
        val dotY = c.y + frame.lookY * r * 0.5f - frame.throttle * r * 0.35f
        drawCircle(accent, radius = 5.dp.toPx(), center = Offset(dotX, dotY))
        drawCircle(Paper, radius = 2.dp.toPx(), center = Offset(dotX, dotY))
        if (active) {
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = r * 0.9f,
                center = c,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}

@Composable
fun ReticlePreview(
    frame: ControlFrame,
    mode: ControlMode,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(mode.accent)
    val transition = rememberInfiniteTransition(label = "reticle")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "reticlePulse"
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        drawCircle(
            color = accent.copy(alpha = 0.08f),
            radius = r,
            center = c
        )
        if (mode == ControlMode.WHEEL) {
            drawArc(
                color = accent.copy(alpha = 0.35f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.72f, c.y - r * 0.72f),
                size = Size(r * 1.44f, r * 1.44f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
            val angle = Math.toRadians((270 + frame.steer * 52).toDouble())
            val nx = c.x + cos(angle).toFloat() * r * 0.68f
            val ny = c.y + sin(angle).toFloat() * r * 0.68f
            drawLine(accent, c, Offset(nx, ny), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(accent, radius = 4.dp.toPx(), center = c)
        } else {
            drawCircle(
                color = accent.copy(alpha = if (active) 0.7f else 0.35f),
                radius = r * 0.3f,
                center = c,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = r * 0.64f * pulse,
                center = c,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawLine(accent.copy(alpha = 0.45f), Offset(c.x - r * 0.42f, c.y), Offset(c.x - r * 0.22f, c.y), strokeWidth = 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.45f), Offset(c.x + r * 0.22f, c.y), Offset(c.x + r * 0.42f, c.y), strokeWidth = 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.45f), Offset(c.x, c.y - r * 0.42f), Offset(c.x, c.y - r * 0.22f), strokeWidth = 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.45f), Offset(c.x, c.y + r * 0.22f), Offset(c.x, c.y + r * 0.42f), strokeWidth = 1.dp.toPx())
            val dot = Offset(c.x + frame.lookX * r * 0.55f, c.y + frame.lookY * r * 0.55f)
            drawLine(accent.copy(alpha = 0.35f), c, dot, strokeWidth = 0.8.dp.toPx())
            drawCircle(accent, radius = 4.dp.toPx(), center = dot)
            drawCircle(Paper, radius = 1.7.dp.toPx(), center = dot)
        }
    }
}

@Composable
fun AnchorMap(
    lookX: Float,
    lookY: Float,
    steerX: Float,
    steerY: Float,
    fireX: Float,
    fireY: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .background(PaperDeep.copy(alpha = 0.45f))
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            drawRoundRect(
                color = Line,
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        AnchorDot(maxWidth, maxHeight, lookX, lookY, Cobalt, "LOOK")
        AnchorDot(maxWidth, maxHeight, steerX, steerY, Moss, "STEER")
        AnchorDot(maxWidth, maxHeight, fireX, fireY, Vermilion, "FIRE")
        Text(
            "injection map",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.AnchorDot(
    maxWidth: Dp,
    maxHeight: Dp,
    x: Float,
    y: Float,
    color: Color,
    label: String
) {
    Box(
        Modifier
            .offset(
                x = (maxWidth * x).coerceIn(8.dp, maxWidth - 44.dp),
                y = (maxHeight * y).coerceIn(8.dp, maxHeight - 28.dp)
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun TopBar(
    onBack: (() -> Unit)? = null,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Ink
                )
            }
        } else {
            Wordmark()
        }
        if (title != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Kicker(text: String, color: Color = Cobalt) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
fun MonoText(text: String, color: Color = Ink, size: TextUnit = 12.sp) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = size),
        color = color
    )
}
