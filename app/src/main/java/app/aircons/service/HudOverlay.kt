package app.aircons.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.LinkState
import app.aircons.data.PodBattery
import kotlin.math.cos
import kotlin.math.sin

class HudOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val view = HudView(service)
    private var added = false

    fun show() {
        if (added) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "AirCons HUD"
        }
        runCatching {
            windowManager.addView(view, params)
            added = true
        }
    }

    fun hide() {
        if (!added) return
        runCatching { windowManager.removeView(view) }
        added = false
    }

    fun update(
        frame: ControlFrame,
        mode: ControlMode,
        link: LinkState,
        battery: PodBattery,
        pps: Int
    ) {
        view.update(frame, mode, link, battery, pps)
    }

    private class HudView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private var frame = ControlFrame.ZERO
        private var mode = ControlMode.HEAD
        private var link = LinkState.IDLE
        private var battery = PodBattery()
        private var pps = 0

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 10f * density
            color = Color.parseColor("#141312")
        }
        private val tinyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 9f * density
            color = Color.parseColor("#6F6A61")
        }
        private val displayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("serif", Typeface.ITALIC)
            textSize = 17f * density
            color = Color.parseColor("#141312")
        }

        fun update(
            control: ControlFrame,
            controlMode: ControlMode,
            linkState: LinkState,
            podBattery: PodBattery,
            packetsPerSecond: Int
        ) {
            frame = control
            mode = controlMode
            link = linkState
            battery = podBattery
            pps = packetsPerSecond
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width == 0 || height == 0) return
            val accent = mode.accent.toInt()
            val top = 44f * density
            val left = 24f * density

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(215, 247, 244, 238)
            val plate = RectF(left - 12f * density, top - 18f * density, left + 172f * density, top + 45f * density)
            canvas.drawRoundRect(plate, 14f * density, 14f * density, paint)

            textPaint.color = accent
            canvas.drawText("A I R C O N S", left, top, textPaint)
            displayPaint.color = Color.parseColor("#141312")
            canvas.drawText(mode.title, left, top + 23f * density, displayPaint)

            val status = when (link) {
                LinkState.STREAMING -> "LIVE"
                LinkState.CONNECTED -> "LINKED"
                LinkState.CONNECTING -> "LINKING"
                LinkState.ERROR -> "ERROR"
                LinkState.IDLE -> "IDLE"
            }
            val statusColor = when (link) {
                LinkState.STREAMING -> Color.parseColor("#1F6F5C")
                LinkState.CONNECTED -> Color.parseColor("#0B5CFF")
                LinkState.CONNECTING -> Color.parseColor("#FF5A36")
                LinkState.ERROR -> Color.parseColor("#B3261E")
                LinkState.IDLE -> Color.parseColor("#6F6A61")
            }
            paint.style = Paint.Style.FILL
            paint.color = statusColor
            canvas.drawCircle(width - 92f * density, top - 3f * density, 3.5f * density, paint)
            tinyPaint.color = statusColor
            canvas.drawText(status, width - 84f * density, top, tinyPaint)

            val batteryText = battery.minimum?.let {
                "L ${battery.left ?: "--"}  R ${battery.right ?: "--"}  $pps Hz"
            } ?: "awaiting telemetry"
            tinyPaint.color = Color.parseColor("#6F6A61")
            canvas.drawText(batteryText, width - 150f * density, top + 16f * density, tinyPaint)

            if (mode == ControlMode.WHEEL) {
                drawWheel(canvas, accent)
            } else {
                drawReticle(canvas, accent)
            }

            tinyPaint.color = Color.parseColor("#6F6A61")
            canvas.drawText("AIRCONS / ${mode.number}", left, height - 28f * density, tinyPaint)
        }

        private fun drawReticle(canvas: Canvas, accent: Int) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = 19f * density
            val dx = frame.lookX * 92f * density
            val dy = frame.lookY * 92f * density
            val dotX = cx + dx
            val dotY = cy + dy

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(cx, cy, radius, paint)

            paint.strokeWidth = 1f * density
            paint.color = Color.argb(115, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(cx, cy, radius * 2.25f, paint)

            paint.color = Color.argb(170, 20, 19, 18)
            canvas.drawLine(cx - radius - 7f * density, cy, cx - radius + 2f * density, cy, paint)
            canvas.drawLine(cx + radius - 2f * density, cy, cx + radius + 7f * density, cy, paint)
            canvas.drawLine(cx, cy - radius - 7f * density, cx, cy - radius + 2f * density, paint)
            canvas.drawLine(cx, cy + radius - 2f * density, cx, cy + radius + 7f * density, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(70, 20, 19, 18)
            canvas.drawCircle(dotX, dotY, 3.4f * density, paint)
            paint.color = accent
            canvas.drawCircle(dotX, dotY, 1.8f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = Color.argb(75, 20, 19, 18)
            canvas.drawLine(cx, cy, dotX, dotY, paint)
        }

        private fun drawWheel(canvas: Canvas, accent: Int) {
            val cx = width / 2f
            val cy = height - 96f * density
            val radius = 80f * density
            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * density
            paint.color = Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawArc(rect, 200f, 140f, false, paint)

            paint.strokeWidth = 0.8f * density
            paint.color = Color.argb(55, 20, 19, 18)
            for (tick in -2..2) {
                val angle = Math.toRadians((270 + tick * 24).toDouble())
                val x1 = cx + cos(angle).toFloat() * (radius - 7f * density)
                val y1 = cy + sin(angle).toFloat() * (radius - 7f * density)
                val x2 = cx + cos(angle).toFloat() * (radius + 3f * density)
                val y2 = cy + sin(angle).toFloat() * (radius + 3f * density)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }

            val needle = Math.toRadians((270 + frame.steer * 52).toDouble())
            val nx = cx + cos(needle).toFloat() * (radius - 4f * density)
            val ny = cy + sin(needle).toFloat() * (radius - 4f * density)
            paint.strokeWidth = 2f * density
            paint.color = accent
            canvas.drawLine(cx, cy, nx, ny, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, 4f * density, paint)

            val barLeft = width - 46f * density
            val barTop = height * 0.35f
            val barHeight = height * 0.3f
            paint.color = Color.argb(45, 20, 19, 18)
            canvas.drawRoundRect(
                RectF(barLeft, barTop, barLeft + 4f * density, barTop + barHeight),
                2f * density,
                2f * density,
                paint
            )
            paint.color = accent
            val throttle = (frame.throttle + 1f) / 2f
            val fillHeight = barHeight * throttle
            canvas.drawRoundRect(
                RectF(
                    barLeft,
                    barTop + barHeight - fillHeight,
                    barLeft + 4f * density,
                    barTop + barHeight
                ),
                2f * density,
                2f * density,
                paint
            )
        }
    }
}
