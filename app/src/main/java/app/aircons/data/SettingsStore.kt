package app.aircons.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val targetAddress: String? = null,
    val targetName: String? = null,
    val modeKey: String = ControlMode.HEAD.key,
    val sensitivity: Float = 1f,
    val smoothing: Float = 0.28f,
    val deadzone: Float = 0.06f,
    val invertPitch: Boolean = false,
    val swapWheelAxes: Boolean = false,
    val autoFire: Boolean = false,
    val haptics: Boolean = true,
    val hudEnabled: Boolean = true,
    val lookAnchorX: Float = 0.78f,
    val lookAnchorY: Float = 0.64f,
    val steerAnchorX: Float = 0.22f,
    val steerAnchorY: Float = 0.74f,
    val fireAnchorX: Float = 0.86f,
    val fireAnchorY: Float = 0.86f,
    val injectionHz: Int = 24,
    val alternatePackets: Boolean = true,
    val phoneMotion: Boolean = false,
    val scriptsEnabled: Boolean = false,
    val activePluginId: String = "builtin",
    val checkUpdatesOnLaunch: Boolean = true
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aircons.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state = _state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
        return next
    }

    fun setTarget(address: String?, name: String?) = update {
        it.copy(targetAddress = address, targetName = name)
    }

    fun setMode(mode: ControlMode) = update { it.copy(modeKey = mode.key) }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        fun f(key: String, value: Float) = prefs.getFloat(key, value)
        fun b(key: String, value: Boolean) = prefs.getBoolean(key, value)
        fun s(key: String, value: String?) = prefs.getString(key, value)
        return AppSettings(
            targetAddress = s("targetAddress", null),
            targetName = s("targetName", null),
            modeKey = s("modeKey", defaults.modeKey) ?: defaults.modeKey,
            sensitivity = f("sensitivity", defaults.sensitivity),
            smoothing = f("smoothing", defaults.smoothing),
            deadzone = f("deadzone", defaults.deadzone),
            invertPitch = b("invertPitch", defaults.invertPitch),
            swapWheelAxes = b("swapWheelAxes", defaults.swapWheelAxes),
            autoFire = b("autoFire", defaults.autoFire),
            haptics = b("haptics", defaults.haptics),
            hudEnabled = b("hudEnabled", defaults.hudEnabled),
            lookAnchorX = f("lookAnchorX", defaults.lookAnchorX),
            lookAnchorY = f("lookAnchorY", defaults.lookAnchorY),
            steerAnchorX = f("steerAnchorX", defaults.steerAnchorX),
            steerAnchorY = f("steerAnchorY", defaults.steerAnchorY),
            fireAnchorX = f("fireAnchorX", defaults.fireAnchorX),
            fireAnchorY = f("fireAnchorY", defaults.fireAnchorY),
            injectionHz = prefs.getInt("injectionHz", defaults.injectionHz),
            alternatePackets = b("alternatePackets", defaults.alternatePackets),
            phoneMotion = b("phoneMotion", defaults.phoneMotion),
            scriptsEnabled = b("scriptsEnabled", defaults.scriptsEnabled),
            activePluginId = s("activePluginId", defaults.activePluginId)
                ?: defaults.activePluginId,
            checkUpdatesOnLaunch = b("checkUpdatesOnLaunch", defaults.checkUpdatesOnLaunch)
        )
    }

    private fun persist(value: AppSettings) {
        prefs.edit()
            .putString("targetAddress", value.targetAddress)
            .putString("targetName", value.targetName)
            .putString("modeKey", value.modeKey)
            .putFloat("sensitivity", value.sensitivity)
            .putFloat("smoothing", value.smoothing)
            .putFloat("deadzone", value.deadzone)
            .putBoolean("invertPitch", value.invertPitch)
            .putBoolean("swapWheelAxes", value.swapWheelAxes)
            .putBoolean("autoFire", value.autoFire)
            .putBoolean("haptics", value.haptics)
            .putBoolean("hudEnabled", value.hudEnabled)
            .putFloat("lookAnchorX", value.lookAnchorX)
            .putFloat("lookAnchorY", value.lookAnchorY)
            .putFloat("steerAnchorX", value.steerAnchorX)
            .putFloat("steerAnchorY", value.steerAnchorY)
            .putFloat("fireAnchorX", value.fireAnchorX)
            .putFloat("fireAnchorY", value.fireAnchorY)
            .putInt("injectionHz", value.injectionHz)
            .putBoolean("alternatePackets", value.alternatePackets)
            .putBoolean("phoneMotion", value.phoneMotion)
            .putBoolean("scriptsEnabled", value.scriptsEnabled)
            .putString("activePluginId", value.activePluginId)
            .putBoolean("checkUpdatesOnLaunch", value.checkUpdatesOnLaunch)
            .apply()
    }
}
