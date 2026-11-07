package app.aircons.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import app.aircons.data.MotionSample
import kotlin.math.abs

class PhoneMotionSource(
    context: Context,
    private val onSample: (MotionSample) -> Unit,
    private val onRate: (Int) -> Unit = {}
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? =
        manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var reference: FloatArray? = null
    private var lastTimestamp = 0L
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var rateStart = 0L
    private var rateCount = 0

    fun start(): Boolean {
        val active = sensor ?: return false
        reference = null
        lastTimestamp = 0L
        smoothYaw = 0f
        smoothPitch = 0f
        smoothRoll = 0f
        rateStart = 0L
        rateCount = 0
        return manager.registerListener(this, active, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        manager.unregisterListener(this)
    }

    fun recenter() {
        reference = null
        lastTimestamp = 0L
        smoothYaw = 0f
        smoothPitch = 0f
        smoothRoll = 0f
        rateStart = 0L
        rateCount = 0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != sensor?.type) return
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val current = floatArrayOf(orientation[0], orientation[1], orientation[2])
        val base = reference
        if (base == null) {
            reference = current
            return
        }
        val yaw = wrapDegrees(Math.toDegrees((current[0] - base[0]).toDouble()).toFloat())
        val pitch = Math.toDegrees((current[1] - base[1]).toDouble()).toFloat()
        val roll = Math.toDegrees((current[2] - base[2]).toDouble()).toFloat()

        val now = System.currentTimeMillis()
        val dt = if (lastTimestamp == 0L) 16L else (now - lastTimestamp).coerceIn(8L, 120L)
        lastTimestamp = now
        val alpha = (0.22f * (dt / 16f)).coerceIn(0.08f, 0.6f)
        smoothYaw += (yaw - smoothYaw) * alpha
        smoothPitch += (pitch - smoothPitch) * alpha
        smoothRoll += (roll - smoothRoll) * alpha

        if (abs(smoothYaw) < 0.02f) smoothYaw = 0f
        if (rateStart == 0L) rateStart = now
        rateCount++
        if (now - rateStart >= 1000L) {
            onRate(((rateCount * 1000L) / (now - rateStart)).toInt())
            rateStart = now
            rateCount = 0
        }
        onSample(
            MotionSample(
                yaw = smoothYaw,
                pitch = smoothPitch,
                roll = smoothRoll,
                timestampMs = now
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun wrapDegrees(value: Float): Float {
        var result = value
        while (result > 180f) result -= 360f
        while (result < -180f) result += 360f
        return result
    }

}
