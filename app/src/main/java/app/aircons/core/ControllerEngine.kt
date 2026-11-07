package app.aircons.core

import app.aircons.aap.AapClient
import app.aircons.data.CalibrationState
import app.aircons.data.ControlMode
import app.aircons.data.MotionSample
import app.aircons.data.SessionBus
import app.aircons.data.SettingsStore
import app.aircons.data.StemEvent
import app.aircons.data.StemPress

class ControllerEngine(
    private val settingsStore: SettingsStore,
    private val onModeChanged: (ControlMode) -> Unit = {}
) {
    var mode: ControlMode = ControlMode.HEAD
        private set

    private var triggerUntil = 0L

    private val tracker = HeadTracker {
        SessionBus.setCalibration(CalibrationState.READY)
    }

    fun setMode(value: ControlMode) {
        mode = value
        SessionBus.setMode(value)
        SessionBus.setFrame(app.aircons.data.ControlFrame.ZERO)
    }

    fun onRawMotion(raw: AapClient.RawMotion) {
        SessionBus.setCalibration(
            if (tracker.isCalibrated) CalibrationState.READY else CalibrationState.COLLECTING
        )
        val settings = settingsStore.state.value
        val sample = tracker.process(raw, settings.smoothing) ?: return
        applySample(sample)
    }

    fun onPhoneMotion(sample: MotionSample) {
        SessionBus.setCalibration(CalibrationState.READY)
        applySample(sample)
    }

    private fun applySample(sample: MotionSample) {
        val settings = settingsStore.state.value
        SessionBus.setMotion(sample)

        val trigger = System.currentTimeMillis() < triggerUntil
        var frame = ControlMapper.map(mode, sample, settings, trigger)

        if (settings.scriptsEnabled) {
            val scripted = PluginManager.map(
                mode = mode,
                yaw = sample.yaw,
                pitch = sample.pitch,
                roll = sample.roll,
                accelH = sample.accelH,
                accelV = sample.accelV,
                trigger = trigger
            )
            if (scripted != null) frame = scripted
            SessionBus.setScript(PluginManager.activeName.value)
        } else {
            SessionBus.setScript("built-in")
        }

        SessionBus.setFrame(frame)
    }

    fun onStem(event: StemEvent) {
        SessionBus.setStem(event)
        when (event.press) {
            StemPress.SINGLE -> {
                val duration = if (settingsStore.state.value.autoFire) 900L else 160L
                triggerUntil = System.currentTimeMillis() + duration
            }

            StemPress.DOUBLE, StemPress.LONG -> recenter()

            StemPress.TRIPLE -> {
                val next = ControlMode.entries[(mode.ordinal + 1) % ControlMode.entries.size]
                settingsStore.setMode(next)
                mode = next
                SessionBus.setMode(next)
                onModeChanged(next)
            }
        }
    }

    fun recenter() {
        tracker.beginCalibration()
        SessionBus.setCalibration(CalibrationState.COLLECTING)
        SessionBus.setMotion(MotionSample.ZERO)
    }
}
