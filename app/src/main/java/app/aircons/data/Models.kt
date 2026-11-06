package app.aircons.data

import kotlin.math.abs

enum class ControlMode(
    val key: String,
    val number: String,
    val title: String,
    val kicker: String,
    val summary: String,
    val hold: String,
    val accent: Long
) {
    HEAD(
        key = "head",
        number = "01",
        title = "Headline",
        kicker = "Wear both pods",
        summary = "Look with your head. Fire from the stem.",
        hold = "Wear both AirPods normally. Keep your phone in view, then zero your gaze.",
        accent = 0xFF0B5CFF
    ),
    SIDEARM(
        key = "sidearm",
        number = "02",
        title = "Sidearm",
        kicker = "One pod as a pistol",
        summary = "Point one pod. The stem is the trigger.",
        hold = "Hold one pod upright like a pistol, stem toward your thumb. Zero it on the target.",
        accent = 0xFFFF5A36
    ),
    WHEEL(
        key = "wheel",
        number = "03",
        title = "Wheel",
        kicker = "Two pods, two hands",
        summary = "Tilt both pods. Steer with the bank.",
        hold = "Hold a pod in each hand, like a wheel at ten and two. Zero with your hands level.",
        accent = 0xFF1F6F5C
    );

    companion object {
        fun fromKey(key: String?): ControlMode = entries.firstOrNull { it.key == key } ?: HEAD
    }
}

data class PodBattery(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val leftCharging: Boolean = false,
    val rightCharging: Boolean = false,
    val caseCharging: Boolean = false
) {
    val minimum: Int? get() = listOfNotNull(left, right).minOrNull()
}

data class EarState(val leftInEar: Boolean = true, val rightInEar: Boolean = true)

data class MotionSample(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val accelH: Float = 0f,
    val accelV: Float = 0f,
    val timestampMs: Long = 0L
) {
    companion object {
        val ZERO = MotionSample()
    }
}

data class ControlFrame(
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val steer: Float = 0f,
    val throttle: Float = 0f,
    val fire: Boolean = false,
    val active: Boolean = false
) {
    val magnitude: Float get() = maxOf(abs(lookX), abs(lookY), abs(steer), abs(throttle))

    companion object {
        val ZERO = ControlFrame()
    }
}

enum class LinkState { IDLE, CONNECTING, CONNECTED, STREAMING, ERROR }

data class StemEvent(
    val press: StemPress,
    val bud: Bud,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class StemPress { SINGLE, DOUBLE, TRIPLE, LONG }

enum class Bud { LEFT, RIGHT, UNKNOWN }

enum class CalibrationState { IDLE, COLLECTING, READY }

data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val file: String
)

data class PluginManifest(val version: Int, val plugins: List<PluginInfo>)

data class UpdateInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val publishedAt: Long = 0L
)
