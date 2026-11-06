package app.aircons

import android.app.Application
import app.aircons.core.PluginManager
import app.aircons.data.SettingsStore

class AirConsApp : Application() {
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        PluginManager.init(this)
    }

    companion object {
        lateinit var instance: AirConsApp
    }
}
