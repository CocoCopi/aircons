package app.aircons.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import app.aircons.AirConsApp
import app.aircons.data.AppSettings
import app.aircons.data.ControlFrame
import app.aircons.data.ControlMode
import app.aircons.data.SessionBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AirConsAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: AirConsAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hud: HudOverlay? = null
    private var injector: GameInjector? = null
    private var hudJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        hud?.hide()
        injector = GameInjector(this)
        hud = HudOverlay(this)

        val settings = AirConsApp.instance.settings
        hudJob?.cancel()
        hudJob = scope.launch {
            combine(SessionBus.sessionActive, settings.state) { active, state ->
                active && state.hudEnabled
            }.distinctUntilChanged().collect { visible ->
                if (visible) hud?.show() else hud?.hide()
            }
        }

        scope.launch {
            while (isActive) {
                hud?.update(
                    frame = SessionBus.frame.value,
                    mode = SessionBus.mode.value,
                    link = SessionBus.link.value,
                    battery = SessionBus.battery.value,
                    pps = SessionBus.pps.value
                )
                delay(33)
            }
        }
    }

    fun inject(frame: ControlFrame, mode: ControlMode, settings: AppSettings) {
        injector?.push(frame, mode, settings)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        hud?.hide()
        hud = null
        injector = null
        instance = null
        super.onDestroy()
    }
}
