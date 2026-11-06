package app.aircons.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import app.aircons.data.AppSettings
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import kotlin.math.abs
import kotlin.math.min

class GameInjector(private val service: AccessibilityService) {
    private var gestureInFlight = false
    private var fireWasDown = false
    private var lastFireAt = 0L

    fun push(frame: ControlFrame, mode: ControlMode, settings: AppSettings) {
        if (!frame.active) return
        val now = System.currentTimeMillis()

        if (frame.fire) {
            val fireDelay = if (settings.autoFire) 85L else Long.MAX_VALUE
            if ((!fireWasDown || settings.autoFire) && now - lastFireAt >= fireDelay) {
                tapFire(settings)
                lastFireAt = now
            }
        }
        fireWasDown = frame.fire

        if (now - lastFireAt < 45L) return

        when (mode) {
            ControlMode.HEAD, ControlMode.SIDEARM -> {
                if (abs(frame.lookX) < 0.02f && abs(frame.lookY) < 0.02f) return
                drag(
                    frame.lookX,
                    -frame.lookY,
                    settings.lookAnchorX,
                    settings.lookAnchorY,
                    settings
                )
            }

            ControlMode.WHEEL -> {
                if (abs(frame.steer) < 0.02f && abs(frame.throttle) < 0.02f) return
                drag(
                    frame.steer,
                    -frame.throttle,
                    settings.steerAnchorX,
                    settings.steerAnchorY,
                    settings
                )
            }
        }
    }

    private fun drag(
        x: Float,
        y: Float,
        anchorX: Float,
        anchorY: Float,
        settings: AppSettings
    ) {
        if (gestureInFlight) return
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val radius = min(width, height) * 0.17f
        val startX = (anchorX * width).coerceIn(12f, width - 12f)
        val startY = (anchorY * height).coerceIn(12f, height - 12f)
        val endX = (startX + x * radius).coerceIn(4f, width - 4f)
        val endY = (startY + y * radius).coerceIn(4f, height - 4f)
        if (abs(endX - startX) < 2f && abs(endY - startY) < 2f) return

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 48L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        gestureInFlight = true
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }
            },
            null
        )
        if (!dispatched) {
            gestureInFlight = false
        }
    }

    private fun tapFire(settings: AppSettings) {
        val metrics = service.resources.displayMetrics
        val x = (settings.fireAnchorX * metrics.widthPixels).coerceIn(8f, metrics.widthPixels - 8f)
        val y = (settings.fireAnchorY * metrics.heightPixels).coerceIn(8f, metrics.heightPixels - 8f)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 38L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        gestureInFlight = true
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    gestureInFlight = false
                }
            },
            null
        )
        if (!dispatched) gestureInFlight = false
        if (settings.haptics) vibrate()
    }

    private fun vibrate() {
        runCatching {
            val vibrator = service.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
    }
}
