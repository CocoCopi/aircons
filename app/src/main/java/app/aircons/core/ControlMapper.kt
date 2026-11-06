package app.aircons.core

import app.aircons.data.AppSettings
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.MotionSample
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

object ControlMapper {
    fun map(
        mode: ControlMode,
        sample: MotionSample,
        settings: AppSettings,
        trigger: Boolean
    ): ControlFrame {
        return when (mode) {
            ControlMode.HEAD -> {
                val x = axis(sample.yaw, 30f, settings)
                val y = axis(sample.pitch, 24f, settings).let { if (settings.invertPitch) -it else it }
                ControlFrame(
                    lookX = x,
                    lookY = y,
                    steer = 0f,
                    throttle = 0f,
                    fire = trigger,
                    active = true
                )
            }

            ControlMode.SIDEARM -> {
                val x = axis(sample.yaw, 22f, settings)
                val y = axis(sample.pitch, 17f, settings).let { if (settings.invertPitch) -it else it }
                ControlFrame(
                    lookX = x,
                    lookY = y,
                    steer = 0f,
                    throttle = 0f,
                    fire = trigger,
                    active = true
                )
            }

            ControlMode.WHEEL -> {
                var steer = axis(sample.roll, 28f, settings)
                var throttle = axis(sample.pitch, 24f, settings).let { if (settings.invertPitch) -it else it }
                if (settings.swapWheelAxes) {
                    val t = steer
                    steer = throttle
                    throttle = t
                }
                ControlFrame(
                    lookX = 0f,
                    lookY = 0f,
                    steer = steer,
                    throttle = throttle,
                    fire = trigger,
                    active = true
                )
            }
        }
    }

    private fun axis(value: Float, fullScale: Float, settings: AppSettings): Float {
        val normalized = (value / fullScale).coerceIn(-1f, 1f)
        val dz = settings.deadzone.coerceIn(0f, 0.6f)
        val afterDeadzone = if (abs(normalized) <= dz) {
            0f
        } else {
            ((abs(normalized) - dz) / (1f - dz)) * normalized.sign
        }
        val curved = afterDeadzone.sign * abs(afterDeadzone).toDouble().pow(1.18).toFloat()
        return (curved * settings.sensitivity).coerceIn(-1f, 1f)
    }
}
