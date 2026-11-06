package app.aircons.core

import app.aircons.aap.AapClient
import app.aircons.data.MotionSample
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

class HeadTracker(
    private val onCalibrated: () -> Unit
) {
    private data class Neutral(val o1: Int, val o2: Int, val o3: Int)

    private val samples = ArrayDeque<Neutral>()
    private var neutral: Neutral? = null
    private var lastTimestamp = 0L

    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothH = 0f
    private var smoothV = 0f

    val isCalibrated: Boolean get() = neutral != null

    fun beginCalibration() {
        samples.clear()
        neutral = null
        smoothYaw = 0f
        smoothPitch = 0f
        smoothRoll = 0f
        smoothH = 0f
        smoothV = 0f
        lastTimestamp = 0L
    }

    fun process(raw: AapClient.RawMotion, smoothing: Float): MotionSample? {
        if (neutral == null) {
            samples.addLast(Neutral(raw.o1, raw.o2, raw.o3))
            if (samples.size < SAMPLE_TARGET) return null
            neutral = Neutral(
                o1 = samples.map { it.o1 }.average().roundToInt(),
                o2 = samples.map { it.o2 }.average().roundToInt(),
                o3 = samples.map { it.o3 }.average().roundToInt()
            )
            samples.clear()
            onCalibrated()
            return null
        }

        val n = neutral ?: return null
        val d1 = (raw.o1 - n.o1).toFloat()
        val d2 = (raw.o2 - n.o2).toFloat()
        val d3 = (raw.o3 - n.o3).toFloat()

        val rawPitch = ((d2 + d3) / 2f) / SCALE * 180f
        val rawYaw = ((d2 - d3) / 2f) / SCALE * 180f
        val rawRoll = d1 / SCALE * 180f

        val dt = if (lastTimestamp == 0L) 16L else (raw.timestampMs - lastTimestamp).coerceIn(8L, 120L)
        lastTimestamp = raw.timestampMs
        val alpha = alphaFor(smoothing, dt)

        smoothYaw += (rawYaw - smoothYaw) * alpha
        smoothPitch += (rawPitch - smoothPitch) * alpha
        smoothRoll += (rawRoll - smoothRoll) * alpha
        smoothH += ((raw.accelH / 1000f) - smoothH) * alpha
        smoothV += ((raw.accelV / 1000f) - smoothV) * alpha

        return MotionSample(
            yaw = smoothYaw,
            pitch = smoothPitch,
            roll = smoothRoll,
            accelH = smoothH,
            accelV = smoothV,
            timestampMs = raw.timestampMs
        )
    }

    private fun alphaFor(smoothing: Float, dtMs: Long): Float {
        val base = (1f - smoothing.coerceIn(0f, 0.92f)).coerceIn(0.08f, 1f)
        val timeScale = (dtMs / 16f).coerceIn(0.65f, 2.5f)
        return (base * timeScale).coerceIn(0.03f, 1f)
    }

    private fun List<Int>.average(): Double = sum().toDouble() / size

    fun tension(sample: MotionSample): Float {
        val speed = (abs(sample.yaw) / 40f + abs(sample.pitch) / 40f + abs(sample.roll) / 60f) / 3f
        return speed.coerceIn(0f, 1f)
    }

    companion object {
        private const val SAMPLE_TARGET = 18
        private const val SCALE = 32000f
    }
}
